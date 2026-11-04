///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25+
//DEPS io.javalin:javalin:7.2.4
//DEPS org.xerial:sqlite-jdbc:3.54.1.0
//DEPS org.slf4j:slf4j-simple:2.0.20
//RUNTIME_OPTIONS --enable-native-access=ALL-UNNAMED
//SOURCES app/*.java

import app.PostHandlers;
import app.PostRepository;
import io.javalin.Javalin;

import java.sql.SQLException;
import java.util.Optional;

void main() throws SQLException {
    var posts = new PostRepository("jdbc:sqlite:" + env("BLOG_DB", "blog.db"));
    posts.init();

    var handlers = new PostHandlers(posts);
    Javalin.create(config -> {
        config.jetty.host = "127.0.0.1"; // no auth: only listen locally
        config.jetty.port = Integer.parseInt(env("PORT", "7070"));
        handlers.register(config);
    }).start();
}

String env(String name, String fallback) {
    return Optional.ofNullable(System.getenv(name)).orElse(fallback);
}
