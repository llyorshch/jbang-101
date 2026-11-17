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

<h1 class="!mb-8"><img src="./img/jbang_logo.svg" alt="JBang" class="mx-auto w-full max-w-[52rem]" /></h1>

Java, without the ceremony

---

# First of all: Why?

<img src="./img/max_rydahl_andersen.jpg" alt="Max Rydahl Andersen" class="mx-auto max-h-90 object-contain" />

<p class="text-center">Kudos to Max Rydahl Andersen</p>

<!--
- Max took a year off: no Java, playing piano, learning French, using Python and npm. When he came back and installed Java on a clean machine, he had to work out which Java, which Maven and which variant he needed. That's when he realised "we really made Java so complicated", which you stop noticing once you're used to it.
- He saw Quarkus team scripts written in Kotlin with kscript and asked "why can't I do this in Java?". JBang started as a Christmas side project, and the //DEPS syntax is copied from kscript on purpose.
- Tako Schotanus is the co-maintainer. Automatic JDK download was his idea, because needing Java installed before you can use JBang was annoying.
-->
---

# Main features

1. 🚀 Zero-Configuration JVM-based execution (Java,Kotlin,Groovy)
2. ☕ Automatic JDK Bootstraping
3. 📦 Dependency resolution + Multi-file and Resource inclusion (maven without xml)
4. 🛠️ ~~Full~~ IDE integration (more on this later)
5. 🧩 Built-in project template support + Project export in multiple formats
6. ⚡ Jar packaging and Native image generation (with GraalVM)
7. 🌍 Remote execution, Catalogs and local CLI installation

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

#### Running AI models with JBang and [JLama](https://github.com/tjake/Jlama)

```bash
jbang app install --force jlama@tjake
jlama restapi tjake/Llama-3.2-1B-Instruct-JQ4 --auto-download
```

#### Running a GitHub Gist and JavaFX
```bash
jbang https://gist.githubusercontent.com/FDelporte/c69a02c57acc892b4c996a9779d4f830/raw/3e9246bd5f2532991ea505e2f6d1248e4e028898/FxDemo.java
```


#### Running code embedded in markdown
```bash
jbang ../doc/markdown_with_java.md
```

#### Running a Jupyter Notebook with a Java Kernel
```bash
# Install JupyterLab (requires Python)
pip install jupyterlab
# Install JBang kernel
jbang install-kernel@jupyter-java --java 25 --enable-preview jbang
#run JupyterLab and create a notebook with the Jbang Kernel
jupyter lab ../doc/jupyter_notebook_that_runs_java.ipynb
```

---

# Example JBang projects

- 💥 A more complex project that went south: `Clients`
- ✂️ The same project but reduced to a sensible size: Clientsv2
- ⏰ A suite of cli time utilities: [tzu](https://github.com/llyorshch/jmg-tools)

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

<v-click>
  <img src="./img/six_seven.gif" alt="6 7 meme" class="absolute bottom-3 left-1/2 -translate-x-1/2 h-36 rounded-lg shadow-lg" />
</v-click>

---

# Again, why? 🤔


<v-clicks depth="1">

## JBang serendipity revelations 🧭

</v-clicks>
<v-clicks depth="2">

- What _is_ the Java mantra?
  - > Write Once, Run Everywhere… finally true for CLIs: no JDK install needed
- What feature of JBang **really** makes the difference?
  - Distribution: JSON catalogs and (heck!) app stores
- Java + JBang + picocli = ?
  - Discoverable, self-describing, zero-setup CLI tools
- Who benefits most from tools that are machine-discoverable and run anywhere with one command?

</v-clicks>

---
layout: full
class: p-1
---

<img src="./img/happy_agents.png" alt="Happy AI agents discovering and using CLI tools built with JBang and Java" class="absolute inset-0 w-full h-full object-cover" />

<!--
Exactly: AGENTS!
-->

---
layout: center
class: text-center
---



<img
  v-motion
  :initial="{ x: -500, rotate: -20, opacity: 0 }"
  :enter="{ x: 0, rotate: 0, opacity: 1, transition: { type: 'spring', stiffness: 90, damping: 9, delay: 2000 } }"
  src="./img/greeting_robot.svg"
  alt="A happy AI robot waving at the audience"
  class="absolute left-6 bottom-2 h-72"
/>

<div
  v-motion
  :initial="{ scale: 0, opacity: 0 }"
  :enter="{ scale: 1, opacity: 1, transition: { type: 'spring', stiffness: 220, damping: 12, delay: 3000 } }"
  class="absolute left-72 bottom-60 origin-bottom-left px-5 py-3 rounded-3xl rounded-bl-none bg-white text-[#3b2378] text-xl font-bold shadow-xl border-2 border-[#3b2378]"
>
  Hi humans! 👋 Thanks for listening! 🎉 Questions? ❓
</div>
