/*
 * Copyright (c) 2016-2019 Threema GmbH
 *
 * Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
 * or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
 * copied, modified, or distributed except according to those terms.
 */

package org.saltyrtc.tasks.webrtc.utils;

import com.goterl.lazysodium.LazySodiumJava;
import com.goterl.lazysodium.SodiumJava;
import com.goterl.lazysodium.interfaces.Box;
import org.saltyrtc.client.annotations.NonNull;
import org.saltyrtc.client.crypto.CryptoException;
import org.saltyrtc.client.crypto.CryptoInstance;
import org.saltyrtc.client.crypto.CryptoProvider;

import static com.goterl.lazysodium.interfaces.Box.BEFORENMBYTES;
import static com.goterl.lazysodium.interfaces.Box.MACBYTES;

/**
 * An implementation of the CryptoInstance interface for lazysodium-java.
 */
public class LazysodiumCryptoInstance implements CryptoInstance {
    @NonNull private final Box.Native sodium;
    @NonNull private final byte[] sharedKey;

    public LazysodiumCryptoInstance(
        @NonNull SodiumJava sodium,
        @NonNull byte[] ownPrivateKey,
        @NonNull byte[] otherPublicKey
    ) throws CryptoException {
        this.sodium = new LazySodiumJava(sodium);

        // Verify key lengths
        if (otherPublicKey.length != CryptoProvider.PUBLICKEYBYTES) {
            throw new CryptoException("Invalid public key length");
        }
        if (ownPrivateKey.length != CryptoProvider.PRIVATEKEYBYTES) {
            throw new CryptoException("Invalid private key length");
        }

        // Precalculate shared key
        final byte[] k = new byte[BEFORENMBYTES];
        final boolean success = this.sodium.cryptoBoxBeforeNm(k, otherPublicKey, ownPrivateKey);
        if (!success) {
            throw new CryptoException("Could not precalculate shared key");
        }
        this.sharedKey = k;
    }

    @NonNull
    @Override
    public byte[] encrypt(@NonNull byte[] data, @NonNull byte[] nonce) throws CryptoException {
        final byte[] ciphertext = new byte[data.length + MACBYTES];
        final boolean success = this.sodium.cryptoBoxEasyAfterNm(ciphertext, data, data.length, nonce, this.sharedKey);
        if (!success) {
            throw new CryptoException("Could not encrypt data");
        }
        return ciphertext;
    }

    @NonNull
    @Override
    public byte[] decrypt(@NonNull byte[] data, @NonNull byte[] nonce) throws CryptoException {
        final byte[] plaintext = new byte[data.length - MACBYTES];
        final boolean success = this.sodium.cryptoBoxOpenEasyAfterNm(plaintext, data, data.length, nonce, this.sharedKey);
        if (!success) {
            throw new CryptoException("Could not decrypt data");
        }
        return plaintext;
    }
}
