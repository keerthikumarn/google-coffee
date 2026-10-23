package com.googlecoffee.support;

import com.googlecoffee.model.Feedback;
import com.googlecoffee.port.FeedbackStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** In-memory FeedbackStore with the same query semantics as the Postgres adapter. */
public class InMemoryFeedbackStore implements FeedbackStore {

    private final List<Feedback> saved = new ArrayList<>();

    public List<Feedback> all() {
        return List.copyOf(saved);
    }

    @Override
    public void save(Feedback feedback) {
        saved.add(feedback);
    }

    @Override
    public List<Feedback> since(long sinceMillis, int limit) {
        return saved.stream()
                .filter(f -> f.createdAt() >= sinceMillis)
                .sorted(Comparator.comparingLong(Feedback::createdAt).reversed())
                .limit(limit)
                .toList();
    }
}
