/*
 * Copyright (c) 2016-2017 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc;

import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class SSLContextHelper {

    public static final String derCertBase64 = "";

    public static SSLContext getSSLContext() throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, KeyManagementException {
        SSLContext sslContext;

        // If the derCertBase64 var is not empty, use it
        if (!derCertBase64.isEmpty()) {
            System.out.println("Using custom certificate as TLS keystore");

            // Load certificate into trust store
            final byte[] der = Base64.decode(derCertBase64, Base64.DEFAULT);
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(der));
            String alias = cert.getSubjectX500Principal().getName();
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null);
            trustStore.setCertificateEntry(alias, cert);

            // Get a trust manager
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
            tmf.init(trustStore);
            TrustManager[] trustManagers = tmf.getTrustManagers();

            // Initialize SSL context
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, null);
        } else {
            System.out.println("Using default SSLContext");
            sslContext = SSLContext.getDefault();
        }

        return sslContext;
    }
}
