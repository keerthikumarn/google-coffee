package com.googlecoffee.adapter.local;

import com.googlecoffee.model.Feedback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PgFeedbackStoreTest extends PostgresTestBase {

    private final PgFeedbackStore store = new PgFeedbackStore(jdbc);

    @BeforeEach
    void createSession() {
        jdbc.sql("INSERT INTO sessions (id, name, table_no, created_at) VALUES ('s1', 'Guest', '1', 0)").update();
    }

    private static Feedback feedback(String id, int rating, long createdAt) {
        return new Feedback(id, "s1", "order-1", "7", rating, "lovely", createdAt);
    }

    @Test
    void savedFeedbackRoundTrips() {
        Feedback f = feedback("f1", 5, 1_000);

        store.save(f);

        assertEquals(List.of(f), store.since(0, 10));
    }

    @Test
    void blankOrderIdAndNullCommentAreStoredAsNoOrderAndEmptyComment() {
        store.save(new Feedback("f1", "s1", " ", "7", 4, null, 1_000));
        store.save(new Feedback("f2", "s1", null, "7", 4, "ok", 2_000));

        List<Feedback> found = store.since(0, 10);
        assertEquals(2, found.size());
        assertTrue(found.stream().allMatch(f -> f.orderId() == null));
        assertEquals("", found.stream().filter(f -> f.id().equals("f1")).findFirst().orElseThrow().comment());
    }

    @Test
    void sinceFiltersByTimeNewestFirstAndHonoursTheLimit() {
        store.save(feedback("old", 3, 1_000));
        store.save(feedback("mid", 4, 2_000));
        store.save(feedback("new", 5, 3_000));

        assertEquals(List.of("new", "mid"), store.since(2_000, 10).stream().map(Feedback::id).toList());
        assertEquals(List.of("new"), store.since(0, 1).stream().map(Feedback::id).toList());
    }

    @Test
    void ratingOutsideOneToFiveIsRejectedByTheDatabase() {
        assertThrows(DataIntegrityViolationException.class, () -> store.save(feedback("f0", 0, 1_000)));
        assertThrows(DataIntegrityViolationException.class, () -> store.save(feedback("f6", 6, 1_000)));
    }

    @Test
    void feedbackFromAnUnknownSessionIsRejected() {
        assertThrows(DataIntegrityViolationException.class,
                () -> store.save(new Feedback("f1", "ghost", null, "7", 5, "", 1_000)));
    }
}
