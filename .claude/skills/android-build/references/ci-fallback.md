# Building through CI when the local toolchain is unreachable

This is the route when preflight says `ROUTE=C`. The build still happens on
real infrastructure — a GitHub Actions runner has the SDK and unrestricted
access to Google's hosts — so the goal is to drive that remote build and bring
the artifact back, not to fight the sandbox.

## 1. Get the code onto a branch CI watches

Check `.github/workflows/*.yml` for the `on:` trigger. A workflow keyed on
`push: branches: ["**"]` builds every branch; one keyed to a single branch will
silently skip yours.

```bash
git push -u origin <branch>
```

## 2. Find the run for your exact commit

Match on `head_sha`, never on "the most recent run" — pushes from elsewhere
race with yours and you end up reading someone else's failure.

```bash
curl -sS "https://api.github.com/repos/<owner>/<repo>/actions/runs?branch=<branch>&per_page=10" \
  | python3 -c "
import sys,json
d=json.load(sys.stdin)
for r in d['workflow_runs']:
    print(r['id'], r['head_sha'][:7], r['status'], r['conclusion'])
"
```

Public repositories answer this unauthenticated, which matters because the
sandbox usually has no `gh` CLI and no token.

## 3. Wait without burning the turn

Poll in a backgrounded shell so the session stays responsive. Never use a
foreground `sleep` loop.

```bash
until [ "$(curl -sS -m 20 "https://api.github.com/repos/<owner>/<repo>/actions/runs/<id>" \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('status'))")" = "completed" ]; do
  sleep 20
done
```

## 4. Read step conclusions, not the run conclusion

This is the step most worth internalising. A run reports `failure` if *any*
step failed, including a test step that runs long after the APK was built. The
APK may exist and be perfectly good.

```bash
curl -sS "https://api.github.com/repos/<owner>/<repo>/actions/runs/<id>/jobs" \
  | python3 -c "
import sys,json
for j in json.load(sys.stdin)['jobs']:
    print('job:', j['name'], j['conclusion'])
    for s in j['steps']:
        print('   ', s['number'], s['name'], '->', s['conclusion'])
"
```

If the assemble step succeeded, the code compiles. Report that plainly rather
than letting a red badge imply the change is broken.

## 5. Retrieve the APK

Two sources, and they do not behave the same in a sandbox:

| Source | Format | Auth | Sandbox reachability |
|---|---|---|---|
| Run artifact | zipped | required | storage host usually **blocked** |
| Release asset | raw `.apk` | none for public repos | usually **reachable** |

Prefer a release asset. If the workflow only publishes from a trunk branch,
give the branch its own tag rather than overwriting the shared one — clobbering
the trunk release with a feature build is a surprise nobody asked for.

```yaml
- name: Publish the APK
  if: github.ref_name == 'main' || github.ref_name == 'my-feature-branch'
  env:
    GH_TOKEN: ${{ github.token }}
    TAG: ${{ github.ref_name == 'main' && 'debug' || 'debug-my-feature' }}
  run: |
    gh release view "$TAG" >/dev/null 2>&1 \
      || gh release create "$TAG" --title "Debug build — $TAG" --notes "bootstrapping"
    gh release upload "$TAG" out/app-debug.apk --clobber
```

Verify before claiming success — a link that 404s is worse than no link:

```bash
curl -sSL -o /dev/null -w "%{http_code} %{size_download} %{content_type}\n" \
  "https://github.com/<owner>/<repo>/releases/download/<tag>/<file>.apk"
```

Expect `200`, a plausible size, and `application/vnd.android.package-archive`.

## 6. Handing the APK over

Debug APKs are routinely 40–80 MB because they carry every ABI and are
undexed-optimised. File-delivery tools cap well below that (30 MiB in Cowork),
so attaching usually fails. Lead with the download link; attempt the file only
if it is comfortably under the cap. Say plainly if delivery failed and why.
