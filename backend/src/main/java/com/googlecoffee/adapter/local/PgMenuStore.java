package com.googlecoffee.adapter.local;

import com.googlecoffee.model.MenuItem;
import com.googlecoffee.port.MenuStore;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Component
@Profile("local")
public class PgMenuStore implements MenuStore {

    private final JdbcClient jdbc;
    private final JsonColumns json;

    public PgMenuStore(JdbcClient jdbc, JsonColumns json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<MenuItem> findAll() {
        return jdbc.sql("SELECT id, name, category, description, price, prep_minutes, tags::text AS tags, popular FROM menu_items")
                .query(this::map)
                .list();
    }

    @Override
    @Transactional
    public void saveAll(List<MenuItem> items) {
        for (MenuItem i : items) {
            jdbc.sql("""
                    INSERT INTO menu_items (id, name, category, description, price, prep_minutes, tags, popular)
                    VALUES (:id, :name, :category, :description, :price, :prep, CAST(:tags AS jsonb), :popular)
                    ON CONFLICT (id) DO NOTHING
                    """)
                    .param("id", i.id())
                    .param("name", i.name())
                    .param("category", i.category())
                    .param("description", i.description())
                    .param("price", i.price())
                    .param("prep", i.prepMinutes())
                    .param("tags", json.write(i.tags()))
                    .param("popular", i.popular())
                    .update();
        }
    }

    private MenuItem map(ResultSet rs, int row) throws SQLException {
        return new MenuItem(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getString("description"),
                rs.getInt("price"),
                rs.getInt("prep_minutes"),
                json.strings(rs.getString("tags")),
                rs.getBoolean("popular"));
    }
}