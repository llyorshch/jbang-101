package app;

import io.javalin.config.JavalinConfig;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;

import java.sql.SQLException;

public class PostHandlers {

    private final PostRepository posts;

    public PostHandlers(PostRepository posts) {
        this.posts = posts;
    }

    public void register(JavalinConfig config) {
        config.routes.get("/", this::index);
        config.routes.get("/posts/new", this::newPost);
        config.routes.post("/posts", this::createPost);
        config.routes.get("/posts/{id}", this::showPost);
        config.routes.post("/posts/{id}/delete", this::deletePost);
    }

    private void index(Context ctx) throws SQLException {
        ctx.html(Views.index(posts.findAll()));
    }

    private void newPost(Context ctx) {
        ctx.html(Views.newPost());
    }

    private void createPost(Context ctx) throws SQLException {
        String title = required(ctx, "title");
        String body = required(ctx, "body");
        long id = posts.create(title, body);
        ctx.redirect("/posts/" + id, HttpStatus.SEE_OTHER);
    }

    private void showPost(Context ctx) throws SQLException {
        Post post = posts.findById(postId(ctx)).orElseThrow(NotFoundResponse::new);
        ctx.html(Views.post(post));
    }

    private void deletePost(Context ctx) throws SQLException {
        if (!posts.delete(postId(ctx))) {
            throw new NotFoundResponse();
        }
        ctx.redirect("/", HttpStatus.SEE_OTHER);
    }

    private static long postId(Context ctx) {
        try {
            return Long.parseLong(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            throw new NotFoundResponse();
        }
    }

    private static String required(Context ctx, String field) {
        String value = ctx.formParam(field);
        if (value == null || value.isBlank()) {
            throw new BadRequestResponse(field + " is required");
        }
        return value.strip();
    }
}
