# SaltyRTC WebRTC Task for Java

[![Java Version](https://img.shields.io/badge/java-7%2B-orange.svg)](https://github.com/saltyrtc/saltyrtc-client-java)
[![License](https://img.shields.io/badge/license-MIT%20%2F%20Apache%202.0-blue.svg)](https://github.com/saltyrtc/saltyrtc-client-java)

This is a [SaltyRTC](https://github.com/saltyrtc/saltyrtc-meta) WebRTC task implementation
for Java 7+.

For now, as long as `RTCPeerConnection` only works on Android, this library
will not work outside of projects.

The development is still ongoing, the current version is only at alpha-level
and should not be used for production yet.

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
        peerConnection.setRemoteDescription(new SdpObserver() {
            ...

            @Override
            public void onSetSuccess() {
                final SessionDescription answer = ...; // Create answer
                task.sendAnswer(answer);
            }
        }, sd);
    }

    @Override
    public void onAnswer(SessionDescription sd) {
        peerConnection.setRemoteDescription(new SdpObserver() { ... }, sd);
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

## Logging

The library uses the slf4j logging API. Configure a logger (e.g. slf4j-simple)
to see the log output.

## Dependency Verification

This project uses [gradle-witness](https://github.com/WhisperSystems/gradle-witness)
to make sure that you always get the exact same versions of your dependencies.

## License

    Copyright (c) 2016 Threema GmbH / SaltyRTC Contributors

    Licensed under the Apache License, Version 2.0, <see LICENSE-APACHE file>
    or the MIT license <see LICENSE-MIT file>, at your option. This file may not be
    copied, modified, or distributed except according to those terms.
