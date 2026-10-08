import { http, HttpResponse } from 'msw'

/**
 * THE SMOKE LAYER -- a deliberately broad, explicitly-scoped set of handlers,
 * and the ONE place in this suite where a request is answered without a test
 * having asked for it.
 *
 * WHY IT HAS TO EXIST
 * The suite's standing rule is that an unhandled request is an ERROR, not a
 * warning. That rule is correct and it is what makes every other test
 * trustworthy. But twelve of the routes fetch on mount, across nineteen
 * distinct endpoints, and the smoke test asserts only "this page mounts" --
 * so without handlers it would fail for reasons that have nothing to do with
 * the page, and the guard would be measuring MSW instead of the application.
 *
 * WHY IT IS NOT A RELAXATION
 * The strict policy stays on globally. This layer is pushed with
 * `server.use(...)` inside the smoke suite only, and MSW discards it on the
 * next `resetHandlers()`. No other test in the repository can see it. A
 * relaxation of `onUnhandledRequest` would have removed the guarantee
 * everywhere to solve a problem in one file.
 *
 * WHAT IT BUYS BACK -- the part that pays for the compromise
 * It RECORDS every endpoint actually requested. That observed set is the
 * runtime counterpart of Phase 4's STATIC scan of `src/services/`: an endpoint
 * the scan misses shows up here as a request nobody predicted, and one the
 * scan invents shows up as a path nothing ever called. A static discovery
 * guard with no runtime witness is grading its own homework.
 */

/** Every endpoint this layer was asked for, in call order, deduplicated. */
const observed = new Map()

export const observedEndpoints = () =>
  [...observed.entries()]
    .map(([key, count]) => ({ endpoint: key, calls: count }))
    .sort((a, b) => a.endpoint.localeCompare(b.endpoint))

export const resetObservedEndpoints = () => observed.clear()

const record = (request) => {
  const { pathname } = new URL(request.url)
  const key = `${request.method} ${pathname}`
  observed.set(key, (observed.get(key) ?? 0) + 1)
}

/**
 * An empty, well-formed 200 for anything under /api/.
 *
 * An EMPTY collection is the right default: it is the shape every list screen
 * must handle, it cannot accidentally satisfy an assertion about content, and
 * it means a page that renders only because the fixture handed it something
 * cannot pass here.
 */
export const smokeHandlers = [
  http.all('*/api/*', ({ request }) => {
    record(request)
    if (request.method === 'DELETE') return new HttpResponse(null, { status: 204 })
    return HttpResponse.json([])
  }),
]
