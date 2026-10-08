import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/**
 * The one MSW server for the whole suite.
 *
 * It is started in `src/test/setup.js` with `onUnhandledRequest: 'error'`.
 * That is the important line: a request nobody wrote a handler for is a
 * FAILURE, not a warning. A silent passthrough is a unit test that quietly
 * depends on a backend being up, and it passes on the developer's machine for
 * exactly as long as their `bootRun` is running.
 *
 * The route smoke suite in `03.3` needs a deliberately broader, empty-200
 * layer. It gets it by pushing scoped handlers with `server.use(...)` -- never
 * by relaxing the policy above, which would remove the guarantee everywhere.
 */
export const server = setupServer(...handlers)
