/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.tests.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.support.ReflectionSupport;
import org.saltyrtc.client.SaltyRTC;
import org.saltyrtc.client.SaltyRTCBuilder;
import org.saltyrtc.client.annotations.NonNull;
import org.saltyrtc.client.annotations.Nullable;
import org.saltyrtc.client.crypto.CryptoProvider;
import org.saltyrtc.client.exceptions.ConnectionException;
import org.saltyrtc.client.keystore.Box;
import org.saltyrtc.client.keystore.KeyStore;
import org.saltyrtc.client.signaling.state.SignalingState;
import org.saltyrtc.client.tasks.Task;
import org.saltyrtc.tasks.webrtc.Config;
import org.saltyrtc.tasks.webrtc.WebRTCTask;
import org.saltyrtc.tasks.webrtc.WebRTCTaskBuilder;
import org.saltyrtc.tasks.webrtc.WebRTCTaskVersion;
import org.saltyrtc.tasks.webrtc.crypto.DataChannelCryptoContext;
import org.saltyrtc.tasks.webrtc.exceptions.IllegalStateError;
import org.saltyrtc.tasks.webrtc.messages.Answer;
import org.saltyrtc.tasks.webrtc.messages.Candidate;
import org.saltyrtc.tasks.webrtc.messages.Candidates;
import org.saltyrtc.tasks.webrtc.messages.Offer;
import org.saltyrtc.tasks.webrtc.utils.*;

import java.nio.ByteBuffer;
import java.util.concurrent.*;

import javax.net.ssl.SSLContext;

import static org.junit.jupiter.api.Assertions.*;

class PeerContext {
    @NonNull SaltyRTC signaling;
    @Nullable WebRTCTask task;
    @Nullable SignalingLoopbackDataChannel sdc;

    PeerContext(@NonNull final SaltyRTC signaling) {
        this.signaling = signaling;
    }
}

class PeerContextPair {
    @NonNull final PeerContext initiator;
    @NonNull final PeerContext responder;

    PeerContextPair(@NonNull final SaltyRTC initiator, @NonNull final SaltyRTC responder) {
        this.initiator = new PeerContext(initiator);
        this.responder = new PeerContext(responder);
    }
}

class LoopbackDataChannelPair<T extends LoopbackDataChannel> {
    @NonNull final LoopbackDataChannel<T> idc;
    @NonNull final LoopbackDataChannel<T> rdc;

    LoopbackDataChannelPair(@NonNull final LoopbackDataChannel<T> idc, @NonNull final LoopbackDataChannel<T> rdc) {
        this.idc = idc;
        this.rdc = rdc;
    }
}

@DisplayName("Integration")
class IntegrationTest {
    @NonNull private static final CryptoProvider cryptoProvider = new LazysodiumCryptoProvider();

    static {
        System.setProperty("org.slf4j.simpleLogger.logFile", "System.out");
        if (Config.DEBUG) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        }
    }

    static void await(@NonNull final CountDownLatch latch) throws InterruptedException {
        await(latch, 5);
    }

    static void await(@NonNull final CountDownLatch latch, long timeoutSeconds) throws InterruptedException {
        if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
            throw new RuntimeException("Timed out after " + timeoutSeconds + " seconds");
        }
    }

    static void connectBoth(@NonNull final PeerContextPair pair, @NonNull final SignalingState state)
        throws ConnectionException, InterruptedException {
        // Latches to wait for a specific state
        final CountDownLatch done = new CountDownLatch(2);

        // Connect both
        pair.initiator.signaling.connect();
        pair.responder.signaling.connect();

        // Register state change event
        pair.initiator.signaling.events.signalingStateChanged.register(event -> {
            if (event.getState() == state) {
                done.countDown();
                return true;
            }
            return false;
        });
        pair.responder.signaling.events.signalingStateChanged.register(event -> {
            if (event.getState() == state) {
                done.countDown();
                return true;
            }
            return false;
        });

        // Wait for the state to fire on both
        await(done);
    }

    static void initiateHandover(@NonNull final PeerContext context, @NonNull final CountDownLatch done) {
        // Register handover event
        context.signaling.events.handover.register((event) -> {
            done.countDown();
            return true;
        });

        // Initiate handover
        context.task.handover(context.sdc);
    }

    static void createSignalingLoopbackChannels(@NonNull final PeerContextPair pair, boolean handover)
        throws InterruptedException {
        // Create and bind signalling loopback channels
        pair.initiator.sdc = new SignalingLoopbackDataChannel(pair.initiator.task.getTransportLink());
        pair.responder.sdc = new SignalingLoopbackDataChannel(pair.responder.task.getTransportLink());
        pair.initiator.sdc.attach(pair.responder.sdc);
        pair.responder.sdc.attach(pair.initiator.sdc);

        if (handover) {
            final CountDownLatch done = new CountDownLatch(2);

            // Initiate the handover process for both
            initiateHandover(pair.initiator, done);
            initiateHandover(pair.responder, done);

            // Wait until the handover process has been completed for both
            await(done);
        }
    }

    static @NonNull LoopbackDataChannelPair<LoopbackDataChannel> createLoopbackChannels() {
        // Create and bind loopback channels
        final LoopbackDataChannel<LoopbackDataChannel> idc = new LoopbackDataChannel<>();
        final LoopbackDataChannel<LoopbackDataChannel> rdc = new LoopbackDataChannel<>();
        idc.attach(rdc);
        rdc.attach(idc);
        return new LoopbackDataChannelPair<>(idc, rdc);
    }

    @Nested
    @DisplayName("SaltyRTC")
    class SaltyRTCTest {
        @Test
        @DisplayName("connects")
        void testConnect() throws Exception {
            // Create SaltyRTC instances
            final SSLContext sslContext = SSLContextHelper.getSSLContext();
            final SaltyRTC initiator = new SaltyRTCBuilder(cryptoProvider)
                .connectTo(Config.SALTYRTC_HOST, Config.SALTYRTC_PORT, sslContext)
                .withServerKey(Config.SALTYRTC_SERVER_PUBLIC_KEY)
                .withKeyStore(new KeyStore(cryptoProvider))
                .usingTasks(new Task[] { new DummyTask() })
                .asInitiator();
            final SaltyRTC responder = new SaltyRTCBuilder(cryptoProvider)
                .connectTo(Config.SALTYRTC_HOST, Config.SALTYRTC_PORT, sslContext)
                .withServerKey(Config.SALTYRTC_SERVER_PUBLIC_KEY)
                .withKeyStore(new KeyStore(cryptoProvider))
                .initiatorInfo(initiator.getPublicPermanentKey(), initiator.getAuthToken())
                .usingTasks(new Task[] { new DummyTask() })
                .asResponder();

            // Enable verbose debug mode
            if (Config.VERBOSE) {
                initiator.setDebug(true);
                responder.setDebug(true);
            }

            // Wait until both connected
            connectBoth(new PeerContextPair(initiator, responder), SignalingState.TASK);

            // Ensure task kicked in
            assertEquals(SignalingState.TASK, initiator.getSignalingState());
            assertEquals(SignalingState.TASK, responder.getSignalingState());
        }
    }

    @Nested
    @DisplayName("WebRTCTask")
    class WebRTCTaskTest {
        @NonNull private PeerContextPair pair;

        @BeforeEach
        void setUp() throws Exception {
            // Create WebRTC task instances
            final WebRTCTask initiatorTask = new WebRTCTaskBuilder().build();
            final WebRTCTask responderTask = new WebRTCTaskBuilder().build();

            // Create SaltyRTC instances
            final SSLContext sslContext = SSLContextHelper.getSSLContext();
            final SaltyRTC initiator = new SaltyRTCBuilder(cryptoProvider)
                .connectTo(Config.SALTYRTC_HOST, Config.SALTYRTC_PORT, sslContext)
                .withServerKey(Config.SALTYRTC_SERVER_PUBLIC_KEY)
                .withKeyStore(new KeyStore(cryptoProvider))
                .usingTasks(new Task[] { initiatorTask })
                .asInitiator();
            final SaltyRTC responder = new SaltyRTCBuilder(cryptoProvider)
                .connectTo(Config.SALTYRTC_HOST, Config.SALTYRTC_PORT, sslContext)
                .withServerKey(Config.SALTYRTC_SERVER_PUBLIC_KEY)
                .withKeyStore(new KeyStore(cryptoProvider))
                .initiatorInfo(initiator.getPublicPermanentKey(), initiator.getAuthToken())
                .usingTasks(new Task[] { responderTask })
                .asResponder();

            // Set pair
            this.pair = new PeerContextPair(initiator, responder);
            this.pair.initiator.task = initiatorTask;
            this.pair.responder.task = responderTask;
        }

        @Test
        @DisplayName("can send offers")
        void testSendOffer() throws Exception {
            connectBoth(this.pair, SignalingState.TASK);
            final CountDownLatchResult<Offer> result = new CountDownLatchResult<>(1);
            final Offer expectedOffer = new Offer("YOLO");

            this.pair.responder.task.setMessageHandler(new NullMessageHandler() {
                @Override
                public void onOffer(@NonNull Offer offer) {
                    result.setResult(offer).countDown();
                }
            });
            pair.initiator.task.sendOffer(expectedOffer);

            await(result);
            assertEquals(expectedOffer, result.getResult());
        }

        @Test
        @DisplayName("can send answers")
        void testSendAnswer() throws Exception {
            connectBoth(this.pair, SignalingState.TASK);
            final CountDownLatchResult<Answer> result = new CountDownLatchResult<>(1);
            final Answer expectedAnswer = new Answer("YOLO");

            this.pair.initiator.task.setMessageHandler(new NullMessageHandler() {
                @Override
                public void onAnswer(@NonNull Answer answer) {
                    result.setResult(answer).countDown();
                }
            });
            pair.responder.task.sendAnswer(expectedAnswer);

            await(result);
            assertEquals(expectedAnswer, result.getResult());
        }

        @Test
        @DisplayName("can send candidates")
        void testSendCandidates() throws Exception {
            connectBoth(this.pair, SignalingState.TASK);
            final CountDownLatchResult<Candidate[]> result = new CountDownLatchResult<>(1);

            final Candidate[] expectedCandidates = new Candidate[] {
                new Candidate("FOO", "data", 0),
                new Candidate("BAR", "data", 1),
                null
            };

            this.pair.responder.task.setMessageHandler(new NullMessageHandler() {
                @Override
                public void onCandidates(@NonNull Candidate[] candidates) {
                    result.setResult(candidates).countDown();
                }
            });
            pair.initiator.task.sendCandidates(expectedCandidates);

            await(result);
            assertArrayEquals(expectedCandidates, result.getResult());
            assertEquals(new Candidates(expectedCandidates), new Candidates(result.getResult()));
        }

        @Test
        @DisplayName("ensure handover message not sent on data channel")
        void testSendBufferedCandidates() throws Exception {
            connectBoth(this.pair, SignalingState.TASK);
            createSignalingLoopbackChannels(this.pair, false);

            // Ensure no messages are being transferred on the data channels
            final CountDownLatchResult<String> result = new CountDownLatchResult<>(2);
            final DataChannelMessageHandler handler = message ->
                result.setException(new Exception("Unexpected message"));
            this.pair.initiator.sdc.setMessageHandler(handler);
            this.pair.responder.sdc.setMessageHandler(handler);

            // Start handover process
            initiateHandover(this.pair.initiator, result);
            initiateHandover(this.pair.responder, result);
            await(result);
            assertNull(result.getResult());
        }

        @Test
        @DisplayName("can communicate on handover data channel")
        void testExchangeDataChannelMessages() throws Exception {
            connectBoth(this.pair, SignalingState.TASK);
            createSignalingLoopbackChannels(this.pair, true);

            // Ensure the application message is being received
            final CountDownLatchResult<Object> initiatorApplicationDataResult = new CountDownLatchResult<>(1);
            this.pair.initiator.signaling.events.applicationData.register(event -> {
                initiatorApplicationDataResult.setResult(event.getData()).countDown();
                return true;
            });
            final CountDownLatchResult<Object> responderApplicationDataResult = new CountDownLatchResult<>(1);
            this.pair.responder.signaling.events.applicationData.register(event -> {
                responderApplicationDataResult.setResult(event.getData()).countDown();
                return true;
            });

            // Ensure the data channel is being used and it's encrypted
            final CountDownLatchResult<ByteBuffer> initiatorRawDataResult = new CountDownLatchResult<>(1);
            this.pair.initiator.sdc.setMessageHandler(message -> initiatorRawDataResult.setResult(message).countDown());
            final CountDownLatchResult<ByteBuffer> responderRawDataResult = new CountDownLatchResult<>(1);
            this.pair.responder.sdc.setMessageHandler(message -> responderRawDataResult.setResult(message).countDown());

            // Send application messages
            this.pair.initiator.signaling.sendApplicationMessage("meow");
            this.pair.responder.signaling.sendApplicationMessage("rawr");

            // Ensure the application messages have been exchanged
            assertEquals("rawr", initiatorApplicationDataResult.getResult());
            assertEquals("meow", responderApplicationDataResult.getResult());

            // Ensure they have been exchanged via the data channel and they were encrypted
            // Note: 77 bytes = 9 (chunking) + 24 (nonce) + 16 (poly1305 mac) + 28 (payload)
            assertEquals(77, initiatorRawDataResult.getResult().limit());
            assertEquals(77, responderRawDataResult.getResult().limit());
        }

        @Test
        @DisplayName("can use a crypto context for a data channel")
        void testCryptoContextWithDataChannel() throws Exception {
            connectBoth(this.pair, SignalingState.TASK);
            final LoopbackDataChannelPair<LoopbackDataChannel> dcPair = createLoopbackChannels();

            // Data to be sent and channel id
            final byte[] expectedData = new byte[] { 1, 0, 57, 5, 9, 0 };
            final int id = 65534;

            // Receive data on the responder
            final CountDownLatchResult<ByteBuffer> dataResult = new CountDownLatchResult<>(1);
            dcPair.rdc.setMessageHandler(message -> dataResult.setResult(message).countDown());

            // Send data via the initiator
            {
                final DataChannelCryptoContext crypto = this.pair.initiator.task.createCryptoContext(id);
                final Box box = crypto.encrypt(expectedData);
                dcPair.idc.send(ByteBuffer.wrap(box.toBytes()));
            }

            // Wait for the data
            await(dataResult);

            // Decrypt data on the responder
            {
                final DataChannelCryptoContext crypto = this.pair.responder.task.createCryptoContext(id);
                final byte[] actualData = crypto.decrypt(
                    new Box(dataResult.getResult(), DataChannelCryptoContext.NONCE_LENGTH));
                assertArrayEquals(expectedData, actualData);
            }
        }

        @Test
        @DisplayName("cannot do handover if disabled via constructor")
        void testHandoverDisabled() throws Exception {
            final SSLContext sslContext = SSLContextHelper.getSSLContext();
            this.pair.responder.task = new WebRTCTaskBuilder()
                .withHandover(false)
                .build();
            this.pair.responder.signaling = new SaltyRTCBuilder(cryptoProvider)
                .connectTo(Config.SALTYRTC_HOST, Config.SALTYRTC_PORT, sslContext)
                .withServerKey(Config.SALTYRTC_SERVER_PUBLIC_KEY)
                .withKeyStore(new KeyStore(cryptoProvider))
                .initiatorInfo(this.pair.initiator.signaling.getPublicPermanentKey(),
                               this.pair.initiator.signaling.getAuthToken())
                .usingTasks(new Task[] { this.pair.responder.task })
                .asResponder();
            connectBoth(this.pair, SignalingState.TASK);

            // Ensure we cannot initiate the handover process
            IllegalStateError exception = assertThrows(IllegalStateError.class, () ->
                this.pair.responder.task.getTransportLink());
            assertEquals("Handover has not been negotiated", exception.getMessage());
            exception = assertThrows(IllegalStateError.class, () -> this.pair.responder.task.handover(null));
            assertEquals("Handover has not been negotiated", exception.getMessage());
        }

        @Test
        @DisplayName("is backwards compatible to legacy v0")
        void testV0BackwardsCompatibility() throws Exception {
            final SSLContext sslContext = SSLContextHelper.getSSLContext();

            // Initiator: Offers only v0
            this.pair.initiator.task = new WebRTCTaskBuilder()
                .withVersion(WebRTCTaskVersion.V0)
                .withMaxChunkLength(1337)
                .build();
            this.pair.initiator.signaling = new SaltyRTCBuilder(cryptoProvider)
                .connectTo(Config.SALTYRTC_HOST, Config.SALTYRTC_PORT, sslContext)
                .withServerKey(Config.SALTYRTC_SERVER_PUBLIC_KEY)
                .withKeyStore(new KeyStore(cryptoProvider))
                .usingTasks(new Task[] { this.pair.initiator.task })
                .asInitiator();

            // Responder: Offers v1 and v0
            final WebRTCTask responderTaskV1 = this.pair.responder.task;
            this.pair.responder.task = new WebRTCTaskBuilder()
                .withVersion(WebRTCTaskVersion.V0)
                .withMaxChunkLength(1337)
                .build();
            this.pair.responder.signaling = new SaltyRTCBuilder(cryptoProvider)
                .connectTo(Config.SALTYRTC_HOST, Config.SALTYRTC_PORT, sslContext)
                .withServerKey(Config.SALTYRTC_SERVER_PUBLIC_KEY)
                .withKeyStore(new KeyStore(cryptoProvider))
                .initiatorInfo(this.pair.initiator.signaling.getPublicPermanentKey(),
                               this.pair.initiator.signaling.getAuthToken())
                .usingTasks(new Task[] { responderTaskV1, this.pair.responder.task })
                .asResponder();

            // Ensure we can still interact just fine
            connectBoth(this.pair, SignalingState.TASK);

            // Ensure the maximum chunk length (known as `max_packet_size`)
            // has been negotiated.
            int maxChunkLength = (int) ReflectionSupport.tryToReadFieldValue(
                WebRTCTask.class.getDeclaredField("maxChunkLength"), this.pair.initiator.task).get();
            assertEquals(1337, maxChunkLength);
            maxChunkLength = (int) ReflectionSupport.tryToReadFieldValue(
                WebRTCTask.class.getDeclaredField("maxChunkLength"), this.pair.responder.task).get();
            assertEquals(1337, maxChunkLength);
        }

        @Test
        @DisplayName("v1 is negotiated if both v1 and v0 are provided")
        void testTaskVersionNegotiation() throws Exception {
            final SSLContext sslContext = SSLContextHelper.getSSLContext();

            // Initiator: Offers v1 and v0 (in that order)
            final int initiatorMaxChunkLength = 1337;

            this.pair.initiator.task = new WebRTCTaskBuilder()
                .withVersion(WebRTCTaskVersion.V1)
                .withMaxChunkLength(initiatorMaxChunkLength)
                .build();
            final WebRTCTask initiatorTaskV0 = new WebRTCTaskBuilder()
                .withVersion(WebRTCTaskVersion.V0)
                .withMaxChunkLength(initiatorMaxChunkLength)
                .build();
            this.pair.initiator.signaling = new SaltyRTCBuilder(cryptoProvider)
                .connectTo(Config.SALTYRTC_HOST, Config.SALTYRTC_PORT, sslContext)
                .withServerKey(Config.SALTYRTC_SERVER_PUBLIC_KEY)
                .withKeyStore(new KeyStore(cryptoProvider))
                .usingTasks(new Task[] { this.pair.initiator.task, initiatorTaskV0 })
                .asInitiator();

            // Responder: Offers v1 and v0 (in that order)
            final int responderMaxChunkLength = 7331;
            this.pair.responder.task = new WebRTCTaskBuilder()
                .withVersion(WebRTCTaskVersion.V1)
                .withMaxChunkLength(responderMaxChunkLength)
                .build();
            final WebRTCTask responderTaskV0 = new WebRTCTaskBuilder()
                .withVersion(WebRTCTaskVersion.V0)
                .withMaxChunkLength(responderMaxChunkLength)
                .build();
            this.pair.responder.signaling = new SaltyRTCBuilder(cryptoProvider)
                .connectTo(Config.SALTYRTC_HOST, Config.SALTYRTC_PORT, sslContext)
                .withServerKey(Config.SALTYRTC_SERVER_PUBLIC_KEY)
                .withKeyStore(new KeyStore(cryptoProvider))
                .initiatorInfo(this.pair.initiator.signaling.getPublicPermanentKey(),
                    this.pair.initiator.signaling.getAuthToken())
                .usingTasks(new Task[] { this.pair.responder.task, responderTaskV0 })
                .asResponder();

            // Ensure the v1 tasks have been chosen
            connectBoth(this.pair, SignalingState.TASK);
            assertEquals(this.pair.initiator.task, this.pair.initiator.signaling.getTask());
            assertEquals(this.pair.responder.task, this.pair.responder.signaling.getTask());

            // Ensure the maximum chunk length has NOT been negotiated
            int maxChunkLength = (int) ReflectionSupport.tryToReadFieldValue(
                WebRTCTask.class.getDeclaredField("maxChunkLength"), this.pair.initiator.task).get();
            assertEquals(initiatorMaxChunkLength, maxChunkLength);
            maxChunkLength = (int) ReflectionSupport.tryToReadFieldValue(
                WebRTCTask.class.getDeclaredField("maxChunkLength"), this.pair.responder.task).get();
            assertEquals(responderMaxChunkLength, maxChunkLength);
            maxChunkLength = (int) ReflectionSupport.tryToReadFieldValue(
                WebRTCTask.class.getDeclaredField("maxChunkLength"), initiatorTaskV0).get();
            assertEquals(initiatorMaxChunkLength, maxChunkLength);
            maxChunkLength = (int) ReflectionSupport.tryToReadFieldValue(
                WebRTCTask.class.getDeclaredField("maxChunkLength"), responderTaskV0).get();
            assertEquals(responderMaxChunkLength, maxChunkLength);
        }
    }
}
