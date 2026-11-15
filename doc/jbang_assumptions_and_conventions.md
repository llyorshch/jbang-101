# JBang: assumptions, conventions and limitations vs Maven/Gradle

Sources: the JBang docs (`docs/modules/ROOT/pages/*.adoc`) and the code that reads the directives (`src/main/java/dev/jbang/source/ProjectBuilder.java`, `Source.java`, `Util.explode`).

## 1. The core model: the source file is the build file

- **One entry file defines the project.** Running `jbang Foo.java` makes `Foo.java` the root. There is no `pom.xml` or `build.gradle`. All build metadata lives in `//` comment *directives* at the top of the file: `//DEPS`, `//REPOS`, `//JAVA`, `//SOURCES`, `//FILES`, `//MAIN`, `//GAV`, `//COMPILE_OPTIONS`, `//RUNTIME_OPTIONS`, `//NATIVE_OPTIONS`, `//JAVAAGENT`, `//MANIFEST`, `//CDS`, `//PREVIEW`, `//MODULE`, `//KOTLIN`, `//GROOVY`, `//DESCRIPTION`, `//DOCS`, `//NOINTEGRATIONS`.
- **Directive syntax is strict.** A directive starts at column 0 with no space after `//`, is case-sensitive, and must be in the first comment block. Code formatters that reflow comments can break them.
- **The shebang is a comment trick.** `///usr/bin/env jbang "$0" "$@" ; exit $?` is valid as both shell and Java. The file can be run directly and IDEs and `javac` still accept it.
- **No standard folder layout.** There's no `src/main/java` or `src/test/java`.
  - Extra source files are listed explicitly with `//SOURCES`. Paths are relative to the file that declares them, and globs and directories are expanded.
  - Resources go into the jar only if listed with `//FILES mount=path`.
- **Naming conventions.** Scripts often use lower-case class names (`hello.java`) so they read like CLI commands. Files without an extension are mapped from kebab case to a class name (`kubectl-my-plugin` → `KubectlMyPlugin`).
- **All directives merge into one project.** `ProjectBuilder.updateProject` walks `//SOURCES` recursively. It merges every included file's dependencies, repos, options, manifest entries and resources into **one flat project**, and the highest `//JAVA` version wins. (`organizing.adoc` still says only the main file's directives count, but the code is newer than that doc.)
- **Source dependencies are the nearest thing to modules.** `//DEPS other.java` (or a URL, or an alias) is built as a separate sub-project and put on the classpath.
- **You don't own the build output.** Compiled jars go to `~/.jbang/cache` (`JBANG_CACHE_DIR`), keyed by a hash of the sources and directives (`Project.getStableId`). There's no `target/` or `build/` folder. Dependencies resolve into `~/.m2` (`JBANG_REPO`).
- **The Java version is part of the script.** `//JAVA 17+` makes JBang pick or download a matching JDK. JBang compiles explicitly instead of relying on Java's single-file launch, so Java 8 works too.

## 2. How dependencies work

- Coordinates are Gradle-style one-liners: `group:artifact:version[:classifier][@type]`.
  - `@pom` imports a BOM, and **only one BOM is supported**.
  - `@fatjar` skips transitive dependencies.
- Git repo links (GitHub, GitLab, Bitbucket) are converted to JitPack coordinates.
- Adding **any** `//REPOS` line removes Maven Central unless you list `central` explicitly.
- Credentials come from `~/.m2/settings.xml`, `.netrc`, `GITHUB_TOKEN`/`GITLAB_TOKEN` and git credential helpers. Lookup is exact-host only.
- Directives accept property placeholders such as `${env.X:default}` and `${os.detected.name}`.

## 3. Limitations compared with Maven/Gradle

| Area | Maven/Gradle | JBang |
|---|---|---|
| **Dependency scopes** | compile, provided, runtime and test scopes; `api` vs `implementation` | One flat classpath: no test, provided or optional scope. Test libraries ship with the app. |
| **Testing** | `src/test`, the surefire plugin or `test` task, coverage, CI reports | No test lifecycle. Tests are just another script, or you export to Maven/Gradle. |
| **Multi-module** | Multi-module and composite builds with boundaries between modules | No module graph. `//SOURCES` merges everything into one compile. Sub-projects from source `//DEPS` are a weak substitute with no shared configuration. |
| **Lifecycle and plugins** | Build phases and a large plugin ecosystem (codegen, shading, checkstyle, spotless, jacoco…) | Fixed steps: resolve, compile, jar, optional native image, run. The only hook is the **experimental** `META-INF/jbang-integration.list` `postBuild`, which Quarkus uses. |
| **Dependency management** | Multiple BOMs, exclusions, `dependencyManagement`, version catalogs, conflict strategies, lock files | One BOM only. No per-dependency exclusions, no lock file. Reproducibility relies on pinned versions, and the global cache can hide changes. |
| **Resources** | Everything in `src/main/resources` is included automatically, with filtering | Each file or pattern must be listed in `//FILES`, with no filtering. `//FILES` doesn't work with `jbang edit` or with scripts run from a URL. |
| **Packaging and publishing** | Sources and javadoc jars, signing, deploy to Nexus or Artifactory | `export local` gives a jar with **machine-specific absolute classpath entries**. `export portable` adds a `lib/` folder; there are also fatjar and native exports. `export mavenrepo` installs only to a local repository; remote publishing goes through JitPack or another tool. No signing. |
| **IDE support** | Native project import in every IDE | Needs a JBang IDE plugin, or `jbang edit`, which builds a temporary project from symlinks. The docs recommend that mode only for single files, and symlinks may need admin rights on Windows. |
| **Java modules (JPMS)** | Full support | `//MODULE` is **experimental**. |
| **Remote scripts** | n/a | Globs in `//SOURCES` don't work when the script is run from a URL, and `//FILES` doesn't either. JBang asks you to trust the source first. |
| **JShell (`.jsh`)** | n/a | Not compiled or cached, can't become a native image, and can't access classes in the default package. |
| **Relative repos** | Resolved against the project | `--repos local=./path` resolves against the **current directory**, not the script's location (experimental). |
| **CI and scale** | Well-defined, cacheable tasks | Per-user global cache with implicit invalidation. No incremental compilation across a large codebase. |

## 4. When JBang fits, and the way out

- **Good fit:** scripts, CLI tools, kubectl or git plugins, prototypes and demos, small services (Quarkus, Micronaut and Spring all work), and running a published Maven artifact directly with `jbang g:a:v`.
- **Poor fit:** large or multi-module codebases, anything needing test-scoped dependencies, quality gates, signed publishing, or reproducible locked builds. That covers most production services in a regulated environment.
- **Way out:** `jbang export maven|gradle script.java` generates a standard project.
  - It carries over `//SOURCES`, `//FILES` (into `src/main/resources`), `//DEPS`, `//REPOS`, `//JAVA`, `//GAV` and `//DESCRIPTION`.
  - The default coordinates are `org.example:<name>:999-SNAPSHOT`.
  - So you can prototype in JBang and move to a full build tool when the project grows.

## References (JBang repo)

- `docs/modules/ROOT/pages/script-directives.adoc`: full directive reference
- `docs/modules/ROOT/pages/organizing.adoc`: multiple source files, `//FILES`, naming
- `docs/modules/ROOT/pages/dependencies.adoc`: BOMs, repositories, authentication
- `docs/modules/ROOT/pages/exporting.adoc`: export to jar, Maven repo, Maven or Gradle project
- `docs/modules/ROOT/pages/integration.adoc`: the build integration hook
- `docs/modules/ROOT/pages/editing.adoc`: IDE support and sandbox mode
- `src/main/java/dev/jbang/source/ProjectBuilder.java`: how directives are merged across sources
