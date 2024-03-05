# VCS Facade

## JDK version

17

## Project properties

| Name                           | Description                                                        | Mandatory |
|--------------------------------|--------------------------------------------------------------------|-----------|
| test.profile                   | Test profile. Possible values are: `bitbucket`, `gitea`, `gitlab`. | **+**     |
| docker.registry                | Docker registry where 3rd-party base images will be pulled from.   | **+**     |
| octopus.github.docker.registry | Docker registry with octopus images.                               | **+**     |
| bitbucket.license              | BitBucket DEV licence. Required if test.profile = `bitbucket`.     |           |

## Features / limitations

VCS servers must be configured to use default ports for http(s)/ssh (urls must not contain port specified).
