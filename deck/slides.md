---
theme: default
title: JBang 101
info: |
  ## JBang 101
  Java scripting, without the ceremony.
class: text-center
transition: slide-left
mdc: true
---

# JBang

Java, without the ceremony

---

![Let's cut to the chase](./img/lets_cut_to_the_chase.jpg)

---

# First of all: Why?

<img src="./img/max_rydahl_andersen.jpg" alt="Max Rydahl Andersen" class="mx-auto max-h-90 object-contain" />

<p class="text-center">Kudos to Max Rydahl Andersen</p>

---

# Main features

1. Zero-Configuration JVM-based execution (Java,Kotlin,Groovy)
2. Automatic JDK Bootstraping
3. Dependency resolution + Multi-file and Resource inclusion (maven without xml)
4. ~~Full~~ IDE integration (more on this later)
5. Built-in project template support + Project export in multiple formats
6. Jar packaging and Native image generation (with GraalVM)
7. Remote execution, Catalogs and local CLI installation

---

# Demo
## Setting up an environment from scratch
```bash
sudo docker run -it --name jbang-101 -h jbang-101 debian
apt update && apt -y install curl vim
export JBANG_DEFAULT_JAVA_VERSION=25
curl -Ls https://sh.jbang.dev | bash -s - app setup
. .bashrc
```

## First script

```bash
jbang init minimal.java
chmod +x minimal.java
./minimal.java
```

## Template-based script

```bash
jbang init --template=cli hello.java
vim hello.java
jbang --verbose hello.java
```

---

# More examples

- A more complex project that went south: `Clients`
- The same project but reduced to a sensible size: Clientsv2
- A suite of cli time utilities: [tzu](https://github.com/llyorshch/jmg-tools)

---

# JBang Trade-offs

<div class="grid grid-cols-2 gap-8 text-sm">
<div>

### ✅ You gain

- The source file **is** the build file: directives (`//DEPS`, `//JAVA`…) instead of XML
- JDK picked or downloaded per script (`//JAVA 17+`)
- One-line Gradle-style dependencies, any Maven repo
- No folder layout, no `target/`: output cached in `~/.jbang`
- Run anything: scripts, URLs, `g:a:v` artifacts
- Way out: `jbang export maven|gradle`

</div>
<div>

### ⚠️ You give up

- One flat classpath: no test/provided scopes
- No test lifecycle, plugins or build phases
- No real multi-module: `//SOURCES` merges into one compile
- Only one BOM, no exclusions, no lock file
- Resources must be listed one by one in `//FILES`
- IDE support needs a plugin or `jbang edit`
- Global cache with implicit invalidation: weak for CI at scale

</div>
</div>

**Great for** scripts, CLIs, plugins, prototypes · **Poor for** large, regulated production codebases

---

# Again, why?

<v-clicks depth="2">

- What _is_ the Java mantra? 
  - > Write Once, Run Everywhere
- Remote execution, catalogs and (heck!) appstores, make cli tooling discoverable and easy to distribute
- Who is going to benefit the most out of all these tools easily discoverable and usable?
  - Exactly: 🤖

</v-clicks>

---
layout: center
class: text-center
---

# Thanks

Questions?
