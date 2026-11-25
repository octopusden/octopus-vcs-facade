# VCS Facade

## Properties

| Name                           | Description                                                               | Mandatory |
|--------------------------------|---------------------------------------------------------------------------|-----------|
| test.platform                  | Test platform. Possible values are: `docker`, `okd`.                      | **+**     |
| test.profile                   | Test profile. Possible values are: `bitbucket`, `gitea` .                 | **+**     |
| docker.registry                | Docker registry where 3rd-party base images will be pulled from.          | **+**     |
| octopus.github.docker.registry | Docker registry with octopus images.                                      | **+**     |
| okd.project                    | OKD project (OpenShift namespace). Mandatory if `test.platform` is `okd`. |           |
| okd.cluster-domain             | OKD cluster domain. Mandatory if `test.platform` is `okd`.                |           |
| okd.web-console-url            | URL of OKD web-console. Used to provide links to OKD pods.                |           |
| bitbucket.license              | BitBucket DEV licence. Mandatory if `test.profile` is `bitbucket`.        |           |

## Build prerequisites

* **java** JDK 21
* **docker** with login performed
* standalone **docker-compose** if `test.platform` is `docker`
* **oc** (OpenShift CLI) with login performed if `test.platform` is `okd`

## Build features

Project version is used as docker image tag and **project-unique identifier** of test environment if `test.platform` is `okd`. By default, it is generated using host name and must be overriden via -Pversion=... on CI/CD.   

## Repository provisioning

Two read endpoints for the component provisioning service, which checks a repository's group and reads its state
before provisioning it. Off by default: nothing below exists, and no authentication is wired, until
`vcs-facade.provisioning.enabled` is `true`. Existing endpoints stay public either way.

### Configuration

With provisioning enabled the service validates bearer tokens against an auth server and **must reach it at startup**
(it reads the OpenID configuration then, and fails to start otherwise). Callers need the `CREATE_REPOSITORIES`
permission, which is mapped from the roles of the user via `octopus-security.roles`. Each VCS service gets a
`provisioning` block: a credential (`token`, or `username` and `password`) and `allowed-groups`, the Bitbucket project
keys or Gitea organisations provisioning may touch (compared case-insensitively; empty means nothing is allowed).

```yaml
vcs-facade:
  provisioning:
    enabled: true
  vcs:
    services:
      - id: bitbucket
        type: BITBUCKET
        # ... the usual settings of the service
        provisioning:
          token: ${PROVISIONING_TOKEN}   # or username and password
          allowed-groups:
            - PROJ
auth-server:
  url: https://auth.example.com
  realm: my-realm
octopus-security:
  roles:
    ROLE_PROVISIONER:
      - CREATE_REPOSITORIES
```

### Endpoints

Both take the repository SSH URL as the `sshUrl` query parameter and a bearer token.

| Endpoint                                          | Answer                                                                                                                                                                                       |
|---------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GET /rest/api/2/groups?sshUrl=`                  | `200 {"exists": true\|false}`: whether the project or organisation exists.                                                                                                                   |
| `GET /rest/api/2/repository/state?sshUrl=`        | `200 {"status": "MISSING"\|"PRESENT", "marker", "defaultBranch", "hasCommits", "rootCommitMarker"}`. `marker` is the repository description, `rootCommitMarker` the `Provisioning-Marker` trailer of the root commit (`null` when absent). |

A missing group or repository is a normal `200` answer. Errors:

| Status | Cause                                                                                                                       |
|--------|-----------------------------------------------------------------------------------------------------------------------------|
| 400    | `INVALID_REQUEST`: no configured VCS service supports the SSH URL.                                                          |
| 401    | No token, or the token is rejected.                                                                                         |
| 403    | The token lacks `CREATE_REPOSITORIES`, or `TARGET_NOT_ALLOWED`: the VCS has no provisioning credential or the group is not allowed. |
| 5xx    | The VCS could not be read. This is never answered as "missing".                                                             |

Paths nothing is mapped to answer `404` and wrong methods `405`, with or without provisioning enabled.
