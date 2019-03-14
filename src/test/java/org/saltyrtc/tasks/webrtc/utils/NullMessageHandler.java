/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.utils;

import org.saltyrtc.client.annotations.NonNull;
import org.saltyrtc.tasks.webrtc.events.MessageHandler;
import org.saltyrtc.tasks.webrtc.messages.Answer;
import org.saltyrtc.tasks.webrtc.messages.Candidate;
import org.saltyrtc.tasks.webrtc.messages.Offer;

/**
 * Implements the MessageHandler interface and does nothing by default.
 */
public class NullMessageHandler implements MessageHandler {
    @Override
    public void onOffer(@NonNull Offer offer) {}

    @Override
    public void onAnswer(@NonNull Answer answer) {}

    @Override
    public void onCandidates(@NonNull Candidate[] candidates) {}
}
