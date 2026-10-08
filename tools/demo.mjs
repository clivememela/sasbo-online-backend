/**
 * `npm run demo` -- the whole sequence, in order, then a browser you can watch.
 *
 * What it does, and the order is the point:
 *
 *   1. `docker-compose.yml` resolved, so a file that cannot start refuses in a
 *      second, before anything slow.
 *   2. Docker up. If the daemon is not answering and Docker Desktop is where it
 *      usually is on Windows, it is started and waited for -- "Docker is down"
 *      here almost always means it was never launched. Elsewhere, a sentence.
 *   3. The two application images built the orchestrator's way -- the server
 *      from its own Dockerfile, the client with `VITE_API_BASE_URL=` EMPTY --
 *      by the same function `test-all.mjs` uses. Not copied: X10 and X11 are
 *      why a second statement of that build would be a second place to get it
 *      wrong.
 *   4. The compose stack up on its host ports, under its own stack id so it
 *      never collides with a gate's `testall` stack.
 *   5. Playwright against it, in the mode asked for -- the time-travel UI by
 *      default -- with `SASBO_CLIENT_URL` / `SASBO_API_URL` pointed at the
 *      published ports, so the same feature files and page objects run as in
 *      the gate. No separate demo suite exists; see the README's "Watching it
 *      run".
 *   6. The stack torn down when the browser closes, unless `--keep`.
 *
 * ⚠️ A native run is a debugging and demonstration convenience. The verdict is
 * still the containerised run (`node tools/test-all.mjs`); register row F9 is a
 * measured reason why a browser on the host is not evidence.
 */
import { spawn, spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { platform } from 'node:os'
import { dirname, resolve } from 'node:path'
import { setTimeout as sleep } from 'node:timers/promises'
import { fileURLToPath } from 'node:url'
import { SUITES } from './merge-reports.mjs'
import {
  STACK_SERVICES,
  buildApplicationImages,
  capture,
  demoUsersPassword,
  head,
  reportImages,
  resolveStack,
  run,
} from './test-all.mjs'

const SUITE_DIR = resolve(dirname(fileURLToPath(import.meta.url)), '..')

/** Its own compose project, so it never shares a namespace with a gate's stack. */
const STACK_ID = 'demo'

/**
 * The password a KEPT demo stack's demonstration users were seeded with, read from its server container -- or null
 * when no stack of this id is running.
 *
 * 🔴 WITHOUT THIS, `--keep` THEN A SECOND RUN SIGNED NOBODY IN. The rehearsal pattern is to start the stack with
 * `--keep` and run again in front of the audience; a fresh password on the second run would recreate the server with
 * it while the database still holds the users seeded with the first, and every journey that signs in would fail on
 * the stage. Asked of Docker, never of the image: the server image need carry no shell for this.
 */
function keptStackPassword(composeArgs) {
  const quiet = { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }
  const listed = spawnSync('docker', ['compose', 'ps', '-q', 'server'], { ...composeArgs, ...quiet })
  const container = (listed.stdout ?? '').trim().split(/\r?\n/)[0]
  if (listed.status !== 0 || !container) return null
  const inspected = spawnSync('docker', ['inspect', '--format', '{{range .Config.Env}}{{println .}}{{end}}', container], quiet)
  const line = (inspected.stdout ?? '').split(/\r?\n/).find((entry) => entry.startsWith('SASBO_SIGNIN_DEMO_PASSWORD='))
  return line?.slice('SASBO_SIGNIN_DEMO_PASSWORD='.length) || null
}

/** Playwright mode -> the npm script that already expresses it. One place. */
const MODES = {
  ui: 'test:ui',
  headed: 'test:headed',
  live: 'test:live',
  headless: 'test',
}

/** Where Docker Desktop lives on Windows unless told otherwise. */
const DOCKER_DESKTOP =
  process.env.SASBO_DOCKER_DESKTOP ?? 'C:\\Program Files\\Docker\\Docker\\Docker Desktop.exe'

/** The checkouts that can be pointed elsewhere: every suite but this one. */
const CHECKOUT_FLAGS = SUITES.map((s) => s.key).filter((key) => key !== 'suite')

function parseArgv(argv) {
  const opts = { mode: 'ui', keep: false, buildImages: true, dirs: {} }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    const flag = a.startsWith('--') ? a.slice(2) : null
    if (a === '--keep') opts.keep = true
    else if (a === '--no-image-build') opts.buildImages = false
    else if (a === '--help' || a === '-h') opts.help = true
    else if (flag && Object.hasOwn(MODES, flag)) opts.mode = flag
    else if (flag && CHECKOUT_FLAGS.includes(flag)) opts.dirs[flag] = argv[++i]
    else {
      process.stderr.write(`demo: unknown option ${a}\n`)
      process.exit(2)
    }
  }
  return opts
}

/**
 * Is the daemon answering. Quiet on "no", because "no" is an expected answer
 * here and the sentence that follows it is the one worth reading -- the shape
 * `imageId` uses, for the reason `capture`'s docblock gives. `version` rather
 * than `info`: it asks the daemon one thing, and `info` gathers a page of
 * plugins and warnings this throws away, at three times the cost.
 */
function dockerAnswers() {
  return spawnSync('docker', ['version', '--format', '{{.Server.Version}}'], { stdio: 'ignore' }).status === 0
}

/**
 * Bring the daemon up if it is not, where that is possible.
 *
 * Only Docker Desktop on Windows is started automatically; anywhere else the
 * message says what to start, because guessing at a launcher is worse than a
 * sentence. Probe first, then wait, backing off: a daemon that is up already
 * costs one probe, and a cold Desktop start is noticed within seconds of
 * answering rather than at the next fixed tick.
 */
async function ensureDocker() {
  if (dockerAnswers()) return true

  if (platform() !== 'win32' || !existsSync(DOCKER_DESKTOP)) {
    process.stdout.write('Docker is not answering. Start Docker Desktop (or the daemon) and run this again.\n')
    return false
  }

  process.stdout.write('Docker is not answering; starting Docker Desktop...\n')
  spawn(DOCKER_DESKTOP, [], { detached: true, stdio: 'ignore' }).unref()

  const deadline = Date.now() + 180_000
  for (let wait = 500; Date.now() < deadline; wait = Math.min(wait * 2, 5_000)) {
    await sleep(wait)
    if (dockerAnswers()) {
      process.stdout.write('Docker is up.\n')
      return true
    }
  }
  process.stdout.write('Docker Desktop did not answer within three minutes. Check it, then run this again.\n')
  return false
}

/**
 * Poll one service's healthcheck status, as Docker holds it, until it reads
 * `healthy` or the deadline passes. `compose ps --format json` prints one
 * object per line on current Compose and an array on older ones; both are read.
 */
async function healthyWithin(composeRead, service, budgetMs) {
  const deadline = Date.now() + budgetMs
  while (Date.now() < deadline) {
    const listed = composeRead('ps', '--format', 'json')
    const text = listed.stdout.trim()
    const rows = text.startsWith('[')
      ? JSON.parse(text)
      : text.split('\n').filter(Boolean).map((line) => JSON.parse(line))
    const row = rows.find((r) => r.Service === service)
    const health = row?.Health ?? '(not running)'
    process.stdout.write(`  ${service}: ${health}\n`)
    if (health === 'healthy') return true
    if (!row) return false
    await sleep(5_000)
  }
  return false
}

/** The teardown line for `--keep`, in the shell the reader is actually in. */
function teardownHint() {
  return platform() === 'win32'
    ? `$env:SASBO_STACK_ID='${STACK_ID}'; docker compose down -v`
    : `SASBO_STACK_ID=${STACK_ID} docker compose down -v`
}

async function main() {
  const opts = parseArgv(process.argv.slice(2))
  if (opts.help) {
    process.stdout.write(
      'Usage: node tools/demo.mjs [--ui|--headed|--live|--headless] [--keep] [--no-image-build]\n' +
        `                          ${CHECKOUT_FLAGS.map((k) => `[--${k} <dir>]`).join(' ')}\n\n` +
        'Builds both application images, starts the compose stack, runs Playwright against\n' +
        'it in the chosen mode (the UI by default), and tears the stack down afterwards.\n',
    )
    return 0
  }

  // Same shape as the gate's suite stage, and deliberately NOT the same stack id
  // or ports: the gate publishes nothing (empty host ports), this publishes the
  // compose defaults so a browser on the host can reach them. Unset here means
  // "the defaults", and an operator's own override still wins.
  const env = { ...process.env, SASBO_STACK_ID: STACK_ID }
  const composeArgs = { cwd: SUITE_DIR, env }
  // The demonstration users' password (`demoUsersPassword`): one value for the server and for Playwright. A KEPT
  // stack's own first -- its users were seeded with it, and a new one would only make every sign-in fail.
  env.SASBO_SIGNIN_DEMO_PASSWORD = process.env.SASBO_SIGNIN_DEMO_PASSWORD || keptStackPassword(composeArgs) || demoUsersPassword({})
  const compose = (...args) => run('docker', ['compose', ...args], composeArgs)
  const composeRead = (...args) => capture('docker', ['compose', ...args], composeArgs)

  // Resolved before Docker is even asked for: `compose config` needs no daemon,
  // and a refusal here costs a second where a Desktop start costs minutes.
  const stack = resolveStack(composeRead)
  if (stack.status !== 0) return stack.status
  const { images, ports } = stack

  // The mail catcher too: a capture is a send since server 99936a5 (A64), and the
  // scenarios read the caught letter from the host through its published port.
  if (!ports.client || !ports.api || !ports.mail) {
    process.stdout.write(
      'docker-compose.yml resolved without a published host port for the client, the server or\n' +
        'the mail catcher; the host could not reach this stack, so it is not started.\n' +
        '(The SASBO_*_HOST_PORT variables must be UNSET or numeric here, not empty.)\n',
    )
    return 1
  }

  if (!(await ensureDocker())) return 1

  if (opts.buildImages) {
    const built = buildApplicationImages(SUITE_DIR, opts, images)
    if (built !== 0) {
      process.stdout.write('the application images did not build; not starting the stack\n')
      return built
    }
  } else {
    process.stdout.write(
      '\n  --no-image-build: running images this run did not build. What you watch is\n' +
        '  those images, and the code only insofar as they match it.\n',
    )
  }

  head('the images the stack will run')
  reportImages(images)

  const target = {
    SASBO_CLIENT_URL: `http://localhost:${ports.client}`,
    SASBO_API_URL: `http://localhost:${ports.api}`,
    SASBO_MAIL_URL: `http://localhost:${ports.mail}`,
  }

  // Ctrl+C reaches the child too; this process survives it so the teardown in
  // `finally` still runs -- including a Ctrl+C during `up`, the slowest step
  // before Playwright and the one most often interrupted. Registered once and
  // never removed: nothing outlives `main`.
  process.on('SIGINT', () => {})
  try {
    // ⚠️ ONE MORE WAIT BEFORE GIVING UP, and both halves of it are measured.
    // Compose's budget for the server is about three minutes (30 s grace, 30
    // checks at 5 s). Rehearsing on this host, Spring Boot took 3-4 minutes to
    // start (the DEBUG root logger -- see the compose file -- and a starved
    // host), `up --wait` gave up seconds before the server turned healthy, the
    // demo refused, and the kept stack was green a minute later. The gate is
    // right to fail fast on a starved host (F11); a demo that refuses in front
    // of an audience while its server is thirty seconds from ready is not.
    //
    // 🔴 NOT A SECOND `up --wait`: that was tried, and it returned "unhealthy"
    // in 1.2 s. Compose reports a container already marked unhealthy at once;
    // it does not wait for the healthcheck to turn. The check keeps running
    // every 5 s regardless, so this polls the status Docker actually holds
    // until it turns, then lets `up --wait` finish the start (the client only
    // starts once the server is healthy).
    let up = compose('up', '-d', '--wait', ...STACK_SERVICES)
    if (up !== 0) {
      head(`the stack was not healthy within compose's budget; watching the server's healthcheck`)
      if (await healthyWithin(composeRead, 'server', 240_000)) {
        up = compose('up', '-d', '--wait', ...STACK_SERVICES)
      }
    }
    if (up !== 0) {
      process.stdout.write('the stack did not come up\n')
      return up
    }

    head(`Playwright (${opts.mode}) against ${target.SASBO_CLIENT_URL}  --  API ${target.SASBO_API_URL}`)

    // 🔴 ONE WORKER UNLESS TOLD OTHERWISE, and the number is measured, not chosen.
    // `playwright.config.js` derives its worker count from `SASBO_SUITE_CPUS`;
    // inside the suite container that variable is also the CPU quota, so the
    // two agree by construction. Natively nothing sets it, every worker gets a
    // Chromium of its own, and they share the host with the Docker VM that is
    // serving them. Measured 2026-09-09 on the same images: the default worker
    // count reddened three to four bank-listing scenarios per run -- the API's
    // first byte for /api/banks/branches took 9.4 s against a 10 s expectation,
    // while the same request idles at under a second -- and one worker passed
    // 87 / 0 failed, the containerised gate's own figures. A red the gate does
    // not produce is not something to watch, so this asks for the run that
    // matches the gate. Set SASBO_SUITE_CPUS yourself to override it.
    return run('npm', ['run', MODES[opts.mode]], {
      cwd: SUITE_DIR,
      shell: platform() === 'win32',
      env: { SASBO_SUITE_CPUS: '1', ...env, ...target },
    })
  } finally {
    if (opts.keep) {
      process.stdout.write(
        `\nstack left up (--keep): client ${target.SASBO_CLIENT_URL}, API ${target.SASBO_API_URL}\n` +
          `tear it down with:  ${teardownHint()}\n`,
      )
    } else {
      compose('down', '-v')
    }
  }
}

process.exit(await main())
