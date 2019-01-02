/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.exceptions;

public class IllegalStateError extends Error {
    public IllegalStateError() {
        super();
    }

    public IllegalStateError(String detailMessage) {
        super(detailMessage);
    }

    public IllegalStateError(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public IllegalStateError(Throwable throwable) {
        super(throwable);
    }
}
