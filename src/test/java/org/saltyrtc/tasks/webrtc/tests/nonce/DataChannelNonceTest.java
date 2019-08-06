/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.tests.nonce;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.saltyrtc.tasks.webrtc.DataChannelNonce;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DataChannelNonce")
class DataChannelNonceTest {
    private static final byte[] cookie = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    private static final byte[] nonce = {
        // Cookie
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
        // Data channel: 4370
        17, 18,
        // Overflow: 4884
        19, 20,
        // Sequence number: 84281096 big endian
        5, 6, 7, 8,
    };

    @Nested
    @DisplayName("from construction")
    class FromConstruction {
        @Test
        @DisplayName("validates the cookie")
        void testCookieValidation() {
            final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                new DataChannelNonce(new byte[] { 0x00, 0x01, 0x02, 0x03 }, 0, 1, 0));
            assertEquals("cookie must be 16 bytes long", exception.getMessage());
        }

        @Test
        @DisplayName("validates the channel id")
        void testChannelValidation() {
            int[] invalid = new int[] { -1, 1 << 16 };
            int[] valid = new int[] { 0, 1 << 16 - 1 };
            for (int value : invalid) {
                final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    new DataChannelNonce(cookie, value, 1, 0));
                assertEquals("channelId must be between 0 and 65534", exception.getMessage());
            }
            for (int value : valid) {
                new DataChannelNonce(cookie, value, 1, 0);
            }
        }

        @Test
        @DisplayName("validates the overflow number")
        void testOverflowValidation() {
            int[] invalid = new int[] { -1, 1 << 16 };
            int[] valid = new int[] { 0, 1 << 16 - 1 };
            for (int value : invalid) {
                final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    new DataChannelNonce(cookie, 0, value, 0));
                assertEquals("overflow must be between 0 and 2**16-1", exception.getMessage());
            }
            for (int value : valid) {
                new DataChannelNonce(cookie, 0, value, 0);
            }
        }

        @Test
        @DisplayName("validates the sequence number")
        void testSequenceValidation() {
            long[] invalid = new long[] { -1, 1L << 32 };
            long[] valid = new long[] { 0, 1L << 32 - 1 };
            for (long value : invalid) {
                final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    new DataChannelNonce(cookie, 0, 1, value));
                assertEquals("sequence must be between 0 and 2**32-1", exception.getMessage());
            }
            for (long value : valid) {
                new DataChannelNonce(cookie, 0, 1, value);
            }
        }

        @Test
        @DisplayName("serializes correctly")
        void testSerialize() {
            final DataChannelNonce nonce = new DataChannelNonce(cookie, 4370, 4884, 84281096L);
            assertArrayEquals(DataChannelNonceTest.nonce, nonce.toBytes());
        }
    }

    @Nested
    @DisplayName("from bytes")
    class FromBytes {
        @Test
        @DisplayName("obeys the minimum byte length")
        void testNonceMinLength() {
            final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                new DataChannelNonce(ByteBuffer.wrap(new byte[] { 0x0 })));
            assertEquals("Buffer limit must be at least 24", exception.getMessage());
        }

        @Test
        @DisplayName("validates the channel id")
        void testChannelValidation() {
            final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                new DataChannelNonce(ByteBuffer.wrap(new byte[]{
                    // Cookie
                    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                    // Data channel: 65535 (invalid)
                    (byte) 255, (byte) 255,
                    // Overflow: 0
                    0, 0,
                    // Sequence number: 0
                    0, 0, 0, 0,
                })));
            assertEquals("channelId must be between 0 and 65534", exception.getMessage());
        }

        @Test
        @DisplayName("parses correctly")
        void testParse() {
            final DataChannelNonce nonce = new DataChannelNonce(ByteBuffer.wrap(DataChannelNonceTest.nonce));
            assertArrayEquals(cookie, nonce.getCookie().getBytes());
            assertEquals(4370, nonce.getChannelId());
            assertEquals(4884, nonce.getOverflow());
            assertEquals(84281096L, nonce.getSequence());
            assertEquals(20976704554760L, nonce.getCombinedSequence());
        }
    }

    @Test
    @DisplayName("bytes -> instance -> bytes")
    void testByteConversionRoundtrip() {
        byte[] bytes = new byte[] {
            // Cookie
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
            0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
            // Data channel id (0x8001)
            -128, 1,
            // Overflow (0x8002)
            -128, 2,
            // Sequence (0x80000003)
            -128, 0, 0, 3,
        };
        final DataChannelNonce nonce = new DataChannelNonce(ByteBuffer.wrap(bytes));
        byte[] bytesAgain = nonce.toBytes();
        assertArrayEquals(bytes, bytesAgain);
    }
}
