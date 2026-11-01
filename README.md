# JBang 101

## Creating a docker container just for the demo

```java
```
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
vim minimal.java
jbang minimal.java
```

## First template based script

```bash
jbang init --template=cli hello.java
vim hello.java
chmod +x hello.java
./hello.java
```

## Templates

```bash
jbang template list
jbang init --template=qcli quarkuscli.java
vim quarkuscli.java
jbang quarkuscli.java jo
```



## Slides

The presentation lives in [`deck/`](deck/) and is published to GitHub Pages at
https://llyorshch.github.io/jbang-101/ by the `Publish deck` workflow on every push to `master`.
Open `deck/index.html` locally to preview. Navigate with arrow keys, space, or swipe.
