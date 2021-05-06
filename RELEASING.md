# Releasing

Set variables:

    export VERSION=X.Y.Z
    export GPG_KEY=$(grep signing.gnupg.keyName gradle.properties | sed 's/signing.gnupg.keyName=//')

Ensure that `ossrhUsername` and `ossrhPassword` are defined in your
`~/.gradle/gradle.properties` file.

Update version numbers:

    vim -p build.gradle README.md CHANGELOG.md

Build:

    ./gradlew clean build

Add and commit:

    git commit -S${GPG_KEY} -m "Release v${VERSION}"

Publish the library to Sonatype OSS / Maven Central:

    ./gradlew publish

Afterwards, go to https://s01.oss.sonatype.org/#stagingRepositories and:

- Close the repository
- Release the repository

Tag and push:

    git tag -s -u ${GPG_KEY} v${VERSION} -m "Version ${VERSION}"
    git push && git push --tags
