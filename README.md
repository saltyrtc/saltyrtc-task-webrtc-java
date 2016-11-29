# SaltyRTC WebRTC Task for Java

[![Java Version](https://img.shields.io/badge/java-7%2B-orange.svg)](https://github.com/saltyrtc/saltyrtc-client-java)
[![License](https://img.shields.io/badge/license-MIT%20%2F%20Apache%202.0-blue.svg)](https://github.com/saltyrtc/saltyrtc-client-java)

This is a [SaltyRTC](https://github.com/saltyrtc/saltyrtc-meta) [WebRTC
task](https://github.com/saltyrtc/saltyrtc-meta/blob/master/Task-WebRTC.md)
implementation for Java 7+.

For now, as long as `RTCPeerConnection` only works on Android, this library
will not work outside of projects.

The development is still ongoing, the current version is only at alpha-level
and should not be used for production yet.


## Installing

The package is available [on Bintray](https://bintray.com/saltyrtc/maven/saltyrtc-client/).
It includes the WebRTC PeerConnection build for the `armeabi-v7a` and `x86` architectures.

Gradle:

```groovy
compile 'org.saltyrtc.tasks.webrtc:saltyrtc-task-webrtc:0.3.2'
```

Maven:

```xml
<dependency>
  <groupId>org.saltyrtc.tasks.webrtc</groupId>
  <artifactId>saltyrtc-task-webrtc</artifactId>
  <version>0.3.2</version>
  <type>pom</type>
</dependency>
```


## Usage

Instantiate the task with the peer connection.

```java
final WebRTCTask task = new WebRTCTask(peerConnection);
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

- v0.3.2: `4c36304a207fff8030a1b21b883c97f30101b44963817107a8d6034d433ca27f`
- v0.3.1: `27856a600db2fe1e1a8d8449123c4a110a481ca828cfa43ee618a65f86ca83d0`
- v0.3.0: `66a92915c1936a8065aa2cbb824ee6ba91d0c2667c22aa0f0bc88a79a4f710fe`


## Publishing

Set variables:

    export VERSION=X.Y.Z
    export GPG_KEY=E7ADD9914E260E8B35DFB50665FDE935573ACDA6
    export BINTRAY_USER=...
    export BINTRAY_KEY=...

Update version numbers:

    vim -p build.gradle README.md CHANGELOG.md

Build:

    ./gradlew build

Add hash to README.md:

    sha256sum build/outputs/aar/saltyrtc-task-webrtc-release.aar

Add and commit:

    git commit -m "Release v${VERSION}"

Publish the library to Bintray:

    ./gradlew bintrayUpload

Tag and push:

    git tag -s -u ${GPG_KEY} v${VERSION} -m "Version ${VERSION}"
    git push && git push --tags

## License

    Copyright (c) 2016 Threema GmbH

    Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
    or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
    copied, modified, or distributed except according to those terms.
