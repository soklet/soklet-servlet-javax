## How To Contribute
 
#### Basics

Pull requests and bug reports are welcomed.  For enhancement pull requests, please ask first to save time!  It's possible the proposed enhancement is outside the scope or design goals of the project.

#### Local Installation

First configure `SOKLET_JAVADOC_HOME` as described under
[Reproducible Javadoc and Packaging](#reproducible-javadoc-and-packaging).

```shell
$ mvn -Dgpg.skip=true install
```

This will test and build unsigned development artifacts and install them to your
local Maven repository. Use `mvn -Dgpg.skip=true verify` to check the build without
installing it. Signing and publication are separate, explicitly authorized steps.

#### Reproducible Javadoc and Packaging

Compilation and runtime compatibility target Java 21. Documentation uses
the separately pinned Corretto **26.0.2.11.1** generator (runtime `26.0.2.1`).
Before any Maven command that packages Javadoc, including `verify` or `install`,
set `SOKLET_JAVADOC_HOME` to that JDK's home directory; keep `JAVA_HOME` on the
JDK used to compile/test. `mvn test` does not require a documentation JDK.

```sh
export SOKLET_JAVADOC_HOME=/absolute/path/to/corretto-26.0.2.11.1
```

CI downloads and checksum-verifies the Linux documentation JDK separately from
the Java 21/25 test matrix. Release builds use Java 21 for compilation and Java 26
for Javadoc.

External package indexes are checked in under `src/main/javadoc/links`, with
source archive and index checksums in `manifest.json`. Check them with
`node scripts/verify-javadoc-links.mjs`. Dependency upgrades must update the
matching versioned index and link target together.

Ordinary Maven builds use the published core Javadoc site. To use a local core
Javadoc directory, for example when working on an unpublished core version, pass:

```shell
mvn -Dgpg.skip=true -Dsoklet.javadoc.location=/absolute/path/to/core-javadoc clean verify
```

CI obtains this index from the published Javadoc artifact for the POM's Soklet
dependency. Generated links still point to `https://javadoc.soklet.com/`.

#### Continuous Integration

CI builds and tests on JDK 21 and 25 using the Soklet dependency declared in
`pom.xml`. Maven resolves that published version from Maven Central. There is
no separate core checkout or commit selector to update.

CI also resolves the matching published core Javadoc through the POM dependency
and uses its package index for documentation generation. The configured JDK 26
and checked-in external Javadoc indexes remain verified build inputs. These jobs
test and package the project without signing or publishing.

#### Publishing to Maven Central

Contact Mark Allen at mark@revetware.com to request publishing access for the `com.soklet` namespace. Generate a [Central Portal user token](https://central.sonatype.org/publish/generate-portal-token/) and configure its generated username and password in `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central-portal</id>
      <username>YOUR_TOKEN_USERNAME</username>
      <password>YOUR_TOKEN_PASSWORD</password>
    </server>
  </servers>
</settings>
```

The server ID must match the `central-publishing-maven-plugin` configuration in `pom.xml`. Before an explicitly owner-approved release, unlock the exact approved signing key through `gpg-agent` in an interactive session. Never put a GPG passphrase in command arguments, environment variables, Maven settings, project files, logs, or release evidence.

Build, test, generate documentation, sign, and upload with the standard command:

```shell
mvn clean deploy
```

The current Central plugin configuration waits for the uploaded deployment to validate but does not publish it automatically. Review the validated deployment in the [Central Publisher Portal](https://central.sonatype.com/publishing/deployments), then select **Publish**. Published coordinates are immutable, so verify the version and artifacts before completing that step.
