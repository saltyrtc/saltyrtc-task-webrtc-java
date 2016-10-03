/*
 * Copyright (c) 2016 Threema GmbH / SaltyRTC Contributors
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.task.webrtc;

import org.saltyrtc.client.annotations.NonNull;
import org.saltyrtc.client.annotations.Nullable;
import org.saltyrtc.client.messages.c2c.TaskMessage;
import org.saltyrtc.client.signaling.SignalingInterface;
import org.saltyrtc.client.tasks.Task;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A dummy task that doesn't do anything.
 */
public class DummyTask implements Task {

    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger("SaltyRTC.DummyTask");

    public boolean initialized = false;
    public Map<Object, Object> peerData;
    protected String name;
    protected SignalingInterface signaling;

    public DummyTask() {
        this.name = "dummy.tasks.saltyrtc.org";
    }

    public DummyTask(String name) {
        this.name = name;
    }

    @Override
    public void init(SignalingInterface signaling, Map<Object, Object> data) {
        this.peerData = data;
        this.initialized = true;
        this.signaling = signaling;
    }

    @Override
    public void onPeerHandshakeDone() {
        // Do nothing
    }

    @Override
    public void onTaskMessage(TaskMessage message) {
        LOG.info("Got new task message");
    }

    @Override
    public void sendSignalingMessage(byte[] payload) {
        LOG.info("Sending signaling message (" + payload.length + " bytes)");
    }

    @NonNull
    @Override
    public String getName() {
        return this.name;
    }

    @NonNull
    @Override
    public List<String> getSupportedMessageTypes() {
        return new ArrayList<>();
    }

    @Nullable
    @Override
    public Map<Object, Object> getData() {
        return null;
    }

}
