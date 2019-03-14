package org.saltyrtc.tasks.webrtc.utils;

import org.saltyrtc.client.annotations.NonNull;
import org.saltyrtc.client.annotations.Nullable;

import java.nio.ByteBuffer;

public class LoopbackDataChannel<RT extends LoopbackDataChannel> {
    @Nullable RT remote;
    @Nullable private DataChannelMessageHandler messageHandler;
    boolean closed = false;

    public void attach(@NonNull final RT other) {
        this.remote = other;
    }

    public void setMessageHandler(@NonNull final DataChannelMessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    public long getMaxMessageSize() {
        return 262144;
    }

    public void close() {
        if (this.closed) {
            throw new RuntimeException("Already closed");
        }
        if (this.remote == null) {
            throw new RuntimeException("Loopback data channel not attached");
        }

        // Move both into closed
        this.closed = true;
        this.remote.closed = true;
    }

    public void send(@NonNull final ByteBuffer message) {
        if (this.closed) {
            throw new RuntimeException("Already closed");
        }
        if (this.remote == null) {
            throw new RuntimeException("Loopback data channel not attached");
        }

        // Forward to the remote channel's link
        this.remote.receive(message);
    }

    protected void receive(@NonNull final ByteBuffer message) {
        if (this.messageHandler != null) {
            this.messageHandler.handle(message);
        }
    }
}
