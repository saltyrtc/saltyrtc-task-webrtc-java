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

The package is available [on
Bintray](https://bintray.com/saltyrtc/maven/saltyrtc-task-webrtc).

Gradle:

```groovy
compile 'org.saltyrtc.tasks.webrtc:saltyrtc-task-webrtc:0.18.0'
```

Maven:

```xml
<dependency>
  <groupId>org.saltyrtc.tasks.webrtc</groupId>
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

### Release Checksums

These are the SHA256 hashes for the published releases of this project:

- v0.18.0: `be01a55f674d3c8ebc5b785d4ea06ddff0a8f7960aaf9d947fc2f155443d95a1`
- v0.17.0: `97b052618be3ac970d359a99ebc4134e78dad9e5f87be4939394b271aca501ce`
- v0.16.1: `af6878a27761624962ab6bc45eedf7b2f5196036d1cf5dcd69fe5724737a2d59`
- v0.16.0: `0bffd6f38b348ae051c9ced7a7a11f2777eba427b1329eb5500166d1884af8f5`
- v0.15.0: `f50d9e22e4bc7059cd2bc5547678aae8e1f2304fe05901bcb0dc0cd1d3d13991`
- v0.14.1: `1c4c8ec94c3aab12d29bf842f23b7598286290d46e4aba76302e002cd96072c9`
- v0.14.0: `f8cc8cc51dab11f263d07a1b23ad18e6f1b718387b646feebb447be3b0fee8e4`
- v0.13.0: `122360a8586526a6293099995b813c96af0c422376f37f1227f36a3415b0d49e`
- v0.12.0: `a6803909f39712b753b475b5b199e9807c767f78790ec3a59b625f80179a43c8`
- v0.11.0: `b4161390b63a0e9166e9991357e57c4a28a1f452f378af0fca571ca19f4aa99d`
- v0.10.0: `fd8602252a130836d0be309e0fdd2bb72741cc92e4346d2ce0e0a95980ae9361`
- v0.9.2: `4142aead0eb700cfe8871e1a0c16e20fdea0249599574ffc0cff3624bd2c778f`
- v0.9.1: `73694f9a4912344457f1c6ae19aaa788131e551c9781554663c3411ce86da6bc`
- v0.9.0: `f9dd33fa19b3be5245ac96c5464b2d1fb6506f87696e272a8264ae6726f09900`
- v0.6.1: `b928cd705501e7be8cf4be0f54fb890f21a5abfd33899d603604bbb7681a7ef1`
- v0.6.0: `7487a1c6e0be4dce7627b62dc1cc9eb17d4d8da38ec8ec88e928163596e50795`
- v0.5.1: `ec74be6551dd4c886f4f4c4928b5645249b9dd203836a5808aed9d6a602c251c`
- v0.5.0: `6949efa248dcbf22b9c2ffec1063ce54557cfce4be11ef6f4d9fcb0549bca150`
- v0.4.1: `105423ed377e8c149cb6a4fe67be63e43bd83102191740edc4e3c4b0f3a9cbfe`
- v0.4.0: `a71ec1a7af32c104179569d037fea249a9c399d2eb27b07456d9ae578170dc2d`
- v0.3.3: `73e9a067c849836a5634e8d61f4bc9e046bb91feebc85ebc6a01de35866ee27e`
- v0.3.2: `4c36304a207fff8030a1b21b883c97f30101b44963817107a8d6034d433ca27f`
- v0.3.1: `27856a600db2fe1e1a8d8449123c4a110a481ca828cfa43ee618a65f86ca83d0`
- v0.3.0: `66a92915c1936a8065aa2cbb824ee6ba91d0c2667c22aa0f0bc88a79a4f710fe`

You can use tools like [gradle
witness](https://github.com/WhisperSystems/gradle-witness) to make sure that
your application always gets the correct version of this library.

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

    Copyright (c) 2016-2019 Threema GmbH

    Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
    or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
    copied, modified, or distributed except according to those terms.
