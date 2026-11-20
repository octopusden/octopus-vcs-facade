## Why

- Component provisioning (components registry ADR-025, decision 1) needs one safe way to create
  a repository with its first content on Bitbucket and Gitea. The only service that does it
  today is closed and Bitbucket-only, so the open-source stack cannot provision a component.
- ADR-025 puts the VCS actions in vcs-facade and everything about templates, Maven and
  components in the new `octopus-component-provisioning-service`. vcs-facade must stay a facade:
  it receives the content as a zip and commits it as given.
- A create can fail half-way or lose its response. The caller must be able to repeat it safely,
  and a repeat must never write over a repository that was made for someone else. A
  **provisioning marker** written in the create call itself says who a repository was made for.
- The provisioning service keeps no database in its first phase: it learns the state of each
  repository by reading it back from vcs-facade, so a failed read must never look like
  "missing".

## What Changes

**Three new endpoints, off by default**
- **Group check**: does the Bitbucket project or Gitea organisation of an SSH URL exist.
- **Repository state**: missing, or the repository's marker, default branch, whether it has
  commits, and the marker trailer on its root commit. A read that fails is an error, never
  "missing".
- **Create** (multipart): creates the repository with the marker as its description and the
  requested branch as its default branch, both in the create call, and pushes the optional zip
  as one commit carrying the marker as a trailer.

**Create is safe to repeat**
- Missing: create, mark, push. Carrying the request's marker: finish what is missing, or answer
  that it is done. Anything else: a conflict, and nothing is written.
- `201` when this call wrote something; `200` when the repository was already finished; `409
  REPOSITORY_EXISTS` or `409 REPOSITORY_CONFLICT`; `502 REPOSITORY_EMPTY` when the repository
  was created but the push failed (a repeat finishes it).
- It never writes without the request's marker, never force-pushes, and never deletes.

**Guard rails**
- The zip must open, contain no `.git` entry, and have no path that escapes the repository.
  Executable bits are kept. Its size is limited.
- Each VCS has an allow-list of projects or organisations; targets outside it get `403`.
- The new endpoints need a token carrying the `CREATE_REPOSITORIES` permission, mapped from roles
  through `octopus-security.roles` with `octopus-security-common`, as in the components registry.
  Only service accounts are meant to have it. With provisioning enabled, the service needs the
  auth server (`auth-server.url`, `auth-server.realm`) at startup.
- Each VCS has a separate provisioning credential with repository-create rights; the existing
  read credential is not used to write.
- `vcs-facade.provisioning.enabled=false` by default. Disabled, the new endpoints do not exist
  and nothing about the service changes.

**Existing API unchanged**
- Existing endpoints keep their paths, behaviour and lack of authentication.

### Dependencies

- octopus-external-systems-client **2.1.0** (released): the Bitbucket and Gitea clients can set a
  repository's description, visibility and default branch in the create call. This change moves
  `external-systems-client.version` from 2.0.106 to 2.1.0.
- octopus-cloud-commons **2.0.20** (`octopus-security-common`), new to vcs-facade.
- The contract is consumed by `octopus-component-provisioning-service`; no other caller changes.
- ADR-025 is still Proposed. If its decision 1 changes before it is accepted, this change follows.

## Affected areas

- `server`: new controller(s), a provisioning service per provider, zip checks, JGit push,
  security configuration, settings (`vcs-facade.provisioning.*`).
- `common`: request/response DTOs and new `VcsFacadeErrorCode` values.
- `client`: the three calls on `VcsFacadeClient`.
- `ft`: create, repeat, conflict and lost-response scenarios on Bitbucket and Gitea; a WireMock
  stub auth server in the Docker and OKD test environments.
- `gradle.properties`: `external-systems-client.version`; new dependencies (JGit, a zip library
  that exposes Unix modes, the security starter).
- `README.md`: the provisioning settings and behaviour.

## Out of scope

- Templates, Maven, parameter substitution, catalogues and the marker's content: the provisioning
  service owns them. vcs-facade treats the marker as an opaque string.
- Deleting repositories, or any public delete endpoint.
- Creating projects or organisations.
- Writing to a repository after its first commit (adding commits, changing settings).
- Other providers. A provider that cannot store the marker in its create call gets no
  provisioning.
