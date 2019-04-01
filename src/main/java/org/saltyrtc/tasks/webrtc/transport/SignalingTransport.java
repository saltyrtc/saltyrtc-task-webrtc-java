/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.transport;

import org.saltyrtc.chunkedDc.Chunker;
import org.saltyrtc.chunkedDc.Unchunker;
import org.saltyrtc.client.annotations.NonNull;
import org.saltyrtc.client.annotations.Nullable;
import org.saltyrtc.client.crypto.CryptoException;
import org.saltyrtc.client.exceptions.OverflowException;
import org.saltyrtc.client.exceptions.ProtocolException;
import org.saltyrtc.client.exceptions.ValidationError;
import org.saltyrtc.client.keystore.Box;
import org.saltyrtc.client.signaling.CloseCode;
import org.saltyrtc.client.signaling.SignalingInterface;
import org.saltyrtc.client.signaling.state.SignalingState;
import org.saltyrtc.tasks.webrtc.WebRTCTask;
import org.saltyrtc.tasks.webrtc.crypto.DataChannelCryptoContext;
import org.saltyrtc.tasks.webrtc.exceptions.IllegalStateError;
import org.saltyrtc.tasks.webrtc.DataChannelNonce;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the original signalling transport by binding to both the task's
 * `SignalingTransportLink` and the application's `SignalingTransportHandler`.
 *
 * This class handles the encryption and decryption as well as nonce
 * validation and chunking/unchunking.
 */
public class SignalingTransport {
    // Logging
    @NonNull private static final Logger LOG = org.slf4j.LoggerFactory.getLogger("SaltyRTC.WebRTC.SignalingTransport");

    // Underlying data channel and associated instances
    @NonNull private final SignalingTransportLink link;
    @NonNull private final SignalingTransportHandler handler;
    @NonNull private final WebRTCTask task;
    @NonNull private final SignalingInterface signaling;
    @NonNull private final DataChannelCryptoContext crypto;

    // Chunking
    private final int chunkLength;
    @NonNull private final Unchunker unchunker = new Unchunker();
    private long messageId = 0;

    // Incoming message queue
    @Nullable private List<byte[]> messageQueue;

    /**
     * Create a new signaling transport.
     *
     * @param link The signalling transport link of the task.
     * @param handler The signalling transport handler of the application.
     * @param task The WebRTC task instance.
     * @param signaling The signaling instance.
     * @param crypto A crypto context associated to the signaling transport's
     *   channel ID.
     * @param maxChunkLength The maximum amount of bytes used for a chunk.
     */
    public SignalingTransport(
        @NonNull final SignalingTransportLink link,
        @NonNull final SignalingTransportHandler handler,
        @NonNull final WebRTCTask task,
        @NonNull final SignalingInterface signaling,
        @NonNull final DataChannelCryptoContext crypto,
        final int maxChunkLength
    ) {
        this.link = link;
        this.handler = handler;
        this.task = task;
        this.signaling = signaling;
        this.crypto = crypto;

        // Determine chunk length
        if (this.handler.getMaxMessageSize() > Integer.MAX_VALUE) {
            this.chunkLength = maxChunkLength;
        } else {
            this.chunkLength = Math.min((int) this.handler.getMaxMessageSize(), maxChunkLength);
        }

        // Initialise message queue
        if (!this.signaling.getHandoverState().getPeer()) {
            this.messageQueue = new ArrayList<>();
        }

        // Bind unchunker events
        this.unchunker.onMessage(SignalingTransport.this::receiveMessage);

        // Tie to transport link
        this.link.tie(this);

        // Done
        LOG.info("Signaling transport created");
    }

    /**
     * Called when the underlying data channel's closing procedure has been
     * started.
     */
    public void closing() {
        // If handover has already happened, set the signalling state to closed
        LOG.info("Closing (remote)");
        if (this.signaling.getHandoverState().getAny()) {
            this.signaling.setState(SignalingState.CLOSING);
        }
    }

    /**
     * Called when the underlying data channel has been closed.
     */
    public void closed() {
        // If handover has already happened, set the signalling state to closed
        LOG.info("Closed (remote)");
        this.unbind();
        if (this.signaling.getHandoverState().getAny()) {
            this.signaling.setState(SignalingState.CLOSED);
        }
    }

    /**
     * Called when a chunk has been received on the underlying data channel.
     *
     * @param chunk The chunk. Note that the chunk MUST be considered
     *   transferred.
     */
    public void receiveChunk(@NonNull final ByteBuffer chunk) {
        LOG.debug("Received chunk");
        try {
            this.unchunker.add(chunk);
        } catch (IllegalArgumentException error) {
            LOG.error("Invalid chunk:", error);
            this.die();
        }
    }

    /**
     * Called when a message has been reassembled from chunks received on the
     * underlying data channel.
     *
     * @param message The reassembled message.
     */
    private void receiveMessage(@NonNull final ByteBuffer message) {
        LOG.debug("Received message");

        // Decrypt message
        final Box box = new Box(message, DataChannelNonce.TOTAL_LENGTH);
        final byte[] decrypted;
        try {
            decrypted = this.crypto.decrypt(box);
        } catch (ValidationError | ProtocolException error) {
            LOG.error("Invalid nonce:", error);
            this.die();
            return;
        } catch (CryptoException error) {
            LOG.error("Could not decrypt incoming data:", error);
            this.die();
            return;
        }

        // Queue message until the transport has been acknowledged by the
        // remote peer with a handover request.
        //
        // Note: This mechanism is required to prevent reordering of messages.
        if (!this.signaling.getHandoverState().getPeer()) {
            this.messageQueue.add(decrypted);
            return;
        }

        // Process message
        this.signaling.onSignalingPeerMessage(decrypted);
    }

    /**
     * Flush the queue of pending messages.
     *
     * This should be called once the remote peer has acknowledged the
     * transport with a handover request (i.e. a 'handover' message).
     *
     * @throws IllegalStateError in case the remote peer has not requested
     *   a handover.
     */
    public void flushMessageQueue() throws IllegalStateError {
        // Ensure handover has been requested
        if (!this.signaling.getHandoverState().getPeer()) {
            throw new IllegalStateError("Remote did not request handover");
        }

        // Flush
        for (final byte[] message: this.messageQueue) {
            this.signaling.onSignalingPeerMessage(message);
        }

        // Remove queue
        this.messageQueue = null;
    }

    /**
     * Send a signalling message on the underlying channel.
     *
     * This will encrypt the message first and then fragment the message into
     * chunks.
     *
     * @param message The signalling message to be sent.
     *
     * @throws OverflowException in case the sequence number would overflow.
     * @throws CryptoException in case the data could not be encrypted.
     */
    public void send(@NonNull final byte[] message) throws OverflowException, CryptoException {
        LOG.debug("Sending message");

        // Encrypt message
        final Box box = this.crypto.encrypt(message);
        final ByteBuffer encrypted = ByteBuffer.wrap(box.toBytes());

        // Split message into chunks
        final Chunker chunker = new Chunker(this.messageId++, encrypted, this.chunkLength);
        while (chunker.hasNext()) {
            // Send chunk
            LOG.debug("Sending chunk");
            try {
                this.handler.send(chunker.next());
            } catch (RuntimeException error) {
                LOG.error("Unable to send chunk:", error);
                this.die();
                return;
            }
        }
    }

    /**
     * Close the underlying data channel and unbind from all events.
     *
     * Note: This is the final state of the transport instance. No further
     *       events will be emitted to either the task or the signalling
     *       instance after this method returned.
     */
    public void close() {
        // Close data channel
        try {
            this.handler.close();
        } catch (RuntimeException error) {
            LOG.error("Unable to close data channel:", error);
        }
        LOG.info("Closed (local)");
        this.unbind();
    }

    /**
     * Closes the task abruptly due to a protocol error.
     */
    private void die() {
        LOG.warn("Closing task due to an error");

        // Close (implicitly closes the data channel as well)
        this.task.close(CloseCode.PROTOCOL_ERROR);
    }

    /**
     * Unbind from all events.
     */
    private void unbind() {
        // Untie from transport link
        this.link.untie();

        // Unbind unchunker events
        this.unchunker.onMessage(message -> {});
    }
}
