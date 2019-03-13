/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.utils;

import org.saltyrtc.client.annotations.NonNull;
import org.saltyrtc.tasks.webrtc.transport.SignalingTransportHandler;

import java.nio.ByteBuffer;

/**
 * Allows to mock the handler class.
 */
public class NullHandler implements SignalingTransportHandler {
    @Override
    public long getMaxMessageSize() {
        return 0;
    }

    @Override
    public void close() {}

    @Override
    public void send(@NonNull ByteBuffer message) {}
}
