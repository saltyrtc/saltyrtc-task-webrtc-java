/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc;

import org.saltyrtc.client.annotations.NonNull;

import static org.saltyrtc.chunkedDc.Common.HEADER_LENGTH;

/**
 * Builds a WebRTCTask instance.
 *
 * The following default values are being used:
 *
 * - Version defaults to v1.
 * - Handover is enabled by default.
 * - The maximum chunk length for the handed over signalling channel is
 *   256 KiB.
 */
public class WebRTCTaskBuilder {
    @NonNull private WebRTCTaskVersion version = WebRTCTaskVersion.V1;
    private boolean handover = true;
    private int maxChunkLength = 262144;

    /**
     * Set the task version
     *
     * @param version The desired task version
     */
    @NonNull public WebRTCTaskBuilder withVersion(@NonNull final WebRTCTaskVersion version) {
        this.version = version;
        return this;
    }

    /**
     * Set whether handover should be negotiated.
     *
     * @param on Enable or disable handover.
     */
    @NonNull public WebRTCTaskBuilder withHandover(final boolean on) {
        this.handover = on;
        return this;
    }

    /**
     * Set the maximum chunk length in bytes for the handed over
     * signalling channel.
     *
     * @param length The maximum byte length of a chunk.
     *
     * @throws IllegalArgumentException in case the maximum chunk length is
     *   less or equal to the chunking header.
     */
    @NonNull public WebRTCTaskBuilder withMaxChunkLength(final int length) {
        if (length <= HEADER_LENGTH) {
            throw new IllegalArgumentException("Maximum chunk length is less than chunking overhead");
        }
        this.maxChunkLength = length;
        return this;
    }

    /**
     * Build the WebRTCTask instance.
     * @return WebRTCTask
     */
    @NonNull public WebRTCTask build() {
        return new WebRTCTask(
            this.version, this.handover, this.maxChunkLength);
    }
}
