/**
 * MOTION, MEASURED IN A BROWSER THAT HAS BEEN ASKED FOR LESS OF IT. `06.2`.
 *
 * P6's Definition of Done says `prefers-reduced-motion` is *"verified BY TOGGLING it"*, and the
 * emphasis is the whole instruction. There are three cheaper things that all look like
 * verification and are not:
 *
 *   ① GREPPING FOR `motion-reduce:` — counts utilities, not behaviour, and says nothing at all
 *      about the 38 animated elements that never got one.
 *   ② ASSERTING THE MEDIA QUERY MATCHES — `matchMedia('(prefers-reduced-motion: reduce)').matches`
 *      being `true` proves the emulation worked, which is a fact about Chrome. `edges.mjs` says
 *      the same thing about `forced-colors` in its own words: making a query MATCH is not the
 *      same as observing what happens because it did.
 *   ③ READING THE STYLESHEET BACK — the rule is there, therefore it works. It is a rule with a
 *      selector, a cascade and a specificity fight against 45 utility classes, and this arc has
 *      already found one lint rule (`05.4`) and one abort guard (`06.1b`) that were present,
 *      correct-looking and dead.
 *
 * 🔑 SO IT MEASURES THE ONE THING THAT CANNOT BE FAKED: **what the browser says it will
 * interpolate**, read off `getComputedStyle().transitionProperty` on the real elements, under
 * both settings, on the built client.
 *
 * ---
 *
 * 🔴 WHAT IT IS LOOKING FOR, AND WHY IT IS NOT "NOTHING MOVES".
 *
 * The ratified posture (`directionContract.md` → *Motion posture*) is specific:
 *
 *     "`prefers-reduced-motion` keeps opacity and state and drops every spatial move."
 *
 * So this asserts BOTH halves, and the second is the one that catches the reflex fix. The
 * well-known snippet — `transition-duration: 0.01ms !important` on everything — passes any
 * check that only looks for movement stopping, and it violates the posture: it also kills the
 * colour and opacity transitions that a user who asked for less motion still has as feedback.
 * A run where NOTHING transitions is a failure here, not a pass.
 *
 * ⚠️ AND IT PROVES ITS OWN INSTRUMENT FIRST (T1). Before trusting a clean reduced-motion run it
 * takes the same reading with the emulation OFF and requires the two to DIFFER. If they do not,
 * the emulation did not land, or the probe is reading a property that never had a spatial value
 * — either way the "clean" result is about nothing. `matrix.mjs` ran clean and half-blind for a
 * whole phase on exactly that shape.
 *
 * ---
 *
 * 🎬 THE TWO AUTHORED MOMENTS, DRIVEN RATHER THAN READ AT REST (`06.3`). The route passes read
 * pages that have settled, so they never see the two things the posture lets past the budget:
 * the card landing when a member is chosen, and the theme's pour. Both exist only while they run
 * — the landing for 900ms after a choice, the pour for 1.2s after a click — so each is triggered
 * under both settings and the browser's own list of running animations is read while it runs,
 * with the same instrument-first rule: the moment must be SEEN with motion on before its absence
 * under reduce means anything. The first run found the card snapping in under reduce, where
 * card.css:492 keeps its arrival as a 200ms fade.
 *
 *   node tools/motion.mjs <baseUrl>
 */
import { launch } from './cdp.mjs';

import { ROUTES, SETTLED, clickByText, pageReady, routeComplaints, storeTheme } from './probe.mjs';

const baseUrl = process.argv[2] ?? 'http://127.0.0.1:4319';

/**
 * The properties whose animation is a SPATIAL move, and the ones that are not.
 *
 * 🔑 `box-shadow` IS ON THE KEEP SIDE AND IT IS THE ONLY DEBATABLE ONE. It is how this design
 * says "raised" — `NavigationCard` lifts on hover — and it moves nothing: no layout, no
 * position, no apparent displacement of the element itself. Vestibular guidance is about motion
 * in the visual field, not about depth cues.
 */
const SPATIAL = ['transform', 'translate', 'rotate', 'scale', 'top', 'left', 'right', 'bottom',
  'width', 'height', 'margin', 'padding', 'inset'];
const KEPT = ['opacity', 'color', 'background-color', 'border-color', 'box-shadow', 'fill', 'stroke'];

/**
 * Read every visible element and say what it would animate.
 *
 * Reports the POPULATION alongside the verdict, because "0 spatial transitions" and "the
 * selector matched nothing" print identically — the failure mode this whole tool family exists
 * to refuse.
 */
const motionCensus = `(() => {
  const spatial = ${JSON.stringify(SPATIAL)};
  const kept = ${JSON.stringify(KEPT)};

  let examined = 0;
  let animating = 0;
  const spatialSites = [];
  let spatialCount = 0;
  let keptSites = 0;
  let runningAnimations = 0;
  let overBudget = 0;
  const slowSites = [];
  // One label for an element, for both lists below. It was written twice, and the first copy's regex had lost its
  // backslash inside this template literal, so it split class names on the letter "s".
  const nameOf = (el) => el.tagName.toLowerCase() +
    (typeof el.className === 'string' && el.className ? '.' + el.className.split(/\\s+/)[0] : '');

  for (const el of document.querySelectorAll('*')) {
    const r = el.getBoundingClientRect();
    if (r.width === 0 && r.height === 0) continue;
    // Counted BEFORE the duration gate: this is the population the claim is over, and a
    // number without its population is the thing this arc keeps being caught by.
    examined += 1;

    const s = getComputedStyle(el);

    // 🔴 THE DURATION GATE, AND THE FIRST RUN OF THIS TOOL IS WHY IT IS HERE.
    //
    // \`transition-property\` DEFAULTS TO \`all\` in the computed style, on every element in
    // the document, whether or not anything was ever authored. Without this gate the first
    // pass reported 5,786 "spatial transitions" across 25 routes -- which is not a
    // measurement of this design, it is a measurement of a CSS default. The reduced pass
    // then read 0 and the tool printed a confident, meaningless success.
    //
    // An element only animates if it has BOTH a property and a non-zero duration. Reading
    // the property alone answers "what WOULD interpolate if a duration were set", which is
    // a different question and the flattering one.
    const durations = (s.transitionDuration || '').split(',').map((d) => parseFloat(d) || 0);
    const runs = durations.some((d) => d > 0);
    if (!runs) continue;

    // The BUDGET, from P6's invariants: under 200ms for state feedback, under 400ms for
    // anything larger. Recorded per element rather than asserted, because the two authored
    // exceptions (the 580ms card and the 1200ms theme pour) are allowed to exceed it and the
    // tool has no way to know which element it is looking at -- the JUDGEMENT stays with a
    // reader, and what the tool owes them is the list.
    const longest = Math.max(...durations, 0) * 1000;
    if (longest > 400) {
      overBudget += 1;
      if (slowSites.length < 8) slowSites.push(nameOf(el) + ' ' + Math.round(longest) + 'ms');
    }

    // \`transitionProperty\` is what the browser will interpolate. \`none\` means it will not,
    // whatever the duration says -- which is exactly what the narrowed rule produces for a
    // transform, and is why the property is read as well as the duration.
    const props = (s.transitionProperty || '').split(',').map((p) => p.trim()).filter(Boolean);
    const live = props.filter((p) => p !== 'none');
    if (live.length) animating += 1;

    // \`all\` is the interesting case: it means EVERY property, spatial ones included, so a
    // narrowing that leaves \`all\` in place has not narrowed anything.
    const movesSpatially = live.some((p) => p === 'all' || spatial.includes(p));
    if (movesSpatially) {
      spatialCount += 1;
      if (spatialSites.length < 6) spatialSites.push(nameOf(el) + ' [' + live.join(' ') + ']');
    }
    if (live.some((p) => p === 'all' || kept.includes(p))) keptSites += 1;

    // A keyframe animation still cycling. \`animate-spin\` is the common one -- and a
    // reduced-motion setting must leave the LOADING STATE announced even when the spinner has
    // stopped, which is \`DataState\`'s \`role="status"\` and not this tool's business.
    if (s.animationName && s.animationName !== 'none') {
      const secs = parseFloat(s.animationDuration) || 0;
      if (secs > 0.001) runningAnimations += 1;
    }
  }

  return { examined, animating, spatialCount, spatialSites, keptSites, runningAnimations, overBudget, slowSites };
})()`;

/**
 * The animations a moment is running, read from the browser's own list WHILE they run. Polls a
 * frame at a time, because the moment's animations are created a frame or two after the act that
 * starts it, and stops at the first reading that finds any.
 *
 * `moves` means a keyframe carries a spatial property and `fades` that one carries opacity — read
 * off the keyframes themselves, not the animation's name, so a renamed keyframe cannot hide a move.
 */
const runningCensus = (filter) => `(async () => {
  const spatial = new Set(${JSON.stringify(SPATIAL)});
  const frame = () => new Promise((resolve) => requestAnimationFrame(resolve));
  const read = () => document.getAnimations().filter((a) => a.effect && (${filter})).map((a) => {
    const keys = new Set(a.effect.getKeyframes().flatMap((k) => Object.keys(k)));
    const target = a.effect.target;
    const classes = target && typeof target.className === 'string' ? target.className.split(/\\s+/) : [];
    return {
      name: a.animationName || '(script)',
      card: classes.includes('mcard'),
      pseudo: a.effect.pseudoElement || '',
      duration: Number(a.effect.getComputedTiming().duration) || 0,
      moves: [...keys].some((k) => spatial.has(k)),
      fades: keys.has('opacity'),
    };
  });
  let found = [];
  for (let i = 0; i < 40 && !found.length; i += 1) {
    found = read();
    if (!found.length) await frame();
  }
  // The state half of the posture: whatever else happens, the thing the user asked for happened.
  const ground = getComputedStyle(document.body).backgroundColor;
  return { found, groundChanged: window.__groundBefore !== undefined && ground !== window.__groundBefore };
})()`;

/** ③ choosing a member: the animations inside the arriving record panel. */
const ARRIVAL = runningCensus(`a.effect.target && a.effect.target.closest && a.effect.target.closest('.panel-arriving')`);
/** ④ switching the theme: the view transition's pseudo-element animations. */
const POUR = runningCensus(`String(a.effect.pseudoElement || '').startsWith('::view-transition')`);

const chooseMember = clickByText('[data-row-action]', '/open the record for/i');
const switchTheme = `(() => {
  window.__groundBefore = getComputedStyle(document.body).backgroundColor;
  return ${clickByText('button[aria-pressed]', '/^Appearance/')};
})()`;

/** One line per distinct animation: name, how many, duration, and whether it moves or fades. */
const summarise = (found) => {
  const groups = new Map();
  for (const a of found) {
    const key = `${a.name} ${a.pseudo} ${Math.round(a.duration * 100) / 100}ms${a.moves ? ' moves' : ''}${a.fades ? ' fades' : ''}`;
    groups.set(key, (groups.get(key) ?? 0) + 1);
  }
  return [...groups].map(([key, n]) => (n > 1 ? `${key} ×${n}` : key));
};

let cdp;
let failures = 0;
let missed = 0;

try {
  cdp = await launch('motion');
  const { send, evaluate, metrics, waitFor, assertBuiltClient } = cdp;

  console.log('identity OK — entry script', await assertBuiltClient(baseUrl));

  const land = async (route) => {
    await send('Page.navigate', { url: baseUrl + route });
    return waitFor(pageReady);
  };

  await metrics(1024);
  await land('/');
  await evaluate(storeTheme(false));

  /** One pass over every route, with the emulation in a given state. */
  const pass = async (label, reduce) => {
    await send('Emulation.setEmulatedMedia', {
      features: reduce ? [{ name: 'prefers-reduced-motion', value: 'reduce' }] : [],
    });

    const totals = { examined: 0, animating: 0, spatial: 0, kept: 0, running: 0, overBudget: 0 };
    const offenders = [];
    const slow = [];

    for (const route of ROUTES) {
      if (!await land(route)) { missed += 1; continue; }
      // Entrance transitions settle before anything is read (T4/T5): a value read
      // mid-transition is whatever it happened to be passing through. A condition, not the
      // fixed 150ms this was.
      if (!await waitFor(SETTLED, { tries: 120, awaitPromise: true })) { missed += 1; continue; }

      const c = await evaluate(motionCensus);
      totals.examined += c.examined;
      totals.animating += c.animating;
      totals.spatial += c.spatialCount;
      totals.kept += c.keptSites;
      totals.running += c.runningAnimations;
      totals.overBudget += c.overBudget;
      if (reduce && c.spatialCount) offenders.push(`${route}: ${c.spatialSites.join(' · ')}`);
      if (!reduce && c.overBudget) slow.push(`${route}: ${c.slowSites.join(' · ')}`);
    }

    console.log(`\n${label}`);
    console.log(`  ${ROUTES.length} routes · ${totals.examined} visible elements examined`);
    console.log(`  transitioning ${totals.animating} · spatial ${totals.spatial} · non-spatial ${totals.kept} · animations running ${totals.running}`);
    if (!reduce) {
      console.log(`  over the 400ms budget: ${totals.overBudget}`);
      for (const line of slow.slice(0, 10)) console.log(`    ${line}`);
    }
    for (const line of offenders.slice(0, 10)) console.log(`    ${line}`);

    return totals;
  };

  const normal = await pass('① emulation OFF — the baseline this arc actually ships', false);
  const reduced = await pass('② prefers-reduced-motion: reduce — TOGGLED, not assumed', true);

  /**
   * Trigger a moment on the register, under one setting, and read what runs. Light theme and a
   * settled register first, so every reading starts from the same page.
   */
  const drive = async (reduce, act, census) => {
    await send('Emulation.setEmulatedMedia', {
      features: reduce ? [{ name: 'prefers-reduced-motion', value: 'reduce' }] : [],
    });
    await evaluate(storeTheme(false));
    if (!await land('/members')
      || !await waitFor(`document.querySelectorAll('tr[data-row]').length > 0`)
      || !await waitFor(SETTLED, { tries: 120, awaitPromise: true })) { missed += 1; return null; }
    if (!await evaluate(act)) { missed += 1; return null; }
    // A census that throws is a reading that did not happen, counted as missed -- caught here, visibly, rather than by
    // going around `evaluate` (cdp.mjs: "a tool that wants to tolerate an exception can catch it").
    try {
      return await evaluate(census, { awaitPromise: true });
    } catch {
      missed += 1;
      return null;
    }
  };

  const moments = {
    arrival: { normal: await drive(false, chooseMember, ARRIVAL), reduced: await drive(true, chooseMember, ARRIVAL) },
    pour: { normal: await drive(false, switchTheme, POUR), reduced: await drive(true, switchTheme, POUR) },
  };
  for (const [label, moment] of [['③ choosing a member', moments.arrival], ['④ switching the theme', moments.pour]]) {
    console.log(`\n${label}`);
    for (const [setting, reading] of [['motion on', moment.normal], ['reduce', moment.reduced]]) {
      const lines = reading ? summarise(reading.found) : ['NOT MEASURED — the page or the act failed'];
      console.log(`  ${setting}: ${lines.length ? lines.join(' · ') : 'no animation ran'}`);
    }
  }

  await send('Emulation.setEmulatedMedia', { features: [] });
  await evaluate(storeTheme(false));

  console.log('\n─── verdict ───');

  // ⚠️ THE INSTRUMENT FIRST (T1). A clean run means nothing until the two passes differ.
  if (normal.spatial === 0) {
    console.log('  ✗ VACUOUS: the baseline pass found NO spatial transitions either, so the');
    console.log('    clean reduced-motion result below is about nothing. Either the emulation');
    console.log('    leaked into pass ①, or the census reads a property that never carried a');
    console.log('    spatial value. Fix the probe before trusting the number.');
    failures += 1;
  } else {
    console.log(`  ✓ the passes differ: ${normal.spatial} spatial transitions normally, ${reduced.spatial} under reduce`);
  }

  // ① every spatial move is dropped.
  if (reduced.spatial > 0) {
    console.log(`  ✗ ${reduced.spatial} elements would still animate a SPATIAL property under reduce`);
    failures += 1;
  } else {
    console.log('  ✓ no spatial transition survives the setting');
  }

  // ② and the non-spatial ones are KEPT — the half a blanket rule fails.
  if (reduced.kept === 0) {
    console.log('  ✗ NOTHING transitions under reduce, including colour and opacity. That is the');
    console.log('    blanket `transition: none` fix, and it contradicts the ratified posture:');
    console.log('    "keeps opacity and state and drops every spatial move". A control whose');
    console.log('    hover and focus colours snap has lost the feedback that says it responded.');
    failures += 1;
  } else {
    console.log(`  ✓ ${reduced.kept} elements keep a non-spatial transition — feedback survives`);
  }

  // ③ nothing is left cycling.
  if (reduced.running > 0) {
    console.log(`  ✗ ${reduced.running} keyframe animations still running under reduce`);
    failures += 1;
  } else {
    console.log('  ✓ no keyframe animation is left cycling');
  }

  // ② THE DURATION BUDGET. Listed, never failed on -- the ratified posture allows exactly two
  // authored exceptions (the 580ms card, the 1200ms theme pour) and this tool cannot tell them
  // from an accident. A gate that cannot distinguish the sanctioned case from the mistake is a
  // gate somebody switches off.
  if (normal.overBudget > 0) {
    console.log(`  ℹ ${normal.overBudget} elements transition for longer than 400ms — listed above.`);
    console.log('    Two authored exceptions are sanctioned; anything else is a finding.');
  } else {
    console.log('  ✓ every transition is inside the 400ms budget');
  }

  // ④ THE AUTHORED MOMENTS. Seen with motion on first, or their absence under reduce is about
  // nothing. Moving is read off the keyframes, and 1ms is the line: the surface rule leaves every
  // keyframe animation alive at 0.01ms, so `animationend` still fires, and that is not movement.
  const { arrival, pour } = moments;
  if (arrival.normal && arrival.reduced) {
    const landing = arrival.normal.found.find((a) => a.card && a.moves);
    if (!landing) {
      console.log('  ✗ VACUOUS: choosing a member with motion on ran no moving animation on the card');
      failures += 1;
    } else {
      console.log(`  ✓ choosing a member plays the authored moment — ${landing.name}, ${Math.round(landing.duration)}ms on the card`);
      const moving = arrival.reduced.found.filter((a) => a.moves && a.duration > 1);
      if (moving.length) {
        console.log(`  ✗ under reduce, the arrival still moves: ${[...new Set(moving.map((a) => a.name))].join(', ')}`);
        failures += 1;
      } else {
        console.log('  ✓ under reduce, nothing in the arrival moves');
      }
      // card.css:492 — the comp keeps the card's arrival as a fade, because the posture keeps opacity.
      const fade = arrival.reduced.found.find((a) => a.card && a.fades && !a.moves && a.duration >= 100);
      if (!fade) {
        console.log('  ✗ under reduce, the card snaps in — the comp fades it (card.css:492, fade-in .2s)');
        failures += 1;
      } else {
        console.log(`  ✓ under reduce, the card still arrives as a fade — ${fade.name}, ${Math.round(fade.duration)}ms`);
      }
    }
  }
  if (pour.normal && pour.reduced) {
    const iris = pour.normal.found.find((a) => a.duration >= 1000);
    if (!iris || !pour.normal.groundChanged) {
      console.log('  ✗ VACUOUS: switching the theme with motion on showed no pour, or no change of theme');
      failures += 1;
    } else {
      console.log(`  ✓ switching the theme pours — ${iris.pseudo}, ${Math.round(iris.duration)}ms`);
      const animating = pour.reduced.found.filter((a) => a.duration > 1);
      if (animating.length) {
        console.log(`  ✗ under reduce, the theme still pours: ${[...new Set(animating.map((a) => a.pseudo))].join(', ')}`);
        failures += 1;
      } else {
        console.log('  ✓ under reduce, no view transition animates');
      }
      if (!pour.reduced.groundChanged) {
        console.log('  ✗ under reduce, the theme did NOT change — the posture keeps state');
        failures += 1;
      } else {
        console.log('  ✓ under reduce, the theme still changes — the state is kept');
      }
    }
  }

  if (missed) {
    console.log(`  ⚠ ${missed} route loads or moments never became ready and were NOT measured`);
    failures += 1;
  }

  // "Every route" is held against the router, not against this file's memory of it.
  for (const complaint of routeComplaints()) {
    console.log(`  ✗ ROUTES  ${complaint}`);
    failures += 1;
  }
} finally {
  await cdp?.close?.();
}

console.log(`\nSTATUS=${failures}`);
process.exit(failures ? 1 : 0);
