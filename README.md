# VCS Facade

## JDK version

17

## Project properties

| Name                           | Description                                                      | UT    | FT    | Release |
|--------------------------------|------------------------------------------------------------------|-------|-------|---------|
| docker.registry                | Docker registry where 3rd-party base images will be pulled from. | **+** | **+** | **+**   |
| octopus.github.docker.registry | Docker registry with octopus images.                             |       | **+** | **+**   |
| bitbucket.license              | BitBucket DEV licence.                                           | **+** | **+** |         |

## Features / limitations

VCS servers must be configured to use default ports for http(s)/ssh (urls must not contain port specified).
