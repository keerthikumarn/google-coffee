package com.googlecoffee.adapter.local;

import com.googlecoffee.model.Feedback;
import com.googlecoffee.port.FeedbackStore;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Component
@Profile("local")
public class PgFeedbackStore implements FeedbackStore {

    private final JdbcClient jdbc;

    public PgFeedbackStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Feedback f) {
        jdbc.sql("""
                INSERT INTO feedback (id, session_id, order_id, table_no, rating, comment, created_at)
                VALUES (:id, :sessionId, :orderId, :table, :rating, :comment, :createdAt)
                """)
                .param("id", f.id())
                .param("sessionId", f.sessionId())
                .param("orderId", f.orderId() == null || f.orderId().isBlank() ? null : f.orderId())
                .param("table", f.table())
                .param("rating", f.rating())
                .param("comment", f.comment() == null ? "" : f.comment())
                .param("createdAt", f.createdAt())
                .update();
    }

    @Override
    public List<Feedback> since(long sinceMillis, int limit) {
        return jdbc.sql("""
                SELECT id, session_id, order_id, table_no, rating, comment, created_at
                FROM feedback WHERE created_at >= :since ORDER BY created_at DESC LIMIT :limit
                """)
                .param("since", sinceMillis)
                .param("limit", limit)
                .query(this::map)
                .list();
    }

    private Feedback map(ResultSet rs, int row) throws SQLException {
        return new Feedback(
                rs.getString("id"),
                rs.getString("session_id"),
                rs.getString("order_id"),
                rs.getString("table_no"),
                rs.getInt("rating"),
                rs.getString("comment"),
                rs.getLong("created_at"));
    }
}
