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

# JBang 101

Java scripting, without the ceremony

---

# Agenda

1. Setting up the demo environment
2. Your first script
3. Template-based scripts
4. Q&A

---

# Demo environment

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

# Thanks!

Questions?
