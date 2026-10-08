import { cn } from '../../lib/utils';

// 🔴 BR-18 LANDED HERE IN 04.1, AS ONE CHANGE IN TWO PLACES.
//
// P0's baseline measured the rendered DOM on all 27 routes: 23 already contained exactly
// one <h1>, the SHELL's — the same string on every page, the application's name. So the
// one landmark a screen-reader user navigates by said nothing about where they were, and
// promoting this component ALONE would have given those 23 routes TWO <h1>s, which is
// precisely the shape /reports/organizer-report had and which P0 filed as F-D03.
//
// The other half is in `Header.jsx`: the topbar carries no heading, because it names the
// APPLICATION and an application name is not a page heading. `headingContract.test.jsx`
// holds both halves together — it mounts every route and asserts no route renders two.
/*
 * 📐 card.html's `.pagehead`, and it is the page head of EVERY route since 2026-09-10.
 *
 * Title left, one line of description under it, the page's actions on the same baseline
 * to the right. The centred variant this component used to default to was the old shell's
 * arrangement, and the owner's instruction is that the whole application takes the mock's;
 * a page with no actions simply has nothing on the right.
 */
export default function PageTitle({ title, subtitle, actions, className }) {
  return (
    <div className={cn('flex flex-wrap items-end justify-between gap-x-6 gap-y-3', className)}>
      {/* The text block yields before the actions do: on a 680px register the description
          wraps to two lines under the title, and Export and Capture stay on its baseline. */}
      <div className="min-w-0 flex-1 basis-60">
        <h1 className="text-balance text-3xl font-semibold leading-[1.15] tracking-[-0.02em] text-ink">
          {title}
        </h1>
        {/* 📱 R-RESP-O: A READABLE MEASURE, AND IT BELONGS HERE RATHER THAN ON EACH PAGE.
            Several of these subtitles are full sentences — Finance Houses' runs to 140
            characters — and with nothing constraining them they set as ONE line across a
            1440px window. `max-w-prose` is ~65ch, and putting it in the shared component
            means the next page to be written gets it without anybody remembering. */}
        {/* 🔴 `text-pretty`, NOT `text-balance` — and the difference showed on the page head
            of every route with a sentence under the title. `balance` equalises the lines of
            a block, which is what you want for a two-line HEADING; applied to a 100-character
            sentence it splits it into two SHORT centred-looking lines of ~320px under a 924px
            title, leaving a hole between the description and the page's actions. `pretty` is
            the body-copy rule: fill the measure, and only protect the last line from a widow. */}
        {subtitle && (
          <p className="mt-1.5 max-w-prose text-pretty text-base text-ink-muted">{subtitle}</p>
        )}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}
