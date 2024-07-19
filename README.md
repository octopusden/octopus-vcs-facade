# VCS Facade

## JDK version

21

## Project properties

| Name                           | Description                                                                  | Mandatory |
|--------------------------------|------------------------------------------------------------------------------|-----------|
| test.profile                   | Test profile. Possible values are: `bitbucket`, `gitea` (default), `gitlab`. |           |
| docker.registry                | Docker registry where 3rd-party base images will be pulled from.             | **+**     |
| octopus.github.docker.registry | Docker registry with octopus images.                                         | **+**     |
| bitbucket.license              | BitBucket DEV licence. Required if test.profile is `bitbucket`.              |           |

## Features / limitations

VCS servers must be configured to use default ports for http(s)/ssh (urls must not contain port specified).

## Using local.properties for Custom Configuration

In order to use custom properties defined in local.properties across all submodules of your Gradle project. 
Create a local.properties file in the root directory of your project. Add your custom properties to this file.
Properties specified in this file will override the properties from gradle.properties in all subprojects. 
Below is an example of what the local.properties file might look like:
    
```properties
test.profile=bitbucket
docker.registry=
octopus.github.docker.registry=docker.io
bitbucket.license=
clusterDomain=
localDomain=
helmNamespace=test-env
helmRelease=test-release
platform=okd
```