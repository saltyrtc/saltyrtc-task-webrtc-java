package org.saltyrtc.tasks.webrtc.exceptions;

public class IllegalStateError extends Error {
    public IllegalStateError() {
        super();
    }

    public IllegalStateError(String detailMessage) {
        super(detailMessage);
    }

    public IllegalStateError(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public IllegalStateError(Throwable throwable) {
        super(throwable);
    }
}
