/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.events;

import org.saltyrtc.client.annotations.NonNull;
import org.saltyrtc.tasks.webrtc.messages.Answer;
import org.saltyrtc.tasks.webrtc.messages.Candidate;
import org.saltyrtc.tasks.webrtc.messages.Offer;

public interface MessageHandler {
	/**
     * Peer sends an offer.
     */
    void onOffer(@NonNull Offer offer);

    /**
     * Peer sends an answer.
     */
    void onAnswer(@NonNull Answer answer);

	/**
	 * Peer sends ICE candidates.
     *
     * Important: While the array cannot be `null`, individual candidates can
     *            be `null`!
     */
    void onCandidates(@NonNull Candidate[] candidates);
}
