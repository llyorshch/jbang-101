package app;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PostRepository {

    private final String dbUrl;

    public PostRepository(String dbUrl) {
        this.dbUrl = dbUrl;
    }

    public void init() throws SQLException {
        try (Connection db = connect(); var stmt = db.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS posts (
                      id         INTEGER PRIMARY KEY AUTOINCREMENT,
                      title      TEXT NOT NULL,
                      body       TEXT NOT NULL,
                      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }

    public List<Post> findAll() throws SQLException {
        try (Connection db = connect();
             var stmt = db.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM posts ORDER BY id DESC")) {
            List<Post> posts = new ArrayList<>();
            while (rs.next()) {
                posts.add(toPost(rs));
            }
            return posts;
        }
    }

    public Optional<Post> findById(long id) throws SQLException {
        try (Connection db = connect();
             var query = db.prepareStatement("SELECT * FROM posts WHERE id = ?")) {
            query.setLong(1, id);
            try (ResultSet rs = query.executeQuery()) {
                return rs.next() ? Optional.of(toPost(rs)) : Optional.empty();
            }
        }
    }

    /** Returns the id of the new post. */
    public long create(String title, String body) throws SQLException {
        try (Connection db = connect();
             var insert = db.prepareStatement("INSERT INTO posts (title, body) VALUES (?, ?) RETURNING id")) {
            insert.setString(1, title);
            insert.setString(2, body);
            try (ResultSet rs = insert.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Returns whether a post was deleted. */
    public boolean delete(long id) throws SQLException {
        try (Connection db = connect();
             var delete = db.prepareStatement("DELETE FROM posts WHERE id = ?")) {
            delete.setLong(1, id);
            return delete.executeUpdate() > 0;
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    private static Post toPost(ResultSet rs) throws SQLException {
        return new Post(rs.getLong("id"), rs.getString("title"), rs.getString("body"), rs.getString("created_at"));
    }
}
