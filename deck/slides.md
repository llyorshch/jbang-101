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

```bash
sudo docker run -it --name jbang-101 -h jbang-101 debian
apt update && apt -y install curl vim
export JBANG_DEFAULT_JAVA_VERSION=25
curl -Ls https://sh.jbang.dev | bash -s - app setup
. .bashrc
```

---

# First script

```bash
jbang init minimal.java
chmod +x minimal.java
vim minimal.java
jbang minimal.java
```

---

# Template-based script

```bash
jbang init --template=cli hello.java
vim hello.java
chmod +x hello.java
./hello.java
```

---

layout: center
class: text-center
---

# Thanks

Questions?
