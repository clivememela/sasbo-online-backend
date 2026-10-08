import { request as httpRequest } from 'node:http'
import { hostname, platform } from 'node:os'

/**
 * Taking the backend down, from inside a scenario.
 *
 * WHY THIS EXISTS AT ALL, GIVEN PHASE 5 SAID "NO NEW HARNESS CODE"
 * The three-state rule -- loading, empty, failed-with-retry -- has one state
 * that cannot be asserted without a real failure. Mocking the response tests
 * the component's handling of a fabricated error; it does not test that a real
 * network failure reaches the real component through a real proxy. The plans
 * settled this before either phase was cut: *"`docker compose stop server`:
 * repeatable, scriptable, and something a scenario can own rather than a step a
 * human remembers."* Phase 5 built the stack that makes it possible and stopped
 * there, so this is the one piece of infrastructure Phase 6 adds, and it is
 * named as an exception rather than slipped in.
 *
 * WHY THE ENGINE API AND NOT THE `docker` CLI
 * The suite runs in Playwright's official image, which has no Docker CLI and
 * must stay pinned and unmodified -- installing one at run time would be a
 * second thing to keep in step with the image for no gain. The Engine's HTTP
 * API over its own socket needs nothing but Node.
 *
 * 🔴 THIS GIVES THE SUITE CONTAINER CONTROL OF THE DOCKER DAEMON. Mounting the
 * socket is not a small permission, and it is granted here deliberately, for a
 * local test stack, with the surface kept to the three calls below. It is worth
 * knowing before this file is copied anywhere that matters.
 */

/**
 * Where the daemon listens.
 *
 * ⚠️ Windows has no unix socket. Node accepts a named pipe as a `socketPath`,
 * which is what makes a NATIVE run on a developer's machine work at all -- and
 * a native run is exactly when someone is debugging this scenario.
 *
 * 🔴 AND macOS DOES NOT HAVE `/var/run/docker.sock` BY DEFAULT. Docker Desktop
 * creates it only when "Allow the default Docker socket to be used" is enabled
 * in Settings > Advanced, and that setting is OFF on a fresh install. The
 * user-scoped socket exists either way, at `~/.docker/run/docker.sock`, so the
 * escape hatch is a variable rather than a support conversation:
 *
 *     SASBO_DOCKER_SOCKET=$HOME/.docker/run/docker.sock npm test
 *
 * `DOCKER_HOST` is honoured too, because a developer whose daemon is not in the
 * default place has usually already set it and should not have to learn a
 * second name for the same fact. Only its `unix://` form means anything here:
 * this module speaks to the socket directly and a `tcp://` daemon would need a
 * different transport, so that case is refused rather than half-supported.
 *
 * ⚠️ Note that this is about the HOST. Inside the suite container the socket is
 * always at the standard path, because `docker-compose.yml` bind-mounts it
 * there -- and on macOS that bind mount is exactly what the Desktop setting
 * above governs.
 */
export function dockerSocket(env = process.env, plat = platform()) {
  if (env.SASBO_DOCKER_SOCKET) return env.SASBO_DOCKER_SOCKET

  const host = env.DOCKER_HOST
  if (host) {
    if (!host.startsWith('unix://')) {
      throw new Error(
        `DOCKER_HOST is set to ${JSON.stringify(host)}, and this suite talks to the Docker\n` +
          `socket directly rather than over TCP. Point SASBO_DOCKER_SOCKET at a socket path\n` +
          `instead, or unset DOCKER_HOST to use the default for this platform.`,
      )
    }
    return host.slice('unix://'.length)
  }

  return plat === 'win32' ? '\\\\.\\pipe\\docker_engine' : '/var/run/docker.sock'
}

// 🔑 EXPORTED, AND ITS INPUTS ARE PARAMETERS. This is the one part of this
// module that needs no daemon, no container and no second stack -- it is a pure
// function of the environment and the platform -- so it is the one part the
// harness can test by CALLING rather than by reading its source. Its spec
// asserts the precedence, the `unix://` slice, the refusal on `tcp://` and the
// Windows pipe; a source scan could assert none of those.
const SOCKET = dockerSocket()

/**
 * How long a service gets to become healthy again after being restarted.
 *
 * 🔴 RAISED FROM 120s BECAUSE 120s TURNED A MACHINE CONDITION INTO A SUITE
 * FAILURE. On a host down to 0.5 GB of free memory, the API container took
 * longer than two minutes to answer its first healthcheck and this gave up --
 * reporting a red run whose actual cause was that the machine was thrashing.
 * The ceiling is not a guess about the application: a Spring Boot container
 * that is going to start at all does so in about forty seconds here, and
 * anything approaching this number means the host, not the code.
 */
const HEALTHY_TIMEOUT_MS = 240_000

/** Between health polls. Not a fixed wait in a test -- a poll interval here. */
const POLL_MS = 500

/**
 * One Engine API call.
 *
 * The daemon being unreachable is the failure most likely to be met by whoever
 * reads this next, and "connect ENOENT /var/run/docker.sock" says nothing about
 * why, so it is translated into the sentence that actually helps.
 */
function engine(method, path) {
  return new Promise((resolve, reject) => {
    const req = httpRequest({ socketPath: SOCKET, method, path, headers: { Host: 'docker' } }, (res) => {
      let body = ''
      res.on('data', (chunk) => (body += chunk))
      res.on('end', () => resolve({ status: res.statusCode, body }))
    })
    req.on('error', (error) =>
      reject(
        new Error(
          `Cannot reach the Docker daemon at ${SOCKET} (${error.message}).\n` +
            `This scenario takes the API container down and brings it back, so it needs\n` +
            `the daemon. In the container stack the socket is bind-mounted into the suite\n` +
            `service; if that mount was removed, this is the failure it causes.`,
        ),
      ),
    )
    req.end()
  })
}

async function engineJson(method, path) {
  const { status, body } = await engine(method, path)
  if (status >= 400) {
    // 🔑 THE STATUS TRAVELS ON THE ERROR, not only inside its sentence.
    // `projectName()` below has to tell "this hostname is not a container" (a
    // native run, 404, expected) from every other failure (which must be
    // loud) -- and it did that by regexing this message. Rewording a sentence
    // would then have turned every native run into a hard failure. A field
    // cannot be reworded by accident.
    const error = new Error(
      `Docker answered ${status} for ${method} ${path}: ${body.slice(0, 400)}`,
    )
    error.status = status
    throw error
  }
  return body ? JSON.parse(body) : null
}

/**
 * Which compose project this suite belongs to.
 *
 * 🔑 READ FROM THE SUITE'S OWN CONTAINER RATHER THAN RECONSTRUCTED. The project
 * name is now `sasbo-automation-testing-${SASBO_STACK_ID}`, so two sessions run
 * two stacks -- and a scenario that rebuilt that string from environment
 * variables would stop the OTHER session's server the moment the two disagreed
 * about a default. The container's own compose label cannot disagree with the
 * stack it is running in.
 *
 * The fallback covers a native run, where there is no suite container to ask.
 */
async function projectName() {
  let self
  try {
    self = await engineJson('GET', `/containers/${hostname()}/json`)
  } catch (error) {
    // 🔴 ONLY ONE CONDITION MAY FALL THROUGH TO THE GUESS, and this used to be
    // a bare `catch {}` that admitted every one of them.
    //
    // The comment said "not running inside a compose-managed container" and the
    // code accepted a socket permission error, an Engine API version mismatch,
    // a daemon hiccup, and a hostname that is not a container id -- proceeding
    // in all of them with a RECONSTRUCTED name.
    //
    // 🔑 WHY THAT IS WORSE THAN IT LOOKS. The reconstructed name is exactly
    // what compose calls the default stack, so the guess is usually RIGHT and
    // the failure is therefore invisible. It diverges only when it matters: a
    // second session, running a second stack. `docker-compose.yml`'s header
    // records what that costs -- two sessions that thought they had two stacks
    // had one, and `down -v` in either destroyed the other's database mid-run.
    // So the fallback quietly reconstructed the very collision SASBO_STACK_ID
    // was introduced to prevent.
    //
    // This module's own stated principle is that a suite which cannot establish
    // which stack it owns must not be stopping containers. That principle now
    // applies to its own lookup: a 404 means the hostname is not a container --
    // a native run, which is the documented case -- and everything else is a
    // real failure that must be loud.
    if (error.status !== 404) {
      throw new Error(
        `Could not establish which compose project this suite belongs to.\n` +
          `  ${error.message}\n\n` +
          `This is refused rather than guessed. The fallback would rebuild the project\n` +
          `name from SASBO_STACK_ID, which is what compose calls the DEFAULT stack -- so\n` +
          `the guess is usually right and silently wrong exactly when a second session is\n` +
          `running a second stack. Stopping a container in that state takes down somebody\n` +
          `else's server, and a teardown takes their database with it.`,
      )
    }
  }

  const project = self?.Config?.Labels?.['com.docker.compose.project']
  if (project) return project

  // A native run: there is no suite container to ask, so the stack this session
  // owns is the one its own environment names.
  return `sasbo-automation-testing-${process.env.SASBO_STACK_ID || 'default'}`
}

/** The container id for one compose service in this suite's own stack. */
async function containerIdFor(service) {
  const project = await projectName()
  const filters = encodeURIComponent(
    JSON.stringify({ label: [`com.docker.compose.project=${project}`, `com.docker.compose.service=${service}`] }),
  )
  const found = await engineJson('GET', `/containers/json?all=1&filters=${filters}`)

  if (!found?.length) {
    throw new Error(
      `No container for compose service ${JSON.stringify(service)} in project ` +
        `${JSON.stringify(project)}. Either the stack is not up, or this suite is ` +
        `pointed at an application it did not start -- in which case it must not be ` +
        `stopping anything.`,
    )
  }
  return found[0].Id
}

/** Stop one service and wait for the daemon to confirm it is no longer running. */
export async function stopService(service) {
  const id = await containerIdFor(service)
  await engine('POST', `/containers/${id}/stop`)
  await waitFor(id, (state) => state.Running === false, `${service} to stop`)
}

/**
 * Start one service and wait until it is HEALTHY, not merely running.
 *
 * The difference is the whole reason this waits: the API answers connection
 * refused for tens of seconds after its process starts, so a scenario that
 * restarted the server and immediately asserted recovery would fail against a
 * server that was coming up correctly -- and would fail intermittently, which
 * is worse. A service with no healthcheck falls back to "running", because
 * waiting forever for a health status that will never arrive is the other way
 * to get this wrong.
 */
export async function startService(service) {
  const id = await containerIdFor(service)
  await engine('POST', `/containers/${id}/start`)
  await waitFor(
    id,
    (state) => (state.Health ? state.Health.Status === 'healthy' : state.Running === true),
    `${service} to become healthy`,
  )
}

async function waitFor(id, predicate, what) {
  const deadline = Date.now() + HEALTHY_TIMEOUT_MS
  let last = null

  while (Date.now() < deadline) {
    const inspected = await engineJson('GET', `/containers/${id}/json`)
    last = inspected.State
    if (predicate(last)) return last
    await new Promise((resolve) => setTimeout(resolve, POLL_MS))
  }

  throw new Error(
    `Timed out after ${HEALTHY_TIMEOUT_MS / 1000}s waiting for ${what}. ` +
      `Last state: ${JSON.stringify(last)}. Every scenario after this one runs against ` +
      `a stack in that state, so this is reported as a failure of the run rather than ` +
      `of one scenario.`,
  )
}
