package com.googlecoffee.service;

import com.google.api.core.ApiFuture;
import com.googlecoffee.web.ApiException;
import org.springframework.http.HttpStatus;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Blocks on a Firestore future with a timeout and turns failures into clean API errors. */
public final class Fs {
    private Fs() {}

    public static <T> T await(ApiFuture<T> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Request interrupted");
        } catch (ExecutionException | TimeoutException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof ApiException api) throw api;
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Database unavailable: " + cause.getClass().getSimpleName());
        }
    }
}
