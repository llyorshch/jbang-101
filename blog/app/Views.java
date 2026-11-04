package app;

import java.util.List;
import java.util.stream.Collectors;

public final class Views {

    private Views() {}

    public static String index(List<Post> posts) {
        String items = posts.stream()
                .map(p -> "<li><a href=\"/posts/%d\">%s</a> <small>%s</small></li>"
                        .formatted(p.id(), escape(p.title()), p.createdAt()))
                .collect(Collectors.joining("\n"));
        return page("Blog", items.isEmpty() ? "<p>No posts yet.</p>" : "<ul>" + items + "</ul>");
    }

    public static String newPost() {
        return page("New post", """
                <form method="post" action="/posts">
                  <p><input name="title" placeholder="Title" required></p>
                  <p><textarea name="body" rows="10" placeholder="Write something..." required></textarea></p>
                  <button>Publish</button>
                </form>
                """);
    }

    public static String post(Post post) {
        return page(post.title(), """
                <p><small>%s</small></p>
                <p class="body">%s</p>
                <form method="post" action="/posts/%d/delete"><button>Delete</button></form>
                """.formatted(post.createdAt(), escape(post.body()), post.id()));
    }

    private static String page(String title, String content) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  <style>
                    body { max-width: 40rem; margin: 2rem auto; padding: 0 1rem; font-family: system-ui, sans-serif; line-height: 1.5; }
                    input, textarea { width: 100%%; box-sizing: border-box; font: inherit; padding: .5rem; }
                    small { color: #666; }
                    .body { white-space: pre-wrap; }
                  </style>
                </head>
                <body>
                  <nav><a href="/">Home</a> | <a href="/posts/new">New post</a></nav>
                  <h1>%s</h1>
                  %s
                </body>
                </html>
                """.formatted(escape(title), escape(title), content);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
