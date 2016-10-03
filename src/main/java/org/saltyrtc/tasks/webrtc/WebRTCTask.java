package org.saltyrtc.tasks.webrtc;

import org.saltyrtc.client.annotations.NonNull;
import org.saltyrtc.client.annotations.Nullable;
import org.saltyrtc.client.exceptions.ValidationError;
import org.saltyrtc.client.helpers.ValidationHelper;
import org.saltyrtc.client.messages.c2c.TaskMessage;
import org.saltyrtc.client.signaling.SignalingInterface;
import org.saltyrtc.client.tasks.Task;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WebRTC Task.
 *
 * This task uses the end-to-end encryption techniques of SaltyRTC to set up a secure WebRTC
 * peer-to-peer connection. It also adds another security layer for data channels that are available
 * to users. The signalling channel will persist after being handed over to a dedicated data channel
 * once the peer-to-peer connection has been set up. Therefore, further signalling communication
 * between the peers does not require a dedicated WebSocket connection over a SaltyRTC server.
 */
public class WebRTCTask implements Task {

    // Logger
    private static final Logger LOG = org.slf4j.LoggerFactory.getLogger("SaltyRTC.WebRTC");

    // Constants as defined by the specification
    private static final String PROTOCOL_NAME = "v0.webrtc.tasks.saltyrtc.org";
    private static final Integer MAX_PACKET_SIZE = 16384;

    // Data fields
    private static final String FIELD_EXCLUDE = "exclude";
    private static final String FIELD_MAX_PACKET_SIZE = "max_packet_size";

    // Initialization state
    private boolean initialized = false;

    // Exclude list
    private Set<Integer> exclude = new HashSet<>();

    // Effective max packet size
    private Integer maxPacketSize;

    @Override
    public void init(SignalingInterface signaling, Map<Object, Object> data) throws ValidationError {
        this.processExcludeList(data.get(FIELD_EXCLUDE));
        this.processMaxPacketSize(data.get(FIELD_MAX_PACKET_SIZE));
        this.initialized = true;
    }

    @Override
    public void onPeerHandshakeDone() {
        // Do nothing
    }

    /**
     * The exclude field MUST contain an Array of WebRTC data channel IDs (non-negative integers)
     * that SHALL not be used for the signalling channel. The client SHALL store this list for usage
     * during handover.
     */
    private void processExcludeList(Object value) throws ValidationError {
        if (value == null) {
            throw new ValidationError(FIELD_EXCLUDE + " field may not be null");
        }
        final List<Integer> ids = ValidationHelper.validateTypedList(value, Integer.class, FIELD_EXCLUDE);
        this.exclude.addAll(ids);
    }

    /**
     * The max_packet_size field MUST contain either 0 or a positive integer. If one client's value
     * is 0 but the other client's value is greater than 0, the larger of the two values SHALL be
     * stored to be used for data channel communication. Otherwise, the minimum of both clients'
     * maximum size SHALL be stored. The stored value SHALL be readable by user applications, so a
     * user application can have its own message chunking implementation if desired.
     */
    private void processMaxPacketSize(Object value) throws ValidationError {
        final Integer maxPacketSize = ValidationHelper.validateInteger(value, 0, Integer.MAX_VALUE, FIELD_MAX_PACKET_SIZE);
        if (maxPacketSize == 0 && MAX_PACKET_SIZE == 0) {
            this.maxPacketSize = 0;
        } else if (maxPacketSize == 0 || MAX_PACKET_SIZE == 0) {
            this.maxPacketSize = Math.max(maxPacketSize, MAX_PACKET_SIZE);
        } else {
            this.maxPacketSize = Math.min(maxPacketSize, MAX_PACKET_SIZE);
        }

    }

    @Override
    public void onTaskMessage(TaskMessage message) {
        LOG.info("New task message arrived");
    }

    @Override
    public void sendSignalingMessage(byte[] payload) {
        LOG.info("TODO: Send signaling message");
    }

    @NonNull
    @Override
    public String getName() {
        return WebRTCTask.PROTOCOL_NAME;
    }

    @NonNull
    @Override
    public List<String> getSupportedMessageTypes() {
        final List<String> types = new ArrayList<>();
        types.add("offer");
        types.add("answer");
        types.add("candidate");
        types.add("handover");
        types.add("close"); // TODO: Hmm... This conflicts with the signaling types.
        return types;
    }

    /**
     * Return the max packet size, or `null` if the task has not yet been initialized.
     */
    @Nullable
    public Integer getMaxPacketSize() {
        if (this.initialized) {
            return this.maxPacketSize;
        }
        return null;
    }

    @Nullable
    @Override
    public Map<Object, Object> getData() {
        final Map<Object, Object> map = new HashMap<>();
        map.put(WebRTCTask.FIELD_EXCLUDE, this.exclude);
        map.put(WebRTCTask.FIELD_MAX_PACKET_SIZE, WebRTCTask.MAX_PACKET_SIZE);
        return map;
    }
}
