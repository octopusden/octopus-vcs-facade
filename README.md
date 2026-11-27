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
before provisioning it, and a create endpoint. The create runs every check on its request and its zip, then answers
`501` until the create itself is implemented: nothing is written to a VCS yet. Off by default: nothing below exists,
and no authentication is wired, until `vcs-facade.provisioning.enabled` is `true`. Existing endpoints stay public either way.

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
    max-content-size: 50MB   # the default
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

`max-content-size` caps the zip upload of the create: a larger upload answers `413`. Uploads are limited this way,
and parsed when the handler reads them, only while provisioning is enabled; otherwise Spring's own
`spring.servlet.multipart.*` settings apply.

### Endpoints

All take the repository SSH URL as the `sshUrl` query parameter and a bearer token.

| Endpoint                                          | Answer                                                                                                                                                                                       |
|---------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GET /rest/api/2/groups?sshUrl=`                  | `200 {"exists": true\|false}`: whether the project or organisation exists.                                                                                                                   |
| `GET /rest/api/2/repository/state?sshUrl=`        | `200 {"status": "MISSING"\|"PRESENT", "marker", "defaultBranch", "hasCommits", "rootCommitMarker"}`. `marker` is the repository description, `rootCommitMarker` the `Provisioning-Marker` trailer of the root commit (`null` when absent). |
| `POST /rest/api/2/repository?sshUrl=`             | `multipart/form-data` with `marker`, `branch`, an optional `commitMessage` and an optional `content`, a zip sent as a file part. Answers `501` for now, after the checks below.                |

A missing group or repository is a normal `200` answer of the reads.

The create runs these checks in order and stops at the first failure:
permission (`401`/`403`), allow-list (`403`), request (`400`), content (`400`/`413`), then `501`.

The request rules: the `marker` is not blank, is a single line, has no leading or trailing whitespace and is at most
255 characters, and the `branch` is given; otherwise `400 INVALID_REQUEST`. Without a `content` part there are no
content checks.

The content rules: `content` is a file part (a plain form field is rejected) and opens as a zip, no entry has a `.git`
path segment at any depth, no path is absolute or escapes the root, no symlink points outside the root (also through
other symlinks of the zip), no path is used twice (or below a file), a directory entry carries no data, and the zip inflates to
at most 10 times `max-content-size` in at most 100,000 entries; otherwise `400 INVALID_CONTENT` naming the first
offending entry. Executable bits and symlinks inside the root are kept. A larger upload answers `413 INVALID_CONTENT`.

Errors:

| Status | Cause                                                                                                                       |
|--------|-----------------------------------------------------------------------------------------------------------------------------|
| 400    | `INVALID_REQUEST`: the `sshUrl` is missing, or no configured VCS service supports the SSH URL; for the create also a bad `marker` or a missing `branch`. |
| 400    | `INVALID_CONTENT`: the zip of the create breaks a content rule.                                                             |
| 401    | No token, or the token is rejected.                                                                                         |
| 403    | The token lacks `CREATE_REPOSITORIES`, or `TARGET_NOT_ALLOWED`: the VCS has no provisioning credential or the group is not allowed. |
| 413    | `INVALID_CONTENT`: the upload is larger than `max-content-size`.                                                            |
| 501    | The create passed every check; it is not implemented yet and writes nothing.                                                |
| 5xx    | The VCS could not be read. This is never answered as "missing".                                                             |

Paths nothing is mapped to answer `404` and wrong methods `405`, with or without provisioning enabled.
