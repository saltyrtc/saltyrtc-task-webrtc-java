# SaltyRTC WebRTC Task for Java

[![Java Version](https://img.shields.io/badge/java-7%2B-orange.svg)](https://github.com/saltyrtc/saltyrtc-client-java)
[![License](https://img.shields.io/badge/license-MIT%20%2F%20Apache%202.0-blue.svg)](https://github.com/saltyrtc/saltyrtc-client-java)

This is a [SaltyRTC](https://github.com/saltyrtc/saltyrtc-meta) [WebRTC
task](https://github.com/saltyrtc/saltyrtc-meta/blob/master/Task-WebRTC.md)
implementation for Java 7+.

For now, as long as `RTCPeerConnection` only works on Android, this library
will not work outside of projects.

This library includes a WebRTC build based on commit
ed01647ea97dbe0ea25ab915237e39143b1978d7 (2017-02-02 04:23:24 -0800). The build
was created using [webrtc-build-docker](https://github.com/threema-ch/webrtc-build-docker).

The development is still ongoing, the current version is only at alpha-level
and should not be used for production yet.


## Installing

The package is available [on Bintray](https://bintray.com/saltyrtc/maven/saltyrtc-client/).
It includes the WebRTC PeerConnection build for the `armeabi-v7a` and `x86` architectures.

Gradle:

```groovy
compile 'org.saltyrtc.tasks.webrtc:saltyrtc-task-webrtc:0.5.1'
```

Maven:

```xml
<dependency>
  <groupId>org.saltyrtc.tasks.webrtc</groupId>
  <artifactId>saltyrtc-task-webrtc</artifactId>
  <version>0.5.1</version>
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


## Dependency Verification

This project uses [gradle-witness](https://github.com/WhisperSystems/gradle-witness)
to make sure that you always get the exact same versions of your dependencies.


## Hashes

These are the SHA256 hashes for the published releases of this project:

- v0.5.1: `ec74be6551dd4c886f4f4c4928b5645249b9dd203836a5808aed9d6a602c251c`
- v0.5.0: `6949efa248dcbf22b9c2ffec1063ce54557cfce4be11ef6f4d9fcb0549bca150`
- v0.4.1: `105423ed377e8c149cb6a4fe67be63e43bd83102191740edc4e3c4b0f3a9cbfe`
- v0.4.0: `a71ec1a7af32c104179569d037fea249a9c399d2eb27b07456d9ae578170dc2d`
- v0.3.3: `73e9a067c849836a5634e8d61f4bc9e046bb91feebc85ebc6a01de35866ee27e`
- v0.3.2: `4c36304a207fff8030a1b21b883c97f30101b44963817107a8d6034d433ca27f`
- v0.3.1: `27856a600db2fe1e1a8d8449123c4a110a481ca828cfa43ee618a65f86ca83d0`
- v0.3.0: `66a92915c1936a8065aa2cbb824ee6ba91d0c2667c22aa0f0bc88a79a4f710fe`


## License

    Copyright (c) 2016 Threema GmbH

    Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
    or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
    copied, modified, or distributed except according to those terms.
