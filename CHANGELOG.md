# Changelog

This project follows semantic versioning.

Possible log types:

- `[added]` for new features.
- `[changed]` for changes in existing functionality.
- `[deprecated]` for once-stable features removed in upcoming releases.
- `[removed]` for deprecated features removed in this release.
- `[fixed]` for any bug fixes.
- `[security]` to invite users to upgrade in case of vulnerabilities.

### v0.13.0 (2018-02-13)

- [changed] Update libjingle builds (commit f7f8cb979b71d14daac57ef9dec4a583e99902b2)
- [changed] Use Java 8

### v0.12.0 (2017-07-27)

- [changed] Update libjingle builds (commit 9c0914f93801eb0c081ae733bd744290e17e5bef)
- [changed] Explicitly dispose data channel after closing

### v0.11.0 (2017-06-29)

- [changed] Update libjingle builds (commit e0eb35dd5387422716020cfdaf0d477b63441c91)

### v0.10.0 (2017-04-03)

- [changed] Update saltyrtc-client to v0.10.+
- [changed] Update chunked-dc to v1.0.0
- [changed] Remove gradle witness

### v0.9.2 (2017-02-28)

- [changed] Fix problems with transitive dependency version pinning

### v0.9.1 (2017-02-16)

- [changed] Remove `allowBackup="true"` and `android:supportsRtl="true"` from `AndroidManifest.xml`

### v0.9.0 (2017-02-07)

- [changed] Update saltyrtc-client to v0.9.+

### v0.6.1 (2017-02-02)

- [fixed] Added missing library (`audio_device_java.jar`)

### v0.6.0 (2017-02-02)

- [changed] Update libjingle builds (commit ed01647ea97dbe0ea25ab915237e39143b1978d7)

### v0.5.1 (2016-12-22)

- [fixed] Fix bad libjingle library path

### v0.5.0 (2016-12-22)

- [changed] Update libjingle builds (commit 0de11aa130b50d834e08a813138fcbbcc945b273)

### v0.4.1 (2016-12-15)

- [added] Make max packet size configurable (#14)

### v0.4.0 (2016-12-12)

- [changed] Update saltyrtc-client to v0.8.+

### v0.3.3 (2016-11-29)

- [fixed] Fix NPE in `WebRTCTask.close()`

### v0.3.2 (2016-11-29)

- [added] Add `clearMessageHandler` method

### v0.3.1 (2016-11-14)

- [added] Make handover optional (#6)
- [changed] Update saltyrtc-client to v0.7.+

### v0.3.0 (2016-10-27)

- [changed] Update saltyrtc-client to v0.6.+

### v0.2.2 (2016-10-25)

- [changed] Add latest libjingle build

### v0.2.1 (2016-10-20)

- [fixed] Update libjingle builds

### v0.2.0 (2016-10-20)

- [changed] Update saltyrtc-client to v0.5.0

### v0.1.9 (2016-10-19)

- [fixed] Downgrade libjingle builds

### v0.1.8 (2016-10-19)

- [fixed] Fix path to arm native libraries

### v0.1.7 (2016-10-19)

- [changed] Update saltyrtc-client to v0.4.0
- [fixed] Fix candidates message handling
- [fixed] Fix bug in task data
- [fixed] Send proper max packet size
- [fixed] Fix boolean check in sendSignalingMessage
- [added] Added WebRTC PeerConnection (libjingle) builds for `arm` and `x86`

### v0.1.6 (2016-10-06)

- [changed] Update saltyrtc-client to v0.3.0

### v0.1.5 (2016-10-06)

- [changed] Update saltyrtc-client

### v0.1.4 (2016-10-06)

- [added] Handle close messages

### v0.1.3 (2016-10-06)

- [changed] The peer connection must now be passed to the `handover` method, not to the constructor.

### v0.1.2 (2016-10-06)

- [changed] The sendXXX methods now only throw `ConnectionException`

### v0.1.1 (2016-10-06)

- [changed] Make `SecureDataChannel` class public

### v0.1.0 (2016-10-06)

- Initial release
