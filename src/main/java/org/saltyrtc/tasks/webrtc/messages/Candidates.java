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

import java.util.*;

public class Candidates implements ToTaskMessage {
    @NonNull private static final String TYPE = "candidates";
    @NonNull private static final String FIELD_CANDIDATES = "candidates";

    @NonNull private final Candidate[] candidates;

    public Candidates(@NonNull final Candidate[] candidates) {
        this.candidates = candidates;
    }

    /**
     * Construct candidates from the "data" field of a TaskMessage.
     */
    public Candidates(@NonNull final Map<String, Object> map) throws ValidationError {
        final List<Map> candidates = ValidationHelper.validateTypedList(
            map.get(FIELD_CANDIDATES), Map.class, FIELD_CANDIDATES, true);
        this.candidates = new Candidate[candidates.size()];

        // Validate and construct candidate instances
        for (int i = 0; i < candidates.size(); i++) {
            final Map candidateMapOrNull = candidates.get(i);
            this.candidates[i] = candidateMapOrNull == null ? null : new Candidate(candidateMapOrNull);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof Candidates)) {
            return false;
        }
        final Candidates other = (Candidates) obj;
        return Objects.deepEquals(this.candidates, other.candidates);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(candidates);
    }

    @NonNull public Candidate[] getCandidates() {
        return this.candidates;
    }

    @Override
    @NonNull public TaskMessage toTaskMessage() {
        final Map<String, Object> data = new HashMap<>();
        final List<Map> candidateList = new ArrayList<>();
        for (Candidate candidate : this.candidates) {
            candidateList.add(candidate == null ? null : candidate.toMap());
        }
        data.put(FIELD_CANDIDATES, candidateList);
        return new TaskMessage(TYPE, data);
    }
}
