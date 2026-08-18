# Reading Android build failures

## Separate "did not compile" from "a test failed"

`assembleDebug` does not run tests. So:

- **Assemble failed** → the code genuinely does not compile. Your problem.
- **Assemble succeeded, a later test step failed** → the code compiles. The
  failure may predate your work entirely.

Before attributing a test failure to your change, check two cheap things:

```bash
# Does the diff even touch the failing area?
git diff --stat <base-branch>..HEAD

# Was the base branch already red?
curl -sS "https://api.github.com/repos/<owner>/<repo>/actions/runs?branch=<base>&per_page=5" \
  | python3 -c "
import sys,json
for r in json.load(sys.stdin)['workflow_runs']:
    print(r['head_sha'][:7], r['conclusion'], r['display_title'][:60])
"
```

A base branch that has been failing for several commits, plus a diff that does
not touch the failing module, is conclusive. Say so once, clearly, and do not
quietly "fix" an unrelated test to turn the badge green — that hides a real
defect the owner needs to see.

## Finding the actual error in CI logs

Gradle prints an internal stack trace of 150+ frames after the failure, so a
short log tail shows nothing but `org.gradle.internal.execution.steps.*`. The
information you want sits *above* it.

Ask for a deeper window (300+ lines) and scan for, in rough order of value:

| Marker | Meaning |
|---|---|
| `> Task :app:<name> FAILED` | which task died — the single most useful line |
| `e: file:///...` | a Kotlin compile error, with file and position |
| `<TestClass> > <test name> FAILED` | a failing test and its assertion site |
| `* What went wrong:` | Gradle's own summary |

Downloading the full log zip is usually not an option: the log host
(`results-receiver.actions.githubusercontent.com`) is typically blocked in a
sandbox even when `api.github.com` is not.

## Failures worth recognising on sight

**`Could not resolve com.android.tools.build:gradle` / any `androidx.*`**
Google Maven is unreachable. Not a version problem — re-run preflight. No
`repositories {}` edit fixes it, because these artifacts exist nowhere else.

**`SDK location not found`**
No `local.properties` with `sdk.dir`, and no `ANDROID_HOME`. Run
`scripts/install-sdk.sh`, then write `local.properties`. Keep that file
gitignored — the path is per-machine and committing it breaks every other
checkout.

**`Unsupported class file major version` / AGP refuses the JDK**
AGP 8.x wants JDK 17–21. A newer JDK on `PATH` is the usual cause. Point
`JAVA_HOME` at a supported one rather than downgrading the project.

**`Installed Build Tools revision X is corrupted`**
A truncated download. Delete `$ANDROID_HOME/build-tools/<version>` and
reinstall that one package.

**Compose compiler version mismatch**
With the `org.jetbrains.kotlin.plugin.compose` plugin the compiler version is
pinned to the Kotlin version, so bumping Kotlin alone is the fix — there is no
separate `composeOptions` version to chase.

## Verify before reporting done

Compilation is the bar for "it builds". State which check actually ran: a
successful `assembleDebug` is evidence; "it looks right" is not. If the only
compiler available was CI's, say that — it is a real verification, just not a
local one.
