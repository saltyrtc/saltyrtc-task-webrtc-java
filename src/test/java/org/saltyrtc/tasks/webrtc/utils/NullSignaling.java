/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.utils;

import org.saltyrtc.client.crypto.CryptoException;
import org.saltyrtc.client.exceptions.ConnectionException;
import org.saltyrtc.client.exceptions.SignalingException;
import org.saltyrtc.client.keystore.Box;
import org.saltyrtc.client.messages.c2c.TaskMessage;
import org.saltyrtc.client.signaling.SignalingInterface;
import org.saltyrtc.client.signaling.SignalingRole;
import org.saltyrtc.client.signaling.state.HandoverState;
import org.saltyrtc.client.signaling.state.SignalingState;

/**
 * Implements the SignalingInterface and does nothing by default.
 */
public class NullSignaling implements SignalingInterface {
    @Override
    public SignalingState getState() {
        return null;
    }

    @Override
    public void setState(SignalingState state) {}

    @Override
    public HandoverState getHandoverState() {
        return null;
    }

    @Override
    public SignalingRole getRole() {
        return null;
    }

    @Override
    public void sendTaskMessage(TaskMessage msg) throws SignalingException, ConnectionException {}

    @Override
    public Box encryptForPeer(byte[] data, byte[] nonce) throws CryptoException {
        return null;
    }

    @Override
    public byte[] decryptFromPeer(Box box) throws CryptoException {
        return new byte[0];
    }

    @Override
    public void onSignalingPeerMessage(byte[] decryptedBytes) {}

    @Override
    public void sendClose(int reason) {}

    @Override
    public void resetConnection(Integer reason) {}
}
