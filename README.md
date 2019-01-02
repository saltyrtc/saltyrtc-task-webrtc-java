# SaltyRTC WebRTC Task for Java

[![Java Version](https://img.shields.io/badge/java-8%2B-orange.svg)](https://github.com/saltyrtc/saltyrtc-client-java)
[![License](https://img.shields.io/badge/license-MIT%20%2F%20Apache%202.0-blue.svg)](https://github.com/saltyrtc/saltyrtc-client-java)
[![Chat on Gitter](https://badges.gitter.im/saltyrtc/Lobby.svg)](https://gitter.im/saltyrtc/Lobby)

This is a [SaltyRTC](https://github.com/saltyrtc/saltyrtc-meta) [WebRTC
task](https://github.com/saltyrtc/saltyrtc-meta/blob/master/Task-WebRTC.md)
implementation for Java 8+.

For now, as long as `RTCPeerConnection` only works on Android, this library
will not work outside of projects.

The development is still ongoing, the current version is only at alpha-level
and should not be used for production yet.

For an application example, please see our [demo
application](https://github.com/saltyrtc/saltyrtc-demo).


## Installing

The package is available [on
Bintray](https://bintray.com/saltyrtc/maven/saltyrtc-client/). It includes the
WebRTC PeerConnection build for the `armeabi-v7a` and `x86` architectures,
based on the release M72. The build was created using
[webrtc-build-docker](https://github.com/threema-ch/webrtc-build-docker).

Gradle:

```groovy
compile 'org.saltyrtc.tasks.webrtc:saltyrtc-task-webrtc:0.15.0'
```

Maven:

```xml
<dependency>
  <groupId>org.saltyrtc.tasks.webrtc</groupId>
  <artifactId>saltyrtc-task-webrtc</artifactId>
  <version>0.15.0</version>
  <type>pom</type>
</dependency>
```


## Usage

Instantiate the task.

```java
final WebRTCTask task = new WebRTCTask();
```

Then, register a message handler:

```java
task.setMessageHandler(new MessageHandler() {
    @Override
    public void onOffer(SessionDescription sd) {
        // Handle offer
    }

    @Override
    public void onAnswer(SessionDescription sd) {
        // Handle answer
    }

    @Override
    public void onCandidates(List<IceCandidate> candidates) {
        for (IceCandidate candidate : candidates) {
            peerConnection.addIceCandidate(candidate);
        }
    }
});
```

Finally, pass the task instance to the `SaltyRTCBuilder` through the
`.usingTasks(...)` method.

Once the signaling channel is open, create offer/answer/candidates as usual and
send them through the signaling channel using the corresponding methods:

- `task.sendOffer(offer)`
- `task.sendAnswer(answer)`
- `task.sendCandidates(candidates)`

As soon as the data channel is open, request a handover of the signaling channel:

```java
dc.registerObserver(new DataChannel.Observer() {
    // ...

    @Override
    public void onStateChange() {
        if (dc.state() == DataChannel.State.OPEN) {
            task.handover();
        }
    }
});
```

To know when the handover is done, subscribe to the SaltyRTC `handover` event.


## Logging

The library uses the slf4j logging API. Configure a logger (e.g. slf4j-simple)
to see the log output.


## Testing

To try a development version of the library, you can build a local version to
the maven repository at `/tmp/maven`:

    ./gradlew uploadArchives

Include it in your project like this:

    repositories {
        ...
        maven { url "/tmp/maven" }
    }


## Hashes

These are the SHA256 hashes for the published releases of this project:

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


## License

    Copyright (c) 2016-2018 Threema GmbH

    Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
    or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
    copied, modified, or distributed except according to those terms.
