import { http, HttpResponse } from 'msw'

/**
 * The mount-time calls a page test has to answer before it can get to the
 * surface it is actually about.
 *
 * WHY THIS IS SHARED WHEN THE DEFAULT HANDLER SET IS NOT
 * The suite's rule is that every test declares the responses it depends on, so
 * that nothing passes because of something another file arranged. These are
 * the exception, and the reason is narrow: `Members` fires four requests on
 * mount and `CaptureMember` nine, none of which the tests are about. Answering
 * them individually in three files produced three copies of the same list that
 * drifted the moment a page gained a fetch -- with the symptom being an
 * unhandled-request error in a test about something else entirely.
 *
 * These are all EMPTY 200s. That matters: an empty collection is the shape
 * every screen must handle, and it cannot accidentally satisfy an assertion
 * about content. A test that needs real data still says so itself, with a
 * later `server.use(...)` that takes precedence.
 *
 * They are `http.get` only. A write is never incidental to rendering a page,
 * so a POST or PUT reaching this layer would be a request a test did not
 * expect -- and it should fail loudly, not be quietly answered.
 *
 * 🔑 ONE WILDCARD, NOT A LIST OF TEN. The GET-only restriction above is the
 * load-bearing property, and it is expressed by the verb, not by enumerating
 * paths. The header three paragraphs up records three copies of this list
 * drifting "the moment a page gained a fetch"; consolidating them to one copy
 * removed two of the three, and left the last one still needing an edit every
 * time a page gains a mount-time read. This has nothing to keep current.
 *
 * A test that needs a specific response still declares it, and a later
 * `server.use(...)` takes precedence over this layer exactly as before.
 */
export const pageFixtures = [http.get('*/api/*', () => HttpResponse.json([]))]
