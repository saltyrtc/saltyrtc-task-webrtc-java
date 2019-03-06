/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc;

import org.saltyrtc.client.annotations.NonNull;
import org.saltyrtc.client.annotations.Nullable;
import org.saltyrtc.client.crypto.CryptoException;
import org.saltyrtc.client.exceptions.*;
import org.saltyrtc.client.helpers.ValidationHelper;
import org.saltyrtc.client.messages.c2c.TaskMessage;
import org.saltyrtc.client.signaling.CloseCode;
import org.saltyrtc.client.signaling.SignalingInterface;
import org.saltyrtc.client.signaling.state.SignalingState;
import org.saltyrtc.client.tasks.Task;
import org.saltyrtc.tasks.webrtc.crypto.DataChannelCryptoContext;
import org.saltyrtc.tasks.webrtc.events.MessageHandler;
import org.saltyrtc.tasks.webrtc.exceptions.IllegalStateError;
import org.saltyrtc.tasks.webrtc.messages.*;
import org.saltyrtc.tasks.webrtc.transport.SignalingTransportHandler;
import org.saltyrtc.tasks.webrtc.transport.SignalingTransport;
import org.saltyrtc.tasks.webrtc.transport.SignalingTransportLink;
import org.slf4j.Logger;

import java.util.*;

/**
 * WebRTC Task Version 1.
 *
 * This task uses the end-to-end encryption techniques of SaltyRTC to set up a
 * secure WebRTC peer-to-peer connection. It also adds another security layer
 * for data channels that is available to applications. The signalling channel
 * will persist after being handed over to a dedicated data channel once the
 * peer-to-peer connection has been set up. Therefore, further signalling
 * communication between the peers does not require a dedicated WebSocket
 * connection over a SaltyRTC server.
 *
 * The task needs to be initialized with the WebRTC peer connection.
 *
 * To send offer/answer/candidates, use the corresponding public methods on
 * this task.
 */
public class WebRTCTask implements Task {
    // Data fields
    @NonNull private static final String FIELD_EXCLUDE = "exclude";
    @NonNull private static final String FIELD_HANDOVER = "handover";
    @NonNull private static final String FIELD_MAX_PACKET_SIZE = "max_packet_size"; // legacy v0

    // Protocol version
    @NonNull private final WebRTCTaskVersion version;

    // Logging
    @NonNull private Logger log = org.slf4j.LoggerFactory.getLogger("SaltyRTC.WebRTC");

    // Initialization state
    private boolean initialized = false;

    // Channel ID and ID exclusion list
    @NonNull private final Set<Integer> exclude = new HashSet<>();
    @Nullable private Integer channelId;

    // Signaling
    @Nullable private SignalingInterface signaling;

    // Signaling transport
    private boolean doHandover;
    private int maxChunkLength;
    @Nullable private SignalingTransportLink link;
    @Nullable private SignalingTransport transport;

    // Message handler
    @Nullable private MessageHandler messageHandler;

    /**
     * Create a new task instance.
     */
    public WebRTCTask(final WebRTCTaskVersion version, final boolean handover, final int maxChunkLength) {
        this.version = version;
        this.doHandover = handover;
        this.maxChunkLength = maxChunkLength;
    }

    /**
     * Set the message handler. It will be notified on incoming messages.
     */
    public void setMessageHandler(@NonNull final MessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    /**
     * Unset the message handler.
     */
    public void clearMessageHandler() {
        this.messageHandler = null;
    }

    /**
     * Initialize the task with the task data from the peer.
     *
     * This method should only be called by the signaling class, not by the
     * application!
     */
    @Override
    public void init(SignalingInterface signaling, Map<Object, Object> data) throws ValidationError {
        this.processExcludeList(data.get(FIELD_EXCLUDE));
        this.processHandover(data.get(FIELD_HANDOVER));
        if (this.version == WebRTCTaskVersion.V0) {
            this.processMaxPacketSize(data.get(FIELD_MAX_PACKET_SIZE));
        }
        this.signaling = signaling;
        this.log = org.slf4j.LoggerFactory.getLogger("SaltyRTC.WebRTC." + this.signaling.getRole().name());
        this.initialized = true;
    }

    /**
     * The exclude field MUST contain an Array of WebRTC data channel IDs
     * (non-negative integers less than 65535) that SHALL not be used for the
     * signalling channel. The client SHALL store this list for usage during
     * handover.
     */
    private void processExcludeList(@Nullable final Object value) throws ValidationError {
        if (value == null) {
            throw new ValidationError(FIELD_EXCLUDE + " field may not be null");
        }
        final List<Integer> ids = ValidationHelper.validateTypedList(value, Integer.class, FIELD_EXCLUDE);
        this.exclude.addAll(ids);
        for (int i = 0; i < 65535; i++) {
            if (!this.exclude.contains(i)) {
                this.channelId = i;
                break;
            }
        }
        if (this.channelId == null) {
            throw new ValidationError("No free data channel id can be found");
        }
    }

    /**
     * Process the handover field from the peer.
     */
    private void processHandover(@Nullable final Object value) throws ValidationError {
        final boolean handover = ValidationHelper.validateBoolean(value, FIELD_HANDOVER);
        if (!handover) {
            this.doHandover = false;
        }
    }

    /**
     * The max_packet_size field MUST contain either 0 or a positive integer.
     * If one client's value is 0 but the other client's value is greater than
     * 0, the larger of the two values SHALL be stored to be used for data
     * channel communication. Otherwise, the minimum of both clients' maximum
     * size SHALL be stored.
     *
     * Note: We don't care about the 0 case since this implementation will
     *       never choose 0.
     */
    private void processMaxPacketSize(@Nullable final Object value) throws ValidationError {
        final Integer remoteMaxPacketSize = ValidationHelper.validateInteger(
            value, 0, Integer.MAX_VALUE, FIELD_MAX_PACKET_SIZE);
        final int localMaxPacketSize = this.maxChunkLength;
        if (remoteMaxPacketSize > 0) {
            this.maxChunkLength = Math.min(localMaxPacketSize, remoteMaxPacketSize);
        }
        this.log.debug("Max packet size: Local requested " + localMaxPacketSize + " bytes, remote requested " +
            remoteMaxPacketSize + " bytes. Using " + this.maxChunkLength + ".");
    }

    /**
     * Used by the signaling class to notify task that the peer handshake is over.
     *
     * This method should only be called by the signaling class, not by the application!
     */
    @Override
    public void onPeerHandshakeDone() {
        // Do nothing.
        // The application should wait for a signaling state change to TASK.
        // Then it can start by sending an offer.
    }

    /**
     * Handle incoming task messages.
     *
     * This method should only be called by the signaling class, not by the
     * application!
     */
    @Override
    public void onTaskMessage(final TaskMessage message) {
        final String type = message.getType();
        this.log.info("New task message arrived: " + type);
        try {
            switch (type) {
                case "offer": {
                    if (this.messageHandler != null) {
                        final Offer offer = new Offer(message.getData());
                        this.messageHandler.onOffer(offer);
                    }
                    } break;
                case "answer": {
                    if (this.messageHandler != null) {
                        final Answer answer = new Answer(message.getData());
                        this.messageHandler.onAnswer(answer);
                    }
                    } break;
                case "candidates": {
                    if (this.messageHandler != null) {
                        final Candidates candidates = new Candidates(message.getData());
                        this.messageHandler.onCandidates(candidates.getCandidates());
                    }
                    } break;
                case "handover": {
                    // Ensure handover has been negotiated
                    if (!this.doHandover) {
                        this.log.error("Received unexpected handover message from peer");
                        this.signaling.resetConnection(CloseCode.PROTOCOL_ERROR);
                        break;
                    }

                    // Discard repeated handover requests
                    if (this.signaling.getHandoverState().getPeer()) {
                        // Note: This is not being treated as a protocol error since previous
                        //       versions had a race condition that could trigger multiple
                        //       sends of 'handover'.
                        this.log.warn("Handover already received");
                        break;
                    }

                    // Update state
                    this.signaling.getHandoverState().setPeer(true);

                    // Flush the message queue of the signaling transport (if any)
                    if (this.transport != null) {
                        try {
                            this.transport.flushMessageQueue();
                        } catch (IllegalStateError error) {
                            this.log.error("Unable to flush message queue:", error);
                            this.signaling.resetConnection(CloseCode.INTERNAL_ERROR);
                            break;
                        }
                    }

                    // Handover process completed?
                    if (this.signaling.getHandoverState().getAll()) {
                        this.log.info("Handover to data channel finished");
                    }
                    } break;
            default:
                this.log.error("Received message with unknown type: " + type);
            }
        } catch (ValidationError e) {
            e.printStackTrace();
            this.log.warn("Validation failed for incoming message", e);
        }
    }


    /**
     * Send a signaling message through a data channel.
     *
     * This method should only be called by the signaling class, not by the
     * application!
     *
     * @param payload Non-encrypted message. The message will be encrypted by
     *   the underlying data channel.
     * @throws SignalingException when signaling or handover state are not as
     *   expected.
     */
    @Override
    public void sendSignalingMessage(final byte[] payload) throws SignalingException {
        if (this.signaling.getState() != SignalingState.TASK) {
            throw new SignalingException(CloseCode.PROTOCOL_ERROR,
                "Could not send signaling message: Signaling state is not 'task'.");
        }
        if (!this.signaling.getHandoverState().getLocal()) {
            throw new SignalingException(CloseCode.PROTOCOL_ERROR,
                "Could not send signaling message: Handover hasn't happened yet");
        }
        if (this.transport == null) {
            throw new SignalingException(CloseCode.PROTOCOL_ERROR,
                "Could not send signaling message: Data channel is not established, yet.");
        }
        try {
            this.transport.send(payload);
        } catch (OverflowException | CryptoException error) {
            throw new SignalingException(CloseCode.PROTOCOL_ERROR,
                "Could not send signaling message:", error);
        }
    }

    /**
     * Return the task protocol name.
     */
    @Override
    @NonNull public String getName() {
        return this.version.toProtocolName();
    }

    /**
     * Return the list of supported message types.
     *
     * This method should only be called by the signaling class, not by the
     * application!
     */
    @Override
    @NonNull public List<String> getSupportedMessageTypes() {
        return Arrays.asList("offer", "answer", "candidates", "handover");
    }

    /**
     * Return the task data used for negotiation in the `auth` message.
     *
     * This method should only be called by the signaling class, not by the
     * application!
     */
    @Override
    @Nullable public Map<Object, Object> getData() {
        final Map<Object, Object> map = new HashMap<>();
        map.put(WebRTCTask.FIELD_EXCLUDE, this.exclude);
        if (this.version == WebRTCTaskVersion.V0) {
            map.put(WebRTCTask.FIELD_MAX_PACKET_SIZE, this.maxChunkLength);
        }
        map.put(WebRTCTask.FIELD_HANDOVER, this.doHandover);
        return map;
    }

    /**
     * Send an offer message to the responder.
     */
    public void sendOffer(@NonNull final Offer offer) throws ConnectionException {
        this.log.debug("Sending offer");
        try {
            this.signaling.sendTaskMessage(offer.toTaskMessage());
        } catch (SignalingException e) {
            this.log.error("Could not send offer: " + CloseCode.explain(e.getCloseCode()));
            e.printStackTrace();
            this.signaling.resetConnection(e.getCloseCode());
        }
    }

    /**
     * Send an answer message to the initiator.
     */
    public void sendAnswer(@NonNull final Answer answer) throws ConnectionException {
        this.log.debug("Sending answer");
        try {
            this.signaling.sendTaskMessage(answer.toTaskMessage());
        } catch (SignalingException e) {
            this.log.error("Could not send answer: " + CloseCode.explain(e.getCloseCode()));
            e.printStackTrace();
            this.signaling.resetConnection(e.getCloseCode());
        }
    }

    /**
     * Send one or more candidates to the peer.
     */
    public void sendCandidates(@NonNull final Candidate[] candidates) throws ConnectionException {
        this.log.debug("Sending candidates");
        try {
            this.signaling.sendTaskMessage(new Candidates(candidates).toTaskMessage());
        } catch (SignalingException e) {
            this.log.error("Could not send candidates: " + CloseCode.explain(e.getCloseCode()));
            e.printStackTrace();
            this.signaling.resetConnection(e.getCloseCode());
        }
    }

    /**
     * Create a `SignalingTransportLink` to be used by the application for the
     * handover process.
     *
     * If the application wishes to hand over the signalling channel, it MUST
     * create an `RTCDataChannel` instance with the following properties:
     *
     * - `negotiated` must be `true`,
     * - `ordered` must be `true`, and
     * - further properties are `label`, `id` and `protocol` as passed to
     *   the factory (attributes of `SignalingTransportLink`) which SHALL NOT
     *   be modified by the application.
     *
     * Once the `RTCDataChannel` instance moves into the `open` state, the
     * `SignalingTransportHandler` SHALL be created. The handover process
     * MUST be initiated immediately (without yielding back to the event loop)
     * once the `open` event fires to prevent messages from being lost.
     *
     * In case the `RTCDataChannel` instance moves into the `closed` state or
     * errors before opening, the application SHALL NOT start the handover
     * process.
     *
     * @return all necessary information to create a dedicated `RTCDataChannel`
     * and contains functions for forwarding events and messages.
     *
     * @throws IllegalStateError in case handover has not been negotiated or no
     *   free channel id could be determined during negotiation.
     */
    @NonNull public SignalingTransportLink getTransportLink() {
        this.log.debug("Create signalling transport link");

        // Make sure that initialization has already happened
        if (!this.initialized) {
            throw new IllegalStateError("Initialization of task has not yet happened");
        }

        // Make sure handover has been negotiated
        if (!this.doHandover) {
            throw new IllegalStateError("Handover has not been negotiated");
        }

        // Make sure the data channel id is set
        if (this.channelId == null) {
            throw new IllegalStateError("Data channel id not set");
        }

        // Return the transport link
        if (this.link == null) {
            this.link = new SignalingTransportLink(this.channelId, this.getName());
        }
        return this.link;
    }

    /**
     * Initiate the handover from WebSocket to a dedicated data channel.
     *
     * This operation is asynchronous. To get notified when the handover is
     * finished, subscribe to the SaltyRTC `HandoverEvent`.
     *
     * @throws IllegalStateError in case handover already requested or has not
     *   been negotiated.
     */
    public void handover(@NonNull final SignalingTransportHandler handler) {
        this.log.debug("Initiate handover");

        // Make sure that initialization has already happened
        if (!this.initialized) {
            throw new IllegalStateError("Initialization of task has not yet happened");
        }

        // Make sure handover has been negotiated
        if (!this.doHandover) {
            throw new IllegalStateError("Handover has not been negotiated");
        }

        // Make sure handover has not already been requested
        if (this.signaling.getHandoverState().getLocal() || this.transport != null) {
            throw new IllegalStateError("Handover already requested");
        }

        // Create crypto context and new signalling transport
        final DataChannelCryptoContext crypto = this.createCryptoContext(this.channelId);
        this.transport = new SignalingTransport(this.link, handler, this, this.signaling, crypto, this.maxChunkLength);

        // Send handover message
        // Note: This will still be sent via the original transport since the
        //       switching logic depends on the local handover state which
        //       SHALL NOT be altered before this call.
        this.sendHandover();
    }

    /**
     * Send a handover message to the peer.
     */
    private void sendHandover() {
        this.log.debug("Sending handover");

        // Send handover message
        final Handover handover = new Handover();
        try {
            this.signaling.sendTaskMessage(handover.toTaskMessage());
        } catch (ConnectionException e) {
            this.log.error("Could not send handover message:", e);
            e.printStackTrace();
            WebRTCTask.this.signaling.resetConnection(CloseCode.PROTOCOL_ERROR);
        } catch (SignalingException e) {
            this.log.error("Could not send answer: " + CloseCode.explain(e.getCloseCode()));
            e.printStackTrace();
            WebRTCTask.this.signaling.resetConnection(e.getCloseCode());
        }

        // Local handover finished
        this.signaling.getHandoverState().setLocal(true);

        // Check whether we're done
        if (this.signaling.getHandoverState().getAll()) {
            this.log.info("Handover to data channel finished");
        }
    }

    /**
     * Return a crypto context to encrypt and decrypt data for a data channel
     * with a specific id.
     *
     * @param channelId The data channel's id.
     */
    @NonNull public DataChannelCryptoContext createCryptoContext(final int channelId) {
        return new DataChannelCryptoContext(channelId, this.signaling);
    }

    /**
     * Close the signaling data channel.
     *
     * @param reason The close code.
     */
    @Override
    public void close(final int reason) {
        this.log.debug("Closing signaling data channel: " + CloseCode.explain(reason));
        if (this.transport != null) {
            this.transport.close();
        }
        this.transport = null;
    }
}
