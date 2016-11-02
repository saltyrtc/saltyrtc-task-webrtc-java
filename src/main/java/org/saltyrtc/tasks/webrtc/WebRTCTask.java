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
import org.saltyrtc.client.exceptions.SignalingException;
import org.saltyrtc.client.exceptions.ValidationError;
import org.saltyrtc.client.helpers.ValidationHelper;
import org.saltyrtc.client.messages.c2c.TaskMessage;
import org.saltyrtc.client.signaling.CloseCode;
import org.saltyrtc.client.signaling.SignalingInterface;
import org.saltyrtc.client.signaling.state.SignalingState;
import org.saltyrtc.client.tasks.Task;
import org.saltyrtc.tasks.webrtc.events.MessageHandler;
import org.saltyrtc.tasks.webrtc.exceptions.IllegalStateError;
import org.saltyrtc.tasks.webrtc.messages.Answer;
import org.saltyrtc.tasks.webrtc.messages.Candidates;
import org.saltyrtc.tasks.webrtc.messages.Handover;
import org.saltyrtc.tasks.webrtc.messages.Offer;
import org.slf4j.Logger;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.nio.ByteBuffer;
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
 * peer-to-peer connection. It also adds another security layer for data channels that is available
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

    // Other constants
    private static final String DC_LABEL = "saltyrtc-signaling";

    // Initialization state
    private boolean initialized = false;

    // Exclude list
    @NonNull
    private Set<Integer> exclude = new HashSet<>();
    @Nullable
    private Integer dcId = null;

    // Effective max packet size
    private Integer maxPacketSize;

    // Signaling
    private SignalingInterface signaling;

    // Data channel
    @Nullable
    private SecureDataChannel sdc;

    // Message handler
    private MessageHandler messageHandler = null;

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
        // Do nothing.
        // The user should wait for a signaling state change to TASK.
        // Then he can start by sending an offer.
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
        for (int i = 0; i <= 65535; i++) {
            if (!this.exclude.contains(i)) {
                this.dcId = i;
                break;
            }
        }
        if (this.dcId == null) {
            throw new ValidationError("Exclude list too big, no free data channel id can be found");
        }
    }

    /**
     * The max_packet_size field MUST contain either 0 or a positive integer. If one client's value
     * is 0 but the other client's value is greater than 0, the larger of the two values SHALL be
     * stored to be used for data channel communication. Otherwise, the minimum of both clients'
     * maximum size SHALL be stored.
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

	/**
     * Set the message handler. It will be notified on incoming messages.
     */
    public void setMessageHandler(@NonNull MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

	/**
     * Handle incoming task messages, notify message handler.
     */
    @Override
    public void onTaskMessage(TaskMessage message) {
        final String type = message.getType();
        this.getLogger().info("New task message arrived: " + type);
        try {
            switch (type) {
                case "offer": {
                    if (this.messageHandler != null) {
                        final Offer offer = new Offer(message.getData());
                        this.messageHandler.onOffer(offer.toSessionDescription());
                    }
                    } break;
                case "answer": {
                    if (this.messageHandler != null) {
                        final Answer answer = new Answer(message.getData());
                        this.messageHandler.onAnswer(answer.toSessionDescription());
                    }
                    } break;
                case "candidates": {
                    if (this.messageHandler != null) {
                        final Candidates candidates = new Candidates(message.getData());
                        this.messageHandler.onCandidates(candidates.toIceCandidates());
                    }
                    } break;
                case "handover": {
                    if (!this.signaling.getHandoverState().getLocal()) {
                        this.sendHandover();
                    }
                    this.signaling.getHandoverState().setPeer(true);
                    if (this.signaling.getHandoverState().getAll()) {
                        this.getLogger().info("Handover to data channel finished");
                    }
                    } break;
                default:
                    this.getLogger().error("Received message with unknown type: " + type);
            }
        } catch (ValidationError e) {
            e.printStackTrace();
            this.getLogger().warn("Validation failed for incoming message", e);
        }
    }

    /**
     * Send a signaling message *through the data channel*.
     *
     * @param payload Non-encrypted message. The message will be encrypted by the underlying secure
     *                data channel.
     */
    @Override
    public void sendSignalingMessage(byte[] payload) throws SignalingException {
        if (this.signaling.getState() != SignalingState.TASK) {
            throw new SignalingException(CloseCode.PROTOCOL_ERROR,
                "Could not send signaling message: Signaling state is not open.");
        }
        if (!this.signaling.getHandoverState().getLocal()) {
            throw new SignalingException(CloseCode.PROTOCOL_ERROR,
                "Could not send signaling message: Handover hasn't happened yet");
        }
        this.sdc.send(new DataChannel.Buffer(ByteBuffer.wrap(payload), true));
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
        types.add("candidates");
        types.add("handover");
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
        map.put(WebRTCTask.FIELD_MAX_PACKET_SIZE, MAX_PACKET_SIZE);
        return map;
    }

    @NonNull
    public SignalingInterface getSignaling() {
        return this.signaling;
    }

    /**
     * Send an offer message to the responder.
     *
     * @throws IllegalArgumentException if the session description is not an offer.
     */
    public void sendOffer(SessionDescription sd) throws ConnectionException {
        final Offer offer = new Offer(sd);
        this.getLogger().debug("Sending offer");
        try {
            this.signaling.sendTaskMessage(offer.toTaskMessage());
        } catch (SignalingException e) {
            getLogger().error("Signaling error: " + CloseCode.explain(e.getCloseCode()));
            e.printStackTrace();
            this.signaling.resetConnection(e.getCloseCode());
        }
    }

    /**
     * Send an answer message to the initiator.
     *
     * @throws IllegalArgumentException if the session description is not an answer.
     */
    public void sendAnswer(SessionDescription sd) throws ConnectionException {
        final Answer answer = new Answer(sd);
        this.getLogger().debug("Sending answer");
        try {
            this.signaling.sendTaskMessage(answer.toTaskMessage());
        } catch (SignalingException e) {
            getLogger().error("Signaling error: " + CloseCode.explain(e.getCloseCode()));
            e.printStackTrace();
            this.signaling.resetConnection(e.getCloseCode());
        }
    }

    /**
     * Send one or more candidates to the peer.
     */
    public void sendCandidates(IceCandidate[] iceCandidates) throws ConnectionException {
        // Validate
        final Candidates candidates = new Candidates(iceCandidates);

        // Send message
        this.getLogger().debug("Sending candidates");
        try {
            this.signaling.sendTaskMessage(candidates.toTaskMessage());
        } catch (SignalingException e) {
            getLogger().error("Signaling error: " + CloseCode.explain(e.getCloseCode()));
            e.printStackTrace();
            this.signaling.resetConnection(e.getCloseCode());
        }
    }

    /**
     * Send one or more candidates to the peer.
     */
    public void sendCandidates(IceCandidate candidate) throws ConnectionException {
        this.sendCandidates(new IceCandidate[] { candidate });
    }

	/**
     * Do the handover from WebSocket to WebRTC data channel on the specified peer connection.
     *
     * This operation is asynchronous. To get notified when the handover is finished, subscribe to
     * the SaltyRTC `HandoverEvent`.
     */
    public synchronized void handover(@NonNull PeerConnection peerConnection) {
        this.getLogger().debug("Initiate handover");

        // Make sure that initialization has already happened
        if (!this.initialized) {
            throw new IllegalStateError("Initialization of task has not yet happened");
        }

        // Make sure handover hasn't already happened
        if (this.signaling.getHandoverState().getAny()) {
            this.getLogger().error("Handover already in process or finished!");
            return;
        }

        // Configure new data channel
        final DataChannel dc;
        DataChannel.Init init = new DataChannel.Init();
        init.id = this.dcId;
        init.negotiated = true;
        init.ordered = true;
        init.protocol = PROTOCOL_NAME;

        // Create data channel
        dc = peerConnection.createDataChannel(DC_LABEL, init);

        // Wrap data channel
        this.sdc = new SecureDataChannel(dc, this);

        // Handle data channel events
        this.sdc.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long l) {
                WebRTCTask.this.getLogger().info("DataChannel: Buffered amount changed");
            }

            @Override
            public void onStateChange() {
                final Logger logger = WebRTCTask.this.getLogger();
                final SignalingInterface signaling = WebRTCTask.this.signaling;
                final SecureDataChannel sdc = WebRTCTask.this.sdc;

                logger.info("DataChannel: State changed to " + sdc.state());
                switch (sdc.state()) {
                    case CONNECTING:
                        break;
                    case OPEN:
                        WebRTCTask.this.sendHandover();
                        break;
                    case CLOSING:
                        if (signaling.getHandoverState().getAny()) {
                            signaling.setState(SignalingState.CLOSING);
                        }
                        break;
                    case CLOSED:
                        if (signaling.getHandoverState().getAny()) {
                            signaling.setState(SignalingState.CLOSED);
                        }
                        break;
                    default:
                        logger.warn("Unknown or invalid data channel state: " + sdc.state());
                }
            }

            @Override
            public synchronized void onMessage(DataChannel.Buffer buffer) {
                // Pass decrypted incoming signaling messages to signaling class
                WebRTCTask.this.signaling.onSignalingPeerMessage(buffer.data.array());
            }
        });
    }

    private synchronized void sendHandover() {
        this.getLogger().debug("Sending handover");

        // Send handover message
        final Handover handover = new Handover();
        try {
            this.signaling.sendTaskMessage(handover.toTaskMessage());
        } catch (ConnectionException e) {
            e.printStackTrace();
            WebRTCTask.this.signaling.resetConnection(CloseCode.PROTOCOL_ERROR);
        } catch (SignalingException e) {
            e.printStackTrace();
            WebRTCTask.this.signaling.resetConnection(e.getCloseCode());
        }

        // Local handover finished
        this.signaling.getHandoverState().setLocal(true);

        // Check whether we're done
        if (this.signaling.getHandoverState().getAll()) {
            this.getLogger().info("Handover to data channel finished");
        }
    }

    /**
     * Return a wrapped data channel.
     *
     * Only call this method *after* handover has taken place!
     *
     * @param dc The data channel to be wrapped.
     * @return A `SecureDataChannel` instance.
     */
    public SecureDataChannel wrapDataChannel(DataChannel dc) {
        this.getLogger().debug("Wrapping data channel " + dc.id());
        return new SecureDataChannel(dc, this);
    }

	/**
	 * Send a 'close' message to the peer and close the connection.
     */
    public void sendClose() {
        this.close(CloseCode.GOING_AWAY);
        this.signaling.resetConnection(CloseCode.GOING_AWAY);
    }

	/**
     * Close the data channel.
     *
     * @param reason The close code.
     */
    @Override
    public void close(int reason) {
        this.getLogger().debug("Closing signaling data channel: " + CloseCode.explain(reason));
        this.sdc.close();
    }
}
