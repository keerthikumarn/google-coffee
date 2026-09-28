package com.googlecoffee.adapter.local;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecoffee.model.OrderItem;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/** JSON <-> JSONB column helpers for the Postgres adapters. */
@Component
@Profile("local")
public class JsonColumns {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private static final TypeReference<List<OrderItem>> ITEMS = new TypeReference<>() {};

    private final ObjectMapper mapper;

    public JsonColumns(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise value to JSON", e);
        }
    }

    public List<String> strings(String json) {
        return read(json, STRINGS);
    }

    public List<OrderItem> items(String json) {
        return read(json, ITEMS);
    }

    private <T> List<T> read(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt JSON column", e);
        }
    }
}
