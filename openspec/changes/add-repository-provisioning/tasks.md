## Delivery

The change is delivered in three pull requests, each with its own unit and functional tests:

1. **Security and reads**: settings, security, allow-list, group check, repository state.
2. **Request and zip checks**: the create endpoint validates its request and content, then
   answers `501` until the next pull request.
3. **Create**: the decision table, the commit and the push.

The change is archived after the third pull request is merged.

> Test-first: each behaviour task starts with a failing test, then the production code.
> Unit tests run under `./gradlew build`; `ft` runs per provider with `-Ptest.profile=bitbucket`
> and `-Ptest.profile=gitea`.

# Pull request 1: security and reads

## 1. Dependencies and settings

- [x] 1.1 Move `external-systems-client.version` to 2.1.0; `./gradlew build` still passes
      unchanged.
- [ ] 1.2 Both `ft` profiles (`bitbucket` and `gitea`) still pass unchanged after the move.
- [x] 1.3 `ProvisioningProperties` (`vcs-facade.provisioning.enabled`) and per-service
      `provisioning` (`token` or `username`/`password`, `allowed-groups`). Test: binding, default
      `enabled=false`, a service without `provisioning` still binds.

## 2. Contract in `common` and `client`

- [x] 2.1 DTOs `GroupExistence` and `RepositoryState` (`status`, `marker`, `defaultBranch`,
      `hasCommits`, `rootCommitMarker`); extend `UnknownJsonFieldToleranceTest` for them.
- [x] 2.2 Error codes `INVALID_REQUEST` and `TARGET_NOT_ALLOWED` with their exceptions. Test: the
      error decoder maps each to its exception; an unknown code still decodes as today.
- [x] 2.3 `VcsFacadeClient`: `getGroupExistence` and `getRepositoryState`. Test against a mock
      server: paths, query, decoding.

## 3. Off by default and security

- [x] 3.0 Add `octopus-security-common` 2.0.20 with `spring-boot-starter-security`,
      `spring-security-oauth2-resource-server` and `spring-security-oauth2-jose`, in the same
      commit as the security chains, so Spring Boot's default security never applies.
- [x] 3.1 Failing tests with default settings: the two read endpoints answer `404`; existing
      endpoints answer without a token and without basic auth; the context starts with no
      `auth-server.*` settings; an unmapped path answers `404` and an unsupported method `405`.
- [x] 3.2 Failing tests with provisioning enabled (`AuthServerClient` mocked): no token → `401`
      with a JSON body; token whose roles lack `CREATE_REPOSITORIES` → `403` with a JSON body;
      existing endpoints still answer without a token.
- [x] 3.3 `WebSecurityConfig` extending `CloudCommonWebSecurityConfig` with an overridden chain
      (provisioning paths authenticated, the rest `permitAll`), `JwtDecoder` with issuer
      validation, JSON entry point and access-denied handler, `PermissionEvaluator` over
      `BasePermissionEvaluator`; all conditional on `vcs-facade.provisioning.enabled=true`.
- [x] 3.4 A `permitAll` chain for the disabled case, so Spring Boot's default security never
      applies.

## 4. Allow-list and credentials

- [x] 4.1 Failing tests: a group outside `allowed-groups` → `403 TARGET_NOT_ALLOWED` on both read
      endpoints with no VCS call; a VCS without a provisioning credential → the same.
- [x] 4.2 Build a provisioning Bitbucket/Gitea client per service from the provisioning
      credential; the existing clients are untouched.

## 5. Group check

- [x] 5.1 Failing tests per provider: existing group → `exists: true`; provider "not found" →
      `exists: false`; provider error → `5xx`.
- [x] 5.2 `GET /rest/api/2/groups` via Bitbucket `getProject` and Gitea `getOrganization`.

## 6. Repository state

- [x] 6.1 Failing tests per provider: missing repository and missing group → `MISSING`; present
      without commits; present with a root trailer; trailer parsing (absent, other trailers,
      trailing whitespace); provider error → `5xx`, never `MISSING`.
- [x] 6.2 `GET /rest/api/2/repository/state`: description, default branch, first-parent walk to
      the root commit.

## 7. Functional tests (both providers)

- [ ] 7.1 ft environment (Docker and OKD): a WireMock stub auth server serving OIDC discovery, a
      JWKS for a test RSA key, and userinfo per token; the server runs with provisioning enabled,
      provisioning credentials, an allow-list and `auth-server.*` pointing at the stub; the
      suite signs one token with the permission and one without.
- [ ] 7.2 Group check and state against real providers, including missing ones.
- [ ] 7.3 Allow-list, `401` and `403` checks; existing endpoints still open.

## 8. Docs

- [ ] 8.1 README: provisioning settings, permission and allow-list.
- [ ] 8.2 OpenAPI annotations on the two read endpoints, with every status and error code.

# Pull request 2: request and zip checks

## 9. Settings, contract and endpoint

- [ ] 9.1 Add Apache Commons Compress; `vcs-facade.provisioning.max-content-size` (default
      50 MB); Spring multipart limits follow it.
- [ ] 9.2 Error code `INVALID_CONTENT` with its exception; `VcsFacadeClient.createRepository`
      (multipart, the zip as a file part). Test against a mock server: path, query, parts.
- [ ] 9.3 `POST /rest/api/2/repository` behind the same security and allow-list; after the
      checks below it answers `501` until pull request 3.

## 10. Request checks

- [ ] 10.1 Failing tests: blank, multi-line, whitespace-padded or over-255 marker; missing
      `branch` → `400 INVALID_REQUEST`, no VCS call.
- [ ] 10.2 `CreateRequestChecks`.

## 11. Zip checks

- [ ] 11.1 Failing tests: not a zip; `.git/` entry at the root and nested (`a/.git/HEAD`);
      absolute path; `../` escape; symlink pointing outside; duplicate paths; over the size
      limit → `413`; a valid zip unpacks with its executable bits and internal symlinks.
- [ ] 11.2 `ContentValidator`, reading the entries with Commons Compress straight from the
      upload (no temporary directory); a zip-bomb guard of 10 times `max-content-size`
      uncompressed and 100,000 entries.

## 12. Functional tests (both providers)

- [ ] 12.1 Invalid request and invalid zip → `400`/`413` with nothing created; a valid request
      reaches `501`.

## 13. Docs

- [ ] 13.1 README: `max-content-size` and the content rules.
- [ ] 13.2 OpenAPI annotations on the create endpoint, with the statuses so far.

# Pull request 3: create

## 14. Settings and contract

- [ ] 14.1 Add JGit; `vcs-facade.provisioning.commit-author.name`/`email`, required when
      provisioning is enabled.
- [ ] 14.2 DTO `CreateRepositoryResult`; error codes `REPOSITORY_EXISTS`, `REPOSITORY_CONFLICT`
      and `REPOSITORY_EMPTY` with their exceptions; `ErrorResponse.repositoryState`.

## 15. Create

- [ ] 15.1 Failing tests for the decision table (design D4) with a fake provider: each row,
      including `409` bodies carrying the state found.
- [ ] 15.2 Failing tests for races: provider create rejects because the repository exists →
      re-read and decide; push rejected → re-read → `200` with our trailer, `409` otherwise.
- [ ] 15.3 Failing test: push fails after create → `502 REPOSITORY_EMPTY`, repository left
      marked.
- [ ] 15.4 `POST /rest/api/2/repository` replaces `501`, synchronous: read, check, create with
      description and default branch (`defaultBranch` on Bitbucket, `default_branch` on Gitea),
      JGit commit with trailer and configured author, create-only push over HTTPS. No force, no
      delete anywhere.

## 16. Functional tests (both providers)

- [ ] 16.1 Create with content: `201`, description stored by the create call, one commit with
      the trailer, default branch as requested (use a non-default name such as `develop`, so a
      fallback to `master` or `main` fails the test), executable bit kept.
- [ ] 16.2 Create without content: `201`, empty and marked, default branch as requested; a
      255-character marker is stored verbatim.
- [ ] 16.3 Repeat after success → `200`, unchanged.
- [ ] 16.4 Lost response: create the marked empty repository first, then create → `201` with
      the content.
- [ ] 16.5 Foreign repository → `409 REPOSITORY_EXISTS`; marked repository with foreign content
      → `409 REPOSITORY_CONFLICT`; nothing written in either.

## 17. Docs

- [ ] 17.1 README: the create decision table.
- [ ] 17.2 OpenAPI annotations on the create endpoint, with every status and error code.
