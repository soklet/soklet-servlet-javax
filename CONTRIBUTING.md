## How To Contribute
 
#### Basics

Pull requests and bug reports are welcomed.  For enhancement pull requests, please ask first to save time!  It's possible the proposed enhancement is outside the scope or design goals of the project.

#### Local Installation

```shell
$ mvn -Dgpg.skip=true install
```

This will test and build unsigned development artifacts and install them to your
local Maven repository. Use `mvn -Dgpg.skip=true verify` to check the build without
installing it. Signing and publication are separate, explicitly authorized steps.

#### Pushing to Maven Central

Contact Mark Allen at mark@revetware.com to request Sonatype deployment access.

Once granted, make sure your ```~/.m2/settings.xml``` file has ```ossrh``` entries:

```xml
<settings>
  <servers>
    <server>
      <id>ossrh</id>
      <username>YOUR_USERNAME_HERE</username>
      <password>YOUR_PASSWORD_HERE</password>
    </server>    
  </servers>
  <profiles>
    <profile>
      <id>ossrh</id>
      <properties>
        <gpg.passphrase>YOUR_PASSPHRASE_HERE</gpg.passphrase>
      </properties>
    </profile>    
  </profiles>
</settings>
```

You can then push to Maven central:

```shell
$ mvn clean deploy -Dgpg.passphrase=YOUR_PASSPHRASE
```
