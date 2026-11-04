# Blog

A very basic blog built with JBang, [Javalin](https://javalin.io) and SQLite.

```bash
cd blog
./Blog.java    # or: jbang Blog.java
```

Then open http://localhost:7070. You can list, read, create and delete posts.

There is no authentication, so the server only listens on `127.0.0.1`.

| Variable  | Default   | Purpose                                |
|-----------|-----------|----------------------------------------|
| `PORT`    | `7070`    | HTTP port                              |
| `BLOG_DB` | `blog.db` | SQLite file, created if it is missing  |

## Layout

JBang is the only build tool. `Blog.java` holds the dependencies and pulls in the rest with `//SOURCES`.

| File                     | Purpose                                 |
|--------------------------|-----------------------------------------|
| `Blog.java`              | Entry point: settings and server start  |
| `app/PostHandlers.java`  | Routes and request handling             |
| `app/PostRepository.java`| SQLite queries                          |
| `app/Views.java`         | HTML pages and escaping                 |
| `app/Post.java`          | The post record                         |

## Tests

[`blog.hurl`](blog.hurl) tests every endpoint using [Hurl](https://hurl.dev). Start a server
with a throwaway database, then run the suite against it:

```bash
BLOG_DB=$(mktemp) ./Blog.java
hurl --test blog.hurl    # in another terminal
```

`mise.toml` sets the default `base_url` to `http://localhost:7070`, which needs [mise](https://mise.jdx.dev)
active in your shell and a one-time `mise trust` in this folder. Without mise, or to test another
server, pass it yourself with `--variable base_url=http://localhost:7171`.
