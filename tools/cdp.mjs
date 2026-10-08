/**
 * One Chrome, one WebSocket, one `evaluate`. The plumbing every measurement tool needs and
 * none of them should own.
 *
 * 🔴 WHY THIS EXISTS: SIX COPIES, AND THEY HAD ALREADY DRIFTED INTO FOUR DEFECTS. `probe.mjs`
 * was written in this same phase because three copies of the overflow probe had drifted to
 * three different hashes — and the harness AROUND it was copied six times in the same commit.
 * The copies were not merely redundant; they disagreed, and every disagreement was a bug:
 *
 *   ① 🔴 `evaluate()` SWALLOWED EXCEPTIONS IN FOUR OF SIX. `matrix`, `edges`, `states` and
 *      `matrix-canary` returned `.result.value` without looking at `exceptionDetails`, so an
 *      expression that THREW in the page came back `undefined` — which `states.mjs` read as
 *      "the step did not land" and `matrix.mjs` read as "not ready". A broken probe was
 *      indistinguishable from a page that failed, in a repository whose entire thesis is that
 *      a silent zero is the enemy. `contrast` and `tokenContrast` had already fixed it locally.
 *   ② 🔴 TWO TOOLS DIED WITH THE WRONG ERROR. `edges` and `states` indexed `targets.find(...)`
 *      directly, so a Chrome that never came up gave `Cannot read properties of undefined`
 *      instead of "no page target on port N".
 *   ③ 🔴 PORT RANGES COLLIDED. `contrast` and `matrix-canary` both drew from `9500 + rand(80)`;
 *      `edges` and `tokenContrast` both from `9600 + rand(80)`. Two tools run together could
 *      fight over one debug port. Ports are allocated here now, from one list.
 *   ④ 🔴 "IS THIS A PHONE" HAD TWO ANSWERS. `metrics()` used `width < 500` in `edges` and
 *      `width < 700` in `matrix` and `contrast`, and `deviceScaleFactor` was `mobile ? 2 : 1`
 *      in two tools and a hard `1` in a third. So the matrix and the edge sweep were NOT
 *      measuring the same 640±1 condition on the same 25 routes, which is the one thing a
 *      matrix and an edge sweep exist to do together.
 *
 * 🔑 AND THE T23 IDENTITY CHECK WAS IN TWO OF SIX. `matrix` and `contrast` proved the server
 * was the built client before measuring it; `edges`, `states`, `matrix-canary` and
 * `tokenContrast` measured whatever held the port. `serve-dist.mjs`'s own header records that
 * this already cost this arc a measurement of the mockups directory index instead of the
 * application. It is `assertBuiltClient()` here, so a tool cannot omit it by forgetting.
 */
import { spawn } from 'node:child_process';
import { rmSync } from 'node:fs';

const CHROME = 'C:/Users/mfazl/AppData/Local/ms-playwright/chromium-1243/chrome-win64/chrome.exe';

/**
 * Debug ports, one range per tool, allocated here so two tools cannot collide.
 *
 * Written out rather than hashed from the tag: a hash gives collisions nobody can predict,
 * and this list is the one place to look when a port is already in use.
 */
const PORTS = Object.freeze({
  matrix: 9400,
  canary: 9450,
  edges: 9500,
  states: 9550,
  contrast: 9600,
  tokens: 9650,
  probe: 9700,
  motion: 9750,
  review: 9800,
  surfaces: 9850,
  vitals: 9900,
});

/**
 * 🔑 THE RASTERISER, MADE REPEATABLE. With motion frozen, two captures still differed on 10 of 350 screenshots -- by one
 * or two channel levels in scattered pixels, and along the antialiased edge of a control at a fractional x ("Add
 * Recruiter" at x=850.84, 1024 wide). Software raster splits the frame into tiles on several threads. One raster
 * thread, no partial raster, every compositor stage run before the frame is drawn, no LCD subpixel text, an sRGB
 * profile and no font hinting take the variance out at the source: three subset captures, 70 of 70 identical where
 * the data arrived. Moved here from `matrix.mjs` in RF, so the surface sweep photographs
 * through the same rasteriser (P9's shared `still()`).
 */
export const STILL_FLAGS = [
  '--num-raster-threads=1',
  '--disable-partial-raster',
  '--run-all-compositor-stages-before-draw',
  '--disable-lcd-text',
  '--force-color-profile=srgb',
  '--font-render-hinting=none',
];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * Launch Chrome, attach to its page target, and return the handful of operations every tool
 * actually needs.
 *
 * `tag` names the port range and the profile directory, so a crashed run leaves an
 * identifiable profile behind rather than an anonymous one.
 */
/**
 * THE ONE ANSWER TO "IS THIS A PHONE" (④): `metrics()` below emulates a mobile viewport by it, and a tool that also
 * switches touch on at a phone width asks it here rather than writing the cutoff again.
 */
export const isPhone = (width) => width < 700;

export async function launch(tag, { flags = [], still = false } = {}) {
  const base = PORTS[tag];
  if (!base) {
    throw new Error(
      `launch('${tag}') has no port range. Add it to PORTS in tools/cdp.mjs rather than ` +
        `picking a number here — two tools drawing from the same range is how the last set collided.`,
    );
  }
  const port = base + Math.floor(Math.random() * 40);
  const profileDir = `${process.env.TEMP}/${tag}-profile-${port}-${Date.now().toString(36)}`;

  const chrome = spawn(CHROME, [
    '--headless=new', '--disable-gpu', '--hide-scrollbars',
    `--remote-debugging-port=${port}`, '--no-first-run',
    /*
     * 🔴 A PROFILE PER RUN, NEVER PER PORT (T40, T43). The port is drawn from a 40-wide range, so runs collided on
     * the same profile directory -- and a REUSED profile is how a page came back holding the last run's theme
     * (T40), and the likeliest reason a first navigation twice failed to become ready inside 30s (T43) while a
     * probe on a clean profile reached the same URL in under a second. A measurement must not inherit state from
     * the measurement before it. The directory is removed on close, so this does not fill the disk.
     */
    `--user-data-dir=${profileDir}`,
    ...(still ? STILL_FLAGS : []),
    ...flags,
    'about:blank',
  ], { stdio: 'ignore' });

  let targets;
  for (let i = 0; i < 60; i++) {
    try {
      targets = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
      if (targets.some((t) => t.type === 'page')) break;
    } catch { /* chrome not up yet */ }
    await sleep(250);
  }
  const page = targets && targets.find((t) => t.type === 'page');
  // Guarded, and it is guarded because two copies were not: indexing the `find()` result
  // directly turns "Chrome never started" into an unrelated TypeError.
  if (!page) {
    chrome.kill();
    throw new Error(`no page target on port ${port} after 15s — did Chrome start?`);
  }

  const ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => {
    ws.onopen = resolve;
    ws.onerror = () => reject(new Error(`could not attach to the page target on port ${port}`));
  });

  let id = 0;
  const pending = new Map();
  /*
   * CDP EVENTS, for the one kind of tool that needs them: request interception. The stub server
   * answers every `/api/` path the same way every time, so a register whose fetch FAILS, HANGS or
   * comes back EMPTY cannot be reached through it -- and those are the states A27 is about. An
   * event with no listener is dropped, exactly as every event was before this existed.
   */
  const listeners = new Map();
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data);
    if (m.id && pending.has(m.id)) {
      const p = pending.get(m.id);
      pending.delete(m.id);
      m.error ? p.reject(new Error(m.error.message)) : p.resolve(m.result);
      return;
    }
    if (m.method) for (const fn of listeners.get(m.method) ?? []) fn(m.params);
  };

  /** Subscribe to a CDP event. Returns the unsubscribe. */
  const on = (method, fn) => {
    if (!listeners.has(method)) listeners.set(method, new Set());
    listeners.get(method).add(fn);
    return () => listeners.get(method).delete(fn);
  };

  /**
   * 🔴 EVERY CDP CALL HAS A DEADLINE, AND WITHOUT ONE A HUNG BROWSER HANGS THE RUN FOR EVER.
   *
   * Measured 2026-09-18, P9's closing gate: `surfaces.mjs` stopped after 178 of 252 rows and sat there. Its last
   * capture and its last log line were both written at 11:30:03; at 12:29:39 nothing had moved, the node process had
   * burned 39.34s of CPU and burned no more between two readings a second apart, and ten Chrome processes were still
   * alive. It was blocked in `await send(...)` on a reply that was never coming -- there was no timer, so the promise
   * simply never settled and the tool could not even say which surface it died on.
   *
   * ⚠️ A TIMEOUT HERE IS NOT A RETRY AND NOT A THRESHOLD NEGOTIATION. It turns an infinite wait into a NAMED failure:
   * the caller sees which method stopped answering, the run ends, and the gate is red instead of pending. 30s is far
   * beyond any real CDP round trip on this box (the slowest measured, a full-page screenshot under memory pressure,
   * is under 3s), so it cannot fire for a call that was merely slow.
   */
  const SEND_CEILING_MS = 30_000;

  const send = (method, params = {}) =>
    new Promise((resolve, reject) => {
      const msg = { id: ++id, method, params };
      const deadline = setTimeout(() => {
        if (!pending.has(msg.id)) return;
        pending.delete(msg.id);
        reject(new Error(
          `CDP ${method} did not answer within ${SEND_CEILING_MS / 1000}s. The browser stopped responding; this is ` +
          `the instrument, not the page. Re-running is not evidence -- see T45.`,
        ));
      }, SEND_CEILING_MS);
      pending.set(msg.id, {
        resolve: (value) => { clearTimeout(deadline); resolve(value); },
        reject: (error) => { clearTimeout(deadline); reject(error); },
      });
      ws.send(JSON.stringify(msg));
    });

  /**
   * Evaluate an expression in the page.
   *
   * 🔴 A THROW IS A THROW. This raises rather than returning `undefined`, because the four
   * copies that returned `undefined` made a broken probe look like a failed page. There is no
   * opt-out: a tool that wants to tolerate an exception can catch it, which is visible in the
   * tool, whereas swallowing it here is visible nowhere.
   */
  const evaluate = async (expression, { awaitPromise = false } = {}) => {
    const r = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise });
    if (r.exceptionDetails) {
      const detail = r.exceptionDetails.exception?.description
        ?? r.exceptionDetails.text
        ?? 'no detail';
      throw new Error(`the page threw while evaluating: ${String(detail).split('\n')[0]}`);
    }
    return r.result.value;
  };

  await send('Page.enable');
  await send('Runtime.enable');

  /*
   * 🔑 `still`: A PHOTOGRAPH THAT CAN BE COMPARED. The repeatable rasteriser above, and motion off for every page this
   * browser opens -- an override on the target, so it survives every navigation. One owner, because the matrix had
   * both and the surface sweep had one, and its captures of an unchanged build did not repeat (T44).
   */
  if (still) await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });

  /**
   * Make one request pattern -- or several -- fail, hang, come back empty or answer with a payload for the length of
   * one capture, and return the teardown. Anything held is released on the way out, so no capture inherits a request
   * the previous one left hanging. A CDP call that fails here is RETURNED by the teardown, and it fails the capture.
   *
   * 🔑 MOVED HERE FROM `review.mjs` when `surfaces.mjs` (`07.2`) needed it too. The stub server answers every path the
   * same way every time, so a failed, hung or empty list is reached only in the browser -- and a second copy of the
   * one driver that reaches them would drift the way three copies of the overflow probe did (probe.mjs).
   */
  const intercept = async ({ url, mode, body }) => {
    const held = [];
    const problems = [];
    // `empty` answers `[]`; `fulfill` answers with the capture's own payload.
    const payload = mode === 'empty' ? [] : mode === 'fulfill' ? await body() : null;
    const off = on('Fetch.requestPaused', ({ requestId }) => {
      if (mode === 'hang') { held.push(requestId); return; }
      const answer = mode === 'fail'
        ? send('Fetch.failRequest', { requestId, errorReason: 'ConnectionRefused' })
        : send('Fetch.fulfillRequest', {
          requestId,
          responseCode: 200,
          responseHeaders: [{ name: 'content-type', value: 'application/json' }],
          body: Buffer.from(JSON.stringify(payload)).toString('base64'),
        });
      answer.catch((e) => problems.push(e.message));
    });
    // 🔑 THE CACHE IS OFF FOR AN INTERCEPTED CAPTURE. A route chunk an earlier capture already loaded is served from
    // memory without ever reaching the network -- so an interception of it would pause nothing, and the "failed
    // chunk" capture would photograph a page that loaded perfectly.
    await send('Network.enable');
    await send('Network.setCacheDisabled', { cacheDisabled: true });
    await send('Fetch.enable', { patterns: [url].flat().map((urlPattern) => ({ urlPattern, requestStage: 'Request' })) });
    return async () => {
      off();
      for (const requestId of held) {
        // The page that held it may already be gone; an unknown request id is the expected answer.
        await send('Fetch.failRequest', { requestId, errorReason: 'Aborted' }).catch(() => {});
      }
      await send('Fetch.disable');
      await send('Network.setCacheDisabled', { cacheDisabled: false });
      await send('Network.disable');
      return problems;
    };
  };

  /**
   * The viewport, with ONE answer to "is this a phone".
   *
   * 🔴 TRUE DEVICE METRICS, NOT A RESIZED WINDOW (T14). Headless Chrome will not lay out below
   * roughly 500px and silently CROPS instead, so `--window-size=390,844` renders a wider page
   * with its right-hand side cut off. `Emulation.setDeviceMetricsOverride` with `mobile: true`
   * is the only thing that produces a real 390px viewport.
   *
   * 🔑 THE 700px CUTOFF IS THE WIDER OF THE TWO THAT WERE IN USE, deliberately: `mobile: true`
   * is what makes a narrow viewport real, and a tablet-width layout measured without it is
   * measured in a window that merely says 768. The two thresholds meant the matrix and the edge
   * sweep disagreed about the same route at the same width.
   */
  const metrics = (width, height = width < 500 ? 844 : 900) => {
    const mobile = isPhone(width);
    return send('Emulation.setDeviceMetricsOverride', {
      width, height, deviceScaleFactor: mobile ? 2 : 1, mobile,
    });
  };

  /**
   * Poll an expression until it is `true`. Returns whether it ever was. `awaitPromise` for an
   * expression that resolves to its answer (`SETTLED`, in probe.mjs).
   */
  const waitFor = async (expression, { tries = 80, every = 150, awaitPromise = false } = {}) => {
    for (let i = 0; i < tries; i++) {
      if (await evaluate(expression, { awaitPromise }) === true) return true;
      await sleep(every);
    }
    return false;
  };

  /**
   * 🔴 T23: PROVE THE SERVER IS OURS BEFORE MEASURING ANYTHING. A 200 says something is there,
   * never that it is yours — and the mockups are built from the same tokens, so a wrong-server
   * measurement looks entirely plausible.
   */
  const assertBuiltClient = async (baseUrl) => {
    await send('Page.navigate', { url: `${baseUrl}/login` });
    // 🔴 A CONDITION, NOT THE FIXED 800ms THIS WAS. On a cold Chrome under memory pressure the
    // document had not finished parsing its head after 800ms, so the entry script was not there
    // yet and a server that was serving perfectly was refused as "not the built client"
    // (2026-09-14, `06.3`). The href clause stops `about:blank` answering for the page.
    const arrived = await waitFor(
      `location.href.startsWith(${JSON.stringify(baseUrl)}) && document.readyState !== 'loading'`,
      { tries: 100 },
    );
    if (!arrived) throw new Error(`${baseUrl}/login never finished loading — is the server up?`);
    const entry = await evaluate(
      "document.querySelector('script[type=module]')?.src || location.href",
    );
    if (!/\/assets\/index-[A-Za-z0-9_-]+\.js$/.test(entry)) {
      throw new Error(`the server at ${baseUrl} is not the built client (entry script: ${entry})`);
    }
    /*
     * 🔴 AND THE APP HAS MOUNTED BEFORE THIS RETURNS -- F-P56's first row. A loaded DOCUMENT is not a mounted app: the
     * entry is a module, which runs after `readyState` leaves 'loading'. Profiles are reused between runs, so this page
     * can mount holding the LAST run's stored theme, and `ThemeProvider`'s mount effect writes that value back to
     * `localStorage`. Returning before it had run let a tool store its first row's theme and then have the app
     * overwrite it: the run's first row, light, photographed dark (P6's closing gate; the P7 merge gate refused the same
     * row with `stored: "true"`). Throttled on purpose it reproduced in 2 of 3 runs, and a trace of every write showed
     * the app's stale write racing the tool's. Waiting for the root to hold the app, then two frames for its effects,
     * closes the window instead of hoping the race is lost.
     */
    const mounted = await waitFor(
      `(async () => {
        if (!document.getElementById('root')?.firstElementChild) return false;
        await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
        return true;
      })()`,
      { tries: 200, awaitPromise: true },
    );
    if (!mounted) throw new Error(`${baseUrl}/login loaded but the application never mounted into #root`);
    return String(entry).split('/').pop();
  };

  /**
   * 🔴 A VERDICT IS TAKEN ON A FRAME THAT HOLDS STILL, AND ONLY WHEN IT REPEATS (F-P61). `SETTLED` answers for one
   * instant: a list that is still loading can flip its control from disabled to enabled a moment later, and its text
   * colour fades between the two. The surface sweep read axe in that moment and photographed the page after it, so
   * three rows failed on screenshots pixel-identical to rows it passed -- 4, 0, 3, 0 and 2 failing rows over five runs
   * of one build. So `read()` is bracketed by two photographs, and its answer counts only when the frames on both
   * sides are identical AND a second bracketed reading returns the same answer on the same frame. The frame it
   * returns is the one the answer was read on. A page that never holds still returns null -- UNMEASURED, never passed.
   */
  const steady = async (read, { tries = 8 } = {}) => {
    const frame = async () => (await send('Page.captureScreenshot', { format: 'png' })).data;
    let previous = null;
    for (let i = 0; i < tries; i += 1) {
      const before = await frame();
      const value = await read();
      const after = await frame();
      const reading = before === after ? { frame: after, key: JSON.stringify(value) } : null;
      if (reading && previous && reading.frame === previous.frame && reading.key === previous.key) return { value, frame: after };
      previous = reading;
      if (!reading) await sleep(150);
    }
    return null;
  };

  const close = () => {
    try { ws.close(); } catch { /* already closed */ }
    chrome.kill();
    // Best effort: Chrome may still hold a handle for a moment, and a profile left behind is litter,
    // never a failed run.
    try { rmSync(profileDir, { recursive: true, force: true }); } catch { /* Chrome still has it */ }
  };

  return { send, evaluate, metrics, waitFor, assertBuiltClient, close, port, sleep, on, intercept, steady };
}

export { sleep };
