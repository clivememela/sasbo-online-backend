#!/usr/bin/env node
/**
 * Run all three SASBO Online test suites and merge their reports into one
 * document.
 *
 * A THIN CONVENIENCE WRAPPER, and deliberately nothing more. The merge itself
 * lives in `merge-reports.mjs` beside this file; this contains no report logic
 * at all. What it is allowed to know: where the three checkouts are, what the
 * one documented command is for each, how to build the two application images
 * the container lane runs, and how to hand the results to the merge.
 *
 * ⚠️ THAT THIRD ITEM IS NEW AND IT IS THE WEAKEST SEAM IN THIS FILE. Building an
 * image means knowing four more things about somebody else's repository -- that
 * the server has a root `Dockerfile` with a `FROM … AS build` stage, that the
 * client is npm, that its build script is `build`, and that its output is
 * `dist/`. Every OTHER per-repository fact here is declared once in the shared
 * `SUITES` table so it cannot drift. These four are imperative code instead.
 * **If a third image or a second build shape ever arrives, move the recipes into
 * that table rather than adding another branch below.**
 *
 * 🔑 EACH SUITE STILL RUNS STANDALONE IN A FRESH CLONE OF ITS OWN REPOSITORY.
 * Nothing here is required for any one of them to run, and none of the three
 * depends on this file. That is the invariant this wrapper must never break --
 * it is a convenience over three independent suites, never a dependency of any.
 *
 * ── Why this is Node and not PowerShell ───────────────────────────────────────
 * It WAS PowerShell -- `test-all.ps1`, 283 lines -- and that made the arc's
 * headline deliverable runnable on exactly one of the two developers' machines.
 * Three things were wrong with it and only one of them was portability:
 *
 *   1. `gradlew.bat` is the Windows wrapper. macOS and Linux need `./gradlew`.
 *      Hard failure, first stage. Handled here by asking the platform.
 *   2. The free-memory probe was WMI -- `Get-CimInstance Win32_OperatingSystem`
 *      -- which exists only on Windows. It is not decoration: see `memoryNote`
 *      below for what its absence cost.
 *   3. 🔴 THE INTERESTING ONE. PowerShell CANNOT SET AN ENVIRONMENT VARIABLE TO
 *      THE EMPTY STRING. Both `$env:X = ''` and
 *      `[Environment]::SetEnvironmentVariable('X','')` DELETE the variable
 *      (register row D10). `docker-compose.yml` distinguishes the two states on
 *      purpose -- `${VAR-default}`, not `${VAR:-default}` -- because an EMPTY
 *      value is what publishes a service on an ephemeral port and lets two
 *      stacks coexist. The PowerShell version therefore carried a temporary
 *      `--env-file` purely to express a state the language could not.
 *
 *      Node sets it directly. MEASURED rather than assumed, via
 *      `docker compose config --format json`: with the three host-port
 *      variables set to '' every service resolved to NO published port, and
 *      with them unset to the fixed 55173 / 55432 / 58080. So the port deletes
 *      the workaround rather than translating it, and one whole class of
 *      "why did my stack collide with the other session's" goes away.
 *
 * ── Where the checkouts are ───────────────────────────────────────────────────
 * The three repositories are NOT hard-coded relative to this file, and that is
 * deliberate: this arc runs from git worktrees, and CI checks each repository
 * out wherever it likes. `SASBO_WORKSPACE` names the directory the three sit
 * in; unset, `findWorkspace()` in `merge-reports.mjs` discovers it. Each
 * checkout can also be overridden individually.
 *
 * ── It builds the two application images, and that is not a convenience ───────
 * 🔴 IT DID NOT, AND THAT MADE THE SUITE LANE'S VERDICT MEANINGLESS. The compose
 * stack consumes `sasbo-online-backend:local` and `sasbo-online-client:local` BY
 * TAG and builds neither -- correctly, because that invariant is what lets the
 * same file run in a pipeline where each application repository is built by its
 * own job. `--server` and `--client` were accepted here and used for exactly one
 * thing: comparing two contract artifacts.
 *
 * So the lane tested whatever happened to be tagged, and reported plausible
 * numbers about software it had never seen. The full sequence is in
 * `buildApplicationImages` below, where the fix is; the short version is that no
 * figure the lane produced before this change was about the code under test.
 *
 * ⚠️ IT IS ALSO A QUALIFICATION ON EVERY CONTAINERISED VERDICT RECORDED BEFORE
 * THIS CHANGE, including the ones that were green. Those results are evidence
 * about the SUITE, and about the application images only to the extent they
 * happened to be current -- which was not checked and cannot be reconstructed.
 *
 * `--no-image-build` opts out for a pipeline, and SAYS SO in the output. Silence
 * was the one option that had to go.
 *
 * ── Usage ─────────────────────────────────────────────────────────────────────
 *   node tools/test-all.mjs
 *   node tools/test-all.mjs --only client
 *   node tools/test-all.mjs --only server --only suite
 *   node tools/test-all.mjs --client .worktrees/client/phase-07-orchestration
 *   node tools/test-all.mjs --out reports/merged --stack-id ci-4711
 *   node tools/test-all.mjs --only suite --determinism   # every suite test three times
 *
 * Exits with the merge's exit code, which is the whole verdict: 0 all green,
 * 1 something failed or a report and an exit code disagree, 2 something did not
 * run. A suite excluded by `--only` is reported as DID NOT RUN rather than
 * quietly omitted.
 */
import { spawn, spawnSync } from 'node:child_process'
import { randomBytes } from 'node:crypto'
import { rmSync, existsSync, readFileSync, writeFileSync, mkdirSync, openSync, closeSync } from 'node:fs'
import { freemem, platform } from 'node:os'
import { join, resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

import { SUITES, checkoutFor, findWorkspace } from './merge-reports.mjs'

const HERE = dirname(fileURLToPath(import.meta.url))
const MERGE = join(HERE, 'merge-reports.mjs')

/**
 * The three repositories -- keys, directories and the one documented command
 * for each -- are declared ONCE, in `merge-reports.mjs`, and imported here.
 *
 * 🔑 They were declared in both, and the COMMAND was in both. The merge renders
 * it as "Produced by" in its DID-NOT-RUN section, so a drift between the two
 * would have the report hand a reader a command this orchestrator no longer
 * runs -- in the one branch a green run never exercises, which is exactly where
 * this repository keeps finding its mistakes.
 */
const CHECKOUTS = SUITES.map((s) => s.dir)

let workspaceFound

/**
 * The workspace, resolved on first USE rather than at import.
 *
 * 🔴 REFUSING, because the README says this refuses and it did not. The walk
 * returns null when no ancestor holds the three checkouts, and every use of the
 * workspace below would then have silently resolved against `null` -- three
 * stages reporting "no such directory", a report saying nothing ran, and a
 * plausible exit 2. That is the same quiet failure the walk replaced, one level
 * further out.
 *
 * ⚠️ AT FIRST USE, NOT AT MODULE LOAD. `demo.mjs` imports this file for its
 * helpers, and `demo --help` or `demo --no-image-build` needs no checkout at
 * all; a refusal fired by the import would answer a question nobody asked, in
 * this tool's name, with this tool's exit code.
 */
function workspace() {
  if (workspaceFound === undefined) {
    workspaceFound = findWorkspace(HERE)
    if (!workspaceFound) {
      process.stderr.write(
        `test-all: could not find the directory holding ${CHECKOUTS.join(', ')}.\n` +
          `Searched upwards from ${HERE}. Set SASBO_WORKSPACE to point at it.\n`,
      )
      process.exit(2)
    }
  }
  return workspaceFound
}

/**
 * 🔴 THE FREE-MEMORY FIGURE IS NOT DECORATION, and it is printed at every stage
 * boundary because its absence cost a gate pass.
 *
 * Measured 2026-09-06: pass 3 of the determinism gate went red with six Vitest
 * workers failing to start -- "Timeout waiting for worker to respond" -- and 62
 * tests that never ran reported as 6 failures. Nothing was wrong with any test.
 * The host was starved: that pass took 7m10s for Gradle against 3m11s, and 109s
 * for Vitest against 29s, while the CONTAINERISED suite in the same pass took
 * 3.9m, exactly as in the two green passes beside it.
 *
 * 🔑 THAT ASYMMETRY IS THE DIAGNOSIS: the native stages slow down and start
 * failing, the containerised one does not, because Docker's VM holds its own
 * allocation. Printing free memory at each boundary turns an hour of
 * investigation into one line of scrollback.
 *
 * ⚠️ AND IT DOES NOT MEAN THE SAME THING ON EVERY PLATFORM, so it says which it
 * is rather than printing a bare number that invites a false comparison. On
 * Linux and macOS `freemem()` counts only genuinely free pages: the kernel
 * lends the rest to the page cache and takes it back on demand, so a healthy
 * machine can report a small number and be under no pressure at all. Windows
 * counts its whole available pool. A Windows reading is roughly "what a process
 * can have"; a macOS one is a floor, not that.
 */
function memoryNote() {
  const mb = Math.round(freemem() / 1024 / 1024)
  const caveat =
    platform() === 'win32'
      ? ''
      : ' (free pages only; the page cache is not counted, so this under-reports what is available)'
  return `host memory free: ${mb.toLocaleString('en')} MB${caveat}`
}

function head(text) {
  const rule = '='.repeat(78)
  process.stdout.write(`\n${rule}\n  ${text}\n  ${memoryNote()}\n${rule}\n`)
}

/**
 * 🔴 THE MOST DANGEROUS STEP IN THIS FILE, so it is written once and inherited
 * by every stage rather than repeated per stage.
 *
 * Without it, a suite whose command dies BEFORE writing a report leaves the
 * previous run's artifacts on disk, and the merge reads a stale green as though
 * it were this run's result. That is the worst shape available to a report
 * merger because it is invisible -- everything looks fine, and the document is
 * about a run that never happened.
 */
function clearReports(dir) {
  const reports = join(dir, 'reports')
  try {
    rmSync(reports, { recursive: true, force: true })
  } catch (error) {
    // Still fatal -- a report that cannot be cleared would be merged as this
    // run's -- but said plainly. On Linux it is a root-owned `reports/` from a
    // container run before `giveOutputBack` existed.
    throw new Error(
      `could not clear ${reports} (${error.code}), so its old report would be read as this run's. ` +
        'If a container left it root-owned: sudo chown -R "$(id -u):$(id -g)" <that directory>',
    )
  }
}

/**
 * Where one repository is checked out -- resolved ONE way, for every caller in
 * BOTH tools.
 *
 * 🔑 IT WAS RESOLVED THREE WAYS. `main()` here read the defaults from the shared
 * `SUITES` table; `checkContractArtifacts` wrote `opts.dirs.server || 'server'`
 * and `opts.dirs.client || 'client'` out again; and `merge-reports.mjs` had a
 * third copy split across its own `main()` and `gather()`. So renaming a
 * checkout directory moved some readers and not others -- and the contract
 * comparison would report `SKIP  not present in both checkouts`, which reads as
 * a repository with no artifacts rather than as a path this file got wrong.
 *
 * ⚠️ THE SHARED ONE LIVES IN `merge-reports.mjs`, beside `SUITES` and
 * `findWorkspace`, because that is where the data it reads lives and because CI
 * calls that file directly. This is the local binding of the workspace, so the
 * three call sites below stay short.
 */
const checkout = (key, opts) => checkoutFor(key, opts.dirs, workspace())

/** A checkout's commit, for the record of what a run tested; `-` when it is none. */
function commitOf(dir) {
  const asked = spawnSync('git', ['-C', dir, 'rev-parse', '--short', 'HEAD'], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'ignore'],
  })
  return asked.status === 0 ? asked.stdout.trim() : '-'
}

/**
 * Run a command, letting its output through to this terminal unchanged.
 *
 * `stdio: 'inherit'` rather than capturing: a captured suite prints nothing for
 * minutes and then dumps everything at the end, and "silent" is
 * indistinguishable from "hung" while you are watching it.
 */
function run(command, args, options = {}) {
  // 🔴 WINDOWS CANNOT EXECUTE A `.bat` OR `.cmd` DIRECTLY, and this is decided
  // here rather than per caller because forgetting it is silent until the stage
  // runs. `CreateProcess` only starts real executables; a batch file has to go
  // through the command interpreter. The PowerShell version this replaced never
  // hit it -- `&` handles batch files natively -- so the port REGRESSED the
  // server stage, and the merged three-repo run is what caught it: the stage
  // reported 127, the merged report said the server DID NOT RUN, and the
  // orchestrator exited 2. Every part of that chain behaved correctly around a
  // stage that never started.
  //
  // `npm` is the other case and it arrives without an extension, so callers
  // still pass `shell` explicitly for it.
  const shell = options.shell ?? (platform() === 'win32' && /\.(bat|cmd)$/i.test(command))

  // Under a shell the command is re-parsed as a string, so a path containing a
  // space -- which this workspace's does, twice -- would split into arguments.
  // The arguments themselves are single tokens here and need no quoting.
  const spawned = shell && /\s/.test(command) ? `"${command}"` : command

  const result = spawnSync(spawned, args, { stdio: 'inherit', ...options, shell })
  // A command that could not be spawned at all -- not on PATH, not executable --
  // has no exit code. 127 is the shell's convention for it, and reporting it as
  // a number keeps every stage's result the same kind of thing.
  if (result.error) {
    process.stdout.write(`could not run ${command}: ${result.error.message}\n`)
    return 127
  }
  return result.status ?? 1
}

/**
 * Run a short docker query and KEEP its output, because this file needs to READ
 * that output rather than show it.
 *
 * Deliberately separate from `run()` instead of a flag on it. `run()`'s whole
 * argument is that a captured suite is indistinguishable from a hung one, and
 * that reasoning holds for every stage; it does not hold for a sub-second
 * `docker compose config`, whose output is data. Two functions keep the default
 * -- show the human everything -- from becoming a parameter somebody flips.
 *
 * ⚠️ On failure the captured stderr is PRINTED, so this is for calls whose
 * failure is a real problem. Capturing a command and then swallowing its error
 * message is how a validation failure becomes "it did not work" -- the daemon's
 * own message is the whole diagnosis. A call whose failure is an EXPECTED,
 * fully-handled answer must not come through here: see `imageId` below, which
 * asks a question whose "no" is ordinary.
 */
function capture(command, args, options = {}) {
  const result = spawnSync(command, args, { encoding: 'utf8', ...options })
  if (result.error) {
    process.stdout.write(`could not run ${command}: ${result.error.message}\n`)
    return { status: 127, stdout: '' }
  }
  const status = result.status ?? 1
  if (status !== 0 && result.stderr) process.stdout.write(result.stderr)
  return { status, stdout: result.stdout ?? '' }
}

/**
 * The id of a local image, or `null` if it is not there.
 *
 * 🔑 ONE ANSWER TO "IS THIS IMAGE PRESENT", where there were two. The CA-image
 * precondition tested `status !== 0` and the provenance line tested for empty
 * output -- two implementations of one question, which is the drift
 * `support/guards.js` writes a whole paragraph against: *"Two implementations of
 * 'is this ours?' is the bug."* They could disagree, and the disagreement would
 * be a missing image reported as present by one caller and absent by the other.
 *
 * ⚠️ NOT `capture()`, and that is the point of it being its own function.
 * "Absent" is an ordinary answer here -- one branch is written to handle it --
 * so docker's `No such image` on stderr would print immediately before this
 * file's own purpose-written explanation, giving one condition two messages.
 * `stdio: 'ignore'` keeps the question quiet and lets the caller do the talking.
 *
 * The id is content-addressed, which is why this returns it rather than a
 * boolean: `reportImages` needs exactly that, and "which software" is the
 * question a provenance line has to answer.
 */
function imageId(tag) {
  const asked = spawnSync('docker', ['image', 'inspect', '--format', '{{.Id}}', tag], {
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'ignore'],
  })
  const id = (asked.stdout ?? '').trim()
  return asked.status === 0 && id ? id : null
}

/**
 * The patched Gradle base image, for a machine whose HTTPS is intercepted.
 *
 * Named here rather than made configurable: `docker/gradle-with-ca.Dockerfile`
 * and this repository's README both build exactly this tag, and a variable
 * would be a third statement of one name that nothing checks.
 */
const GRADLE_CA_IMAGE = 'sasbo-gradle-ca:local'

/**
 * BUILD THE TWO APPLICATION IMAGES THE CONTAINER LANE RUNS.
 *
 * 🔴 WITHOUT THIS THE SUITE LANE'S VERDICT IS ABOUT WHATEVER IS TAGGED ON THE
 * MACHINE, and that is not a hypothetical. THE WHOLE SEQUENCE, measured
 * 2026-09-09, because each step produced a confident number and none of the
 * three was the truth:
 *
 *   1. Images two and three days old -- built from a tree carrying neither the
 *      incumbent's commit nor the merge under test -- gave a considered-looking
 *      `2 failed`. That number was about software nobody had built that week.
 *   2. Rebuilt from the tree actually under test, the same command gave `14`.
 *      ⚠️ THAT NUMBER WAS NOT THE TRUTH EITHER: the rebuild was done WITHOUT the
 *      empty `VITE_API_BASE_URL`, so the client called the deployed backend from
 *      inside the compose network. See the client build below -- that is X11.
 *   3. Rebuilt correctly, by this function, the lane is green.
 *
 * 🔑 SO THE LESSON IS NOT "STALE IMAGES GIVE 2 AND FRESH ONES GIVE 14". It is
 * that a containerised lane will report a plausible figure about whatever it was
 * handed, and it takes TWO separate corrections before the figure means anything.
 * Both corrections now happen here, in code, rather than in somebody's memory.
 *
 * 🔑 THE COMPOSE FILE IS NOT THE PROBLEM AND MUST NOT BE "FIXED". Its header
 * states the invariant -- "Nothing here builds from a sibling directory" -- and
 * that is what lets the same file run in a pipeline where each application
 * repository is built by its own job and never checked out beside this one. In
 * a pipeline the images arrive current by construction. ON A DEVELOPER'S
 * MACHINE NOTHING SUPPLIES THAT GUARANTEE, and this orchestrator is the one
 * component that is already TOLD where all three checkouts are. So the gap
 * closes here, where the paths are known, rather than there, where knowing them
 * would break the invariant.
 *
 * ⚠️ THE TAGS ARE READ FROM THE RESOLVED COMPOSE CONFIG, never written out
 * here. `docker-compose.yml` takes `${SASBO_SERVER_IMAGE:-...}`, so a run that
 * overrides either variable would otherwise have this function build the
 * DEFAULT tag while the lane started the override -- rebuilding diligently and
 * testing the stale image anyway, which is this whole finding wearing a fix.
 *
 * @param suiteDir this repository, holding the client Dockerfile.
 * @param images `{ server, client }` -- the tags compose actually resolved.
 * @returns 0 when both images are built, non-zero with a reason on stdout.
 */
function buildApplicationImages(suiteDir, opts, images) {
  const serverDir = checkout('server', opts)
  const clientDir = checkout('client', opts)
  const clientDockerfile = join(suiteDir, 'docker', 'client.Dockerfile')

  // 🔴 EVERY CHEAP PRECONDITION FIRST, BEFORE ANY BUILD STARTS -- and the
  // ordering is the finding rather than tidiness. Each of these is one
  // `existsSync`; the server build below runs `./gradlew build` inside a
  // container and costs minutes. Checked downstream, a client checkout with no
  // `node_modules` bought a full Gradle build before the run refused -- and a
  // fresh worktree is exactly that case, which is the normal working mode here.
  const required = [
    [serverDir, `cannot build ${images.server}: no such directory ${serverDir}`, 127],
    [clientDir, `cannot build ${images.client}: no such directory ${clientDir}`, 127],
    [
      join(clientDir, 'node_modules'),
      `cannot build ${images.client}: ${clientDir} has no node_modules.\n` +
        `  Run \`npm ci\` there first. This refuses rather than installing, because a\n` +
        `  minutes-long install nobody asked for inside a test run is its own surprise.`,
      1,
    ],
    [clientDockerfile, `cannot build ${images.client}: no ${clientDockerfile}`, 127],
  ]

  for (const [path, why, code] of required) {
    if (existsSync(path)) continue
    process.stdout.write(
      `${why}\n  Point a checkout at --server/--client <dir>, or pass --no-image-build if\n` +
        `  the images are supplied by their own pipeline jobs and are already current.\n`,
    )
    return code
  }

  const server = buildServerImage(serverDir, images.server)
  if (server !== 0) return server

  return buildClientImage(clientDir, clientDockerfile, images.client)
}

/**
 * The server image, from the application repository's OWN Dockerfile, unmodified.
 *
 * That image is the artifact which actually deploys. Running the API through
 * Gradle instead would carry Spring Boot devtools, which changes classloading and
 * caching -- a different program from the one that ships.
 */
function buildServerImage(serverDir, tag) {
  const buildArgs = ['build', '-t', tag]

  if (process.env.SASBO_CA_BUNDLE) {
    // 🔴 THE BASE IMAGE IS DERIVED FROM THEIR DOCKERFILE, NOT NAMED HERE.
    // BuildKit substitutes a base by its exact reference, so a hardcoded
    // `gradle:9.5-jdk21` stops matching the day they bump it -- and the
    // substitution then silently does nothing. The build fails deep inside
    // dependency resolution with `PKIX path building failed`, which reads as
    // the Gradle repositories being down. Reading the FROM line means a bump
    // is followed rather than missed.
    const dockerfile = readFileSync(join(serverDir, 'Dockerfile'), 'utf8')
    const base = dockerfile.match(/^FROM\s+(\S+)\s+AS\s+build/im)?.[1]

    if (!base) {
      process.stdout.write(
        `SASBO_CA_BUNDLE is set, but the server Dockerfile has no \`FROM <image> AS build\`\n` +
          `  stage to substitute. Refusing rather than building without the extra CA, which\n` +
          `  would fail later with a certificate error that names the Gradle repositories.\n`,
      )
      return 1
    }

    // 🔴 THIS ONE IS NOT BUILT HERE, AND THE REASON IS WHY THE REFUSAL IS
    // REASONED RATHER THAN LAZY. `docker/gradle-with-ca.Dockerfile` needs two
    // things from its build context that no orchestrator can produce: the
    // proxy's root certificate, and a PRE-POPULATED 164 MB Gradle distribution
    // directory -- the application's wrapper pins a Gradle the base image does
    // not ship, and downloading it through an inspecting proxy exceeds the
    // wrapper's 10-second network timeout. Both are one-off, machine-local, and
    // human-prepared. So this checks and explains instead of guessing.
    //
    // Without the check the substitution resolves to nothing and the build fails
    // deep in dependency resolution with `PKIX path building failed`, which
    // reads as the Gradle repositories being down.
    if (!imageId(GRADLE_CA_IMAGE)) {
      process.stdout.write(
        `SASBO_CA_BUNDLE is set, so the server image needs ${GRADLE_CA_IMAGE}, and it is\n` +
          `  not on this machine. It cannot be built from here: its context needs the proxy\n` +
          `  root certificate AND a pre-populated Gradle distribution, both machine-local.\n` +
          `  The README's "If a build or install fails with a certificate error" section has\n` +
          `  the one-off commands, and ${process.env.SASBO_CA_BUNDLE} is the certificate.\n`,
      )
      return 1
    }
    buildArgs.push('--build-context', `${base}=docker-image://${GRADLE_CA_IMAGE}`)
  }

  head(`building ${tag}  --  from ${serverDir}`)
  return run('docker', [...buildArgs, serverDir])
}

/**
 * The client image: the production bundle, then nginx over it.
 *
 * Two steps, and the first one carries the trap that cost a whole investigation.
 */
function buildClientImage(clientDir, clientDockerfile, tag) {
  head(`building ${tag}  --  from ${clientDir}`)

  // 🔴 `VITE_API_BASE_URL` EMPTY, AND IT IS LOAD-BEARING -- X11.
  //
  // `client/.env` is TRACKED and points at the deployed Render backend. Vite
  // inlines whatever is set at build time, so a plain `npm run build` produces a
  // bundle that calls the internet from inside the compose network. Measured:
  // every data-reading scenario failed with "Showing 0-0 of 0 members" and
  // `toHaveCount 1 / Received 0` -- 14 failures, and NOT ONE of them mentions a
  // base URL. It reads as an unseeded database, which is where the investigation
  // goes; the API was serving 241 KB of members throughout. Empty makes the
  // bundle call same-origin, and the client image's nginx proxies /api to the
  // server.
  //
  // 🔑 A README CANNOT ENFORCE THIS AND SAID SO FOR THREE DAYS. An orchestrator
  // that builds the image cannot forget it.
  //
  // ⚠️ EMPTY, NOT UNSET, and the distinction is the same one the compose file's
  // `${VAR-default}` depends on -- verified here rather than assumed, because
  // PowerShell CANNOT express it (register row D10) and Windows is where this
  // runs. Probed 2026-09-09: a child of this process sees the variable PRESENT
  // with an empty value, so it overrides the tracked `.env` rather than falling
  // back to it.
  const built = run('npm', ['run', 'build'], {
    cwd: clientDir,
    shell: platform() === 'win32',
    env: { ...process.env, VITE_API_BASE_URL: '' },
  })
  if (built !== 0) return built

  const dist = join(clientDir, 'dist')
  if (!existsSync(dist)) {
    process.stdout.write(`the client build reported success but produced no ${dist}\n`)
    return 1
  }

  return run('docker', ['build', '-f', clientDockerfile, '-t', tag, dist])
}

/**
 * WHICH images the lane is about to run, printed so the scrollback carries it.
 *
 * `X10` asked this orchestrator to either build the images or "assert their age
 * and provenance". It builds them, so the content is current by construction --
 * and printing the id is what makes that checkable afterwards rather than merely
 * true at the time. Two runs printing the same id ran the same software.
 *
 * 🔴 THE ID, AND DELIBERATELY NOT THE CREATION DATE. This printed
 * `{{.Id}} {{.Created}}` first, and the first run after the fix reported images
 * dated the PREVIOUS DAY -- while the build had just run and succeeded. Nothing
 * was wrong: a fully cached rebuild produces the byte-identical image, which is
 * the same image, and its `Created` is when that content was first built. So the
 * date said "stale" about an image that was exactly current.
 *
 * 🔑 That is `X10`'s own failure mode wearing this fix's clothes -- a plausible
 * number that is not about what the reader thinks it is about. The id is
 * content-addressed, so it cannot make that mistake: it answers "which software"
 * rather than "how old", and "which software" is the question.
 */
function reportImages(images) {
  for (const tag of Object.values(images)) {
    process.stdout.write(`  ${tag.padEnd(30)} ${imageId(tag) ?? 'NOT PRESENT'}\n`)
  }
}

/**
 * The services the stack IS. One list, read by the gate and by `demo.mjs`.
 *
 * `mail` is the catcher the server sends to: since server 99936a5 a capture is a
 * send (register row A64), so a stack without it cannot capture a member at all.
 */
export const STACK_SERVICES = ['postgres', 'mail', 'server', 'client']

/**
 * The demonstration users' password for a stack this tool starts -- the October build's sign-in.
 *
 * 🔑 MADE HERE, PER STACK, AND WRITTEN NOWHERE. The server seeds one user per position only when
 * `SASBO_SIGNIN_DEMO_PASSWORD` is set, and the suite signs them in with the same value; `docker-compose.yml`
 * hands it to both from the environment `docker compose` runs in. So no file carries a password, and every stack
 * has its own: 18 random bytes, 24 characters -- past the sign-in rules' ten, and nobody's common password. One
 * already in the environment (a workflow's, an operator's) is used as it is.
 */
export function demoUsersPassword(env = process.env) {
  return env.SASBO_SIGNIN_DEMO_PASSWORD || randomBytes(18).toString('base64url')
}

/**
 * What `docker-compose.yml` RESOLVES to under the current environment: the image
 * tag each application service will start, and the host port each publishes.
 *
 * 🔴 VALIDATE THE FILE BEFORE ASKING IT TO START ANYTHING. The suite's own
 * harness guards read `docker-compose.yml` as TEXT -- they have no opinion
 * about whether it would actually resolve. `docker compose config` parses it
 * as YAML, substitutes every variable and validates the result, and it is the
 * only check that catches a reference the daemon cannot resolve. It belongs
 * here and not in the harness project, because it needs the compose CLI and
 * that project deliberately runs without one. Cheap, and it fails in a second
 * rather than after the stack has half come up with a confusing partial error.
 *
 * 🔑 `--format json` RATHER THAN `-q`, and the extra output is why. `-q`
 * answers only "would this file resolve". The resolved document ALSO carries
 * the image tag every service will actually start -- the one fact the build
 * must not guess -- and the port each one publishes, which is what a browser
 * on the host needs. Same invocation, same validation, two more things known.
 *
 * @param composeRead the caller's bound `docker compose` reader, so the
 *                    document read is the document later started.
 * @returns `{ status }` non-zero with the reason on stdout, or
 *          `{ status: 0, images: { server, client }, ports: { client, api, mail }, registry }`
 *          -- a port is `null` when the service publishes none; `registry` maps
 *          every service whose image comes from a registry, rather than from the
 *          two builds here, to that image -- read from the document, never listed.
 */
export function resolveStack(composeRead) {
  const configured = composeRead('config', '--format', 'json')
  if (configured.status !== 0) {
    process.stdout.write('docker-compose.yml did not validate; not starting the stack\n')
    return { status: configured.status }
  }

  const services = JSON.parse(configured.stdout).services ?? {}
  const images = { server: services.server?.image, client: services.client?.image }

  if (!images.server || !images.client) {
    process.stdout.write(
      'docker-compose.yml resolved without an image for the server or the client service;\n' +
        'not starting a stack this orchestrator cannot identify.\n',
    )
    return { status: 1 }
  }

  const published = (service) => {
    const port = services[service]?.ports?.[0]?.published
    return port ? String(port) : null
  }
  const built = new Set([images.server, images.client])
  return {
    status: 0,
    images,
    ports: { client: published('client'), api: published('server'), mail: published('mail') },
    registry: Object.fromEntries(
      Object.entries(services)
        .filter(([, service]) => !built.has(service.image))
        .map(([name, service]) => [name, service.image]),
    ),
  }
}

/**
 * The stack's registry images, fetched WHILE the two application images build.
 *
 * 🔑 ON A FRESH RUNNER THEY CAME DOWN AFTER THE BUILD, one after another: about
 * two minutes of server build, then `up` fetching postgres and the mail catcher,
 * then `run` the ~2 GB Playwright image -- waiting nothing needed to wait for
 * (the deploy arc's ask 12, 2026-09-21). The build can hide most of it.
 *
 * 🔴 ONLY WHAT IS NOT ON THIS MACHINE, which is the rule `up` and `run` already
 * apply -- so this changes WHEN an image arrives, never WHICH one. Fetching a
 * tag that is present would replace it whenever its registry had moved it, and
 * two local runs of the same commits would test different software.
 *
 * ⚠️ AND THAT IS CHECKED HERE, IMAGE BY IMAGE, because the flag that says it
 * does it does not: Compose v5.3.1's `pull --policy missing` fetched a tag that
 * was present -- measured 2026-09-21 by pointing `hello-world:latest` locally at
 * another image, after which that pull moved the tag exactly as `--policy
 * always` did. So only the services whose image is absent are named to it.
 *
 * ⚠️ NEVER FATAL. If it fails, `up` and `run` fetch whatever is still missing,
 * exactly as they did before this existed, and fail loudly there if the
 * registry is unreachable. `--quiet`, so it does not interleave progress bars
 * with the build's output.
 *
 * @param registry `{ service: image }`, from `resolveStack`.
 * @returns `{ done, stop, services }` -- `done` resolves to the pull's exit
 *          code; `services` are the ones it is fetching.
 */
function pullAhead(composeArgs, registry) {
  const services = Object.entries(registry)
    .filter(([, image]) => !imageId(image))
    .map(([service]) => service)
  if (!services.length) return { done: Promise.resolve(0), stop() {}, services }
  const child = spawn('docker', ['compose', 'pull', '--quiet', ...services], {
    ...composeArgs,
    stdio: ['ignore', 'inherit', 'inherit'],
  })
  const done = new Promise((resolve) => {
    child.on('error', (error) => {
      process.stdout.write(`  could not start the pull: ${error.message}\n`)
      resolve(127)
    })
    child.on('exit', (code) => resolve(code ?? 1))
  })
  return { done, stop: () => child.kill(), services }
}

/** One function per stage key, joined to the shared `SUITES` table below. */
const RUNNERS = {
  server(dir) {
      // 🔴 `--no-daemon`, and ONLY because this is an orchestrator.
      //
      // A Gradle daemon outlives the build that started it, by design and
      // usefully so. But this script runs three suites back to back on one
      // machine, and a daemon left holding a JVM through the CLIENT stage is
      // memory the client's Vitest workers then cannot have. Observed: a daemon
      // started at 21:07 was still resident after four passes, and the pass
      // that went red went red at worker startup.
      //
      // ⚠️ NOT `gradlew --stop` after the stage, which is the obvious
      // alternative: `--stop` kills every daemon of that Gradle version on the
      // machine, including one another session has warm for its own work.
      // `--no-daemon` reaches no further than this invocation. It costs this
      // stage its warm JVM, which is the right trade for a wrapper that is not
      // the everyday way to run the suite -- `./gradlew verify` in the
      // repository still gets a daemon.
      const wrapper = platform() === 'win32' ? 'gradlew.bat' : './gradlew'
      return run(join(dir, wrapper), ['verify', '--no-daemon'], { cwd: dir })
  },

  client(dir) {
      // `shell: true` for npm alone, and only on Windows, where npm is a `.cmd`
      // shim that CreateProcess cannot execute directly. Node 20 stopped
      // guessing this for us.
      const npm = (...args) => run('npm', args, { cwd: dir, shell: platform() === 'win32' })

      // 🔴 THE SAME FOUR COMMANDS AS THE GITHUB GATE'S CLIENT LANE, in its order:
      // lint, test, build, budget. This stage ran `npm test` alone, and the gate
      // calls itself this file's twin -- so F12's bundle (670 KiB against a
      // 650 KiB ceiling) was red on the runner and green here (register E14).
      //
      // 🔑 ONLY `npm test`'s code is the stage's exit code, because the merge
      // asserts that code against the test report: a lint failure folded into
      // it would read as "the report and the exit code disagree", which is not
      // what happened. The rest travel as steps, and the merge names them.
      // Unlike the gate, a failed lint does not stop the tests -- here more
      // evidence is free. The budget reads the build, so it runs only after one.
      const steps = []
      const step = (name, ...args) => {
        const code = npm(...args)
        steps.push({ name, code })
        return code
      }
      step('lint', 'run', 'lint')
      const code = npm('test')
      if (step('build', 'run', 'build') === 0) step('budget', 'run', 'budget')
      return { code, steps }
  },

  // Async for one reason: the registry pull runs beside the build (`pullAhead`).
  async suite(dir, opts) {
      // The containerised run is the authoritative one. A native run -- browser
      // on the host, application wherever -- is a debugging convenience and its
      // result is not evidence; register row F9 is a measured reason why.
      //
      // The EMPTY values are the point, not an oversight: empty publishes each
      // service on an ephemeral port, so this stack cannot collide with another
      // session's. See the note at the top of this file for why the PowerShell
      // version needed a temporary file to say this. ⚠️ One per published port --
      // the mail catcher's is the fourth, and a port left out here is a fixed one.
      const env = {
        ...process.env,
        SASBO_STACK_ID: opts.stackId,
        SASBO_DB_HOST_PORT: '',
        SASBO_API_HOST_PORT: '',
        SASBO_CLIENT_HOST_PORT: '',
        SASBO_MAIL_HOST_PORT: '',
        // Set BOTH ways, never inherited: a `test:gate` left exported in somebody's
        // shell would otherwise turn every ordinary run into a determinism pass.
        SASBO_SUITE_SCRIPT: opts.determinism ? 'test:gate' : 'test',
        // One value for the server, which seeds the demonstration users with it,
        // and the suite, which signs them in -- `demoUsersPassword` says why it is made here.
        SASBO_SIGNIN_DEMO_PASSWORD: demoUsersPassword(process.env),
      }
      // Two bindings of ONE invocation, differing only in whether the output is
      // shown or read. 🔑 The `cwd` and that `env` are load-bearing -- the stack
      // id and the three empty host ports decide which stack is addressed and on
      // which ports -- so writing them out a second time for the config read
      // would mean a flag added to one and not the other made the document this
      // file READS a different document from the one it STARTS. The tags below
      // come out of that read, so the drift would be this whole finding
      // reappearing inside its own fix.
      const composeArgs = { cwd: dir, env }
      const compose = (...args) => run('docker', ['compose', ...args], composeArgs)
      const composeRead = (...args) => capture('docker', ['compose', ...args], composeArgs)

      // Validated and resolved before anything starts -- `resolveStack` above
      // says why, and why it is `--format json`.
      const stack = resolveStack(composeRead)
      if (stack.status !== 0) return stack.status
      const { images } = stack

      if (opts.buildImages) {
        const pulling = pullAhead(composeArgs, stack.registry)
        process.stdout.write(
          pulling.services.length
            ? `\n  fetching ${pulling.services.join(', ')} alongside the build -- the images not on this machine\n`
            : '\n  every registry image the stack uses is already on this machine\n',
        )
        const builtImages = buildApplicationImages(dir, opts, images)
        if (builtImages !== 0) {
          pulling.stop()
          await pulling.done
          process.stdout.write('the application images did not build; not running the suite\n')
          return builtImages
        }
        const pulled = await pulling.done
        if (pulled !== 0) {
          process.stdout.write(`  fetching ahead exited ${pulled}; \`up\` and \`run\` fetch what is missing, as before\n`)
        }
      } else {
        // ⚠️ NOT SILENT, deliberately. This is the state X10 was: a lane running
        // images of unknown provenance. It is legitimate in a pipeline, where
        // each application repository is built by its own job -- and it is a
        // qualification on the verdict either way, so it is said out loud.
        process.stdout.write(
          '\n  --no-image-build: running images this run did not build. The suite result is\n' +
            '  evidence about these images, and about the code only insofar as they match it.\n',
        )
      }

      head('the images the suite lane will run')
      reportImages(images)

      if (opts.determinism) {
        process.stdout.write(
          '\n  --determinism: the suite lane runs `npm run test:gate` -- every test three times\n' +
            '  over (--repeat-each=3). The server and client lanes run once, as always.\n',
        )
      }

      const up = compose('up', '-d', '--wait', ...STACK_SERVICES)
      if (up !== 0) {
        process.stdout.write('the stack did not come up; not running the suite against it\n')
        // Kept and torn down here too: an unhealthy service's own log is the
        // only record of WHY, and this path used to return with the stack up.
        keepStackLogs(composeArgs, opts)
        compose('down', '-v')
        return up
      }
      let suiteExit
      try {
        suiteExit = compose('run', '--rm', 'suite')
        return suiteExit
      } finally {
        if (suiteExit !== 0) keepStackLogs(composeArgs, opts)
        giveOutputBack(compose)
        // Always torn down, including when the suite failed -- a stack left up
        // is the next session's mystery. Its exit code is deliberately NOT this
        // stage's result: `return` inside `try` fixes the value before the
        // `finally` runs, so a teardown that fails cannot overwrite the suite's
        // own verdict with its own.
        compose('down', '-v')
      }
  },
}

/**
 * The stack's own logs, kept when the suite lane went red, BEFORE `down -v`
 * removes the containers that hold them.
 *
 * 🔴 WHY: on 2026-09-21 (p17) the server's log was the only record of why three
 * captures outran the page -- the POST times, PDFBox's fallback-font warning,
 * the gap before the letters left (register row A76) -- and it survived only
 * because it was read by hand while the suite still ran. The teardown took it
 * with the containers. The deploy arc's GitHub gate asked for the same thing:
 * a CI reader has nothing else.
 *
 * ⚠️ WRITTEN BY THE CHILD, NOT CAPTURED. A stack's logs run to megabytes and
 * `spawnSync` buffers one, so `capture` would lose them to ENOBUFS; the file
 * descriptor is handed to `docker compose logs` instead. Only on a red lane: a
 * green one needs no evidence, and the file would be noise beside its reports.
 *
 * 🔴 IT NEVER THROWS, AND THAT IS THE LOAD-BEARING PART. It runs inside the
 * suite lane's `finally`, where an exception does two things at once: it skips
 * the `down -v` after it, and it REPLACES the suite's own exit code with a
 * stack trace, because a `finally` that throws overrides the `return` it
 * follows. And it can throw on Linux, where Docker Desktop's file sharing does
 * not smooth things over: the suite container runs as root, so `reports/` on
 * the bind mount is root-owned and a host-side `mkdir` there meets EACCES (the
 * deploy arc's P6 review, 2026-09-21). Evidence must never cost the verdict.
 *
 * 🔑 SO THE LOGS GO BESIDE THE MERGED REPORT, not into the suite's `reports/`:
 * `<the parent of --out>/stack/compose-logs.txt` -- `<workspace>/reports/stack/`
 * with the default `--out`. That directory is the HOST's (the merge writes its
 * sibling), so a Linux runner can write it, and a run that sends `--out` outside
 * a synced folder sends the megabytes of logs there too.
 */
function keepStackLogs(composeArgs, opts) {
  let fd
  try {
    const dir = join(dirname(resolve(workspace(), opts.out)), 'stack')
    mkdirSync(dir, { recursive: true })
    const file = join(dir, 'compose-logs.txt')
    fd = openSync(file, 'w')
    const kept = spawnSync('docker', ['compose', 'logs', '--no-color', '--timestamps'], {
      ...composeArgs,
      stdio: ['ignore', fd, fd],
    })
    process.stdout.write(
      kept.status === 0
        ? `  the stack's own logs are kept in ${file}\n`
        : `  the stack's logs could not be kept (docker compose logs: ${kept.error?.message ?? `exit ${kept.status}`})\n`,
    )
  } catch (error) {
    process.stdout.write(`  the stack's logs could not be kept: ${error.message}\n`)
  } finally {
    if (fd !== undefined) closeSync(fd)
  }
}

/**
 * What the suite container writes through the `.:/suite` mount, handed back to
 * whoever ran this -- on Linux.
 *
 * 🔴 THE CONTAINER IS ROOT, and on Linux a bind mount translates nothing, so
 * `reports/`, `test-results/` and the two `.features-gen` directories come out
 * ROOT's. The next run's `clearReports` then meets EACCES (`force` forgives
 * only ENOENT), and the stage dies before its tests -- which a reader takes for
 * a failed test. Found by the deploy arc reading this file (2026-09-21, its
 * ask 11); its GitHub gate's own `sudo chown` covered its runs and nobody
 * else's, and becomes a no-op after this.
 *
 * 🔑 THE CONTAINER DOES IT, because root is the only party allowed to: one more
 * `run --rm --no-deps` of the same service, `chown -R` to this process's
 * uid:gid through the same mount. Linux only, and only for a caller who is not
 * root -- Docker Desktop's file sharing already maps ownership, and root owns
 * the files either way. Never fatal: it is housekeeping after the verdict.
 */
// `.features-gen*` is a glob the container's shell expands: every directory the
// suite compiles features into carries that prefix (its `GENERATED_PREFIX`),
// and a list of them was how `.features-gen-acb` got missed. Unmatched, the
// pattern stays literal and the `-e` test skips it.
const OUTPUT_DIRS = ['reports', 'test-results', 'playwright-report', 'blob-report', '.features-gen*']

// The host's facts are parameters, defaulted to this process's, so the Linux
// branch can be driven from any machine.
function giveOutputBack(compose, host = { os: platform(), uid: process.getuid?.(), gid: process.getgid?.() }) {
  if (host.os !== 'linux' || typeof host.uid !== 'number' || host.uid === 0) return
  const owner = `${host.uid}:${host.gid}`
  const script = `for d in ${OUTPUT_DIRS.join(' ')}; do if [ -e "$d" ]; then chown -R ${owner} "$d" || exit 1; fi; done`
  const code = compose('run', '--rm', '--no-deps', '--entrypoint', 'sh', 'suite', '-c', script)
  process.stdout.write(
    code === 0
      ? `  the suite's output is ${owner}'s again\n`
      : `  the suite's output could not be handed back to ${owner} (exit ${code}); the next run may not clear it\n`,
  )
}

/**
 * The stages, in order: the shared table joined to the runner for each key.
 *
 * This list is the single source for the `--only` validation, its default, the
 * run order and the closing summary -- four separate enumerations of the same
 * three keys in the PowerShell version, so adding a stage meant getting four
 * edits right.
 */
const STAGES = SUITES.map((s) => ({
  key: s.key,
  title: `${s.label}  --  ${s.command}`,
  run: RUNNERS[s.key],
}))

/**
 * The client's copies of the API contract artifacts must equal the server's
 * originals.
 *
 * 🔴 WHY THIS HAS NO HOME BUT HERE. The client asserts that every path its
 * services call appears in its copy of `api-surface.txt`, and the server
 * asserts that ITS copy is exactly what the application routes. Neither can
 * assert the two files are the SAME file: the client may not read the server's
 * checkout (a suite that reaches into a sibling runs only where both happen to
 * sit side by side), and the refresh between them is a documented manual copy.
 *
 * So the client's contract guard is only as good as a file somebody remembered
 * to re-copy. Stale, it passes for a path that now 404s and reddens for one
 * that works -- and it does that quietly, because a stale artifact looks
 * exactly like a current one.
 *
 * 🔑 This orchestrator is the ONLY thing that sees both checkouts at once
 * without either repository depending on the other, which is what makes the
 * comparison possible here and nowhere else.
 *
 * ⚠️ IT ADDS NO NEW EXIT VALUE. The exit contract is 0 / 1 / 2 and is stated in
 * three places; a fourth meaning would have to be threaded through all of them.
 * Drift is a failure, so it reports through the existing `1`.
 *
 * Returns `{ drifted, artifacts }`, one `{ name, state, line }` per artifact --
 * `state` `ok`, `drift` or `skip`, and `line` exactly the line printed for it,
 * without its indent -- so a pipeline reads which file drifted as data rather
 * than by matching this function's wording (the deploy arc's ask 10).
 */
function checkContractArtifacts(opts) {
  // `debit-window.json` since both mains carry it (ACB, 2026-09-22): the run-window rule the client's
  // `debitDate.js` is tested against, written by the same `generateApiSurface` as the other two. A triple
  // from before ACB holds it on neither side, which is a SKIP; one side alone is DRIFT (E9).
  // `month-end-examples.json` since month end's 01.2 (merged 2026-09-30): the server's golden answers, from which
  // the client's handlers are built -- a fourth file that can drift between the two sides (BR55, MD48).
  const ARTIFACTS = ['api-surface.txt', 'api-vocabulary.json', 'debit-window.json', 'month-end-examples.json']
  const serverDir = checkout('server', opts)
  const clientDir = checkout('client', opts)

  let drifted = false
  const artifacts = []
  const say = (name, state, line) => {
    artifacts.push({ name, state, line })
    process.stdout.write(`  ${line}\n`)
  }
  for (const name of ARTIFACTS) {
    const original = join(serverDir, name)
    const copy = join(clientDir, 'src', 'test', name)

    // 🔴 ONE SIDE HOLDING IT AND THE OTHER NOT IS THE STALEST STATE THERE IS, NOT A
    // NEUTRAL ONE -- register row E9. This read `!existsSync(original) || !existsSync(copy)`
    // and reported SKIP for all three cases, so the client missing a file the server
    // publishes looked exactly like a pair of repositories that never had one. That is
    // how the client's `api-surface.txt` sat a route behind the server's on 2026-09-16/17
    // while this line said SKIP. Absent on BOTH sides is still a skip: there is nothing to
    // compare and nothing to be stale about.
    const hasOriginal = existsSync(original)
    const hasCopy = existsSync(copy)
    if (!hasOriginal && !hasCopy) {
      say(name, 'skip', `SKIP   ${name} -- in neither checkout`)
      continue
    }
    if (!hasOriginal || !hasCopy) {
      drifted = true
      const held = hasOriginal ? 'the server' : 'the client'
      const lacks = hasOriginal ? 'the client' : 'the server'
      say(name, 'drift', `DRIFT  ${name} -- ${held} has it, ${lacks} does not`)
      continue
    }
    // Newlines normalised before comparing: the two repositories are checked out
    // on the same machine with the same autocrlf setting today, and that is not
    // a property worth making this depend on. Both sides already assert their
    // own file's line endings separately.
    const norm = (p) => readFileSync(p, 'utf8').replace(/\r\n/g, '\n')
    if (norm(original) === norm(copy)) {
      say(name, 'ok', `OK     ${name}`)
    } else {
      drifted = true
      say(name, 'drift', `DRIFT  ${name}`)
    }
  }

  if (drifted) {
    process.stdout.write(
      '\n  The client is carrying a STALE copy of an API contract artifact.\n' +
        '  Its contract guard is therefore checking an API this server no longer has:\n' +
        '  it will pass for a path that now 404s, and redden for one that works.\n\n' +
        '  Regenerate and re-copy:\n' +
        '    (server) ./gradlew generateApiSurface\n' +
        `    cp <server>/api-surface.txt      <client>/src/test/\n` +
        `    cp <server>/api-vocabulary.json  <client>/src/test/\n` +
        `    cp <server>/debit-window.json    <client>/src/test/\n`,
    )
  }
  return { drifted, artifacts }
}

function parseArgv(argv) {
  // 🔴 `buildImages` DEFAULTS TO TRUE, and the default is the whole fix. The
  // failure mode of not building is a plausible number about the wrong software
  // and no indication that anything was wrong -- so the safe state has to be the
  // one nobody has to remember to ask for.
  const opts = { only: [], out: 'reports/merged', stackId: 'testall', dirs: {}, buildImages: true }
  const keys = STAGES.map((s) => s.key)
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--out') opts.out = argv[++i]
    else if (a === '--stack-id') opts.stackId = argv[++i]
    else if (a === '--no-image-build') opts.buildImages = false
    else if (a === '--contract-only') opts.contractOnly = true
    else if (a === '--determinism') opts.determinism = true
    else if (a === '--help' || a === '-h') opts.help = true
    else if (a === '--only') {
      const key = argv[++i]
      if (!keys.includes(key)) {
        process.stderr.write(`test-all: --only expects one of ${keys.join(', ')}\n`)
        process.exit(2)
      }
      opts.only.push(key)
    } else if (a.startsWith('--') && keys.includes(a.slice(2))) {
      opts.dirs[a.slice(2)] = argv[++i]
    } else {
      process.stderr.write(`test-all: unknown option ${a}\n`)
      process.exit(2)
    }
  }
  if (opts.only.length === 0) opts.only = keys
  // 🔴 THE SUITE THIS RUN TESTS IS THE ONE THIS FILE BELONGS TO, unless told
  // otherwise -- as `demo.mjs` has always done. The default used to be the
  // WORKSPACE's `sasbo-automation-testing/`, the primary checkout, so this tool
  // run from a worktree built the worktree's images, then ran main's suite
  // against them: p20 and p21 (2026-09-21) each tested hub main's 116 tests
  // while the worktree under review had 128, and both reds were read as the
  // worktree's until their line numbers were matched to main's file (E19).
  if (!opts.dirs.suite) opts.dirs.suite = resolve(HERE, '..')
  // Refused rather than ignored: a "determinism pass" in which the suite never ran
  // would read as one that held.
  if (opts.determinism && (opts.contractOnly || !opts.only.includes('suite'))) {
    process.stderr.write('test-all: --determinism repeats the suite lane, so it needs that lane in scope and no --contract-only\n')
    process.exit(2)
  }
  return opts
}

async function main() {
  const opts = parseArgv(process.argv.slice(2))
  if (opts.help) {
    process.stdout.write(
      'Usage: node tools/test-all.mjs [--only server|client|suite]...\n' +
        '                              [--server <dir>] [--client <dir>] [--suite <dir>]\n' +
        '                              [--out <dir>] [--stack-id <id>] [--no-image-build]\n' +
        '                              [--determinism]\n' +
        '       node tools/test-all.mjs --contract-only [--server <dir>] [--client <dir>] [--out <dir>]\n\n' +
        '--determinism runs the suite lane as `npm run test:gate`: every test three times.\n' +
        'It needs the suite lane in scope; the other lanes still run once.\n\n' +
        `Checkouts are resolved under ${findWorkspace(HERE) ?? '(no ancestor holds all three)'}\n` +
        '(override with SASBO_WORKSPACE, or per-repository with the flags above).\n\n' +
        'The suite stage BUILDS the two application images from the server and client\n' +
        'checkouts before starting the stack, because the compose file consumes them by\n' +
        'tag and builds neither. --no-image-build skips that, for a pipeline where each\n' +
        'application repository is built by its own job; the run then says so, because a\n' +
        'lane running images of unknown provenance must not do it quietly.\n',
    )
    return 0
  }

  // 🔑 THE CONTRACT CHECK ALONE -- the deploy arc's P6 ask 3. Proving that the gate
  // reddens on contract drift needed a full suite lane (~40 billed runner minutes);
  // the comparison itself needs two checkouts and a second. No stage runs and no
  // image is built. The exit is the comparison's own -- 0 clean, 1 drift -- and a
  // result.json lands where the report would, scoped to the contract, so a pipeline
  // reads the answer as data rather than from this output.
  if (opts.contractOnly) {
    head('the contract artifacts, alone -- no stage runs')
    const { drifted: drift, artifacts } = checkContractArtifacts(opts)
    const code = drift ? 1 : 0
    const outDir = resolve(workspace(), opts.out)
    mkdirSync(outDir, { recursive: true })
    const result = {
      schema: 1,
      scope: 'contract',
      verdict: { code, headline: drift ? 'FAILED — contract drift' : 'PASSED — the contract artifacts agree' },
      inScopePassed: !drift,
      contract: drift ? 'drift' : 'clean',
      // Which file, as data: [{ name, state: ok|drift|skip, line }] -- see
      // `checkContractArtifacts`. merge-reports writes `null` here: it is told
      // the state and never the files.
      contractArtifacts: artifacts,
      failuresTotal: 0,
      failures: [],
      sections: [],
    }
    writeFileSync(join(outDir, 'result.json'), `${JSON.stringify(result, null, 2)}\n`, 'utf8')
    process.stdout.write(`\ntest-all exiting ${code} (contract only; result.json in ${outDir})\n`)
    return code
  }

  // What this run tests, by checkout AND commit, before anything runs -- so a
  // run's log answers "which code?" without anyone reconstructing it (E19).
  head('the checkouts this run tests')
  for (const stage of STAGES) {
    if (!opts.only.includes(stage.key)) continue
    const dir = checkout(stage.key, opts)
    process.stdout.write(`  ${stage.key.padEnd(7)} ${commitOf(dir).padEnd(10)} ${dir}\n`)
  }

  const exitCodes = {}
  // A stage returns its tests' exit code, or `{ code, steps }` when it also runs
  // commands outside its tests (the client stage) -- see `merge-reports --step`.
  const stepCodes = {}
  for (const stage of STAGES) {
    if (!opts.only.includes(stage.key)) continue

    const dir = checkout(stage.key, opts)
    head(stage.title)

    if (!existsSync(dir)) {
      process.stdout.write(`no such directory: ${dir}\n`)
      exitCodes[stage.key] = 127
    } else {
      clearReports(dir)
      const ran = await stage.run(dir, opts)
      exitCodes[stage.key] = typeof ran === 'number' ? ran : ran.code
      if (typeof ran !== 'number') stepCodes[stage.key] = ran.steps
    }
    const steps = (stepCodes[stage.key] || []).map((x) => `, ${x.name} ${x.code}`).join('')
    process.stdout.write(`${stage.key} exited ${exitCodes[stage.key]}${steps ? ` (outside its tests${steps})` : ''}\n`)
  }

  head('the API contract artifacts')
  const contractDrift = checkContractArtifacts(opts).drifted

  head('merging the three reports')

  // `--contract` puts the comparison's answer into result.json as its own flag,
  // so a reader of a red run can say "drift" rather than only "failed".
  const mergeArgs = [MERGE, '--out', opts.out, '--contract', contractDrift ? 'drift' : 'clean']
  for (const stage of STAGES) {
    if (opts.dirs[stage.key]) mergeArgs.push(`--${stage.key}`, opts.dirs[stage.key])
    // 🔴 WHICH STAGES WERE IN SCOPE, told rather than left to be inferred. Without
    // it the merge reads whatever report is lying in an excluded stage's directory
    // and reports it as this run's result -- measured 2026-09-09, a `--only suite`
    // run announced "client PASSED 8 tests" from a file written two hours earlier
    // by a different command. `clearReports` cannot cover that case, because it
    // never touches a directory this invocation is not using.
    if (opts.only.includes(stage.key)) mergeArgs.push('--only', stage.key)
    // The exit code is half the claim, handed over so the REPORT can assert the
    // two halves against each other -- see `merge-reports.mjs`'s header.
    if (stage.key in exitCodes) mergeArgs.push('--exit', `${stage.key}:${exitCodes[stage.key]}`)
    for (const x of stepCodes[stage.key] || []) mergeArgs.push('--step', `${stage.key}:${x.name}:${x.code}`)
  }
  // 🔴 SASBO_WORKSPACE IS PASSED EXPLICITLY, and leaving it out was the same bug
  // this file's own walk-up exists to prevent -- one file over.
  //
  // `merge-reports.mjs` resolves the three checkouts itself, and unset it falls
  // back to a FIXED two-level hop from its own location. In a worktree that
  // lands inside `.worktrees/`, so the stages would run here against the walked
  // root while the merge looked somewhere else entirely: three suites execute,
  // the report says all three DID NOT RUN, and the exit code is a plausible 2.
  const mergeExit = run(process.execPath, mergeArgs, {
    cwd: workspace(),
    env: { ...process.env, SASBO_WORKSPACE: workspace() },
  })

  process.stdout.write('\n')
  for (const stage of STAGES) {
    const state =
      stage.key in exitCodes
        ? `process exit ${exitCodes[stage.key]}`
        : 'not run in this invocation'
    process.stdout.write(`  ${stage.key.padEnd(26)} ${state}\n`)
  }
  // Drift reports through the EXISTING `1`. The exit contract is 0 / 1 / 2 and
  // is stated in three places; a fourth meaning would have to be threaded
  // through all of them. If the merge already found a failure that value is
  // unchanged; if everything else ran green, a stale contract artifact is still
  // a failure and must not be reported as a pass.
  //
  // 🔴 AND IT OUTRANKS A `2`, as a failing test does in the merge itself
  // (failures before absences). This folded drift only into a `0`, so a
  // `--only client` run with drift exited 2 -- "something did not run" --
  // while the GitHub gate called the same facts 1 (the deploy arc's truth-table
  // row V18). Any other code is the merge's own failure and stands.
  const exitCode = contractDrift && (mergeExit === 0 || mergeExit === 2) ? 1 : mergeExit
  if (contractDrift) {
    process.stdout.write('  API contract artifacts     DRIFTED (see above)\n')
  }

  process.stdout.write(`\ntest-all exiting ${exitCode}\n`)
  return exitCode
}

/**
 * The pieces `tools/demo.mjs` reuses, beside `STACK_SERVICES` and
 * `resolveStack` above: the image build with both of X10/X11's corrections and
 * the `run`/`capture`/`head`/`reportImages` quartet. Exported rather than
 * copied, because a second statement of how the client image is built is a
 * second place to forget `VITE_API_BASE_URL=` -- which is the whole of X11.
 * `giveOutputBack` and `pullAhead` are not the demo's: they are exported so they
 * can be driven on their own -- the first's Linux branch from a machine that is
 * not Linux, the second against a compose file of its own.
 */
export { run, capture, head, reportImages, buildApplicationImages, giveOutputBack, pullAhead }

// Only as the entry point. An `import` of the exports above must not start
// three suites. `import.meta.main` is Node 24.2+, and package.json pins >=24.
if (import.meta.main) process.exit(await main())
