## ADDED Requirements

### Requirement: Provisioning is off by default

The service SHALL NOT expose the group check, repository state or create endpoints unless
`vcs-facade.provisioning.enabled` is `true`. Existing endpoints SHALL behave the same whether
provisioning is enabled or not, and SHALL NOT require a token.

#### Scenario: Disabled by default

- **WHEN** the service runs without `vcs-facade.provisioning.enabled`
- **THEN** `GET /rest/api/2/groups` and `GET /rest/api/2/repository/state` answer `404`,
  `POST /rest/api/2/repository` answers `405` (the path serves the existing repository read),
  and no repository is created

#### Scenario: Existing responses unchanged when disabled

- **WHEN** provisioning is disabled and a caller sends an existing request
- **THEN** the status, body and headers are the same as before this change

#### Scenario: Existing endpoints stay open when enabled

- **WHEN** provisioning is enabled and a caller sends `GET /rest/api/2/repository?sshUrl=…`
  without a token
- **THEN** the answer is the same as with provisioning disabled

### Requirement: Provisioning endpoints require the CREATE_REPOSITORIES permission

The group check, repository state and create endpoints SHALL require a bearer token whose roles
map to the `CREATE_REPOSITORIES` permission through `octopus-security.roles`.

#### Scenario: No token

- **WHEN** a caller sends any provisioning request without a token
- **THEN** the answer is `401` and nothing is read from or written to the VCS

#### Scenario: Token without the permission

- **WHEN** a caller sends any provisioning request with a valid token lacking
  `CREATE_REPOSITORIES`
- **THEN** the answer is `403` and nothing is read from or written to the VCS

### Requirement: Provisioning acts only on allowed targets

For each VCS, the service SHALL accept a provisioning request only when the VCS has a
provisioning credential and the SSH URL's project or organisation is in that VCS's
`provisioning.allowed-groups`. Otherwise it SHALL answer `403 TARGET_NOT_ALLOWED` without
contacting the VCS. Writes SHALL use the provisioning credential only.

#### Scenario: Project not in the allow-list

- **WHEN** an authorised caller asks to create `ssh://…/other/new-repo.git` and `other` is not
  allowed
- **THEN** the answer is `403 TARGET_NOT_ALLOWED` and no repository is created

### Requirement: Group check

`GET /rest/api/2/groups?sshUrl=` SHALL answer `200 {"exists": true}` when the repository's
project or organisation exists, and `200 {"exists": false}` when the VCS reports it missing. Any
other failure to read it SHALL be an error status, never `exists: false`.

#### Scenario: Missing organisation

- **WHEN** the organisation of the SSH URL does not exist
- **THEN** the answer is `200 {"exists": false}`

#### Scenario: VCS unreachable

- **WHEN** the VCS fails to answer
- **THEN** the answer is a `5xx` error, not `exists: false`

### Requirement: Repository state

`GET /rest/api/2/repository/state?sshUrl=` SHALL answer `200` with `status` `MISSING` when the
VCS reports the repository or its group missing. Otherwise it SHALL answer `200` with `status`
`PRESENT`, the repository description as `marker`, `defaultBranch`, `hasCommits`, and the
`Provisioning-Marker` trailer of the default branch's root commit as `rootCommitMarker` (null
when there is none). Any other failure to read SHALL be an error status, never `MISSING`.

#### Scenario: Repository made by an earlier create

- **WHEN** the repository was created with marker `m1` and content
- **THEN** the state is `PRESENT`, `marker` `m1`, `hasCommits` true and `rootCommitMarker` `m1`

#### Scenario: Read fails

- **WHEN** the VCS answers with a server error while reading the repository
- **THEN** the answer is a `5xx` error, not `MISSING`

### Requirement: Create writes the marker in the create call

`POST /rest/api/2/repository?sshUrl=` (multipart: `marker`, `branch`, optional
`commitMessage`, optional `content`: a zip sent as a file part, with a filename, as any HTTP
client's multipart file upload sends it) SHALL create a missing repository with the marker as its
description and `branch` as its default branch, both in the same VCS call that creates it, and,
when content is sent, push it to `branch` as exactly one commit whose message ends with the
trailer `Provisioning-Marker: <marker>`. It SHALL answer `201`.

The marker SHALL be non-blank, a single line without leading or trailing whitespace and at most
255 characters, and `branch` SHALL be
given. Otherwise the answer is `400 INVALID_REQUEST` and nothing is created.

#### Scenario: Create with content

- **WHEN** the repository is missing and the request carries marker `m1`, branch `main` and a zip
- **THEN** the answer is `201`, the description is `m1`, `main` is the default branch and holds
  one commit with the zip's files and the trailer `Provisioning-Marker: m1`

#### Scenario: Create without content

- **WHEN** the repository is missing and the request carries marker `m1`, branch `main` and no
  zip
- **THEN** the answer is `201` and the repository exists, empty, with description `m1` and
  default branch `main`

#### Scenario: Branch missing

- **WHEN** the request carries a marker but no `branch`
- **THEN** the answer is `400 INVALID_REQUEST` and no repository is created

#### Scenario: Longest marker

- **WHEN** the repository is missing and the marker is 255 characters long
- **THEN** the answer is `201` and the description equals the marker exactly

#### Scenario: Executable bits are kept

- **WHEN** the zip holds `gradlew` with the executable bit set
- **THEN** `gradlew` is committed with mode `100755`

### Requirement: Create is safe to repeat

When the repository exists, create SHALL decide from what it finds and SHALL NOT write unless the
repository's description equals the request's marker:

- description equals the marker, no commits, content sent: push, answer `201`;
- description equals the marker and nothing is missing: answer `200`;
- description equals the marker, commits present, root commit trailer differs, content sent:
  answer `409 REPOSITORY_CONFLICT`;
- any other description: answer `409 REPOSITORY_EXISTS`.

Every `409` body SHALL include the state found. Create SHALL never force-push, overwrite a
branch, or delete a repository.

#### Scenario: Repeat after success

- **WHEN** a create with marker `m1` and content succeeded and the same request is sent again
- **THEN** the answer is `200` and the repository is unchanged

#### Scenario: Repeat after a lost response

- **WHEN** the repository was created with description `m1` but holds no commits, and a create
  with marker `m1` and content is sent
- **THEN** the content is pushed and the answer is `201`

#### Scenario: Someone else's repository

- **WHEN** the repository exists with another description, or none
- **THEN** the answer is `409 REPOSITORY_EXISTS`, nothing is written, and the body shows the
  description found

#### Scenario: Our repository with someone else's content

- **WHEN** the description is `m1` but the root commit has no `Provisioning-Marker: m1` trailer
- **THEN** the answer is `409 REPOSITORY_CONFLICT` and nothing is written

#### Scenario: Concurrent creates

- **WHEN** two creates with marker `m1` and the same content run at once
- **THEN** one answers `201`, the other `200` or `201`, and the repository holds exactly one
  commit

### Requirement: A lost push is reported, not hidden

When create made the repository but could not push the content, it SHALL answer
`502 REPOSITORY_EMPTY` and leave the repository, marked and empty, in place.

#### Scenario: Push fails after create

- **WHEN** the repository is created and the push fails
- **THEN** the answer is `502 REPOSITORY_EMPTY`, the repository exists with the marker, and a
  repeat of the request pushes the content

### Requirement: Content is checked before anything is written

Create SHALL check the zip before creating the repository and SHALL answer
`400 INVALID_CONTENT`, creating nothing, when the zip does not open, contains an entry with a
`.git` path segment, contains an absolute path or a path or symlink that escapes the repository
root. An upload larger than `vcs-facade.provisioning.max-content-size` SHALL answer `413`.

#### Scenario: Zip with a .git directory

- **WHEN** the zip contains `.git/config`
- **THEN** the answer is `400 INVALID_CONTENT` naming that entry, and the repository is not
  created

#### Scenario: Path escaping the root

- **WHEN** the zip contains `../outside.txt`
- **THEN** the answer is `400 INVALID_CONTENT` and the repository is not created
