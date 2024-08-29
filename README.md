# VCS Facade

## Features / limitations

VCS servers must be configured to use default ports for http(s)/ssh (urls must not contain port specified).

## Properties

| Name                           | Description                                                               | Mandatory |
|--------------------------------|---------------------------------------------------------------------------|-----------|
| test.platform                  | Test platform. Possible values are: `docker`, `okd`.                      | **+**     |
| test.profile                   | Test profile. Possible values are: `bitbucket`, `gitea` .                 | **+**     |
| docker.registry                | Docker registry where 3rd-party base images will be pulled from.          | **+**     |
| octopus.github.docker.registry | Docker registry with octopus images.                                      | **+**     |
| okd.project                    | OKD project (OpenShift namespace). Mandatory if `test.platform` is `okd`. |           |
| okd.cluster-domain             | OKD cluster domain. Mandatory if `test.platform` is `okd`.                |           |
| okd.pull-secrets               | OKD pull secrets name. Mandatory if `test.platform` is `okd`.             |           |
| okd.web-console-url            | URL of OKD web-console. Used to provide links to OKD pods.                |           |
| bitbucket.license              | BitBucket DEV licence. Mandatory if `test.profile` is `bitbucket`.        |           |

## Build prerequisites

* **java** JDK 21
* **docker** with login performed
* standalone **docker-compose** if `test.platform` is `docker`
* **oc** (OpenShift CLI) with login performed if `test.platform` is `okd`

## Build features

Project version is used as docker image tag and **project-unique identifier** of test environment if `test.platform` is `okd`. By default, it is generated using host name and must be overriden via -PbuildVersion=... on CI/CD.   

