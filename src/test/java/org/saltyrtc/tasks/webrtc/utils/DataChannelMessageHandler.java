package org.saltyrtc.tasks.webrtc.utils;

import org.saltyrtc.client.annotations.NonNull;

import java.nio.ByteBuffer;

public interface DataChannelMessageHandler {
    void handle(@NonNull ByteBuffer message);
}
