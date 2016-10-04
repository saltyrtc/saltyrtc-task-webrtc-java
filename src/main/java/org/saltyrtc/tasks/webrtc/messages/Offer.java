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
import org.webrtc.SessionDescription;

import java.util.HashMap;
import java.util.Map;

public class Offer implements ToTaskMessage {

    public static String TYPE = "offer";

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

    public Offer(Map<String, Object> map) throws ValidationError {
        ValidationHelper.validateType(map.get("type"), TYPE);
        this.sdp = ValidationHelper.validateString(map.get("sdp"), "sdp");
    }

    public String getSdp() {
        return sdp;
    }

    @Override
    public TaskMessage toTaskMessage() {
        final Map<String, Object> data = new HashMap<>();
        data.put("type", TYPE);
        data.put("sdp", this.sdp);
        return new TaskMessage(TYPE, data);
    }
}
