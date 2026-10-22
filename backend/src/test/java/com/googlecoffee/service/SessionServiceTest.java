package com.googlecoffee.service;

import com.googlecoffee.model.Session;
import com.googlecoffee.support.InMemorySessionStore;
import com.googlecoffee.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionServiceTest {

    private SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService(new InMemorySessionStore());
    }

    @Test
    void createTrimsNameAndTableAndGeneratesUniqueIds() {
        Session a = service.create("  Asha ", " 7 ", List.of());
        Session b = service.create("Asha", "7", List.of());

        assertEquals("Asha", a.name());
        assertEquals("7", a.table());
        assertNotEquals(a.id(), b.id());
    }

    @Test
    void createKeepsOnlyKnownPreferencesWithoutDuplicates() {
        Session s = service.create("Asha", "7", List.of("vegan", "gluten-free", "vegan", "cold"));

        assertEquals(List.of("vegan", "cold"), s.preferences());
    }

    @Test
    void createdSessionCanBeFetched() {
        Session s = service.create("Asha", "7", List.of("vegan"));

        assertEquals(s, service.get(s.id()));
    }

    @Test
    void getRejectsBlankIdAsBadRequest() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.get(" ")).status());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ApiException.class, () -> service.get(null)).status());
    }

    @Test
    void getUnknownIdIsNotFound() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.get("nope")).status());
    }

    @Test
    void updatePreferencesChangesOnlyPreferences() {
        Session original = service.create("Asha", "7", List.of("vegan"));

        Session updated = service.updatePreferences(original.id(), List.of("low-sugar", "bogus"));

        assertEquals(List.of("low-sugar"), updated.preferences());
        assertEquals(original.id(), updated.id());
        assertEquals(original.name(), updated.name());
        assertEquals(original.table(), updated.table());
        assertEquals(original.createdAt(), updated.createdAt());
        assertEquals(updated, service.get(original.id()));
    }

    @Test
    void updatePreferencesOfUnknownSessionIsNotFound() {
        assertEquals(HttpStatus.NOT_FOUND,
                assertThrows(ApiException.class, () -> service.updatePreferences("nope", List.of())).status());
    }
}
