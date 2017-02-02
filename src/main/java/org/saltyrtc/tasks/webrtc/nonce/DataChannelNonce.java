/*
 * Copyright (c) 2016-2017 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.nonce;

import org.saltyrtc.client.helpers.UnsignedHelper;
import org.saltyrtc.client.nonce.Nonce;

import java.nio.ByteBuffer;

/**
 * A SaltyRTC data channel nonce.
 *
 * Nonce structure:
 *
 * |CCCCCCCCCCCCCCCC|DD|OO|QQQQ|
 *
 * - C: Cookie (16 byte)
 * - D: Data channel ID (2 byte)
 * - O: Overflow number (2 byte)
 * - Q: Sequence number (4 byte)
 */
public class DataChannelNonce extends Nonce {

    private int channelId;

    /**
     * Create a new nonce.
     *
     * Note that due to the lack of unsigned data types in Java, we'll use
     * larger signed types. That means that the user must check that the values
     * are in the correct range. If the arguments are out of range, an
     * unsigned `IllegalArgumentException` is thrown.
     *
     * See also: http://stackoverflow.com/a/397997/284318.
     */
    public DataChannelNonce(byte[] cookie, int channelId, int overflow, long sequence) {
        validateCookie(cookie);
        validateChannelId(channelId);
        validateOverflow(overflow);
        validateSequence(sequence);
        this.cookie = cookie;
        this.channelId = channelId;
        this.overflow = overflow;
        this.sequence = sequence;
    }

    /**
     * Create a new nonce from raw binary data.
     */
    public DataChannelNonce(ByteBuffer buf) {
        if (buf.limit() < TOTAL_LENGTH) {
            throw new IllegalArgumentException("Buffer limit must be at least " + TOTAL_LENGTH);
        }

        final byte[] cookie = new byte[COOKIE_LENGTH];
        buf.get(cookie, 0, COOKIE_LENGTH);
        validateCookie(cookie);

        final int channelId = UnsignedHelper.readUnsignedShort(buf.getShort());
        validateChannelId(channelId);

        final int overflow = UnsignedHelper.readUnsignedShort(buf.getShort());
        validateOverflow(overflow);

        final long sequence = UnsignedHelper.readUnsignedInt(buf.getInt());
        validateSequence(sequence);

        this.cookie = cookie;
        this.channelId = channelId;
        this.overflow = overflow;
        this.sequence = sequence;
    }

    @Override
    public byte[] toBytes() {
        // Pack data
        ByteBuffer buffer = ByteBuffer.allocate(Nonce.TOTAL_LENGTH);
        buffer.put(this.cookie);
        buffer.putShort(UnsignedHelper.getUnsignedShort(this.channelId));
        buffer.putShort(UnsignedHelper.getUnsignedShort(this.overflow));
        buffer.putInt(UnsignedHelper.getUnsignedInt(this.sequence));

        // Return underlying array
        return buffer.array();
    }

    /**
     * A channel id should be an uint16.
     */
    private void validateChannelId(int channelId) {
        if (channelId < 0 || channelId >= (1 << 16)) {
            throw new IllegalArgumentException("channelId must be between 0 and 2**16-1");
        }
    }

    /**
     * Return the channel id.
     */
    public int getChannelId() {
        return this.channelId;
    }

}
