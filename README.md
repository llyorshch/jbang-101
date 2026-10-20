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
vim minimal.java
```

```java
void main(String[] args) {
  var greeting = (args.length > 0 ? args[0] : "world");
  IO.println("Hello "+greeting+"!");
}
```


## First template based script

```bash
jbang init --template=cli hello.java
chmod +x hello.java
./hello.java
```

## Templates

```bash
jbang template list
jbang init --template=qcli quarkuscli.java
jbang quarkuscli.java jo


```


