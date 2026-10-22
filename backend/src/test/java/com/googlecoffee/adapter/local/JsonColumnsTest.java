package com.googlecoffee.adapter.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecoffee.model.OrderItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonColumnsTest {

    private final JsonColumns json = new JsonColumns(new ObjectMapper());

    @Test
    void stringListsRoundTrip() {
        assertEquals(List.of("vegan", "cold"), json.strings(json.write(List.of("vegan", "cold"))));
    }

    @Test
    void orderItemsRoundTrip() {
        List<OrderItem> items = List.of(new OrderItem("espresso", "Espresso", 2, 160, "extra hot"));

        assertEquals(items, json.items(json.write(items)));
    }

    @Test
    void nullAndBlankColumnsReadAsEmptyLists() {
        assertEquals(List.of(), json.strings(null));
        assertEquals(List.of(), json.strings("  "));
        assertEquals(List.of(), json.items(null));
    }

    @Test
    void corruptJsonFailsLoudlyInsteadOfReturningPartialData() {
        assertThrows(IllegalStateException.class, () -> json.strings("{not json"));
    }
}
