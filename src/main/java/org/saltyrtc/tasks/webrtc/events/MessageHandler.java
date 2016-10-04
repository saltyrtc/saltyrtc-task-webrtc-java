/*
 * Copyright (c) 2016 Threema GmbH / SaltyRTC Contributors
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.events;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.util.List;

public interface MessageHandler {
	/**
     * Peer sends an offer.
     */
    void onOffer(SessionDescription sd);

    /**
     * Peer sends an answer.
     */
    void onAnswer(SessionDescription sd);

	/**
	 * Peer sends ICE candidates.
     */
    void onCandidates(List<IceCandidate> candidates);
}
