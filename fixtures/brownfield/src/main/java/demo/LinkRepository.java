package demo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import javax.sql.DataSource;

/** JDBC access to the links table. */
@Repository
public class LinkRepository {

    private final DataSource dataSource;

    public LinkRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Inserts a new row on every call; there is no lookup for an existing link. */
    public void insert(Link link) {
        String sql = "INSERT INTO links (code, target_url) VALUES (?, ?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, link.code());
            ps.setString(2, link.targetUrl());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("could not save link", e);
        }
    }
}
