/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.utils;

import org.saltyrtc.client.annotations.NonNull;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class Assertions {
    public static void assertListOfBytesEquals(
        @NonNull final List<byte[]> expectedList,
        @NonNull final List<byte[]> actualList
    ) {
        assertEquals(expectedList.size(), actualList.size());
        for (int i = 0; i < expectedList.size(); ++i) {
            assertArrayEquals(expectedList.get(i), actualList.get(i));
        }
    }
}
