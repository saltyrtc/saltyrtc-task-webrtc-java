/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.support.ReflectionSupport;
import org.saltyrtc.client.annotations.NonNull;
import org.saltyrtc.client.cookie.Cookie;
import org.saltyrtc.client.cookie.CookiePair;
import org.saltyrtc.client.crypto.CryptoException;
import org.saltyrtc.client.exceptions.OverflowException;
import org.saltyrtc.client.exceptions.ProtocolException;
import org.saltyrtc.client.exceptions.ValidationError;
import org.saltyrtc.client.keystore.Box;
import org.saltyrtc.client.nonce.CombinedSequence;
import org.saltyrtc.client.nonce.CombinedSequencePair;
import org.saltyrtc.client.nonce.CombinedSequenceSnapshot;
import org.saltyrtc.client.signaling.SignalingInterface;
import org.saltyrtc.tasks.webrtc.DataChannelNonce;
import org.saltyrtc.tasks.webrtc.utils.NullSignaling;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class NullCryptoSignaling extends NullSignaling {
    @Override
    public Box encryptForPeer(byte[] data, byte[] nonce) {
        // Don't actually encrypt
        return new Box(nonce, data);
    }

    @Override
    public byte[] decryptFromPeer(Box box) {
        // Don't actually decrypt
        return box.getData();
    }
}

class InvalidNonceBox extends Box {
    InvalidNonceBox() {
        super(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 }, new byte[] {});
    }
}

@DisplayName("DataChannelCryptoContext")
class DataChannelCryptoContextTest {
    private static final int CHANNEL_ID = 1337;
    @NonNull private static final Cookie COOKIE = new Cookie(new byte[] {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
    });
    @NonNull private static final DataChannelNonce NONCE = new DataChannelNonce(COOKIE.getBytes(), CHANNEL_ID, 0, 11);

    @Test
    @DisplayName("returns correct overhead and nonce length")
    void testConstants() {
        assertEquals(40, DataChannelCryptoContext.OVERHEAD_LENGTH);
        assertEquals(24, DataChannelCryptoContext.NONCE_LENGTH);
    }

    @Nested
    @DisplayName("encrypt")
    class Encrypt {
        @NonNull private SignalingInterface signaling;
        @NonNull private DataChannelCryptoContext context;

        @BeforeEach
        void setUp() {
            this.signaling = new NullCryptoSignaling();
            this.context = new DataChannelCryptoContext(CHANNEL_ID, this.signaling);
        }

        @Test
        @DisplayName("uses expected channel id")
        void testChannelId() throws OverflowException, CryptoException {
            for (int i = 0; i < 10; ++i) {
                final Box box = this.context.encrypt(new byte[] {});
                final DataChannelNonce nonce = new DataChannelNonce(ByteBuffer.wrap(box.getNonce()));
                assertEquals(CHANNEL_ID, nonce.getChannelId());
            }
        }

        @Test
        @DisplayName("uses expected cookie")
        void testCookie() throws Exception {
            final CookiePair cookiePair = (CookiePair) ReflectionSupport.tryToReadFieldValue(
                DataChannelCryptoContext.class.getDeclaredField("cookiePair"), this.context).get();
            final byte[] cookie = cookiePair.getOurs().getBytes();

            for (int i = 0; i < 10; ++i) {
                final Box box = this.context.encrypt(new byte[] {});
                final DataChannelNonce nonce = new DataChannelNonce(ByteBuffer.wrap(box.getNonce()));
                assertArrayEquals(cookie, nonce.getCookieBytes());
            }
        }

        @Test
        @DisplayName("uses expected combined sequence number")
        void testCombinedSequenceNumber() throws Exception {
            final CombinedSequencePair csnPair = (CombinedSequencePair) ReflectionSupport.tryToReadFieldValue(
                DataChannelCryptoContext.class.getDeclaredField("csnPair"), this.context).get();
            final CombinedSequence csn = new CombinedSequence(csnPair.getOurs().getSequenceNumber(), csnPair.getOurs().getOverflow());

            for (int i = 0; i < 10; ++i) {
                final Box box = this.context.encrypt(new byte[] {});
                final DataChannelNonce nonce = new DataChannelNonce(ByteBuffer.wrap(box.getNonce()));
                final CombinedSequenceSnapshot expectedCsn = csn.next();
                assertEquals(expectedCsn.getOverflow(), nonce.getOverflow());
                assertEquals(expectedCsn.getSequenceNumber(), nonce.getSequence());
                assertEquals(expectedCsn.getCombinedSequence(), nonce.getCombinedSequence());
            }
        }

        @Test
        @DisplayName("can encrypt bytes")
        void testEncrypt() throws OverflowException, CryptoException {
            final byte[] bytes = new byte[] { 1, 2, 3, 4 };
            final Box box = this.context.encrypt(bytes);
            assertArrayEquals(bytes, box.getData());
        }
    }

    @Nested
    @DisplayName("decrypt")
    class Decrypt {
        @NonNull private SignalingInterface signaling;
        @NonNull private DataChannelCryptoContext context;

        @BeforeEach
        void setUp() {
            this.signaling = new NullCryptoSignaling();
            this.context = new DataChannelCryptoContext(CHANNEL_ID, this.signaling);
        }

        @Test
        @DisplayName("rejects invalid nonce size")
        void testInvalidNonce() {
            final InvalidNonceBox invalidNonceBox = new InvalidNonceBox();
            final ValidationError error = assertThrows(ValidationError.class, () -> this.context.decrypt(invalidNonceBox));
            assertEquals(
                "Unable to create nonce, reason: java.lang.IllegalArgumentException: Buffer limit must be at least 24",
                error.getMessage());
        }

        @Test
        @DisplayName("rejects cookie if local and remote cookie are identical")
        void testSameCookieRejection() throws OverflowException, CryptoException {
            final Box box = this.context.encrypt(new byte[] {});
            final ValidationError error = assertThrows(ValidationError.class, () -> this.context.decrypt(box));
            assertEquals("Local and remote cookies are equal", error.getMessage());
        }

        @Test
        @DisplayName("rejects cookie if modified")
        void testModifiedCookieRejection() throws ProtocolException, CryptoException, ValidationError {
            final Box box1 = new Box(NONCE.toBytes(), new byte[] {});

            // Applies remote cookie
            this.context.decrypt(box1);

            // Verifies remote cookie
            final byte[] otherCookie = new byte[] { 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1 };
            final Box box2 = new Box(new DataChannelNonce(otherCookie, CHANNEL_ID, 0, 11).toBytes(), new byte[] {});
            final ValidationError error = assertThrows(ValidationError.class, () -> this.context.decrypt(box2));
            assertEquals("Remote cookie changed", error.getMessage());
        }

        @Test
        @DisplayName("rejects repeated combined sequence number")
        void testRepeatedCombinedSequenceNumberRejection() throws ProtocolException, CryptoException, ValidationError {
            final Box box = new Box(NONCE.toBytes(), new byte[] {});

            // Applies remote CSN
            this.context.decrypt(box);

            // Verifies remote CSN
            final ValidationError error = assertThrows(ValidationError.class, () -> this.context.decrypt(box));
            assertEquals("CSN reuse detected", error.getMessage());
        }

        @Test
        @DisplayName("rejects invalid data channel id")
        void testInvalidDataChannelId() {
            final DataChannelNonce nonce = new DataChannelNonce(COOKIE.getBytes(), 1338, 0, 11);
            final Box box = new Box(nonce.toBytes(), new byte[] {});
            final ValidationError error = assertThrows(ValidationError.class, () -> this.context.decrypt(box));
            assertEquals("Data channel id in nonce does not match", error.getMessage());
        }

        @Test
        @DisplayName("can decrypt bytes")
        void testDecrypt() throws ProtocolException, CryptoException, ValidationError {
            final byte[] bytes = new byte[] { 1, 2, 3, 4 };
            final Box box = new Box(NONCE.toBytes(), bytes);
            assertArrayEquals(this.context.decrypt(box), bytes);
        }
    }
}
