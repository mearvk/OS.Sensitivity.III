# Darling SDK symlinks — why some are dangling, and how to resolve them

This Darling tree is the **lean** copy warehoused for the SLeeLa OS Creator (see
`../SOURCES.md`): the top-level Darling framework is present, but Darling's
**git submodules** — the `src/external/<project>/` source trees — are **not**
checked out here, by design, to keep the warehouse lean.

## What you will see

- **848 symlinks resolve** normally (links whose targets live inside this tree).
- **~2,015 symlinks are dangling.** These are the macOS SDK framework header
  links under
  `Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/System/Library/Frameworks/`.
  They follow Darling's standard two-hop shape, e.g.:

  ```
  AppKit.framework/Headers          -> Versions/C/Headers
  AppKit.framework/Versions/C/Headers -> ../../../.../src/external/cocotron/AppKit/include/AppKit
  ```

  The final hop points into `src/external/<project>/...`, which is an
  **uninitialized git submodule mount point** in this lean copy, so the link has
  no target on disk yet.

## This is expected, not corruption

`.gitmodules` in this directory defines **149 submodules** (the `src/external/*`
projects: cocotron, cfnetwork, corefoundation, foundation, dyld, cctools, icu,
JavaScriptCore, and so on). The dangling header links become valid the moment
those submodules are populated — they are a normal part of Darling's build, not
broken data. The symlinks themselves are tracked correctly in git (git stores
the link, not its target).

## How to resolve the dangling links

Initialize the Darling submodules from the upstream project (this fetches the
`src/external/*` sources the links point at):

```sh
cd linux/os-loader/userland/darling
# point at the upstream so submodule URLs resolve, then populate:
git submodule update --init --recursive
# the header links under MacOSX.sdk/.../Frameworks/ now resolve.
```

Alternatively, build Darling directly from upstream
(`github.com/darlinghq/darling`), which initializes these submodules as part of
its documented build, then warehouse only the pieces you need.

## Why they are not vendored here

Populating all 149 submodules would add hundreds of megabytes of external source
(much of it large: icu, JavaScriptCore, Heimdal, cctools) and duplicate upstream
trees that already have their own homes. The lean warehouse intentionally keeps
only the top-level Darling framework; the OS Creator / build step fetches the
submodule payloads at assembly time as needed. The same compatibility capability
is also warehoused, fully, under `../../../../macos/compat-loader/darling/` for
the macOS shape.
