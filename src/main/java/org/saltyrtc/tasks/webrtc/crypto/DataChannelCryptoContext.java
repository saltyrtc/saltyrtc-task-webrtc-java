/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.crypto;

import org.saltyrtc.client.annotations.NonNull;
import org.saltyrtc.client.annotations.Nullable;
import org.saltyrtc.client.cookie.CookiePair;
import org.saltyrtc.client.crypto.CryptoException;
import org.saltyrtc.client.exceptions.OverflowException;
import org.saltyrtc.client.exceptions.ProtocolException;
import org.saltyrtc.client.exceptions.ValidationError;
import org.saltyrtc.client.keystore.Box;
import org.saltyrtc.client.nonce.CombinedSequencePair;
import org.saltyrtc.client.nonce.CombinedSequenceSnapshot;
import org.saltyrtc.client.signaling.SignalingInterface;
import org.saltyrtc.tasks.webrtc.nonce.DataChannelNonce;

import java.nio.ByteBuffer;

/**
 * Can encrypt and decrypt data for a data channel with a specific id.
 *
 * This class is NOT thread-safe.
 */
public class DataChannelCryptoContext {
    @NonNull public static int OVERHEAD_LENGTH = 40;

    // SaltyRTC
    private final int channelId;
    @NonNull private final SignalingInterface signaling;
    @NonNull private final CookiePair cookiePair;
    @NonNull private final CombinedSequencePair csnPair;
    @Nullable private Long lastIncomingCsn;

    public DataChannelCryptoContext(final int channelId, @NonNull final SignalingInterface signaling) {
        this.channelId = channelId;
        this.signaling = signaling;
        this.cookiePair = new CookiePair();
        this.csnPair = new CombinedSequencePair();
    }

    /**
     * Encrypt data to be sent on the channel.
     *
     * @param data The bytes to be encrypted.
     *
     * @throws OverflowException in case the sequence number would overflow.
     * @throws CryptoException in case the data could not be encrypted.
     */
    public @NonNull Box encrypt(@NonNull final byte[] data) throws OverflowException, CryptoException {
        // Get next outgoing CSN
        final CombinedSequenceSnapshot csn = this.csnPair.getOurs().next();

        // Create nonce
        final DataChannelNonce nonce = new DataChannelNonce(
            this.cookiePair.getOurs().getBytes(),
            this.channelId,
            csn.getOverflow(), csn.getSequenceNumber());

        // Encrypt data
        return this.signaling.encryptForPeer(data, nonce.toBytes());
    }

    public @NonNull byte[] decrypt(@NonNull final Box box) throws ValidationError, ProtocolException, CryptoException {
        // Validate nonce
        final DataChannelNonce nonce;
        try {
            nonce = new DataChannelNonce(ByteBuffer.wrap(box.getNonce()));
        } catch (IllegalArgumentException e) {
            throw new ValidationError("Unable to create nonce, reason: " + e.toString());
        }

        // Make sure cookies are not the same
        if (nonce.getCookie().equals(this.cookiePair.getOurs())) {
            throw new ValidationError("Local and remote cookies are equal");
        }

        // If this is the first decrypt attempt, store peer cookie
        if (this.cookiePair.getTheirs() == null) {
            this.cookiePair.setTheirs(nonce.getCookie());
        }

        // Otherwise make sure the peer cookie didn't change
        else if (!nonce.getCookie().equals(this.cookiePair.getTheirs())) {
            throw new ValidationError("Remote cookie changed");
        }

        // Make sure that two consecutive incoming messages do not have the
        // exact same CSN.
        //
        // Note: This very loose check ensures that unreliable/unordered data
        //       channels do not break.
        if (this.lastIncomingCsn != null && nonce.getCombinedSequence() == this.lastIncomingCsn) {
            throw new ValidationError("CSN reuse detected!");
        }

        // Validate data channel id
        if (nonce.getChannelId() != this.channelId) {
            throw new ValidationError("Data channel id in nonce does not match");
        }

        // Update incoming CSN
        this.lastIncomingCsn = nonce.getCombinedSequence();

        // Decrypt data
        return this.signaling.decryptFromPeer(box);
    }
}
