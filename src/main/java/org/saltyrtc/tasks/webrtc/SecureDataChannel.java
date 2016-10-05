/*
 * Copyright (c) 2016 Threema GmbH / SaltyRTC Contributors
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc;

import org.saltyrtc.chunkedDc.Chunker;
import org.saltyrtc.chunkedDc.Unchunker;
import org.saltyrtc.client.annotations.NonNull;
import org.saltyrtc.client.annotations.Nullable;
import org.saltyrtc.client.cookie.Cookie;
import org.saltyrtc.client.exceptions.CryptoFailedException;
import org.saltyrtc.client.exceptions.OverflowException;
import org.saltyrtc.client.exceptions.ValidationError;
import org.saltyrtc.client.keystore.Box;
import org.saltyrtc.client.nonce.CombinedSequence;
import org.saltyrtc.client.nonce.CombinedSequencePair;
import org.saltyrtc.tasks.webrtc.nonce.DataChannelNonce;
import org.slf4j.Logger;
import org.webrtc.DataChannel;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A wrapper for a DataChannel that will encrypt and decrypt data on the fly.
 *
 * It should match the API of the WebRTC `DataChannel`.
 *
 * Unfortunately, the `DataChannel` class does not provide an interface that we could implement.
 * https://bugs.chromium.org/p/webrtc/issues/detail?id=6221
 */
class SecureDataChannel {

    // Logger
    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger("SaltyRTC.SecureDataChannel");

    // Chunking
    private static final int CHUNK_COUNT_GC = 32;
    private static final int CHUNK_MAX_AGE = 60000;
    private final AtomicInteger messageNumber = new AtomicInteger(0);
    private final AtomicInteger chunkCount = new AtomicInteger(0);
    private final Unchunker unchunker = new Unchunker();

    @NonNull
    private final DataChannel dc;
    @NonNull
    private final WebRTCTask task;
    @NonNull
    private final Cookie ownCookie;
    @Nullable
    private Cookie peerCookie;
    @NonNull
    private final CombinedSequencePair csnPair;
    @Nullable
    private Long lastIncomingCsn;
    @Nullable
    private DataChannel.Observer observer;

    public SecureDataChannel(@NonNull DataChannel dc, @NonNull WebRTCTask task) {
        this.dc = dc;
        this.task = task;
        this.ownCookie = new Cookie();
        this.csnPair = new CombinedSequencePair();

        // Register a message listener for the unchunker
        this.unchunker.onMessage(new Unchunker.MessageListener() {
            @Override
            public void onMessage(ByteBuffer buffer) {
                SecureDataChannel.this.onMessage(buffer);
            }
        });
    }

    /**
     * Register a new data channel observer.
     *
     * It will be notified when something changes, e.g. when a new message
     * arrives or if the state changes.
     */
    public void registerObserver(final DataChannel.Observer observer) {
        this.observer = observer;
        this.dc.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long l) {
                observer.onBufferedAmountChange(l);
            }

            @Override
            public void onStateChange() {
                observer.onStateChange();
            }

            @Override
            public void onMessage(DataChannel.Buffer buffer) {
                LOG.debug("Received chunk");

                // Register the chunk. Once the message is complete, the original
                // observer will be called in the `onMessage` method.
                SecureDataChannel.this.unchunker.add(buffer.data);

                // Clean up old chunks regularly
                if (SecureDataChannel.this.chunkCount.getAndIncrement() > CHUNK_COUNT_GC) {
                    SecureDataChannel.this.unchunker.gc(CHUNK_MAX_AGE);
                    SecureDataChannel.this.chunkCount.set(0);
                }
            }
        });
    }

    public void unregisterObserver() {
        this.observer = null;
        this.dc.unregisterObserver();
    }

    private void onMessage(ByteBuffer buffer) {
        LOG.debug("Decrypting incoming data...");

        final Box box = new Box(buffer, DataChannelNonce.TOTAL_LENGTH);

        // Validate nonce
        try {
            this.validateNonce(new DataChannelNonce(ByteBuffer.wrap(box.getNonce())));
        } catch (ValidationError e) {
            LOG.error("Invalid nonce: " + e);
            LOG.error("Closing data channel");
            this.close();
            return;
        }

        // Decrypt data
        final byte[] data;
        try {
            data = this.task.getSignaling().decryptFromPeer(box);
        } catch (CryptoFailedException e) {
            LOG.error("Could not decrypt incoming data: ", e);
            return;
        }

        // Pass decrypted data to original observer
        DataChannel.Buffer decryptedBuffer =
                new DataChannel.Buffer(ByteBuffer.wrap(data), true);
        if (this.observer != null) {
            this.observer.onMessage(decryptedBuffer);
        } else {
            // TODO: Cache message?
            LOG.warn("Received new message, but no observer is configured.");
        }
    }

    /**
     * Encrypt and send a message through the data channel.
     *
     * @return a binary flag that indicates whether the message could be sent.
     */
    public boolean send(DataChannel.Buffer buffer) {
        LOG.debug("Encrypting outgoing data...");

        // Encrypt
        final Box box;
        try {
            final byte[] data = buffer.data.array();
            box = this.encryptData(data);
        } catch (CryptoFailedException e) {
            LOG.error("Could not encrypt outgoing data: ", e);
            return false;
        } catch (OverflowException e) {
            LOG.error("CSN overflow: ", e);
            LOG.error("Closing data channel");
            this.close();
            return false;
        }
        final ByteBuffer encryptedBytes = ByteBuffer.wrap(box.toBytes());

        // Chunkify
        // TODO: Don't chunkify if chunk size is 0
        final int msgId = this.messageNumber.getAndIncrement();
        Chunker chunker = new Chunker(msgId, encryptedBytes, this.task.getMaxPacketSize());

        // Send chunks
        while (chunker.hasNext()) {
            final DataChannel.Buffer out = new DataChannel.Buffer(chunker.next(), true);
            if (!this.dc.send(out)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate the nonce of incoming messages.
     */
    private void validateNonce(DataChannelNonce nonce) throws ValidationError {
        // Make sure cookies are not the same
        if (nonce.getCookie().equals(this.ownCookie)) {
            throw new ValidationError("Local and remote cookies are equal");
        }

        // If this is the first message, store peer cookie
        if (this.peerCookie == null) {
            this.peerCookie = nonce.getCookie();
        }

        // Otherwise make sure the peer cookie didn't change
        else if (!nonce.getCookie().equals(this.peerCookie)) {
            throw new ValidationError("Remote cookie changed");
        }

        // Make sure that two consecutive incoming messages do not have the exact same CSN
        if (this.lastIncomingCsn != null && nonce.getCombinedSequence() == this.lastIncomingCsn) {
            throw new ValidationError("CSN reuse detected!");
        }

        // Validate data channel id
        if (nonce.getChannelId() != this.dc.id()) {
            throw new ValidationError("Data channel id in nonce does not match actual data channel id");
        }

        // OK!
        this.lastIncomingCsn = nonce.getCombinedSequence();
    }

    /**
     * Encrypt arbitrary data for the peer using the session keys.
     *
     * @param data Plain data bytes.
     * @return Encrypted box.
     */
    @Nullable
    private Box encryptData(@NonNull byte[] data) throws CryptoFailedException, OverflowException {
        // Get next CSN
        final CombinedSequence csn = this.csnPair.getOurs().next();

        // Create nonce
        final DataChannelNonce nonce = new DataChannelNonce(
            this.ownCookie.getBytes(),
            this.dc.id(),
            csn.getOverflow(), csn.getSequenceNumber());

        // Encrypt
        return this.task.getSignaling().encryptForPeer(data, nonce.toBytes());
    }

    public String label() {
        return this.dc.label();
    }

    public int id() {
        return this.dc.id();
    }

    public DataChannel.State state() {
        return this.dc.state();
    }

    public long bufferedAmount() {
        return this.dc.bufferedAmount();
    }

    public void dispose() {
        this.dc.dispose();
    }

    public void close() {
        // TODO: Send close msg?
        this.dc.close();
    }

}
