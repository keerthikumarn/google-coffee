package com.googlecoffee.adapter.local;

import com.googlecoffee.model.Session;
import com.googlecoffee.port.SessionStore;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Component
@Profile("local")
public class PgSessionStore implements SessionStore {

    private final JdbcClient jdbc;
    private final JsonColumns json;

    public PgSessionStore(JdbcClient jdbc, JsonColumns json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void save(Session s) {
        jdbc.sql("""
                INSERT INTO sessions (id, name, table_no, preferences, created_at)
                VALUES (:id, :name, :table, CAST(:prefs AS jsonb), :createdAt)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, table_no = EXCLUDED.table_no,
                                               preferences = EXCLUDED.preferences
                """)
                .param("id", s.id())
                .param("name", s.name())
                .param("table", s.table())
                .param("prefs", json.write(s.preferences()))
                .param("createdAt", s.createdAt())
                .update();
    }

    @Override
    public Optional<Session> find(String id) {
        return jdbc.sql("SELECT id, name, table_no, preferences::text AS preferences, created_at FROM sessions WHERE id = :id")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    private Session map(ResultSet rs, int row) throws SQLException {
        return new Session(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("table_no"),
                json.strings(rs.getString("preferences")),
                rs.getLong("created_at"));
    }
}
