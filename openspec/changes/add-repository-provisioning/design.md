## Context

ADR-025, decision 1 (components registry, `docs/registry/adr/025-component-provisioning.md`)
gives vcs-facade three VCS actions for component provisioning and leaves their contract to this
repository. The caller is `octopus-component-provisioning-service`; it builds the marker and the
zip, and reads state back from vcs-facade instead of keeping a database.

Today vcs-facade:

- has no authentication on any endpoint;
- already serves `GET /rest/api/2/repository?sshUrl=` (returns the repository, `404` when it is
  missing);
- runs requests through a `RequestProcessor` that answers `202` with a retry hint when a request
  runs longer than `fastWorkTimoutSecs`: an in-memory job map per node by default, or deferred
  results shared through Valkey when `deferred-result.enabled=true`;
- reads Bitbucket and Gitea through octopus-external-systems-client, with one credential per VCS.

## Goals / Non-Goals

**Goals:** a create that is safe to repeat after any failure, including a lost response; a state
read that never turns a failure into "missing"; no write without the request's marker; nothing
changes while the feature is off.

**Non-goals:** templates and the marker's content; deletes; creating projects or organisations;
any write after the first commit; providers other than Bitbucket and Gitea.

## Decisions

### D1. Endpoints

| Call | Path | Answer |
|---|---|---|
| Group check | `GET /rest/api/2/groups?sshUrl=` | `200 {"exists": true\|false}` |
| Repository state | `GET /rest/api/2/repository/state?sshUrl=` | `200` with the state (D3) |
| Create | `POST /rest/api/2/repository?sshUrl=` (multipart) | D4 |

- **The state read gets its own path.** `GET /rest/api/2/repository?sshUrl=` already exists,
  is unauthenticated and answers `404` for a missing repository. Changing it would break its
  callers, and "missing" must be a normal answer here, not an error. The ADR's wording ("read a
  repository's state") does not fix a path.
- **Missing is `200`, not `404`**, for both reads. A `404` from vcs-facade can also mean an
  unknown path or a disabled feature; the caller must never read those as "missing".
- `sshUrl` is a query parameter, as in every existing endpoint. The group check takes a
  repository SSH URL and checks its project or organisation.

### D2. The marker

- An opaque string from the caller: non-blank, a single line, no leading or trailing whitespace,
  at most 255 characters (taken as the smaller description limit of the two providers; the ft
  suite stores a 255-character marker on both). Surrounding whitespace is refused rather than
  trimmed, so the description and the trailer can both be compared exactly.
- Written **as the repository description in the create call** (needs external-systems-client
  2.1.0), and as a `Provisioning-Marker: <marker>` trailer on the root commit.
- vcs-facade compares markers for exact equality and never parses them.

### D3. Repository state

```json
{ "status": "MISSING" | "PRESENT",
  "marker": "…" | null,            // the description, verbatim
  "defaultBranch": "main" | null,
  "hasCommits": true | false,
  "rootCommitMarker": "…" | null } // the Provisioning-Marker trailer of the root commit
```

- The root commit is the end of the default branch's first-parent history. Repositories at
  provisioning time are young, so walking the history through the provider API is acceptable in
  phase 1.
- **Bitbucket's `defaultBranch` is partial.** external-systems-client 2.1.0 has no default-branch
  call, and `BitbucketBranch` drops Bitbucket's `isDefault` flag. vcs-facade walks the default
  branch's history and names a branch only when exactly one branch points at its head commit
  (the default branch always does); with several, or with no commits, `defaultBranch` is null.
  A freshly provisioned repository has one branch, so the answer is right there, and create does
  not use the field. Follow-up: expose the default branch in the Bitbucket client, then read it
  directly. Gitea reports `default_branch` itself.
- **Bitbucket's empty repository.** An empty repository answers `404` for its commits, and so does one
  that vanished mid-read. So that `404` (or an empty first page) counts as "no commits" only when the
  branch list is empty too; branches without readable commits, or a "not found" for the branches,
  is a failed read and answers `5xx`.
- Any provider failure answers `5xx` with an error body. Only a provider "not found" for the
  repository becomes `MISSING`; a "not found" for the project or organisation does too.

### D4. Create: decide from what is there

Read the state first, then:

| What is there | Request has content | Result |
|---|---|---|
| Missing | – | create with description = marker; push if content → `201` |
| Our marker, no commits | yes | push → `201` |
| Our marker, no commits | no | `200` |
| Our marker, commits, root trailer = marker | – | `200` |
| Our marker, commits, root trailer ≠ marker | yes | `409 REPOSITORY_CONFLICT` |
| Our marker, commits | no | `200` |
| Any other description | – | `409 REPOSITORY_EXISTS` |

- "Our marker" means the description equals the request's marker exactly.
- Every `409` body carries the state that was found, so the caller can show it.
- **Created, then the push fails**: `502 REPOSITORY_EMPTY`. The repository stays, empty and
  marked; a repeat pushes.
- **Races** are settled by reading again, never by rolling back:
  - the provider's create call fails → read again once. If the repository is there now, decide
    from the table; if it is still missing, the original failure is the answer. Neither client
    reports the HTTP status of a failed create, so "already exists" cannot be told from other
    failures by the error itself;
  - the push is a create-only ref update (expected old value: none) and never forced; when it is
    rejected, read again: our trailer on the root → `200`, anything else →
    `409 REPOSITORY_CONFLICT`.
- **The state after a push is the one the push made, not read back.** Gitea acknowledges a push
  but goes on reporting the repository as empty for up to about a second. So a `201` after a
  push answers with the state the push produced (marker, branch, a root commit carrying the
  marker), and a rejected push reads the state up to 6 times, 500 ms apart, while the repository
  still looks empty, before it settles on `409 REPOSITORY_CONFLICT`. A repeat sent within that
  second may see the repository as ours and empty, push, get rejected, and then end at `200`
  through the same path.
- **No write without the marker, no deletes**, in any path.
- **Synchronous, not through the `RequestProcessor`.** The other endpoints answer `202` with a
  retry hint when a request runs long, and the caller polls with the same request id. For
  create that means sending the multipart body, zip included, again on every poll. Create is
  safe to repeat by design, so a client timeout followed by a repeat is the recovery path. The
  zip size limit (D6) bounds the run time.

### D5. The push

- **No working tree.** The commit is built in a temporary bare repository on disk: each zip
  entry is streamed into a blob with JGit's `ObjectInserter`, the tree is built with `DirCache`
  using the entry's mode (`100644`, `100755` when the zip marks it executable, `120000` for a
  symlink), and one
  parentless commit is made. Nothing is unpacked to the filesystem, so modes come out exact on
  any OS, and blobs stay off the heap. An in-memory repository would hold a whole inflated zip,
  up to the zip-bomb limit (D6), in memory per running create. The directory is deleted in every
  outcome.
- Pushed as `refs/heads/<branch>` over HTTPS to the clone URL the provider reports, with the
  provisioning credential:
  - username and password: basic auth on both providers;
  - token on Gitea: basic auth, the token as the password;
  - token on Bitbucket: `Authorization: Bearer <token>`.
- Commit author and committer come from settings (`vcs-facade.provisioning.commit-author.*`).
  The message is the request's `commitMessage` (default `Initial content`) followed by a blank
  line and the `Provisioning-Marker` trailer.
- **`branch` is required on every create and sets the default branch in the create call**:
  `defaultBranch` on Bitbucket, `default_branch` on Gitea. Bitbucket does not take the default
  branch from the first push: an empty repository's default is the name given at create, else
  the instance-wide default, and pushing another branch leaves the default pointing at a branch
  that does not exist. `BitbucketCreateRepository` already has `defaultBranch`, but defaults it
  to `master` when the caller sets nothing, so leaving `branch` optional would make Bitbucket
  and Gitea disagree. Requiring it costs the caller nothing: the target branch always comes
  from CRS.

### D6. Zip checks

Before anything is written, and before the repository is created:

- the part is a zip that opens;
- no entry has a path segment `.git`;
- no entry is absolute or escapes the root after normalising `..`; no symlink points outside the
  root;
- no two entries have the same path;
- the upload is at most `vcs-facade.provisioning.max-content-size` (default 50 MB); Spring's
  multipart limits are set to match, and a larger upload answers `413`;
- **zip-bomb guard:** the entries inflate to at most 10 × `max-content-size` in total, checked
  against the bytes actually read, not only the sizes the zip declares, and there are at most
  100,000 entries.

A failed check answers `400 INVALID_CONTENT` naming the first offending entry; nothing is
created. Unix modes are read with a zip library that exposes them (Apache Commons Compress), so
executable bits survive. Symlinks inside the root are kept as symlinks (`120000`).

### D7. Security, credentials and allow-list

- **`octopus-security-common`** (octopus-cloud-commons 2.0.20, built on the same Spring Boot
  3.2.2), wired as the components registry does:
  - `WebSecurityConfig` extends `CloudCommonWebSecurityConfig` and overrides its `open`
    `securityFilterChain`. The library's own chain makes `anyRequest().authenticated()`, which
    would lock every existing endpoint; the override matches only the provisioning requests
    (`GET` and `HEAD` on the two read paths, `POST` on the create path) and makes them
    `authenticated()`. Every other request matches no chain and passes no security filter. CSRF
    protection stays on and never checks the reads; the create, authenticated by a bearer token
    alone, is exempted with `ignoringRequestMatchers`, as Spring advises for token-only APIs.
  - A `JwtDecoder` bean built from `auth-server.url` and `auth-server.realm` with issuer
    validation, as in CRS `WebSecurityConfig.jwtDecoder()`.
  - JSON `401`/`403` bodies through an entry point and an access-denied handler, so callers get
    the usual `ErrorResponse` rather than an empty body.
  - A `PermissionEvaluator` extending `BasePermissionEvaluator`; the controllers carry
    `@PreAuthorize("@permissionEvaluator.hasPermission('CREATE_REPOSITORIES')")`.
- **How permissions are resolved.** The library does not read roles from the token: on every
  request `UserInfoGrantedAuthoritiesConverter` calls the auth server's userinfo endpoint and
  turns its roles into `ROLE_*` authorities, and `SecurityService` maps those through
  `octopus-security.roles` (keys like `ROLE_<name>`). `AuthServerClient` also fetches the OIDC
  discovery document when the context starts, so with provisioning enabled the auth server must
  be reachable at startup; the service fails fast otherwise, as CRS does.
- **Disabled means no auth wiring at all.** `WebSecurityConfig` and `AuthServerClient` load only
  when `vcs-facade.provisioning.enabled=true`. Because Spring Security is on the classpath, the
  disabled case needs its own `permitAll` chain (as CRS `AnonymousSecurityConfig` does);
  without it Spring Boot's default chain would put basic auth on every endpoint. Disabled, no
  `auth-server.*` settings are needed.
- No token → `401`; a token without the permission → `403`.
- Per VCS service, under `vcs-facade.vcs.services[].provisioning`:
  - a credential (`token`, or `username` and `password`) used only for creating and pushing;
  - `allowed-groups`: the Bitbucket project keys or Gitea organisations provisioning may touch.
- A target outside the allow-list → `403 TARGET_NOT_ALLOWED`, on all three endpoints. A VCS with
  no provisioning credential → `403 TARGET_NOT_ALLOWED` too: provisioning is not configured there.
- `vcs-facade.provisioning.enabled=false` by default; the controllers and the chain are created
  only when it is `true`.

### D8. Errors

New `VcsFacadeErrorCode` values: `REPOSITORY_EXISTS`, `REPOSITORY_CONFLICT`, `REPOSITORY_EMPTY`,
`INVALID_CONTENT`, `INVALID_REQUEST`, `TARGET_NOT_ALLOWED`. A client built before this change
decodes an unknown code as it does today; `UnknownJsonFieldToleranceTest` covers the DTOs.

| Case | Status | Code |
|---|---|---|
| Bad marker, missing `branch` | 400 | `INVALID_REQUEST` |
| Zip check failed | 400 | `INVALID_CONTENT` |
| Upload over the limit | 413 | `INVALID_CONTENT` |
| Outside the allow-list / VCS not configured | 403 | `TARGET_NOT_ALLOWED` |
| Project or organisation missing on create | 404 | `NOT_FOUND` |
| Not ours | 409 | `REPOSITORY_EXISTS` |
| Ours, content not ours | 409 | `REPOSITORY_CONFLICT` |
| Created, push lost | 502 | `REPOSITORY_EMPTY` |
| Provider read failed | 5xx | `OTHER` |

### D9. Functional tests

- **A stub auth server instead of Keycloak.** A WireMock container in the ft environment (Docker
  and OKD) serves the OIDC discovery document, a JWKS for a test-only RSA key, and a userinfo
  endpoint that answers per bearer token: one token whose roles map to `CREATE_REPOSITORIES`,
  one whose roles do not. The ft suite signs both tokens with the test key. A self-signed token
  alone would not be enough, because permissions come from userinfo (D7). CRS uses WireMock the
  same way in `UserInfoConverterIntegrationTest` and `FatJarStartupIntegrationTest`.
- Unit tests replace `AuthServerClient` with a mock, as CRS does.
- Both providers: create with and without content, repeat after success, repeat after a lost
  push (simulated by creating the marked empty repository first), conflict on a foreign
  repository, conflict on foreign content, the description stored by the create call, executable
  bits kept, zip rejections, allow-list and permission rejections, and disabled-by-default.

## Risks / Trade-offs

- **Root commit walk** costs one page per 25–100 commits. Fine for new repositories; a long-lived
  repository read many times would be slow. Revisit if the provisioning service polls old
  repositories.
- **Abandoned creates** leave an empty marked repository to delete by hand (ADR-025, accepted).
- **First authenticated endpoints** in vcs-facade: a misconfigured chain could expose or block
  the API. The ft suite asserts that existing endpoints still answer without a token.
- **Temporary disk**: one unpacked zip per running create, bounded by the size limit.
- **Auth server on the request path**: every provisioning request makes one userinfo call, and a
  service with provisioning enabled does not start while the auth server is down. Both match
  CRS and only touch provisioning traffic.
