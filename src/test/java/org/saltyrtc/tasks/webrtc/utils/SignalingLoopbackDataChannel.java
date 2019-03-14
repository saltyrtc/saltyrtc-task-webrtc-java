package org.saltyrtc.tasks.webrtc.utils;

import org.saltyrtc.client.annotations.NonNull;
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
            throw new RuntimeException("Already closed");
        }
        if (this.remote == null) {
            throw new RuntimeException("Loopback data channel not attached");
        }

        // Move both into closing, then into closed
        this.closed = true;
        this.link.closing();
        this.remote.closed = true;
        this.remote.link.closing();
        this.link.closed();
        this.remote.link.closed();
    }

    protected void receive(@NonNull final ByteBuffer message) {
        super.receive(message);
        this.link.receive(message);
    }
}
