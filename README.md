# SaltyRTC WebRTC Task for Java

[![Build status](https://circleci.com/gh/saltyrtc/saltyrtc-task-webrtc-java.svg?style=shield&circle-token=:circle-token)](https://circleci.com/gh/saltyrtc/saltyrtc-task-webrtc-java)
[![Java Version](https://img.shields.io/badge/java-8%2B-orange.svg)](https://github.com/saltyrtc/saltyrtc-task-webrtc-java)
[![License](https://img.shields.io/badge/license-MIT%20%2F%20Apache%202.0-blue.svg)](https://github.com/saltyrtc/saltyrtc-task-webrtc-java)
[![Chat on Gitter](https://badges.gitter.im/saltyrtc/Lobby.svg)](https://gitter.im/saltyrtc/Lobby)

This is a [SaltyRTC](https://github.com/saltyrtc/saltyrtc-meta) [WebRTC
task](https://github.com/saltyrtc/saltyrtc-meta/blob/master/Task-WebRTC.md)
implementation for Java 8+.

For an application example, please see our [demo
application](https://github.com/saltyrtc/saltyrtc-demo).


## Installing

The package is available on Maven Central.

Gradle:

```groovy
compile 'org.saltyrtc:saltyrtc-task-webrtc:0.18.0'
```

Maven:

```xml
<dependency>
  <groupId>org.saltyrtc</groupId>
  <artifactId>saltyrtc-task-webrtc</artifactId>
  <version>0.18.0</version>
  <type>pom</type>
</dependency>
```


## Usage

To create the task instance, you need to use the `WebRTCTaskBuilder` instance
which can be used to configure the task before creating it.

The below configuration represents the default values chosen by the builder as
if you had not configured the builder and just called `.build()` directly.

```java
final WebRTCTask task = new WebRTCTaskBuilder()
    .withVersion(WebRTCTaskVersion.V1)
    .withHandover(true)
    .withMaxChunkLength(262144)
    .build();
```

To send offers, answers and candidates, use the following task methods:

* `task.sendOffer(offer: @NonNull Offer): void`
* `task.sendAnswer(answer: @NonNull Answer): void`
* `task.sendCandidates(candidate: @NonNull Candidate[]): void`

You can register an event handler in the following way:

```java
task.setMessageHandler(new MessageHandler() {
    @Override
    public void onOffer(@NonNull Offer offer) {
        // Handle offer
    }

    @Override
    public void onAnswer(@NonNull Answer answer) {
        // Handle answer
    }

    @Override
    public void onCandidates(@NonNull Candidate[] candidates) {
        // Handle candidates
    }
});
```

### Data Channel Crypto Context

The task provides another security layer for data channels which can be
leveraged by usage of a `DataChannelCryptoContext` instance. To retrieve such
an instance, call:

```java
final DataChannelCryptoContext context = task.createCryptoContext(dataChannel.id);
```

You can encrypt messages on the sending end in the following way:

```java
final Box box = context.encrypt(yourData);
dataChannel.send(ByteBuffer.wrap(box.toBytes()));
```

On the receiving end, decrypt the message by the use of the crypto context:

```java
final Box box = new Box(message);
final byte[] yourData = context.decrypt(
    box, DataChannelCryptoContext.NONCE_LENGTH);
```

Note, that you should not use a crypto context for a data channel that is being
used for handover. The task will take care of encryption and decryption itself.

### Handover

Before initiating the handover, the application needs to fetch the
`SignalingTransportLink` instance which contains the necessary information to
create a data channel.

```java
final SignalingTransportLink link = task.getTransportLink();

final DataChannel.Init parameters = new DataChannel.Init();
parameters.id = link.getId();
parameters.negotiated = true;
parameters.ordered = true;
parameters.protocol = link.getProtocol();

final DataChannel dataChannel = peerConnection.createDataChannel(
    link.getLabel(), parameters);
```

Note that the data channel used for handover **must** be created with the
label and parameters as shown in the above code snippet.

Now that you have created the channel, you need to implement the
`ISignalingTransportHandler` interface. Below is a minimal handler that forwards
the necessary events and messages to the created data channel.

```java
final ISignalingTransportHandler handler = new ISignalingTransportHandler() {
    @Override
    public long getMaxMessageSize() {
        return peerConnection.sctp().getMaxMessageSize();
    }

    @Override
    public void close() {
        dataChannel.close();
    }

    @Override
    public void send(@NonNull final ByteBuffer message) {
        // Note: Always send binary
        dataChannel.send(new DataChannel.Buffer(message, true));
    }
};
```

Furthermore, you have to bind all necessary events in order to connect the data
channel to the `SignalingTransportLink`.

```java
dataChannel.registerObserver(new DataChannel.Observer() {
    // [...]

    @Override
    public void onStateChange() {
        switch (dataChannel.state()) {
            case OPEN:
                task.handover(handler);
                break;
            case CLOSING:
                link.closing();
                break;
            case CLOSED:
                link.closed();
                break;
        }
    }

    @Override
    public void onMessage(@NonNull final DataChannel.Buffer buffer) {
        if (!buffer.binary) {
            // Note: This should be handled as a protocol error
            task.close(CloseCode.PROTOCOL_ERROR);
        } else {
            link.receive(buffer.data);
        }
    }

    // [...]
});
```

The above setup will forward the `CLOSING`/`CLOSED` state and all messages to
the task by the use of the `SignalingTransportLink`. On `OPEN`, the handover
will be initiated.

To be signalled once the handover is finished, you need to register the
`handover` event on the SaltyRTC client instance.

### Logging

The library uses the slf4j logging API. Configure a logger (e.g. slf4j-simple)
to see the log output.


## Manual Testing

To try a development version of the library, you can build a local version to
the maven repository at `/tmp/maven`:

    ./gradlew uploadArchives

Include it in your project like this:

    repositories {
        ...
        maven { url "/tmp/maven" }
    }


## Coding Guidelines

Unfortunately we cannot use all Java 8 features, in order to be compatible with
Android API <24. Please avoid using the following APIs:

- `java.lang.annotation.Repeatable`
- `AnnotatedElement.getAnnotationsByType(Class)`
- `java.util.stream`
- `java.lang.FunctionalInterface`
- `java.lang.reflect.Method.isDefault()`
- `java.util.function`

The CI tests contains a script to ensure that these APIs aren't being called.
You can also run it manually:

    bash .circleci/check_android_support.sh


## Automated Testing

### 1. Preparing the Server

First, clone the `saltyrtc-server-python` repository.

    git clone https://github.com/saltyrtc/saltyrtc-server-python
    cd saltyrtc-server-python

Then create a test certificate for localhost, valid for 5 years.

    openssl req -new -newkey rsa:1024 -nodes -sha256 \
        -out saltyrtc.csr -keyout saltyrtc.key \
        -subj '/C=CH/O=SaltyRTC/CN=localhost/'
    openssl x509 -req -days 1825 \
        -in saltyrtc.csr \
        -signkey saltyrtc.key -out saltyrtc.crt

Create a Java keystore containing this certificate.

    keytool -import -trustcacerts -alias root \
        -file saltyrtc.crt -keystore saltyrtc.jks \
        -storetype JKS -storepass saltyrtc -noprompt

Create a Python virtualenv with dependencies:

    python3 -m virtualenv venv
    venv/bin/pip install .[logging]

Finally, start the server with the following test permanent key:

    export SALTYRTC_SERVER_PERMANENT_KEY=0919b266ce1855419e4066fc076b39855e728768e3afa773105edd2e37037c20 # Public: 09a59a5fa6b45cb07638a3a6e347ce563a948b756fd22f9527465f7c79c2a864
    venv/bin/saltyrtc-server -v 5 serve -p 8765 \
        -sc saltyrtc.crt -sk saltyrtc.key \
        -k $SALTYRTC_SERVER_PERMANENT_KEY

### 2. Running Tests

Make sure that the certificate keystore from the server is copied or symlinked
to this repository:

    ln -s path/to/saltyrtc-server-python/saltyrtc.jks

With the server started in the background and the `saltyrtc.jks` file in the
current directory, run the tests:

    ./gradlew test


## Security

### Signing

Releases are signed with the following PGP ED25519 public key:

    sec   ed25519 2021-05-05 [SC] [expires: 2025-05-04]
          27655CDD319B686A73661526DCD186BEB204C8FD
    uid           SaltyRTC (Release signing key)

### Responsible Disclosure / Reporting Security Issues

Please report security issues directly to one or both of the following contacts:

- Danilo Bargen
    - Email: mail@dbrgn.ch
    - Threema: EBEP4UCA
    - GPG: [EA456E8BAF0109429583EED83578F667F2F3A5FA][keybase-dbrgn]
- Lennart Grahl
    - Email: lennart.grahl@gmail.com
    - Threema: MSFVEW6C
    - GPG: [3FDB14868A2B36D638F3C495F98FBED10482ABA6][keybase-lgrahl]

[keybase-dbrgn]: https://keybase.io/dbrgn
[keybase-lgrahl]: https://keybase.io/lgrahl


## License

    Copyright (c) 2016-2021 Threema GmbH

    Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
    or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
    copied, modified, or distributed except according to those terms.
