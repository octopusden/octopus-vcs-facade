## Context

ADR-025, decision 1 (components registry, `docs/registry/adr/025-component-provisioning.md`)
gives vcs-facade three VCS actions for component provisioning and leaves their contract to this
repository. The caller is `octopus-component-provisioning-service`; it builds the marker and the
zip, and reads state back from vcs-facade instead of keeping a database.

Today vcs-facade:

- has no authentication on any endpoint;
- already serves `GET /rest/api/2/repository?sshUrl=` (returns the repository, `404` when it is
  missing);
- runs requests through `processRequest`, a per-node in-memory job map that answers `202` with a
  retry hint when a request runs longer than `fastWorkTimoutSecs`;
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

- An opaque string from the caller: non-blank, a single line, at most 255 characters (the
  smallest description limit of the supported providers; the ft suite confirms it on both).
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
  - the provider rejects the create because the repository now exists → read again and decide
    from the table once more;
  - the push is a create-only ref update (expected old value: none) and never forced; when it is
    rejected, read again: our trailer on the root → `200`, anything else →
    `409 REPOSITORY_CONFLICT`.
- **No write without the marker, no deletes**, in any path.
- **Synchronous, not through `processRequest`.** The job map is per node and in memory, so a
  retry routed to another node starts a second create; with a multipart body the client would
  also re-upload the zip on every poll. Create is safe to repeat by design, so a client timeout
  followed by a repeat is the recovery path. The zip size limit (D6) bounds the run time.

### D5. The push

- Unpack the checked zip into a temporary directory, `git init` with JGit, add everything keeping
  executable bits, commit once, push `HEAD:refs/heads/<branch>` over HTTPS with the provisioning
  credential. Delete the temporary directory afterwards in every outcome.
- Commit author and committer come from settings (`vcs-facade.provisioning.commit-author.*`).
  The message is the request's `commitMessage` (default `Initial content`) followed by a blank
  line and the `Provisioning-Marker` trailer.
- `branch` is required when content is sent. Gitea gets it as `default_branch` in the create
  call; on Bitbucket the ft suite checks that the first pushed branch becomes the default, and
  if not, the default branch is set after the push (the marker is already in place, so a crash
  there leaves a recognisable repository).

### D6. Zip checks

Before anything is written, and before the repository is created:

- the part is a zip that opens;
- no entry has a path segment `.git`;
- no entry is absolute or escapes the root after normalising `..`; no symlink points outside the
  root;
- the upload is at most `vcs-facade.provisioning.max-content-size` (default 50 MB); Spring's
  multipart limits are set to match.

A failed check answers `400 INVALID_CONTENT` naming the first offending entry; nothing is
created. Unix modes are read with a zip library that exposes them (Apache Commons Compress), so
executable bits survive.

### D7. Security, credentials and allow-list

- **Authentication only on the new endpoints.** A security filter chain matches the three paths;
  every other path stays `permitAll`. Disabled, the chain is not created and no issuer settings
  are needed.
- Permission `CREATE_REPOSITORIES`, resolved from token roles through `octopus-security.roles`,
  as in the components registry (`octopus-security-common` from octopus-cloud-commons). If that
  library cannot be limited to these paths, use Spring's OAuth2 resource server directly and bind
  the same `octopus-security.roles` map. Either way the setting has the same shape as in CRS.
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
| Bad marker, missing `branch` with content | 400 | `INVALID_REQUEST` |
| Zip check failed | 400 | `INVALID_CONTENT` |
| Upload over the limit | 413 | `INVALID_CONTENT` |
| Outside the allow-list / VCS not configured | 403 | `TARGET_NOT_ALLOWED` |
| Project or organisation missing on create | 404 | `NOT_FOUND` |
| Not ours | 409 | `REPOSITORY_EXISTS` |
| Ours, content not ours | 409 | `REPOSITORY_CONFLICT` |
| Created, push lost | 502 | `REPOSITORY_EMPTY` |
| Provider read failed | 5xx | `OTHER` |

### D9. Functional tests

- A test-only RSA key: the ft server trusts its public key, and the ft suite signs its own tokens
  with and without the permission. No identity server runs in ft.
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

## Open questions

- Does `octopus-security-common` allow a chain limited to some paths while others stay open (D7)?
- Does Bitbucket 9.4 make the first pushed branch the default branch of an empty repository (D5)?
- Bitbucket's description limit: confirm 255 is the smaller of the two (D2).
