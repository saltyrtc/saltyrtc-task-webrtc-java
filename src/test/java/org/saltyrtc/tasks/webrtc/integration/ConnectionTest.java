/*
 * Copyright (c) 2016 Threema GmbH / SaltyRTC Contributors
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.integration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.saltyrtc.client.SaltyRTC;
import org.saltyrtc.client.SaltyRTCBuilder;
import org.saltyrtc.client.events.EventHandler;
import org.saltyrtc.client.events.SignalingStateChangedEvent;
import org.saltyrtc.client.keystore.KeyStore;
import org.saltyrtc.client.signaling.state.SignalingState;
import org.saltyrtc.client.tasks.Task;
import org.saltyrtc.tasks.webrtc.Config;
import org.saltyrtc.tasks.webrtc.SSLContextHelper;
import org.saltyrtc.tasks.webrtc.WebRTCTask;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConnectionTest {

    static {
        System.setProperty("org.slf4j.simpleLogger.logFile", "System.out");
        if (Config.DEBUG) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        }
    }

    private SaltyRTC initiator;
    private SaltyRTC responder;
    private Map<String, Boolean> eventsCalled;

    @Before
    public void setUp() throws Exception {
        // Get SSL context
        final SSLContext sslContext = SSLContextHelper.getSSLContext();

        // Create SaltyRTC instances for initiator and responder
        initiator = new SaltyRTCBuilder()
                .connectTo(Config.SALTYRTC_HOST, Config.SALTYRTC_PORT, sslContext)
                .withKeyStore(new KeyStore())
                .usingTasks(new Task[]{ new WebRTCTask() })
                .asInitiator();
        responder = new SaltyRTCBuilder()
                .connectTo(Config.SALTYRTC_HOST, Config.SALTYRTC_PORT, sslContext)
                .withKeyStore(new KeyStore())
                .usingTasks(new Task[]{ new WebRTCTask() })
                .initiatorInfo(initiator.getPublicPermanentKey(), initiator.getAuthToken())
                .asResponder();

        // Enable verbose debug mode
        if (Config.VERBOSE) {
            initiator.setDebug(true);
            responder.setDebug(true);
        }

        // Initiate event registry
        eventsCalled = new HashMap<>();
        final String[] events = new String[] { "Connected", "Error", "Closed" };
        for (String event : events) {
            eventsCalled.put("initiator" + event, false);
            eventsCalled.put("responder" + event, false);
        }

        // Register event handlers
        initiator.events.signalingStateChanged.register(new EventHandler<SignalingStateChangedEvent>() {
            @Override
            public boolean handle(SignalingStateChangedEvent event) {
                switch (event.getState()) {
                    case TASK:
                        eventsCalled.put("initiatorConnected", true);
                        break;
                    case ERROR:
                        eventsCalled.put("initiatorError", true);
                        break;
                    case CLOSED:
                        eventsCalled.put("initiatorClosed", true);
                        break;
                }
                return false;
            }
        });
        responder.events.signalingStateChanged.register(new EventHandler<SignalingStateChangedEvent>() {
            @Override
            public boolean handle(SignalingStateChangedEvent event) {
                switch (event.getState()) {
                    case TASK:
                        eventsCalled.put("responderConnected", true);
                        break;
                    case ERROR:
                        eventsCalled.put("responderError", true);
                        break;
                    case CLOSED:
                        eventsCalled.put("responderClosed", true);
                        break;
                }
                return false;
            }
        });
    }

    @Test
    public void testConnectionSpeed() throws Exception {
        // Max 1s for handshake
        final int MAX_DURATION = 1000;

        // Latches to test connection state
        final CountDownLatch connectedPeers = new CountDownLatch(2);
        initiator.events.signalingStateChanged.register(new EventHandler<SignalingStateChangedEvent>() {
            @Override
            public boolean handle(SignalingStateChangedEvent event) {
                if (event.getState() == SignalingState.TASK) {
                    connectedPeers.countDown();
                }
                return false;
            }
        });
        responder.events.signalingStateChanged.register(new EventHandler<SignalingStateChangedEvent>() {
            @Override
            public boolean handle(SignalingStateChangedEvent event) {
                if (event.getState() == SignalingState.TASK) {
                    connectedPeers.countDown();
                }
                return false;
            }
        });

        // Connect server
        final long startTime = System.nanoTime();
        initiator.connect();
        responder.connect();

        // Wait for full handshake
        final boolean bothConnected = connectedPeers.await(2 * MAX_DURATION, TimeUnit.MILLISECONDS);
        final long endTime = System.nanoTime();
        assertTrue(bothConnected);
        assertFalse(eventsCalled.get("responderError"));
        assertFalse(eventsCalled.get("initiatorError"));
        long durationMs = (endTime - startTime) / 1000 / 1000;
        System.out.println("Full handshake took " + durationMs + " milliseconds");

        // Disconnect
        responder.disconnect();
        initiator.disconnect();

        assertTrue("Duration time (" + durationMs + "ms) should be less than " + MAX_DURATION + "ms",
                   durationMs < MAX_DURATION);
    }

    @After
    public void tearDown() {
        initiator.disconnect();
        responder.disconnect();
    }

}
