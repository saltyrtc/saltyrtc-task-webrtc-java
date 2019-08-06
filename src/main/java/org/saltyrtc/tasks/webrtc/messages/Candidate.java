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
import org.saltyrtc.client.exceptions.ValidationError;
import org.saltyrtc.client.helpers.ValidationHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable POJO containing candidate information.
 */
public class Candidate {
    @NonNull private static final String FIELD_CANDIDATE = "candidate";
    @NonNull private static final String FIELD_SDP_MID = "sdpMid";
    @NonNull private static final String FIELD_SDP_M_LINE_INDEX = "sdpMLineIndex";

    @NonNull private final String sdp;
    @Nullable private final String sdpMid;
    @Nullable private final Integer sdpMLineIndex;

    public Candidate(@NonNull final String sdp, @Nullable final String sdpMid,
                     @Nullable final Integer sdpMLineIndex) {
        this.sdp = sdp;
        this.sdpMid = sdpMid;
        this.sdpMLineIndex = sdpMLineIndex;
    }

    public Candidate(@NonNull final Map map) throws ValidationError {
        this.sdp = ValidationHelper.validateString(map.get(FIELD_CANDIDATE), FIELD_CANDIDATE);
        if (map.get(FIELD_SDP_MID) != null) {
            this.sdpMid = ValidationHelper.validateString(map.get(FIELD_SDP_MID), FIELD_SDP_MID);
        } else {
            this.sdpMid = null;
        }
        if (map.get(FIELD_SDP_M_LINE_INDEX) != null) {
            this.sdpMLineIndex = ValidationHelper.validateInteger(
                map.get(FIELD_SDP_M_LINE_INDEX), 0, 65535, FIELD_SDP_M_LINE_INDEX);
        } else {
            this.sdpMLineIndex = null;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Candidate)) {
            return false;
        }
        final Candidate other = (Candidate) obj;
        return
            Objects.equals(this.sdp, other.sdp) &&
            Objects.equals(this.sdpMid, other.sdpMid) &&
            Objects.equals(this.sdpMLineIndex, other.sdpMLineIndex);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sdp, sdpMid, sdpMLineIndex);
    }

    @NonNull public String getSdp() {
        return this.sdp;
    }

    @Nullable public String getSdpMid() {
        return this.sdpMid;
    }

    @Nullable public Integer getSdpMLineIndex() {
        return this.sdpMLineIndex;
    }

    @NonNull public Map<String, Object> toMap() {
        final Map<String, Object> candidateMap = new HashMap<>();
        candidateMap.put(FIELD_CANDIDATE, this.getSdp());
        candidateMap.put(FIELD_SDP_MID, this.getSdpMid());
        candidateMap.put(FIELD_SDP_M_LINE_INDEX, this.getSdpMLineIndex());
        return candidateMap;
    }
}
