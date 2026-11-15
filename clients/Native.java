///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/*
 * Builds a GraalVM native executable of the clients app (Spring AOT + native-image).
 * Run it from clients/: ./Native.java --help
 */

static final Path EXPORT_DIR = Path.of("build", "export");
static final Path EXPORTED_JAR = EXPORT_DIR.resolve("ClientsApp.jar");
static final Path AOT_DIR = Path.of("build", "aot");
static final Path AOT_SOURCES = AOT_DIR.resolve("sources");
static final Path AOT_RESOURCES = AOT_DIR.resolve("resources");
static final Path AOT_CLASSES = AOT_DIR.resolve("classes");
static final Path METADATA_DIR = Path.of("native");
static final Path METADATA_FILE = METADATA_DIR.resolve("reachability-metadata.json");
static final Path AGENT_LOG = Path.of("build", "agent-run.log");
static final Path BINARY = Path.of("build", "native", "clients");
static final Path LINUX_BINARY = Path.of("build", "native-linux", "clients");
static final Path LINUX_METADATA_DIR = METADATA_DIR.resolve("linux");
/**
 * GraalVM native-image builder for --linux, pinned to its multi-platform index digest
 * (linux/amd64 + linux/arm64). To refresh it, read the digest of the tag and replace the constant:
 * {@code docker buildx imagetools inspect ghcr.io/graalvm/native-image-community:25} (the "Digest:"
 * line), or {@code crane digest ghcr.io/graalvm/native-image-community:25}.
 */
static final String LINUX_BUILDER =
    "ghcr.io/graalvm/native-image-community:25@sha256:0d936f32bb8acb5bc60c41b33e05f064d7a6aaf36b726538296c54949bd4a3c0";
/**
 * The runtime base image of the native container image, used by --collect --linux because it has
 * the fontconfig, FreeType and fonts that Java2D needs (the builder image has none). Keep it equal
 * to DEFAULT_BASE in Containerize.java.
 */
static final String LINUX_RUNTIME_BASE =
    "eclipse-temurin:25-jre@sha256:ecdc0adb37bde1a281c4d245a3cd535004b1fc8322d560f5b09463b248c81df0";
static final String CONTAINER_WORKDIR = "/work";
static final String BUILDER_MOUNT = "/graalvm-image";
static final String MAIN_CLASS = "app.ClientsApp";
static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

static final String USAGE =
    """
    Usage: ./Native.java [options]

    Builds a GraalVM native executable of the clients app (Spring AOT + native-image).
    GraalVM comes from GRAALVM_HOME, or from JAVA_HOME when it contains bin/native-image.

    Options:
      --collect     Re-record the reachability metadata in %s with the tracing agent
                    (runs clients.hurl against the app; needs hurl on the PATH)
      --linux       Build a Linux executable for the container runtime's architecture: export
                    and AOT run here, native-image runs in a pinned GraalVM builder container
                    (needs docker or podman; no network inside the container). Uses the
                    metadata in %s plus the Linux-only additions in %s.
                    With --collect, records %s instead, running the app under the agent
                    in the builder container (hurl runs here against a loopback port).
      --skip-build  Stop after the AOT processing (and --collect), skip native-image
      --verbose     Show JBang, Spring AOT and javac output and stack traces
      -h, --help    Show this help

    Output: %s (with --linux: %s)
    """
        .formatted(METADATA_DIR, METADATA_DIR, LINUX_METADATA_DIR, LINUX_METADATA_DIR, BINARY, LINUX_BINARY);

/** Parsed command line. */
record Options(boolean collect, boolean linux, boolean skipBuild, boolean verbose) {}

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
  var collect = false;
  var linux = false;
  var skipBuild = false;
  var verbose = false;
  for (var arg : args) {
    switch (arg) {
      case "-h", "--help" -> {
        return null;
      }
      case "--collect" -> collect = true;
      case "--linux" -> linux = true;
      case "--skip-build" -> skipBuild = true;
      case "--verbose" -> verbose = true;
      default -> throw new Failure("unknown argument '" + arg + "' (see --help)");
    }
  }
  return new Options(collect, linux, skipBuild, verbose);
}

void run(Options options) throws Exception {
  if (!Files.isRegularFile(Path.of("ClientsApp.java"))) {
    throw new Failure("run this from the clients/ directory (ClientsApp.java not found)");
  }
  var bin = graalBin();
  exportPortableJar(options.verbose());
  var classpath = aotProcess(bin, options.verbose());
  if (options.collect() && options.linux()) {
    collectLinuxMetadata(classpath);
  } else if (options.collect()) {
    collectMetadata(bin, classpath);
  } else if (!Files.isRegularFile(METADATA_FILE)) {
    throw new Failure("no native metadata; run ./Native.java --collect first");
  }
  if (options.skipBuild()) {
    return;
  }
  if (options.linux()) {
    buildLinuxNative(classpath);
  } else {
    buildNative(bin, classpath);
  }
}

/** GRAALVM_HOME wins; JAVA_HOME is accepted only when it is a GraalVM (has native-image). */
Path graalBin() {
  var graal = System.getenv("GRAALVM_HOME");
  if (graal != null && !graal.isBlank()) {
    var bin = Path.of(graal, "bin");
    if (!Files.isExecutable(bin.resolve("native-image"))) {
      throw new Failure("GRAALVM_HOME has no bin/native-image; point it at a GraalVM 25 install");
    }
    return bin;
  }
  var java = System.getenv("JAVA_HOME");
  if (java != null && !java.isBlank() && Files.isExecutable(Path.of(java, "bin", "native-image"))) {
    return Path.of(java, "bin");
  }
  throw new Failure(
      "GraalVM not found: set GRAALVM_HOME to a GraalVM 25 install, e.g."
          + " export GRAALVM_HOME=/path/to/graalvm-25 (with SDKMAN: $(sdk home java <graalce-version>))");
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
 * Steps 2-3: Spring AOT generates sources, resources and proxy classes; the GraalVM javac compiles
 * the sources. Returns the runtime classpath used by the agent run and native-image.
 */
List<String> aotProcess(Path bin, boolean verbose) throws IOException, InterruptedException {
  System.out.println("Running Spring AOT processing...");
  deleteTree(AOT_DIR);
  for (var dir : List.of(AOT_SOURCES, AOT_RESOURCES, AOT_CLASSES)) {
    Files.createDirectories(dir);
  }
  var appClasspath = new ArrayList<String>();
  appClasspath.add(EXPORTED_JAR.toString());
  libraryJars().forEach(jar -> appClasspath.add(jar.toString()));
  var compileClasspath = String.join(File.pathSeparator, appClasspath);

  runQuiet(
      List.of(
          bin.resolve("java").toString(), "-cp", compileClasspath,
          "org.springframework.boot.SpringApplicationAotProcessor", MAIN_CLASS,
          AOT_SOURCES.toString(), AOT_RESOURCES.toString(), AOT_CLASSES.toString(),
          "com.flywire.sample", "clients"),
      verbose, "Spring AOT processing");

  System.out.println("Compiling AOT sources...");
  List<String> sources;
  try (Stream<Path> files = Files.walk(AOT_SOURCES)) {
    sources = files.filter(p -> p.toString().endsWith(".java")).map(Path::toString).sorted().toList();
  }
  if (sources.isEmpty()) {
    throw new Failure("Spring AOT generated no sources in " + AOT_SOURCES);
  }
  var argfile = AOT_DIR.resolve("sources.txt");
  Files.write(argfile, sources, StandardCharsets.UTF_8);
  runQuiet(
      List.of(
          bin.resolve("javac").toString(), "-parameters", "--release", "25", "-proc:none", "-nowarn",
          "-d", AOT_CLASSES.toString(), "-cp", compileClasspath, "@" + argfile),
      verbose, "javac of AOT sources");

  var runtime = new ArrayList<String>(List.of(AOT_CLASSES.toString(), AOT_RESOURCES.toString()));
  runtime.addAll(appClasspath);
  return List.copyOf(runtime);
}

/**
 * Step 4 (--collect): runs the app under the native-image tracing agent and drives it with
 * clients.hurl, which only sends synthetic data, so no real client data ends up in the metadata.
 */
void collectMetadata(Path bin, List<String> classpath) throws IOException, InterruptedException {
  System.out.println("Collecting native metadata with the tracing agent...");
  var dbDir = Files.createTempDirectory("clients-native-");
  var port = freeLoopbackPort();
  deleteTree(METADATA_DIR);
  Files.createDirectories(METADATA_DIR);
  var builder =
      new ProcessBuilder(
              bin.resolve("java").toString(), "-Dspring.aot.enabled=true", "-Djava.awt.headless=true",
              "-agentlib:native-image-agent=config-output-dir=" + METADATA_DIR, "-cp", hostClasspath(classpath),
              MAIN_CLASS)
          .redirectErrorStream(true)
          .redirectOutput(AGENT_LOG.toFile());
  builder.environment().put("PORT", Integer.toString(port));
  builder.environment().put("CLIENTS_DB", dbDir.resolve("clients").toString());
  var app = builder.start();
  try {
    var baseUrl = "http://127.0.0.1:" + port;
    awaitReady(app, URI.create(baseUrl + "/api/clients"));
    System.out.println("App is up on " + baseUrl + ", running clients.hurl...");
    var exit = exec(List.of("hurl", "--test", "--variable", "base_url=" + baseUrl, "clients.hurl"));
    if (exit != 0) {
      throw new Failure("hurl tests failed (exit " + exit + "); app log in " + AGENT_LOG);
    }
  } finally {
    app.destroy(); // SIGTERM, so the agent writes its metadata on shutdown
    if (!app.waitFor(30, TimeUnit.SECONDS)) {
      app.destroyForcibly();
    }
    deleteTree(dbDir);
  }
  if (!Files.isRegularFile(METADATA_FILE)) {
    throw new Failure("the agent wrote no " + METADATA_FILE + "; see " + AGENT_LOG);
  }
  System.out.println("Metadata written to " + METADATA_DIR + " (commit it)");
}

/**
 * --collect --linux: the same recording on Linux, which loads Linux-only JNI and reflection for
 * Java2D/fontconfig. It runs in the runtime base image (it has the fonts) with the builder image's
 * GraalVM JDK mounted read-only into it (an image mount: Docker 28+ or Podman). Writes the whole
 * Linux recording to native/linux, which --linux passes to native-image next to native/. The app
 * is published only on a host loopback port and keeps its database in the container's /tmp.
 */
void collectLinuxMetadata(List<String> classpath) throws IOException, InterruptedException {
  System.out.println("Collecting Linux native metadata with the tracing agent in a container...");
  var runtime = containerRuntime();
  ensureImage(runtime, LINUX_BUILDER);
  ensureImage(runtime, LINUX_RUNTIME_BASE);
  var javaHome = builderJavaHome(runtime);
  if (javaHome == null) {
    throw new Failure("the builder image " + LINUX_BUILDER + " sets no JAVA_HOME");
  }
  var port = freeLoopbackPort();
  var cidFile = AGENT_LOG.resolveSibling("agent-linux.cid");
  Files.deleteIfExists(cidFile);
  deleteTree(LINUX_METADATA_DIR);
  Files.createDirectories(LINUX_METADATA_DIR);
  var command = new ArrayList<String>(builderRun(runtime, false));
  command.addAll(
      List.of(
          "--cidfile", cidFile.toString(),
          "--publish", "127.0.0.1:" + port + ":" + port,
          "--env", "PORT=" + port,
          "--env", "SERVER_ADDRESS=0.0.0.0",
          "--env", "CLIENTS_DB=/tmp/clients-db/clients",
          "--mount", "type=image,source=" + LINUX_BUILDER + ",target=" + BUILDER_MOUNT,
          "--entrypoint", BUILDER_MOUNT + javaHome + "/bin/java",
          LINUX_RUNTIME_BASE,
          "-Dspring.aot.enabled=true", "-Djava.awt.headless=true",
          // The Linux native image uses Java2D's JNI text layout (see ClientsApp), so record that.
          "-Dsun.font.layout.ffm=false",
          "-agentlib:native-image-agent=config-output-dir=" + unixPath(LINUX_METADATA_DIR),
          "-cp", linuxClasspath(classpath), MAIN_CLASS));
  var log = AGENT_LOG.resolveSibling("agent-run-linux.log");
  var app = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
  try {
    var baseUrl = "http://127.0.0.1:" + port;
    awaitReady(app, URI.create(baseUrl + "/api/clients"), log);
    System.out.println("App is up on " + baseUrl + " (in the container), running clients.hurl...");
    var exit = exec(List.of("hurl", "--test", "--variable", "base_url=" + baseUrl, "clients.hurl"));
    if (exit != 0) {
      throw new Failure("hurl tests failed (exit " + exit + "); app log in " + log);
    }
  } finally {
    stopContainer(runtime, cidFile); // SIGTERM to the JVM, so the agent writes its metadata
    if (!app.waitFor(60, TimeUnit.SECONDS)) {
      app.destroyForcibly();
    }
    Files.deleteIfExists(cidFile);
  }
  if (!Files.isRegularFile(LINUX_METADATA_DIR.resolve("reachability-metadata.json"))) {
    throw new Failure("the agent wrote no metadata to " + LINUX_METADATA_DIR + "; see " + log);
  }
  System.out.println("Linux metadata written to " + LINUX_METADATA_DIR + " (commit it)");
}

/** Pulls a pinned image unless it is already present. */
void ensureImage(String runtime, String image) throws IOException, InterruptedException {
  var present =
      new ProcessBuilder(runtime, "image", "inspect", image)
          .redirectErrorStream(true)
          .redirectOutput(ProcessBuilder.Redirect.DISCARD)
          .start()
          .waitFor();
  if (present != 0 && exec(List.of(runtime, "pull", image)) != 0) {
    throw new Failure("could not pull " + image);
  }
}

/** Stops only the container this run started, by the id docker wrote to the cid file. */
void stopContainer(String runtime, Path cidFile) throws IOException, InterruptedException {
  if (!Files.isRegularFile(cidFile)) {
    return;
  }
  var id = Files.readString(cidFile, StandardCharsets.UTF_8).trim();
  if (!id.matches("[0-9a-f]{12,64}")) {
    throw new Failure("unexpected container id in " + cidFile);
  }
  new ProcessBuilder(runtime, "stop", "--time", "30", id)
      .redirectErrorStream(true)
      .redirectOutput(ProcessBuilder.Redirect.DISCARD)
      .start()
      .waitFor();
}

void awaitReady(Process app, URI uri) throws InterruptedException {
  awaitReady(app, uri, AGENT_LOG);
}

void awaitReady(Process app, URI uri, Path log) throws InterruptedException {
  var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
  var deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
  while (System.nanoTime() < deadline) {
    if (!app.isAlive()) {
      throw new Failure("the app exited (code " + app.exitValue() + ") before it was ready; see " + log);
    }
    try {
      if (client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200) {
        return;
      }
    } catch (IOException e) {
      // Not listening yet.
    }
    Thread.sleep(1000);
  }
  throw new Failure("the app was not ready after " + STARTUP_TIMEOUT.toSeconds() + "s; see " + log);
}

/** Step 6: native-image takes minutes and reports progress, so its output goes to the console. */
void buildNative(Path bin, List<String> classpath) throws IOException, InterruptedException {
  System.out.println("Building the native executable (this takes a few minutes)...");
  Files.createDirectories(BINARY.getParent());
  var start = System.nanoTime();
  var exit =
      exec(
          List.of(
              bin.resolve("native-image").toString(), "-cp", hostClasspath(classpath),
              "-H:ConfigurationFileDirectories=" + METADATA_DIR,
              "-o", BINARY.toString(), MAIN_CLASS));
  if (exit != 0 || !Files.isRegularFile(BINARY)) {
    throw new Failure("native-image failed (exit " + exit + ")");
  }
  // Java2D needs <java.home>/lib/fontconfig.bfc; ClientsApp sets java.home to the binary's dir.
  var fontConfig = bin.getParent().resolve("lib").resolve("fontconfig.bfc");
  if (Files.isRegularFile(fontConfig)) {
    var target = BINARY.resolveSibling("lib").resolve("fontconfig.bfc");
    Files.createDirectories(target.getParent());
    Files.copy(fontConfig, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
  }
  var seconds = Duration.ofNanos(System.nanoTime() - start).toSeconds();
  System.out.printf("Binary: %s (%.1f MB)%n", BINARY, Files.size(BINARY) / (1024.0 * 1024.0));
  System.out.printf("Built in %dm %02ds%n", seconds / 60, seconds % 60);
  System.out.println("Run it: CLIENTS_DB=./data/clients ./" + BINARY);
}

/**
 * Step 6 (--linux): native-image must run on Linux to produce a Linux executable, so it runs in
 * the pinned builder container. clients/ is mounted at /work (all paths are relative to it), the
 * container runs as the host user so the outputs aren't root-owned, and it has no network.
 */
void buildLinuxNative(List<String> classpath) throws IOException, InterruptedException {
  var runtime = containerRuntime();
  System.out.println("Building the Linux native executable in " + LINUX_BUILDER + " (this takes a few minutes)...");
  deleteTree(LINUX_BINARY.getParent());
  Files.createDirectories(LINUX_BINARY.getParent());
  var configDirs = new ArrayList<String>(List.of(unixPath(METADATA_DIR)));
  if (Files.isDirectory(LINUX_METADATA_DIR)) {
    configDirs.add(unixPath(LINUX_METADATA_DIR));
  }
  var command = new ArrayList<String>(builderRun(runtime, true));
  command.add(LINUX_BUILDER); // its entrypoint is native-image
  command.addAll(
      List.of(
          "-cp", linuxClasspath(classpath),
          "-H:ConfigurationFileDirectories=" + String.join(",", configDirs),
          "-o", unixPath(LINUX_BINARY), MAIN_CLASS));
  var start = System.nanoTime();
  var exit = exec(command);
  if (exit != 0 || !Files.isRegularFile(LINUX_BINARY)) {
    throw new Failure("native-image in the builder container failed (exit " + exit + ")");
  }
  copyBuilderFontConfig(runtime);
  var seconds = Duration.ofNanos(System.nanoTime() - start).toSeconds();
  System.out.printf("Binary: %s (%.1f MB)%n", LINUX_BINARY, Files.size(LINUX_BINARY) / (1024.0 * 1024.0));
  System.out.printf("Built in %dm %02ds%n", seconds / 60, seconds % 60);
  System.out.println("Package it: ./Containerize.java --native");
}

/**
 * Creates {@code lib/} next to the binary and copies {@code <JAVA_HOME>/lib/fontconfig.bfc} from
 * the builder image into it when the image has one. Linux JDKs usually don't; Java2D then asks the
 * system fontconfig library, but only when {@code <java.home>/lib} exists (sun.awt.FontConfiguration
 * treats a missing directory as "an OS-specific config file was found"), so lib/ must exist even
 * when it stays empty.
 */
void copyBuilderFontConfig(String runtime) throws IOException, InterruptedException {
  var target = LINUX_BINARY.resolveSibling("lib");
  Files.createDirectories(target);
  var javaHome = builderJavaHome(runtime);
  if (javaHome == null) {
    return;
  }
  var command = new ArrayList<String>(builderRun(runtime, true));
  command.addAll(
      List.of("--entrypoint", "cp", LINUX_BUILDER, javaHome + "/lib/fontconfig.bfc", unixPath(target) + "/"));
  var process = new ProcessBuilder(command).redirectErrorStream(true).start();
  process.getInputStream().readAllBytes();
  if (process.waitFor() != 0) {
    System.out.println("The builder JDK has no lib/fontconfig.bfc; Java2D uses the system fontconfig.");
  }
}

/** JAVA_HOME from the builder image's configuration, or null when it isn't set. */
String builderJavaHome(String runtime) throws IOException, InterruptedException {
  var process =
      new ProcessBuilder(
              runtime, "image", "inspect", "--format", "{{range .Config.Env}}{{println .}}{{end}}", LINUX_BUILDER)
          .redirectError(ProcessBuilder.Redirect.DISCARD)
          .start();
  var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
  if (process.waitFor() != 0) {
    return null;
  }
  return output.lines()
      .filter(line -> line.startsWith("JAVA_HOME="))
      .map(line -> line.substring("JAVA_HOME=".length()))
      .filter(home -> home.startsWith("/"))
      .findFirst()
      .orElse(null);
}

/**
 * The common part of every builder container run, up to (not including) the image name. Without
 * network only the default bridge is used, and only for the published loopback port.
 */
List<String> builderRun(String runtime, boolean offline) throws IOException, InterruptedException {
  var workdir = Path.of("").toAbsolutePath().toString();
  var command = new ArrayList<String>(List.of(runtime, "run", "--rm"));
  if (offline) {
    command.addAll(List.of("--network", "none"));
  }
  command.addAll(
      List.of(
          "--user", idOutput("-u") + ":" + idOutput("-g"),
          "--env", "HOME=/tmp",
          "--volume", workdir + ":" + CONTAINER_WORKDIR,
          "--workdir", CONTAINER_WORKDIR));
  return command;
}

String linuxClasspath(List<String> classpath) {
  return String.join(":", classpath.stream().map(entry -> unixPath(Path.of(entry))).toList());
}

/** docker, or podman when docker isn't installed. */
String containerRuntime() {
  for (var candidate : List.of("docker", "podman")) {
    if (onPath(candidate)) {
      return candidate;
    }
  }
  throw new Failure("--linux needs docker or podman on the PATH");
}

boolean onPath(String executable) {
  var path = System.getenv("PATH");
  if (path == null) {
    return false;
  }
  return Stream.of(path.split(File.pathSeparator))
      .filter(dir -> !dir.isBlank())
      .anyMatch(dir -> Files.isExecutable(Path.of(dir, executable)));
}

String idOutput(String flag) throws IOException, InterruptedException {
  var process = new ProcessBuilder("id", flag).redirectError(ProcessBuilder.Redirect.DISCARD).start();
  var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
  if (process.waitFor() != 0 || !output.matches("\\d+")) {
    throw new Failure("could not read the current user and group ids with 'id " + flag + "'");
  }
  return output;
}

/** A path relative to clients/, as seen inside the container (always '/'-separated). */
String unixPath(Path relative) {
  if (relative.isAbsolute()) {
    throw new Failure("expected a path relative to clients/, got " + relative);
  }
  return relative.toString().replace(File.separatorChar, '/');
}

String hostClasspath(List<String> classpath) {
  return String.join(File.pathSeparator, classpath);
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

/** Runs a command, showing its output only with --verbose or when it fails. */
void runQuiet(List<String> command, boolean verbose, String what) throws IOException, InterruptedException {
  var process = new ProcessBuilder(command).redirectErrorStream(true).start();
  var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
  var exit = process.waitFor();
  if (verbose || exit != 0) {
    System.out.print(output);
  }
  if (exit != 0) {
    throw new Failure(what + " failed (exit " + exit + ")");
  }
}

/** Runs a command with inherited IO and returns its exit code. */
int exec(List<String> command) throws InterruptedException {
  try {
    return new ProcessBuilder(command).inheritIO().start().waitFor();
  } catch (IOException e) {
    throw new Failure(command.getFirst() + " not found on the PATH or not executable", e);
  }
}

int freeLoopbackPort() throws IOException {
  try (var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
    return socket.getLocalPort();
  }
}

void deleteTree(Path root) throws IOException {
  if (!Files.exists(root)) {
    return;
  }
  try (Stream<Path> paths = Files.walk(root)) {
    for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
      Files.delete(path);
    }
  }
}
