/*
 * Copyright (c) 2016 Threema GmbH / SaltyRTC Contributors
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.task.webrtc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public class SSLContextHelper {

    public static SSLContext getSSLContext() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, KeyManagementException {
        SSLContext sslContext;

        // If a file called "saltyrtc.jks" exists, we use it
        File kf = new File("saltyrtc.jks");
        if (!Config.IGNORE_JKS && kf.exists() && !kf.isDirectory()) {
            System.out.println("Using saltyrtc.jks as TLS keystore");

            // Initialize KeyStore
            final String password = "saltyrtc";
            java.security.KeyStore ks = java.security.KeyStore.getInstance("JKS");
            try {
                ks.load(new FileInputStream(kf), password.toCharArray());
            } catch (FileNotFoundException e) {
                // Should not happen, as we check previously.
                e.printStackTrace();
                throw new RuntimeException("FileNotFoundException");
            }

            // Initialize TrustManager
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);

            // Initialize SSLContext
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);
        } else {
            System.out.println("Using default SSLContext");
            sslContext = SSLContext.getDefault();
        }

        return sslContext;
    }
}
