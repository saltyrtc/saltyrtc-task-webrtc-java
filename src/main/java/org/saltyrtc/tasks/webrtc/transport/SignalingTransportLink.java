/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.transport;

import org.saltyrtc.client.annotations.NonNull;
import org.saltyrtc.client.annotations.Nullable;
import org.saltyrtc.tasks.webrtc.exceptions.UntiedException;

import java.nio.ByteBuffer;

/**
 * Will be provided by the task and contains all necessary information
 * needed to create a dedicated data channel for the purpose of exchanging
 * signalling data.
 *
 * It also contains a collection of functions that must be called by the
 * application to forward messages and events from the dedicated data
 * channel to the task.
 */
public class SignalingTransportLink  {
    @NonNull private static final String LABEL = "saltyrtc-signaling";
    private final int id;
    @NonNull private final String protocol;
    @Nullable private SignalingTransport transport;

    public SignalingTransportLink(final int id, @NonNull final String protocol) {
        this.id = id;
        this.protocol = protocol;
    }

    /**
     * Must be used as `label` argument when creating the `RTCDataChannel`.
     */
    @NonNull public String getLabel() {
        return LABEL;
    }

    /**
     * Must be used as `id` property as part of the `RTCDataChannelInit`
     * passed for construction of an `RTCDataChannel`.
     */
    public int getId() {
        return this.id;
    }

    /**
     * Must be used as `protocol` property as part of the
     * `RTCDataChannelInit` passed for construction of an `RTCDataChannel`.
     */
    @NonNull public String getProtocol() {
        return this.protocol;
    }

    /**
     * Must be called when the underlying data channel has moved into the
     * `closing` state.
     *
     * @throws UntiedException in case it is not tied to a SignalingTransport.
     */
    public void closing() throws UntiedException {
        if (this.transport == null) {
            throw new UntiedException();
        }
        this.transport.closing();
    }

    /**
     * Must be called when the underlying data channel has moved into the
     * `closed` state.
     *
     * @throws UntiedException in case it is not tied to a SignalingTransport.
     */
    public void closed() throws UntiedException {
        if (this.transport == null) {
            throw new UntiedException();
        }
        this.transport.closed();
    }

    /**
     * Must be called when a message has been received on the underlying
     * data channel.
     *
     * @param message A signalling message whose content SHALL NOT be
     *   modified by the application before dispatching it. The application
     *   MUST consider the message as transferred after calling this.
     *
     * @throws UntiedException in case it is not tied to a SignalingTransport.
     */
    public void receive(@NonNull final ByteBuffer message) throws UntiedException {
        if (this.transport == null) {
            throw new UntiedException();
        }
        this.transport.receiveChunk(message);
    }

    /**
     * Untie the link from a `SignalingTransport` instance.
     */
    public void untie() {
        this.transport = null;
    }

    /**
     * Tie the link to a `SignalingTransport` instance.
     */
    public void tie(@NonNull SignalingTransport transport) {
        this.transport = transport;
    }
}
