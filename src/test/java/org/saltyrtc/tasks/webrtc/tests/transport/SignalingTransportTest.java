/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.tests.transport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.support.ReflectionSupport;
import org.saltyrtc.chunkedDc.Common;
import org.saltyrtc.client.annotations.NonNull;
import org.saltyrtc.client.annotations.Nullable;
import org.saltyrtc.client.crypto.CryptoException;
import org.saltyrtc.client.exceptions.OverflowException;
import org.saltyrtc.client.keystore.Box;
import org.saltyrtc.client.signaling.state.HandoverState;
import org.saltyrtc.client.signaling.state.SignalingState;
import org.saltyrtc.tasks.webrtc.WebRTCTask;
import org.saltyrtc.tasks.webrtc.WebRTCTaskVersion;
import org.saltyrtc.tasks.webrtc.crypto.DataChannelCryptoContext;
import org.saltyrtc.tasks.webrtc.exceptions.IllegalStateError;
import org.saltyrtc.tasks.webrtc.transport.SignalingTransportHandler;
import org.saltyrtc.tasks.webrtc.transport.SignalingTransport;
import org.saltyrtc.tasks.webrtc.transport.SignalingTransportLink;
import org.saltyrtc.tasks.webrtc.utils.NullHandler;
import org.saltyrtc.tasks.webrtc.utils.NullSignaling;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.saltyrtc.tasks.webrtc.utils.Assertions.assertListOfBytesEquals;

/**
 * Fakes the signalling and simulates a state where the task has kicked in and
 * the handover process has already been started.
 *
 * Keeps track of the state and stores received peer messages.
 */
class FakeSignaling extends NullSignaling {
    @NonNull private SignalingState state = SignalingState.TASK;
    @NonNull private HandoverState handoverState = new HandoverState();
    @NonNull public List<byte[]> messages = new ArrayList<>();

    FakeSignaling() {
        this.handoverState.setPeer(true);
    }

    @Override
    public SignalingState getState() {
        return this.state;
    }

    @Override
    public void setState(SignalingState state) {
        if (state != null) {
            this.state = state;
        }
    }

    @Override
    public HandoverState getHandoverState() {
        return this.handoverState;
    }

    @Override
    public void onSignalingPeerMessage(byte[] message) {
        this.messages.add(message);
    }

    @Override
    public Box encryptForPeer(byte[] data, byte[] nonce) throws CryptoException {
        // Don't actually encrypt
        return new Box(nonce, data);
    }

    @Override
    public byte[] decryptFromPeer(Box box) throws CryptoException {
        // Don't actually decrypt
        return box.getData();
    }
}

class FakeWebRTCTask extends WebRTCTask {
    public boolean closed = false;
    @Nullable public SignalingTransport transport;

    FakeWebRTCTask() {
        super(WebRTCTaskVersion.V0, true, 65536);
    }

    @Override
    public void close(int reason) {
        this.transport.close();
        this.closed = true;
    }
}

class TransportTuple {
    @NonNull public final SignalingTransportLink link;
    @NonNull public final SignalingTransport transport;

    public TransportTuple(@NonNull final SignalingTransportLink link, @NonNull final SignalingTransport transport) {
        this.link = link;
        this.transport = transport;
    }
}

@DisplayName("SignalingTransport")
class SignalingTransportTest {
    private static final int ID = 1337;

    // Defines a maximum payload size of 2 bytes per chunk
    private static final long MAX_MESSAGE_SIZE = Common.HEADER_LENGTH + 2;

    // Expected message
    private static final byte[] MESSAGE = new byte[] { 1, 2, 3, 4, 5, 6 };

    // Expected chunks (ignoring the first 12 chunks that contain the nonce)
    private static final List<byte []> CHUNKS = Collections.unmodifiableList(Arrays.asList(
        new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 12, 1, 2 },
        new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 13, 3, 4 },
        new byte[] { 1, 0, 0, 0, 0, 0, 0, 0, 14, 5, 6 }
    ));

    // Instances
    @NonNull private FakeSignaling signaling;
    @NonNull private FakeWebRTCTask task;
    @NonNull private DataChannelCryptoContext context;

    @BeforeEach
    void setUp() {
        this.signaling = new FakeSignaling();
        this.task = new FakeWebRTCTask();
        this.context = new DataChannelCryptoContext(ID, this.signaling);
    }

    @NonNull TransportTuple createTransport(@NonNull final SignalingTransportHandler handler) {
        final SignalingTransportLink link = new SignalingTransportLink(ID, "fake-protocol");
        final SignalingTransport transport = new SignalingTransport(
            link, handler, this.task, this.signaling, this.context, 20);
        this.task.transport = transport;
        return new TransportTuple(link, transport);
    }

    @Test
    @DisplayName("binds and forwards closing")
    void testClosing() {
        final NullHandler handler = new NullHandler();
        final TransportTuple tuple = this.createTransport(handler);

        // Before closed
        assertEquals(SignalingState.TASK, this.signaling.getState());

        // Close
        tuple.link.closed();
        assertEquals(SignalingState.CLOSED, this.signaling.getState());
    }

    @Test
    @DisplayName("sends a message encrypted and in chunks")
    void testSendEncryptedMessageInChunks() throws OverflowException, CryptoException {
        final List<byte[]> actualChunks = new ArrayList<>();
        final NullHandler handler = new NullHandler() {
            @Override
            public long getMaxMessageSize() {
                return MAX_MESSAGE_SIZE;
            }

            @Override
            public void send(@NonNull ByteBuffer message) {
                actualChunks.add(message.array().clone());
            }
        };
        final TransportTuple tuple = this.createTransport(handler);

        // Send message
        tuple.transport.send(MESSAGE);

        // Compare chunks
        assertListOfBytesEquals(CHUNKS, actualChunks.subList(12, actualChunks.size()));
    }

    @Test
    @DisplayName("binds, reassembles and decrypts a message")
    void testReassemblesAndReceivesMessage() {
        final NullHandler handler = new NullHandler() {
            @Override
            public long getMaxMessageSize() {
                return MAX_MESSAGE_SIZE;
            }
        };
        final TransportTuple tuple = this.createTransport(handler);

        // Before nonce and chunks
        assertEquals(0, this.signaling.messages.size());

        // Add fake nonce
        for (byte i = 0; i < 8; ++i) {
            // Cookie
            tuple.link.receive(ByteBuffer.wrap(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, i, (byte) 255, (byte) 255 }));
        }
        // Data channel id: 1337
        tuple.link.receive(ByteBuffer.wrap(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 8, 5, 57 }));
        // Overflow number: 0
        tuple.link.receive(ByteBuffer.wrap(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 9, 0, 0 }));
        // Sequence number: 42
        tuple.link.receive(ByteBuffer.wrap(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 10, 0, 0 }));
        tuple.link.receive(ByteBuffer.wrap(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 11, 0, 42 }));

        // Add first two chunks
        assertEquals(0, this.signaling.messages.size());
        tuple.link.receive(ByteBuffer.wrap(CHUNKS.get(0)));
        assertEquals(0, this.signaling.messages.size());
        tuple.link.receive(ByteBuffer.wrap(CHUNKS.get(1)));
        assertEquals(0, this.signaling.messages.size());

        // Add last chunk
        tuple.link.receive(ByteBuffer.wrap(CHUNKS.get(2)));
        assertEquals(1, this.signaling.messages.size());
        assertArrayEquals(MESSAGE, this.signaling.messages.get(0));
    }

    @Test
    @DisplayName("closes on error correctly")
    void testCloseOnError() throws OverflowException, CryptoException {
        final NullHandler handler = new NullHandler() {
            @Override
            public long getMaxMessageSize() {
                return MAX_MESSAGE_SIZE;
            }

            @Override
            public void close() {
                throw new RuntimeException("still nope");
            }

            @Override
            public void send(@NonNull ByteBuffer message) {
                throw new RuntimeException("nope");
            }
        };
        final TransportTuple tuple = this.createTransport(handler);

        // Trigger failure while sending
        tuple.transport.send(MESSAGE);

        // Ensure untied
        IllegalStateError error;
        error = assertThrows(IllegalStateError.class, tuple.link::closing);
        assertEquals("Not tied to a SignalingTransport", error.getMessage());
        error = assertThrows(IllegalStateError.class, tuple.link::closed);
        assertEquals("Not tied to a SignalingTransport", error.getMessage());
        error = assertThrows(IllegalStateError.class, () -> tuple.link.receive(ByteBuffer.wrap(new byte [] {})));
        assertEquals("Not tied to a SignalingTransport", error.getMessage());
    }

    @Test
    @DisplayName("queues messages until handover requested by remote")
    @SuppressWarnings("unchecked")
    void testMessageQueueingBeforeHandover() throws Exception {
        final NullHandler handler = new NullHandler() {
            @Override
            public long getMaxMessageSize() {
                return MAX_MESSAGE_SIZE;
            }
        };
        this.signaling.getHandoverState().setPeer(false);
        final TransportTuple tuple = this.createTransport(handler);

        // Before nonce and chunks
        assertEquals(0, this.signaling.messages.size());

        // Add fake nonce
        for (byte i = 0; i < 8; ++i) {
            // Cookie
            tuple.link.receive(ByteBuffer.wrap(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, i, (byte) 255, (byte) 255 }));
        }
        // Data channel id: 1337
        tuple.link.receive(ByteBuffer.wrap(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 8, 5, 57 }));
        // Overflow number: 0
        tuple.link.receive(ByteBuffer.wrap(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 9, 0, 0 }));
        // Sequence number: 42
        tuple.link.receive(ByteBuffer.wrap(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 10, 0, 0 }));
        tuple.link.receive(ByteBuffer.wrap(new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 11, 0, 42 }));
        // Add all chunks
        for (final byte[] chunk: CHUNKS) {
            tuple.link.receive(ByteBuffer.wrap(chunk));
        }

        // Expect messages to be queued
        assertEquals(0, this.signaling.messages.size());
        List<byte[]> messageQueue;
        messageQueue = (List<byte[]>) ReflectionSupport.tryToReadFieldValue(
            SignalingTransport.class.getDeclaredField("messageQueue"), tuple.transport).get();
        assertNotNull(messageQueue);
        assertArrayEquals(MESSAGE, messageQueue.get(0));

        // Flush queue
        final IllegalStateError error = assertThrows(IllegalStateError.class, tuple.transport::flushMessageQueue);
        assertEquals("Remote did not request handover", error.getMessage());
        this.signaling.getHandoverState().setPeer(true);
        tuple.transport.flushMessageQueue();

        // Expect messages to be processed now
        messageQueue = (List<byte[]>) ReflectionSupport.tryToReadFieldValue(
            SignalingTransport.class.getDeclaredField("messageQueue"), tuple.transport).get();
        assertNull(messageQueue);
        assertEquals(1, this.signaling.messages.size());
        assertArrayEquals(MESSAGE, this.signaling.messages.get(0));
    }
}
