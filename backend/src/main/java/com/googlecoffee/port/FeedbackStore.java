package com.googlecoffee.port;

import com.googlecoffee.model.Feedback;
 
import java.util.List;
 
public interface FeedbackStore {
    void save(Feedback feedback);
 
    List<Feedback> since(long sinceMillis, int limit);
}
