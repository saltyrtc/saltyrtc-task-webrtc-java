/*
 * Copyright (c) 2016-2018 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.messages;

import org.saltyrtc.client.exceptions.ValidationError;
import org.saltyrtc.client.helpers.ValidationHelper;
import org.saltyrtc.client.messages.c2c.TaskMessage;
import org.webrtc.SessionDescription;

import java.util.HashMap;
import java.util.Map;

public class Offer implements ToTaskMessage {

    public static final String TYPE = "offer";

    private String sdp;

    public Offer(String sdp) {
        this.sdp = sdp;
    }

    public Offer(SessionDescription sd) {
        if (sd.type != SessionDescription.Type.OFFER) {
            throw new IllegalArgumentException("Session description is not an offer, but " + sd.type);
        }
        this.sdp = sd.description;
    }

	/**
     * Construct an offer from the "data" field of a TaskMessage.
     */
    public Offer(Map<String, Object> map) throws ValidationError {
        final Map<String, Object> offer = ValidationHelper.validateStringObjectMap(map.get("offer"), "offer");
        ValidationHelper.validateType(offer.get("type"), TYPE);
        this.sdp = ValidationHelper.validateString(offer.get("sdp"), "sdp");
    }

    public String getSdp() {
        return sdp;
    }

    @Override
    public TaskMessage toTaskMessage() {
        final Map<String, Object> offer = new HashMap<>();
        offer.put("type", TYPE);
        offer.put("sdp", this.sdp);
        final Map<String, Object> data = new HashMap<>();
        data.put("offer", offer);
        return new TaskMessage(TYPE, data);
    }

    public SessionDescription toSessionDescription() {
        return new SessionDescription(SessionDescription.Type.OFFER, this.getSdp());
    }
}
