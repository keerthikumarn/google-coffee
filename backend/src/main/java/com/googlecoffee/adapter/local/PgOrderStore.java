package com.googlecoffee.adapter.local;

import com.googlecoffee.model.Order;
import com.googlecoffee.model.OrderStatus;
import com.googlecoffee.port.OrderStore;
import com.googlecoffee.web.ApiException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
@Profile("local")
public class PgOrderStore implements OrderStore {

    private static final String COLUMNS =
            "id, code, session_id, customer_name, table_no, items::text AS items, total, prep_minutes, status, created_at, updated_at";

    private final JdbcClient jdbc;
    private final JsonColumns json;
    private final ApplicationEventPublisher events;

    public PgOrderStore(JdbcClient jdbc, JsonColumns json, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.json = json;
        this.events = events;
    }

    @Override
    public void insert(Order o) {
        jdbc.sql("""
                INSERT INTO orders (id, code, session_id, customer_name, table_no, items, total, prep_minutes,
                                    status, created_at, updated_at)
                VALUES (:id, :code, :sessionId, :customerName, :table, CAST(:items AS jsonb), :total, :prep,
                        :status, :createdAt, :updatedAt)
                """)
                .param("id", o.id())
                .param("code", o.code())
                .param("sessionId", o.sessionId())
                .param("customerName", o.customerName())
                .param("table", o.table())
                .param("items", json.write(o.items()))
                .param("total", o.total())
                .param("prep", o.prepMinutes())
                .param("status", o.status().name())
                .param("createdAt", o.createdAt())
                .param("updatedAt", o.updatedAt())
                .update();
        events.publishEvent(new OrderChangedEvent(o));
    }

    @Override
    public Optional<Order> find(String id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM orders WHERE id = :id")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    @Override
    public List<Order> findBySession(String sessionId, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM orders WHERE session_id = :sid ORDER BY created_at DESC LIMIT :limit")
                .param("sid", sessionId)
                .param("limit", limit)
                .query(this::map)
                .list();
    }

    @Override
    public List<Order> findActive() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM orders WHERE status IN (:statuses) ORDER BY created_at")
                .param("statuses", OrderStatus.ACTIVE.stream().map(Enum::name).toList())
                .query(this::map)
                .list();
    }

    /**
     * Single conditional UPDATE: only succeeds if the current status is one that may move to `next`,
     * so concurrent staff actions can't make an illegal jump.
     */
    @Override
    public Order updateStatus(String id, OrderStatus next) {
        List<String> allowedFrom = Arrays.stream(OrderStatus.values())
                .filter(s -> s.canTransitionTo(next))
                .map(Enum::name)
                .toList();
        int updated = allowedFrom.isEmpty() ? 0 : jdbc.sql("""
                UPDATE orders SET status = :next, updated_at = :now
                WHERE id = :id AND status IN (:allowedFrom)
                """)
                .param("next", next.name())
                .param("now", System.currentTimeMillis())
                .param("id", id)
                .param("allowedFrom", allowedFrom)
                .update();
        if (updated == 0) {
            Order current = find(id).orElseThrow(() -> ApiException.notFound("Order"));
            throw new ApiException(HttpStatus.CONFLICT,
                    "Order is " + current.status() + " and can't move to " + next);
        }
        Order order = find(id).orElseThrow(() -> ApiException.notFound("Order"));
        events.publishEvent(new OrderChangedEvent(order));
        return order;
    }

    private Order map(ResultSet rs, int row) throws SQLException {
        OrderStatus status;
        try {
            status = OrderStatus.valueOf(rs.getString("status"));
        } catch (IllegalArgumentException e) {
            status = OrderStatus.PLACED;
        }
        return new Order(
                rs.getString("id"),
                rs.getString("code"),
                rs.getString("session_id"),
                rs.getString("customer_name"),
                rs.getString("table_no"),
                json.items(rs.getString("items")),
                rs.getInt("total"),
                rs.getInt("prep_minutes"),
                status,
                rs.getLong("created_at"),
                rs.getLong("updated_at"));
    }
}
