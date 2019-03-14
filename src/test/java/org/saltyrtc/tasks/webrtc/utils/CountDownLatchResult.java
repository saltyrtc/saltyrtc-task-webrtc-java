package org.saltyrtc.tasks.webrtc.utils;

import org.saltyrtc.client.annotations.NonNull;
import org.saltyrtc.client.annotations.Nullable;

import java.util.concurrent.CountDownLatch;

/**
 * A countdown latch with an additional result attached to it.
 */
public class CountDownLatchResult<T> extends CountDownLatch {
    private @Nullable Exception exception;
    private @Nullable T result;

    public CountDownLatchResult(int count) {
        super(count);
    }

    public @NonNull CountDownLatchResult<T> setResult(T result) {
        if (this.exception != null) {
            throw new RuntimeException("Cannot set result, exception has been set");
        }
        this.result = result;
        return this;
    }

    public @NonNull CountDownLatchResult<T> setException(Exception exception) {
        if (this.exception != null) {
            throw new RuntimeException("Cannot set exception, already set");
        }
        this.exception = exception;
        return this;
    }

    public T getResult() throws Exception {
        if (this.exception != null) {
            throw this.exception;
        }
        return this.result;
    }
}
