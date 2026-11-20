> Test-first: each behaviour task starts with a failing test, then the production code.
> Unit tests run under `./gradlew build`; `ft` runs per provider with `-Ptest.profile=bitbucket`
> and `-Ptest.profile=gitea`.

## 1. Dependencies and settings

- [ ] 1.1 Move `external-systems-client.version` to 2.1.0; `./gradlew build` and both `ft`
      profiles still pass unchanged.
- [ ] 1.2 Add JGit, Apache Commons Compress and the security dependency (`octopus-security-common`
      or Spring's OAuth2 resource server, per design D7 once the open question is answered).
- [ ] 1.3 `ProvisioningProperties` (`vcs-facade.provisioning.enabled`, `max-content-size`,
      `commit-author.name`/`email`) and per-service `provisioning` (`token` or
      `username`/`password`, `allowed-groups`). Test: binding, defaults (`enabled=false`, 50 MB),
      a service without `provisioning` still binds.

## 2. Contract in `common` and `client`

- [ ] 2.1 DTOs `GroupExistence`, `RepositoryState` (`status`, `marker`, `defaultBranch`,
      `hasCommits`, `rootCommitMarker`), `CreateRepositoryResult`; extend
      `UnknownJsonFieldToleranceTest` for them.
- [ ] 2.2 Error codes `REPOSITORY_EXISTS`, `REPOSITORY_CONFLICT`, `REPOSITORY_EMPTY`,
      `INVALID_CONTENT`, `INVALID_REQUEST`, `TARGET_NOT_ALLOWED` with their exceptions. Test: the
      error decoder maps each to its exception; an unknown code still decodes as today.
- [ ] 2.3 `VcsFacadeClient`: `getGroupExistence`, `getRepositoryState`, `createRepository`
      (multipart). Test against a mock server: paths, query, parts, decoding.

## 3. Off by default and security

- [ ] 3.1 Failing test: with default settings the three endpoints answer `404`.
- [ ] 3.2 Failing tests with provisioning enabled: no token → `401`; token without
      `CREATE_REPOSITORIES` → `403`; existing endpoints answer without a token as before.
- [ ] 3.3 Conditional controllers and a security chain limited to the provisioning paths;
      `octopus-security.roles` maps roles to `CREATE_REPOSITORIES`.

## 4. Allow-list and credentials

- [ ] 4.1 Failing tests: a group outside `allowed-groups` → `403 TARGET_NOT_ALLOWED` on all three
      endpoints with no VCS call; a VCS without a provisioning credential → the same.
- [ ] 4.2 Build a provisioning Bitbucket/Gitea client per service from the provisioning
      credential; the existing clients are untouched.

## 5. Group check

- [ ] 5.1 Failing tests per provider: existing group → `exists: true`; provider "not found" →
      `exists: false`; provider error → `5xx`.
- [ ] 5.2 `GET /rest/api/2/groups` via Bitbucket `getProject` and Gitea `getOrganization`.

## 6. Repository state

- [ ] 6.1 Failing tests per provider: missing repository and missing group → `MISSING`; present
      without commits; present with a root trailer; trailer parsing (absent, other trailers,
      trailing whitespace); provider error → `5xx`, never `MISSING`.
- [ ] 6.2 `GET /rest/api/2/repository/state`: description, default branch, first-parent walk to
      the root commit.

## 7. Zip checks

- [ ] 7.1 Failing tests: not a zip; `.git/` entry at the root and nested (`a/.git/HEAD`);
      absolute path; `../` escape; symlink pointing outside; over the size limit → `413`; a valid
      zip unpacks with its executable bits.
- [ ] 7.2 `ContentValidator` and unpacking with Commons Compress into a temporary directory that
      is removed in every outcome.

## 8. Create

- [ ] 8.1 Failing tests for request checks: blank, multi-line or over-255 marker; content without
      `branch` → `400 INVALID_REQUEST`, nothing created.
- [ ] 8.2 Failing tests for the decision table (design D4) with a fake provider: each row,
      including `409` bodies carrying the state found.
- [ ] 8.3 Failing tests for races: provider create rejects because the repository exists →
      re-read and decide; push rejected → re-read → `200` with our trailer, `409` otherwise.
- [ ] 8.4 Failing test: push fails after create → `502 REPOSITORY_EMPTY`, repository left marked.
- [ ] 8.5 `POST /rest/api/2/repository`, synchronous: read, check, create with description
      (Gitea also `default_branch`), JGit commit with trailer and configured author, create-only
      push over HTTPS. No force, no delete anywhere.
- [ ] 8.6 Spring multipart limits follow `max-content-size`.

## 9. Functional tests (both providers)

- [ ] 9.1 ft server config: provisioning enabled, provisioning credentials, allow-list, a test
      RSA public key; the suite signs tokens with and without the permission.
- [ ] 9.2 Create with content: `201`, description stored by the create call, one commit with the
      trailer, default branch as requested, executable bit kept.
- [ ] 9.3 Create without content: `201`, empty and marked.
- [ ] 9.4 Repeat after success → `200`, unchanged.
- [ ] 9.5 Lost response: create the marked empty repository first, then create → `201` with the
      content.
- [ ] 9.6 Foreign repository → `409 REPOSITORY_EXISTS`; marked repository with foreign content →
      `409 REPOSITORY_CONFLICT`; nothing written in either.
- [ ] 9.7 Group check and state against real providers, including missing ones.
- [ ] 9.8 Allow-list, `401`, `403` and disabled-by-default checks; existing endpoints still open.
- [ ] 9.9 Answer the design's open questions from these runs (Bitbucket default branch after the
      first push, description limit) and update design D2/D5 to match.

## 10. Docs

- [ ] 10.1 README: provisioning settings, permission, allow-list, and the create decision table.
- [ ] 10.2 OpenAPI annotations on the three endpoints, with every status and error code.
