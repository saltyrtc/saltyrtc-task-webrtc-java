/*
 * Copyright (c) 2016-2017 Threema GmbH
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

public class Answer implements ToTaskMessage {

    public static String TYPE = "answer";

    private String sdp;

    public Answer(String sdp) {
        this.sdp = sdp;
    }

    public Answer(SessionDescription sd) {
        if (sd.type != SessionDescription.Type.ANSWER) {
            throw new IllegalArgumentException("Session description is not an answer, but " + sd.type);
        }
        this.sdp = sd.description;
    }

    /**
     * Construct an answer from the "data" field of a TaskMessage.
     */
    public Answer(Map<String, Object> map) throws ValidationError {
        final Map<String, Object> answer = ValidationHelper.validateStringObjectMap(map.get("answer"), "answer");
        ValidationHelper.validateType(answer.get("type"), TYPE);
        this.sdp = ValidationHelper.validateString(answer.get("sdp"), "sdp");
    }

    public String getSdp() {
        return sdp;
    }

    @Override
    public TaskMessage toTaskMessage() {
        final Map<String, Object> answer = new HashMap<>();
        answer.put("type", TYPE);
        answer.put("sdp", this.sdp);
        final Map<String, Object> data = new HashMap<>();
        data.put("answer", answer);
        return new TaskMessage(TYPE, data);
    }

    public SessionDescription toSessionDescription() {
        return new SessionDescription(SessionDescription.Type.ANSWER, this.getSdp());
    }
}
