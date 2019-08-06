/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.messages;

import org.saltyrtc.client.annotations.NonNull;
import org.saltyrtc.client.exceptions.ValidationError;
import org.saltyrtc.client.helpers.ValidationHelper;
import org.saltyrtc.client.messages.c2c.TaskMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Offer implements ToTaskMessage {
    @NonNull private static final String TYPE = "offer";

    @NonNull private final String sdp;

    public Offer(@NonNull final String sdp) {
        this.sdp = sdp;
    }

    /**
     * Construct an offer from the "data" field of a TaskMessage.
     */
    public Offer(@NonNull final Map<String, Object> map) throws ValidationError {
        final Map<String, Object> offer = ValidationHelper.validateStringObjectMap(map.get("offer"), "offer");
        ValidationHelper.validateType(offer.get("type"), TYPE);
        this.sdp = ValidationHelper.validateString(offer.get("sdp"), "sdp");
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Offer)) {
            return false;
        }
        final Offer other = (Offer) obj;
        return Objects.equals(this.sdp, other.sdp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sdp);
    }

    @NonNull public String getSdp() {
        return this.sdp;
    }

    @Override
    @NonNull public TaskMessage toTaskMessage() {
        final Map<String, Object> offer = new HashMap<>();
        offer.put("type", TYPE);
        offer.put("sdp", this.sdp);
        final Map<String, Object> data = new HashMap<>();
        data.put("offer", offer);
        return new TaskMessage(TYPE, data);
    }
}
