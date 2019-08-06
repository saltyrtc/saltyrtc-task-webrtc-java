/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc;

import org.saltyrtc.client.annotations.NonNull;

public enum WebRTCTaskVersion {
    V0,
    V1;

    @NonNull public String toProtocolName() {
        switch (this) {
            case V0:
                return "v0.webrtc.tasks.saltyrtc.org";
            case V1:
                return "v1.webrtc.tasks.saltyrtc.org";
            default:
                throw new RuntimeException("Unhandled version: " + this.toString());
        }
    }
}
