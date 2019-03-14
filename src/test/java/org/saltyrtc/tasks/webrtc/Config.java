/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc;

/**
 * Test configuration.
 */
public class Config {
    public static String SALTYRTC_HOST = "localhost";
    public static int SALTYRTC_PORT = 8765;
    public static boolean IGNORE_JKS = false;
    public static String SALTYRTC_SERVER_PRIVATE_KEY = "0919b266ce1855419e4066fc076b39855e728768e3afa773105edd2e37037c20";
    public static String SALTYRTC_SERVER_PUBLIC_KEY = "09a59a5fa6b45cb07638a3a6e347ce563a948b756fd22f9527465f7c79c2a864";
    // Show debug output
    public static boolean DEBUG = true;
    // Show verbose output, e.g. websocket frames
    public static boolean VERBOSE = false;
}
