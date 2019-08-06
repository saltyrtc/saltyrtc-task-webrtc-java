/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.transport;

import org.saltyrtc.client.annotations.NonNull;

import java.nio.ByteBuffer;

/**
 * An implementation of this handler must be provided by the application
 * in order to hand over a signalling channel to a dedicated data channel
 * controlled by the application.
 *
 * It contains a collection of functions called by the task to communicate
 * with the dedicated data channel.
 */
public interface SignalingTransportHandler {
    /**
     * Will be called to retrieve the maximum amount of bytes that can be
     * sent in a single message.
     */
    long getMaxMessageSize();

    /**
     * Will be called to start the closing procedure of the underlying data
     * channel.
     */
    void close();

    /**
     * Will be called to send a message on the underlying data channel.
     *
     * @param message A signalling message that SHALL NOT be modified
     *   or reordered by the application. It is already encrypted and
     *   obeys `maxMessageSize`. Note that `message` MUST be immediately
     *   handled or copied since the underlying buffer will be reused.
     */
    void send(@NonNull ByteBuffer message);
}
