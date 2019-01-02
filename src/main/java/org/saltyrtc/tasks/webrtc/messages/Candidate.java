/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.messages;

import org.saltyrtc.client.annotations.NonNull;
import org.saltyrtc.client.annotations.Nullable;

/**
 * Immutable POJO containing candidate information.
 */
class Candidate {
    @NonNull
    private final String candidate;
    @Nullable
    private final String sdpMid;
    @Nullable
    private final Integer sdpMLineIndex;

    public Candidate(@NonNull String candidate, @Nullable String sdpMid, @Nullable Integer sdpMLineIndex) {
        this.candidate = candidate;
        this.sdpMid = sdpMid;
        this.sdpMLineIndex = sdpMLineIndex;
    }

    public String getCandidate() {
        return candidate;
    }

    public String getSdpMid() {
        return sdpMid;
    }

    public Integer getSdpMLineIndex() {
        return sdpMLineIndex;
    }
}
