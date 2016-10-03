/*
 * Copyright (c) 2016 Threema GmbH / SaltyRTC Contributors
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc;

import org.saltyrtc.client.annotations.NonNull;
import org.saltyrtc.client.annotations.Nullable;
import org.saltyrtc.client.exceptions.ConnectionException;
import org.saltyrtc.client.exceptions.ProtocolException;
import org.saltyrtc.client.exceptions.SignalingException;
import org.saltyrtc.client.exceptions.ValidationError;
import org.saltyrtc.client.helpers.ValidationHelper;
import org.saltyrtc.client.messages.c2c.TaskMessage;
import org.saltyrtc.client.signaling.SignalingInterface;
import org.saltyrtc.client.signaling.SignalingRole;
import org.saltyrtc.client.tasks.Task;
import org.saltyrtc.tasks.webrtc.exceptions.IllegalStateError;
import org.slf4j.Logger;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WebRTC Task.
 *
 * This task uses the end-to-end encryption techniques of SaltyRTC to set up a secure WebRTC
 * peer-to-peer connection. It also adds another security layer for data channels that are available
 * to users. The signalling channel will persist after being handed over to a dedicated data channel
 * once the peer-to-peer connection has been set up. Therefore, further signalling communication
 * between the peers does not require a dedicated WebSocket connection over a SaltyRTC server.
 *
 * The task needs to be initialized with the WebRTC peer connection.
 *
 * To send offer/answer/candidates, use the corresponding public methods on this task.
 */
public class WebRTCTask implements Task {

    // Constants as defined by the specification
    private static final String PROTOCOL_NAME = "v0.webrtc.tasks.saltyrtc.org";
    private static final Integer MAX_PACKET_SIZE = 16384;

    // Data fields
    private static final String FIELD_EXCLUDE = "exclude";
    private static final String FIELD_MAX_PACKET_SIZE = "max_packet_size";

    // Initialization state
    private boolean initialized = false;

    // Exclude list
    private Set<Integer> exclude = new HashSet<>();

    // Effective max packet size
    private Integer maxPacketSize;

    // Signaling
    private SignalingInterface signaling;

    // Peer connection
    @NonNull
    final private PeerConnection pc;

    /**
     * Initialize WebRTC task with a WebRTC peer connection.
     * @param pc A `PeerConnection` instance.
     */
    public WebRTCTask(@NonNull PeerConnection pc) {
        this.pc = pc;
    }

    private Logger getLogger() {
        String name;
        if (this.signaling == null) {
            name = "SaltyRTC.WebRTC";
        } else {
            name = "SaltyRTC.WebRTC." + this.signaling.getRole().name();
        }
        return org.slf4j.LoggerFactory.getLogger(name);
    }

    @Override
    public void init(SignalingInterface signaling, Map<Object, Object> data) throws ValidationError {
        this.processExcludeList(data.get(FIELD_EXCLUDE));
        this.processMaxPacketSize(data.get(FIELD_MAX_PACKET_SIZE));
        this.signaling = signaling;
        this.initialized = true;
    }

    @Override
    public void onPeerHandshakeDone() {
        this.getLogger().info("DONE");
        if (this.signaling.getRole() == SignalingRole.Initiator) {
            this.createAndSendOffer();
        }
    }

    /**
     * The exclude field MUST contain an Array of WebRTC data channel IDs (non-negative integers)
     * that SHALL not be used for the signalling channel. The client SHALL store this list for usage
     * during handover.
     */
    private void processExcludeList(Object value) throws ValidationError {
        if (value == null) {
            throw new ValidationError(FIELD_EXCLUDE + " field may not be null");
        }
        final List<Integer> ids = ValidationHelper.validateTypedList(value, Integer.class, FIELD_EXCLUDE);
        this.exclude.addAll(ids);
    }

    /**
     * The max_packet_size field MUST contain either 0 or a positive integer. If one client's value
     * is 0 but the other client's value is greater than 0, the larger of the two values SHALL be
     * stored to be used for data channel communication. Otherwise, the minimum of both clients'
     * maximum size SHALL be stored. The stored value SHALL be readable by user applications, so a
     * user application can have its own message chunking implementation if desired.
     */
    private void processMaxPacketSize(Object value) throws ValidationError {
        final Integer maxPacketSize = ValidationHelper.validateInteger(value, 0, Integer.MAX_VALUE, FIELD_MAX_PACKET_SIZE);
        if (maxPacketSize == 0 && MAX_PACKET_SIZE == 0) {
            this.maxPacketSize = 0;
        } else if (maxPacketSize == 0 || MAX_PACKET_SIZE == 0) {
            this.maxPacketSize = Math.max(maxPacketSize, MAX_PACKET_SIZE);
        } else {
            this.maxPacketSize = Math.min(maxPacketSize, MAX_PACKET_SIZE);
        }
    }

    @Override
    public void onTaskMessage(TaskMessage message) {
        this.getLogger().info("New task message arrived");
        final String type = message.getType();
        switch (type) {
            case "offer":
                SessionDescription sd = this.validateOffer(message);
                this.processOffer(sd);
                break;
            case "answer":
                SessionDescription sd = this.validateAnswer(message);
                this.processAnswer(sd);
                break;
            case "candidate":
                List<IceCandidate> candidates = this.validateCandidates(message);
                this.processCandidates(candidates);
                break;
            default:
                this.getLogger().error("Received message with unknown type: " + type);
        }
    }

    /**
     * Send a signaling message *through the data channel*.
     */
    @Override
    public void sendSignalingMessage(byte[] payload) {
        this.getLogger().info("TODO: Send signaling message");
    }

    @NonNull
    @Override
    public String getName() {
        return WebRTCTask.PROTOCOL_NAME;
    }

    @NonNull
    @Override
    public List<String> getSupportedMessageTypes() {
        final List<String> types = new ArrayList<>();
        types.add("offer");
        types.add("answer");
        types.add("candidate");
        types.add("handover");
        types.add("close"); // TODO: Hmm... This conflicts with the signaling types.
        return types;
    }

    /**
     * Return the max packet size, or `null` if the task has not yet been initialized.
     */
    @Nullable
    public Integer getMaxPacketSize() {
        if (this.initialized) {
            return this.maxPacketSize;
        }
        return null;
    }

    @Nullable
    @Override
    public Map<Object, Object> getData() {
        final Map<Object, Object> map = new HashMap<>();
        map.put(WebRTCTask.FIELD_EXCLUDE, this.exclude);
        map.put(WebRTCTask.FIELD_MAX_PACKET_SIZE, WebRTCTask.MAX_PACKET_SIZE);
        return map;
    }

    @NonNull
    public SignalingInterface getSignaling() {
        return this.signaling;
    }

    /**
     * Create a WebRTC offer and send it to the responder.
     *
     * @throws IllegalStateError if the responder tries to send an offer.
     */
    private void createAndSendOffer() {
        if (this.signaling.getRole() != SignalingRole.Initiator) {
            throw new IllegalStateError("Only the initiator may send an offer");
        }

        this.getLogger().debug("Creating a WebRTC offer");
        this.pc.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                try {
                    WebRTCTask.this.sendOffer(sessionDescription);
                } catch (ProtocolException | SignalingException | ConnectionException e) {
                    e.printStackTrace();
                    WebRTCTask.this.getLogger().warn("Could not send offer", e);
                }
            }

            @Override
            public void onCreateFailure(String s) {
                WebRTCTask.this.getLogger().warn("Could not create offer");
            }

            @Override
            public void onSetSuccess() { }

            @Override
            public void onSetFailure(String s) { }
        }, new MediaConstraints());
    }

    /**
     * Send an offer message to the responder.
     *
     * @throws IllegalArgumentException if the session description is not an offer.
     * @throws IllegalStateError if the responder tries to send an offer.
     */
    public void sendOffer(SessionDescription sd) throws ProtocolException, SignalingException, ConnectionException {
        // Validate
        if (sd.type != SessionDescription.Type.OFFER) {
            throw new IllegalArgumentException("Session description must have type 'OFFER'");
        }
        if (this.signaling.getRole() != SignalingRole.Initiator) {
            throw new IllegalStateError("Only the initiator may send an offer");
        }

        // Send message
        final Map<String, Object> data = new HashMap<>();
        data.put("type", "offer");
        if (sd.type != SessionDescription.Type.valueOf("rollback")) {
            data.put("sdp", sd.description);
        }
        this.getLogger().debug("Sending offer");
        this.signaling.sendTaskMessage(new TaskMessage("offer", data));
    }

    /**
     * Send an answer message to the initiator.
     *
     * @throws IllegalArgumentException if the session description is not an answer.
     * @throws IllegalStateError if the initiator tries to send an answer.
     */
    public void sendAnswer(SessionDescription sd) throws ProtocolException, SignalingException, ConnectionException {
        // Validate
        if (sd.type != SessionDescription.Type.ANSWER) {
            throw new IllegalArgumentException("Session description must have type 'ANSWER'");
        }
        if (this.signaling.getRole() != SignalingRole.Responder) {
            throw new IllegalStateError("Only the responder may send an answer");
        }

        // Send message
        final Map<String, Object> data = new HashMap<>();
        data.put("type", "answer");
        if (sd.type != SessionDescription.Type.valueOf("rollback")) {
            data.put("sdp", sd.description);
        }
        this.getLogger().debug("Sending answer");
        this.signaling.sendTaskMessage(new TaskMessage("answer", data));
    }

    /**
     * Send one or more candidates to the peer.
     */
    public void sendCandidates(IceCandidate[] candidates) throws ProtocolException,  SignalingException, ConnectionException {
        final Map<String, Object> data = new HashMap<>();
        final List<Map<String, Object>> candidateList = new ArrayList<>();
        for (IceCandidate candidate : candidates) {
            final Map<String, Object> candidateMap = new HashMap<>();
            candidateMap.put("candidate", candidate.sdp);
            candidateMap.put("sdpMid", candidate.sdpMid); // TODO: Can this be null?
            candidateMap.put("sdpMLineIndex", candidate.sdpMLineIndex); // TODO: Can this be non-positive?
            candidateList.add(candidateMap);
        }
        data.put("candidates", candidateList);
        this.getLogger().debug("Sending candidates");
        this.signaling.sendTaskMessage(new TaskMessage("candidate", data));
    }

    /**
     * Send one or more candidates to the peer.
     */
    public void sendCandidates(IceCandidate candidate) throws ProtocolException, SignalingException, ConnectionException {
        this.sendCandidates(new IceCandidate[] { candidate });
    }
}
