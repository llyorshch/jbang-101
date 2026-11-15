///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS com.google.cloud.tools:jib-core:0.28.2
//RUNTIME_OPTIONS --sun-misc-unsafe-memory-access=allow

import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LogEvent;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.TarImage;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/*
 * Builds an OCI image of the clients app with Jib: no Dockerfile, no Docker daemon.
 * Run it from clients/: ./Containerize.java --help
 */

/**
 * eclipse-temurin:25-jre, pinned to its multi-platform index digest. To refresh it, read the
 * digest of the tag and replace the constant, for example:
 * {@code docker buildx imagetools inspect eclipse-temurin:25-jre} (the "Digest:" line), or
 * {@code crane digest eclipse-temurin:25-jre}.
 */
static final String DEFAULT_BASE =
    "eclipse-temurin:25-jre@sha256:ecdc0adb37bde1a281c4d245a3cd535004b1fc8322d560f5b09463b248c81df0";

static final Path EXPORT_DIR = Path.of("build", "export");
static final Path EXPORTED_JAR = EXPORT_DIR.resolve("ClientsApp.jar");
static final Path IMAGE_INPUT_DIR = Path.of("build", "image-input");
static final Path TARBALL = Path.of("build", "clients-image.tar");
static final Path NATIVE_TARBALL = Path.of("build", "clients-native-image.tar");
static final Path NATIVE_BINARY = Path.of("build", "native-linux", "clients");
static final AbsoluteUnixPath APP_DIR = AbsoluteUnixPath.get("/app");
static final AbsoluteUnixPath DATA_DIR = AbsoluteUnixPath.get("/data");
static final String USER = "10001:10001";
static final Set<Platform> PUSH_PLATFORMS =
    Set.of(new Platform("amd64", "linux"), new Platform("arm64", "linux"));

static final String USAGE =
    """
    Usage: ./Containerize.java [options]

    Builds the clients app image with Jib (no Dockerfile, no Docker daemon).

    Options:
      --native             Package the Linux native executable instead of the JVM app: runs
                           ./Native.java --linux first (needs GRAALVM_HOME and docker or podman).
                           The image has a single platform, the binary's linux architecture.
      --base <ref>         Base image (default: %s)
      --tag <name>         Image name inside the tarball (default: clients:<git-short-sha>,
                           with --native clients:<git-short-sha>-native)
      --platform <os/arch> Tarball platform (default: host, e.g. linux/arm64; with --native it
                           must match the binary, which is the default)
      --push <ref>         Push a linux/amd64 + linux/arm64 image to <ref> instead of writing a
                           tarball (with --native: only the binary's platform). <ref> must name
                           its registry host (an approved registry only). Credentials come from
                           the Docker config / credential helpers. Asks for confirmation.
      --verbose            Show Jib and JBang output and stack traces
      -h, --help           Show this help

    Output (default): %s (with --native: %s)
    """
        .formatted(DEFAULT_BASE, TARBALL, NATIVE_TARBALL);

/** Parsed command line. */
record Options(
    String base, String tag, Platform platform, String push, boolean nativeImage, boolean verbose) {}

/** Signals a user-facing failure: printed without a stack trace unless --verbose. */
static final class Failure extends RuntimeException {
  Failure(String message) {
    super(message);
  }

  Failure(String message, Throwable cause) {
    super(message, cause);
  }
}

void main(String[] args) {
  var verbose = List.of(args).contains("--verbose");
  try {
    var options = parse(args);
    if (options == null) {
      System.out.print(USAGE);
      return;
    }
    run(options);
  } catch (Failure e) {
    System.err.println("error: " + e.getMessage());
    if (verbose) {
      e.printStackTrace();
    }
    System.exit(1);
  } catch (Exception e) {
    System.err.println("error: unexpected failure (" + e.getClass().getSimpleName() + ")");
    if (verbose) {
      e.printStackTrace();
    } else {
      System.err.println("Re-run with --verbose for details.");
    }
    System.exit(1);
  }
}

/** Returns null when help was requested. */
Options parse(String[] args) {
  String base = DEFAULT_BASE;
  String tag = null;
  Platform platform = null;
  String push = null;
  var nativeImage = false;
  var verbose = false;
  for (var i = 0; i < args.length; i++) {
    switch (args[i]) {
      case "-h", "--help" -> {
        return null;
      }
      case "--verbose" -> verbose = true;
      case "--native" -> nativeImage = true;
      case "--base" -> base = value(args, ++i, "--base");
      case "--tag" -> tag = value(args, ++i, "--tag");
      case "--push" -> push = value(args, ++i, "--push");
      case "--platform" -> platform = platform(value(args, ++i, "--platform"));
      default -> throw new Failure("unknown argument '" + args[i] + "' (see --help)");
    }
  }
  if (push != null && tag != null) {
    throw new Failure("--tag only applies to the tarball; with --push the reference is the name");
  }
  return new Options(base, tag, platform, push, nativeImage, verbose);
}

String value(String[] args, int index, String flag) {
  if (index >= args.length || args[index].startsWith("--")) {
    throw new Failure(flag + " needs a value (see --help)");
  }
  return args[index];
}

Platform platform(String value) {
  var parts = value.split("/");
  if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
    throw new Failure("--platform must look like linux/amd64, got '" + value + "'");
  }
  return new Platform(parts[1], parts[0]);
}

Platform hostPlatform() {
  var arch = System.getProperty("os.arch");
  return new Platform(arch.equals("aarch64") || arch.equals("arm64") ? "arm64" : "amd64", "linux");
}

void run(Options options) throws Exception {
  if (!Files.isRegularFile(Path.of("ClientsApp.java"))) {
    throw new Failure("run this from the clients/ directory (ClientsApp.java not found)");
  }
  var base = reference(options.base(), "--base");
  var git = GitInfo.read();
  var target = options.push() == null ? null : pushTarget(options.push());
  if (options.nativeImage()) {
    if (options.tag() != null) {
      reference(options.tag(), "--tag");
    }
    buildLinuxNative(options.verbose());
  } else if (target != null && !confirmPush(target, PUSH_PLATFORMS)) {
    throw new Failure("aborted, nothing pushed");
  }

  var builder = Jib.from(withDockerCredentials(base, options.verbose()));
  Platform platform;
  Set<Platform> pushPlatforms;
  String defaultName;
  Path tarball;
  if (options.nativeImage()) {
    platform = nativeBinaryPlatform();
    if (options.platform() != null && !samePlatform(options.platform(), platform)) {
      throw new Failure(
          "--platform " + describe(options.platform()) + " doesn't match the native binary ("
              + describe(platform) + "); a Linux native executable runs on one architecture only");
    }
    pushPlatforms = Set.of(platform);
    defaultName = "clients:" + git.shortSha() + "-native";
    tarball = NATIVE_TARBALL;
    configureNative(builder);
    // Asked only now, once the binary's platform is known, and still before anything is pushed.
    if (target != null && !confirmPush(target, pushPlatforms)) {
      throw new Failure("aborted, nothing pushed");
    }
  } else {
    platform = options.platform() != null ? options.platform() : hostPlatform();
    pushPlatforms = PUSH_PLATFORMS;
    defaultName = "clients:" + git.shortSha();
    tarball = TARBALL;
    exportPortableJar(options.verbose());
    configureJvm(builder, normalizedJar(git.commitTime()), libraryJars());
  }
  builder
      .setEnvironment(
          Map.of("SERVER_ADDRESS", "0.0.0.0", "PORT", "8080", "CLIENTS_DB", "/data/clients"))
      .setUser(USER)
      .addVolume(DATA_DIR)
      .addExposedPort(Port.tcp(8080))
      .setLabels(labels(git));
  // Creation time stays at Jib's default (the epoch), so equal inputs give equal digests.

  Containerizer containerizer;
  if (options.push() == null) {
    var name = options.tag() != null ? options.tag() : defaultName;
    builder.setPlatforms(Set.of(platform));
    containerizer =
        Containerizer.to(TarImage.at(tarball.toAbsolutePath()).named(reference(name, "--tag")));
    System.out.printf("Building %s (%s) into %s%n", name, describe(platform), tarball);
  } else {
    builder.setPlatforms(pushPlatforms);
    containerizer =
        Containerizer.to(withDockerCredentials(target, options.verbose()));
  }
  containerizer
      .setToolName("clients-containerize")
      .setApplicationLayersCache(Path.of("build", "jib-cache").toAbsolutePath())
      .addEventHandler(LogEvent.class, jibLogger(options.verbose()));

  JibContainer result;
  try {
    result = builder.containerize(containerizer);
  } catch (ExecutionException e) {
    var cause = e.getCause() == null ? e : e.getCause();
    throw new Failure("image build failed: " + describe(cause), e);
  }
  System.out.println("Image digest: " + result.getDigest());
  System.out.println("Image id:     " + result.getImageId());
  System.out.println("Revision:     " + git.sha());
}

/** JVM image: the dependency jars and the app jar, run by the base image's java. */
void configureJvm(JibContainerBuilder builder, Path appJar, List<Path> libs) throws IOException {
  builder
      .addFileEntriesLayer(dependenciesLayer(libs))
      .addFileEntriesLayer(applicationLayer(appJar))
      .setEntrypoint(
          "java",
          "-XX:MaxRAMPercentage=75",
          "-Djava.awt.headless=true",
          "-jar",
          APP_DIR.resolve("ClientsApp.jar").toString());
}

/**
 * Native image: one layer with the executable, the JDK libraries native-image emitted next to it
 * (AWT, for the case diagrams) and lib/ with fontconfig.bfc when there is one. ClientsApp makes
 * /app the java.home; Java2D only uses the system fontconfig when /app/lib exists, so the
 * directory is always added. The base image provides glibc, fontconfig, FreeType and the fonts.
 */
void configureNative(JibContainerBuilder builder) throws IOException {
  var dir = NATIVE_BINARY.getParent();
  var layer = FileEntriesLayer.builder().setName("application");
  layer.addEntry(
      NATIVE_BINARY,
      APP_DIR.resolve("clients"),
      FilePermissions.fromOctalString("755"));
  try (Stream<Path> files = Files.list(dir)) {
    for (var library : files.filter(p -> p.getFileName().toString().endsWith(".so")).sorted().toList()) {
      layer.addEntry(library, APP_DIR.resolve(library.getFileName().toString()));
    }
  }
  var libDir = dir.resolve("lib");
  if (!Files.isDirectory(libDir)) {
    throw new Failure(libDir + " is missing; rebuild with ./Native.java --linux");
  }
  layer.addEntry(libDir, APP_DIR.resolve("lib"));
  var fontConfig = libDir.resolve("fontconfig.bfc");
  if (Files.isRegularFile(fontConfig)) {
    layer.addEntry(fontConfig, APP_DIR.resolve("lib").resolve("fontconfig.bfc"));
  }
  layer.addEntry(dataDirEntry());
  builder
      .addFileEntriesLayer(layer.build())
      .setEntrypoint(APP_DIR.resolve("clients").toString(), "-Djava.awt.headless=true");
}

/** Runs ./Native.java --linux, which compiles the executable in a GraalVM builder container. */
void buildLinuxNative(boolean verbose) throws InterruptedException {
  var graal = System.getenv("GRAALVM_HOME");
  if (graal == null || graal.isBlank()) {
    throw new Failure(
        "--native needs GRAALVM_HOME set to a GraalVM 25 install (Native.java runs Spring AOT with it)");
  }
  var command = new ArrayList<>(List.of("jbang", "Native.java", "--linux"));
  if (verbose) {
    command.add("--verbose");
  }
  int exit;
  try {
    exit = new ProcessBuilder(command).inheritIO().start().waitFor();
  } catch (IOException e) {
    throw new Failure("jbang not found on the PATH or not executable", e);
  }
  if (exit != 0 || !Files.isRegularFile(NATIVE_BINARY)) {
    throw new Failure("./Native.java --linux failed (exit " + exit + ")");
  }
}

/** The binary's architecture, from its ELF header (e_machine), so the image can't mislabel it. */
Platform nativeBinaryPlatform() throws IOException {
  byte[] header;
  try (var in = Files.newInputStream(NATIVE_BINARY)) {
    header = in.readNBytes(20);
  }
  var elf = header.length == 20 && header[0] == 0x7f && header[1] == 'E' && header[2] == 'L' && header[3] == 'F';
  if (!elf) {
    throw new Failure(NATIVE_BINARY + " is not a Linux (ELF) executable");
  }
  var machine = (header[18] & 0xff) | (header[19] & 0xff) << 8; // little-endian on both targets
  return switch (machine) {
    case 0x3e -> new Platform("amd64", "linux");
    case 0xb7 -> new Platform("arm64", "linux");
    default -> throw new Failure(NATIVE_BINARY + " has an unsupported architecture (ELF machine " + machine + ")");
  };
}

boolean samePlatform(Platform a, Platform b) {
  return a.getOs().equals(b.getOs()) && a.getArchitecture().equals(b.getArchitecture());
}

String describe(Platform platform) {
  return platform.getOs() + "/" + platform.getArchitecture();
}

/** Asks before anything leaves the machine; only an explicit "y" continues. */
boolean confirmPush(ImageReference target, Set<Platform> platforms) throws IOException {
  var names = platforms.stream().map(this::describe).sorted().toList();
  System.out.printf("About to PUSH %s to:%n  %s%n", String.join(" + ", names), target);
  if (platforms.size() == 1) {
    System.out.println("This is a single-platform image (a native executable runs on one architecture).");
  }
  System.out.print("Only push to a company-approved registry. Type 'y' to continue: ");
  System.out.flush();
  var answer = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
  return answer != null && answer.trim().equals("y");
}

/** Step 1: JBang builds the app jar and copies its dependencies into build/export/lib. */
void exportPortableJar(boolean verbose) throws IOException, InterruptedException {
  System.out.println("Exporting ClientsApp.java with JBang...");
  var command =
      List.of("jbang", "export", "portable", "--force", "-O", EXPORTED_JAR.toString(), "ClientsApp.java");
  var process = new ProcessBuilder(command).redirectErrorStream(true).start();
  var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
  var exit = process.waitFor();
  if (verbose || exit != 0) {
    System.out.print(output);
  }
  if (exit != 0 || !Files.isRegularFile(EXPORTED_JAR)) {
    throw new Failure("jbang export failed (exit " + exit + ")");
  }
}

/**
 * JBang stamps jar entries with the build time. Rewrites the jar with every entry at the commit
 * time, so an unchanged app produces a byte-identical layer and image.
 */
Path normalizedJar(Instant entryTime) throws IOException {
  Files.createDirectories(IMAGE_INPUT_DIR);
  var target = IMAGE_INPUT_DIR.resolve("ClientsApp.jar");
  var time = entryTime.toEpochMilli();
  try (var source = new ZipFile(EXPORTED_JAR.toFile());
      var out = new ZipOutputStream(Files.newOutputStream(target))) {
    for (var entries = source.entries(); entries.hasMoreElements(); ) {
      var original = entries.nextElement();
      var copy = new ZipEntry(original.getName());
      copy.setTime(time);
      out.putNextEntry(copy);
      try (var in = source.getInputStream(original)) {
        in.transferTo(out);
      }
      out.closeEntry();
    }
  }
  return target;
}

List<Path> libraryJars() throws IOException {
  try (Stream<Path> files = Files.list(EXPORT_DIR.resolve("lib"))) {
    var jars = files.filter(p -> p.toString().endsWith(".jar")).sorted().toList();
    if (jars.isEmpty()) {
      throw new Failure("no dependency jars found in " + EXPORT_DIR.resolve("lib"));
    }
    return jars;
  }
}

/** Layer 1: third-party jars, which rarely change. */
FileEntriesLayer dependenciesLayer(List<Path> libs) {
  var layer = FileEntriesLayer.builder().setName("dependencies");
  libs.forEach(jar -> layer.addEntry(jar, APP_DIR.resolve("lib").resolve(jar.getFileName().toString())));
  return layer.build();
}

/** Layer 2: the app jar, plus an empty /data owned by the runtime user. */
FileEntriesLayer applicationLayer(Path appJar) throws IOException {
  return FileEntriesLayer.builder()
      .setName("application")
      .addEntry(appJar, APP_DIR.resolve("ClientsApp.jar"))
      .addEntry(dataDirEntry())
      .build();
}

/** An empty /data directory owned by the runtime user. */
FileEntry dataDirEntry() throws IOException {
  var emptyDir = IMAGE_INPUT_DIR.resolve("data");
  Files.createDirectories(emptyDir);
  try (Stream<Path> stale = Files.list(emptyDir)) {
    if (stale.findAny().isPresent()) {
      throw new Failure(emptyDir + " must be empty");
    }
  }
  return new FileEntry(
      emptyDir,
      DATA_DIR,
      FilePermissions.fromOctalString("750"),
      FileEntriesLayer.DEFAULT_MODIFICATION_TIME,
      USER);
}

Map<String, String> labels(GitInfo git) {
  var labels = new LinkedHashMap<String, String>();
  // Also overrides the description/version labels inherited from the base image.
  labels.put("org.opencontainers.image.title", "clients");
  labels.put(
      "org.opencontainers.image.description",
      "Client onboarding/offboarding sample (Spring Boot + Flowable CMMN), synthetic data only");
  labels.put("org.opencontainers.image.version", git.shortSha());
  labels.put("org.opencontainers.image.source", git.source());
  labels.put("org.opencontainers.image.revision", git.sha());
  labels.put("org.opencontainers.image.created", git.commitTime().toString());
  return labels;
}

ImageReference reference(String value, String flag) {
  try {
    return ImageReference.parse(value);
  } catch (InvalidImageReferenceException e) {
    throw new Failure(flag + " is not a valid image reference: '" + value + "'");
  }
}

/** A push target must name its registry explicitly, so nothing goes to Docker Hub by default. */
ImageReference pushTarget(String value) {
  var firstComponent = value.split("/", 2)[0];
  var namesRegistry =
      value.contains("/") && (firstComponent.contains(".") || firstComponent.contains(":"));
  if (!namesRegistry) {
    throw new Failure(
        "--push needs a fully qualified reference with a registry host, e.g."
            + " <registry-host>/clients:<tag>");
  }
  return reference(value, "--push");
}

/** Credentials only from the Docker config file and credential helpers; nothing is logged. */
RegistryImage withDockerCredentials(ImageReference reference, boolean verbose) {
  var factory =
      CredentialRetrieverFactory.forImage(
          reference, event -> {
            if (verbose) {
              System.out.println("[credentials] " + event.getMessage());
            }
          });
  return RegistryImage.named(reference)
      .addCredentialRetriever(factory.dockerConfig())
      .addCredentialRetriever(factory.wellKnownCredentialHelpers());
}

Consumer<LogEvent> jibLogger(boolean verbose) {
  return event -> {
    var level = event.getLevel();
    if (level == LogEvent.Level.ERROR || level == LogEvent.Level.WARN) {
      System.err.println("[jib] " + event.getMessage());
    } else if (verbose
        || level == LogEvent.Level.LIFECYCLE && !event.getMessage().isBlank()) {
      System.out.println("[jib] " + event.getMessage());
    }
  };
}

String describe(Throwable error) {
  var message = error.getMessage();
  return error.getClass().getSimpleName() + (message == null ? "" : ": " + message.lines().findFirst().orElse(""));
}

/** Revision metadata for the labels and the default tag. */
record GitInfo(String sha, String shortSha, Instant commitTime, String source) {

  static GitInfo read() {
    var sha = git("rev-parse", "HEAD");
    if (sha == null) {
      throw new Failure("git metadata unavailable: run inside the repository");
    }
    var time = Instant.ofEpochSecond(Long.parseLong(git("log", "-1", "--format=%ct")));
    var shortSha = git("rev-parse", "--short", "HEAD");
    return new GitInfo(sha, shortSha, time, withoutUserInfo(git("config", "--get", "remote.origin.url")));
  }

  /** Never copy credentials embedded in a remote URL (https://user:token@host/...) into a label. */
  private static String withoutUserInfo(String remote) {
    if (remote == null) {
      return "unknown";
    }
    return remote.replaceFirst("^(https?://)[^@/]*@", "$1");
  }

  private static String git(String... args) {
    try {
      var command = new ArrayList<String>();
      command.add("git");
      command.addAll(List.of(args));
      var process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
      var out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
      return process.waitFor() == 0 && !out.isEmpty() ? out : null;
    } catch (IOException e) {
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
  }
}
