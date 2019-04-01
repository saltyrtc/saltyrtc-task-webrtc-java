package org.saltyrtc.tasks.webrtc.utils;

import org.saltyrtc.client.annotations.NonNull;
import org.saltyrtc.tasks.webrtc.exceptions.IllegalStateError;
import org.saltyrtc.tasks.webrtc.exceptions.UntiedException;
import org.saltyrtc.tasks.webrtc.transport.SignalingTransportHandler;
import org.saltyrtc.tasks.webrtc.transport.SignalingTransportLink;

import java.nio.ByteBuffer;

public class SignalingLoopbackDataChannel extends LoopbackDataChannel<SignalingLoopbackDataChannel>
    implements SignalingTransportHandler {
    @NonNull private final SignalingTransportLink link;

    public SignalingLoopbackDataChannel(@NonNull final SignalingTransportLink link) {
        this.link = link;
    }

    @Override
    public void close() {
        if (this.closed) {
            throw new IllegalStateError("Already closed");
        }
        if (this.remote == null) {
            throw new IllegalStateError("Loopback data channel not attached");
        }

        // Move both into closing, then into closed
        this.closed = true;
        this.remote.closed = true;
        try {
            this.link.closing();
            this.remote.link.closing();
            this.link.closed();
            this.remote.link.closed();
        } catch (UntiedException error) {
            throw new IllegalStateError(error.getMessage());
        }
    }

    protected void receive(@NonNull final ByteBuffer message) {
        super.receive(message);
        try {
            this.link.receive(message);
        } catch (UntiedException error) {
            throw new IllegalStateError(error.getMessage());
        }
    }
}
