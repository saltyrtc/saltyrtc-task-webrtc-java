/*
 * Copyright (c) 2016 Threema GmbH / SaltyRTC Contributors
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.messages;

import org.saltyrtc.client.exceptions.ValidationError;
import org.saltyrtc.client.helpers.ValidationHelper;
import org.saltyrtc.client.messages.c2c.TaskMessage;
import org.webrtc.IceCandidate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Candidates implements ToTaskMessage {

    public static String TYPE = "candidates";
    private static String FIELD_CANDIDATES = "candidates";
    private static String FIELD_CANDIDATE = "candidate";
    private static String FIELD_SDP_MID = "sdpMid";
    private static String FIELD_SDP_M_LINE_INDEX = "sdpMLineIndex";

    private Candidate[] candidates;

    public Candidates(Candidate[] candidates) {
        this.candidates = candidates;
    }

    public Candidates(IceCandidate[] candidates) {
        this.candidates = new Candidate[candidates.length];
        for (int i = 0; i < candidates.length; i++) {
            final IceCandidate c = candidates[i];
            this.candidates[i] = new Candidate(c.sdp, c.sdpMid, c.sdpMLineIndex);
        }
    }

    /**
     * Construct candidates from the "data" field of a TaskMessage.
     */
    public Candidates(Map<String, Object> map) throws ValidationError {
        List<Map> candidates = ValidationHelper.validateTypedList(map.get(FIELD_CANDIDATES), Map.class, FIELD_CANDIDATES);
        this.candidates = new Candidate[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            final Map candidateMap = candidates.get(i);
            final String sdp = ValidationHelper.validateString(candidateMap.get(FIELD_CANDIDATE), FIELD_CANDIDATE);
            String sdpMid = null;
            if (candidateMap.get(FIELD_SDP_MID) != null) {
                sdpMid = ValidationHelper.validateString(candidateMap.get(FIELD_SDP_MID), FIELD_SDP_MID);
            }
            Integer sdpMLineIndex = null;
            if (candidateMap.get(FIELD_SDP_M_LINE_INDEX) != null) {
                sdpMLineIndex = ValidationHelper.validateInteger(candidateMap.get(FIELD_SDP_M_LINE_INDEX), 0, 65535, FIELD_SDP_M_LINE_INDEX);
            }
            this.candidates[i] = new Candidate(sdp, sdpMid, sdpMLineIndex);
        }
    }

    public Candidate[] getCandidates() {
        return candidates;
    }

    @Override
    public TaskMessage toTaskMessage() {
        final Map<String, Object> data = new HashMap<>();
        final List<Map> candidateList = new ArrayList<>();
        for (Candidate candidate : this.candidates) {
            final Map<Object, Object> candidateMap = new HashMap<>();
            candidateMap.put(FIELD_CANDIDATE, candidate.getCandidate());
            candidateMap.put(FIELD_SDP_MID, candidate.getSdpMid());
            candidateMap.put(FIELD_SDP_M_LINE_INDEX, candidate.getSdpMLineIndex());
            candidateList.add(candidateMap);
        }
        data.put(FIELD_CANDIDATES, candidateList);
        return new TaskMessage(TYPE, data);
    }

    public List<IceCandidate> toIceCandidates() {
        final List<IceCandidate> candidates = new ArrayList<>();
        for (Candidate c : this.candidates) {
            candidates.add(new IceCandidate(c.getSdpMid(), c.getSdpMLineIndex(), c.getCandidate()));
        }
        return candidates;
    }
}
