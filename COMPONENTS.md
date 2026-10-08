# Component inventory — SASBO Online client

**Unit `10.2`. Read from the source on `design/09-p8-prep`, 2026-09-18.**

This is the parts list. [`DESIGN.md`](./DESIGN.md) is the system — the tokens, the type ramp, the
shadow vocabulary, and the named rules (**The Faint-Ink Rule**, **The Token-Only Rule**, **The
Sixteen-Pixel Rule**, **The Coarse-Pointer Rule**, **The Container Rule**, **The On-Brand Rule**,
**The Tabular Rule**, **The One Engraving Rule**, **The Reduced-Motion Rule**). Those rules are
cited here by name and never restated; where a component obeys one, the citation *is* the record.

Everything below was read out of `src/components/` and its tests. Where the code does not settle a
question, this file says **not established** rather than guessing.

---

## How to read a state block

Every component carries the same twelve-row checklist, so the inventory can be checked for
completeness rather than only for content. A state that does **not** exist is listed and marked
absent with the reason, because an inventory that silently omits what is missing cannot be audited.

| Row | What it means here |
| --- | --- |
| **default** | at rest, nothing happening |
| **hover** | pointer over it — always through the `hover-hover:` variant, so a tap never latches it lit |
| **focus / focus-visible** | keyboard focus; the 2px `brand-ring` unless noted |
| **active** | the press itself |
| **disabled** | the control exists and refuses |
| **busy / in-flight** | a request this control started has not answered |
| **error** | the *value* is wrong, or the *request* failed — the two are different states and are marked as such |
| **empty** | the answer arrived and was nothing |
| **loading** | the answer has not arrived |
| **selected** | this one is the current choice |
| **open / closed** | it has a popup, a drawer or a panel |
| **read-only** | shows a value, refuses to be typed into |

The distinction between **error** and **loading/empty** is load-bearing across the whole layer:
*a request that has not answered is not an empty answer, and a request that failed is not an empty
answer either.* `DataState`, `Table`, `Pager`, `SelectInput`, `SearchableSelect`, `PositionTreeSelect`
and `FigureStrip` all encode that separately, and each one has a test that fails if the branches
collapse into each other.

**There is no `src/components/ui/`.** `components.json` points its `ui` alias at
`@/components/ui`, and no such directory exists — no shadcn registry component was ever vendored in.
Everything in this inventory is hand-written in this repository.

---

# Form controls — `src/components/form/`

Barrel: `src/components/form/index.js`. It exports `Field`, `controlClasses`, `TextInput`,
`SelectInput`, `DateField`, `SearchableSelect`, `Button`, `CheckboxGroup`, `FilterForm`,
`PositionTreeSelect`, `AddressAutocomplete`, and the `fieldKinds` / `options` helpers.
`LoadFailureFrame` and `MapsScope` are not in the barrel and are imported by path.

---

## `Button` — `src/components/form/Button.jsx`

**What it is for.** Every control that is pressed. There is no second button in this application, by
design — `src/lib/utils.js`'s `pick()` throws in DEV on an unknown `variant` or `size`, so a typo
cannot quietly render a neutral button.

**When rather than a neighbour.** `href` turns it into an `<a>`, and that is the choice, not a
convenience: a control that *navigates or downloads* must be a link so it appears in a screen
reader's link rota, opens on the modifier the user already knows, and shows a destination. When it
is a link, `type`, `disabled` and `loading` do not apply — a link that must be unavailable is not
rendered, or is rendered without an `href`. Reach for `variant="danger"` only for something
destructive; `variant="pagination"` exists so `Pager` can be retuned without touching `default`.

**Props that change behaviour.**

| Prop | Type | Default | Effect |
| --- | --- | --- | --- |
| `variant` | `'default' \| 'primary' \| 'ghost' \| 'danger' \| 'pagination'` | `'default'` | skin; unknown value **throws** in DEV (`Button.jsx:132`) |
| `size` | `'sm' \| 'md' \| 'icon'` | `'md'` | 32px / 38px / square 38px; `icon` makes `aria-label` the caller's obligation |
| `href` | `string` | — | renders `<a>` instead of `<button>`; `type`/`disabled`/`loading` then do nothing |
| `type` | `string` | `'button'` | **not** `'submit'` by default — a button inside a form does not submit unless asked |
| `disabled` | `boolean` | `false` | |
| `loading` | `boolean` | `false` | disables, sets `aria-busy`, prepends a spinner, **keeps the label** |
| `onClick` | `fn` | — | |

`BUTTON_VARIANTS` and `BUTTON_SIZES` are exported frozen, for tests that enumerate.

**States.**

| State | How |
| --- | --- |
| default | per `VARIANTS` (`Button.jsx:37`), resting shadow on `default`/`primary`/`pagination`, none on `ghost` |
| hover | `hover-hover:hover:` colour swap, 120ms, colour-only |
| focus-visible | `ring-2 ring-brand-ring`, outline removed — a test proves the ring survives the removal |
| active | colour swap **plus `active:translate-y-px`** — the press you can feel; cancelled while disabled |
| disabled | `disabled:opacity-50`, `cursor-not-allowed`, shadow dropped, press cancelled, out of the tab order |
| **busy** | `loading` — this is the state, not the caller's. `isDisabled = disabled \|\| loading` (`:112`), `aria-busy` (`:117`), `Loader2` in the leading slot, `motion-reduce:animate-none`. Rationale in the file: a submit that stays clickable while its write is in flight is how the register gets duplicate members, and 53 call sites solving it each is 53 chances to forget. The label is kept so the accessible name does not vanish and the control does not resize. |
| error | **Not present, deliberately.** A button holds no value, so `aria-invalid` is only passed straight through `...rest` for the one case that matters — a submit described by a form-level error summary. |
| empty | **Not present.** A button with no children is a defect, not a state. |
| loading | see **busy** — the same prop. |
| selected | **Not present.** Selection belongs to the thing selected. `ThemeToggle` uses `aria-pressed` on its own element rather than asking this for a pressed variant. |
| open / closed | **Not present.** A button that discloses carries its own `aria-expanded` at the call site (`Header.jsx` hamburger, `SidebarItem`'s chevron). |
| read-only | **Not present.** Not meaningful for a control that holds nothing. |

**Accessibility contract.** `<button type="button">` or `<a href>`. `aria-busy` while loading.
`size="icon"` renders no text, so the caller **must** pass `aria-label` — the shell's hamburger,
close and bell all do. `touch-target` gives a 44px hit area on a coarse pointer without growing the
visible control (**The Coarse-Pointer Rule**).

**Held by.** `src/components/form/Button.test.jsx` — six states plus the ring, both themes, a
mutation-checked focus ring, an unknown-variant throw, and the 44px hit area. Also exercised by
`Modal.test.jsx`, `Modal.busy.test.jsx`, `Pager.test.jsx`, `DataState.test.jsx`, `shell.test.jsx`.

---

## `Field` / `controlClasses` — `src/components/form/Field.jsx`

**What it is for.** The chrome every form control wears: label, required marker, hint, error — and
the `id` / `aria-describedby` / `aria-invalid` wiring. It does **not** render the control.

**When rather than a neighbour.** Use `Field` directly only when building a *new* control shape;
`TextInput`, `SelectInput`, `SearchableSelect`, `DateField` and `AddressAutocomplete` already go
through it. `children` is a **function** receiving `{ id, describedBy, invalid }`, which is why it
can wrap an `<input>`, a native `<select>`, a listbox trigger and a portalled date sheet alike. A
wrapper that rendered its own `<input>` is how this codebase grew four of them (BR-4).

**Props.**

| Prop | Type | Default | Effect |
| --- | --- | --- | --- |
| `label` | node | — | renders a real `<label htmlFor>`; omitted → no label element at all |
| `required` | `boolean` | — | red `*` (`aria-hidden`) **plus** an `sr-only` "(required)" |
| `error` | node | — | replaces the hint, `role="alert"`, and sets `invalid: true` for the child |
| `hint` | node | — | **suppressed while `error` shows** |
| `children` | `({id, describedBy, invalid}) => node` | — | required; the control |
| `labelClassName` | `string` | — | present, and **no caller in `src/` passes it** |

`controlClasses({ invalid, className })` (`Field.jsx:109`) is the shared control skin: `field-text`
(**The Sixteen-Pixel Rule**), `touch-size` (**The Coarse-Pointer Rule**), 14px radius, 38px floor,
`placeholder:text-ink-muted` (**The Faint-Ink Rule** — a placeholder is text), the focus ring, and
`disabled:opacity-60`.

**States.** These are the wrapper's, not the control's.

| State | How |
| --- | --- |
| default | label above, control, hint below |
| hover / active | **Not present.** The wrapper is not a target; the control inside it is. |
| focus | **Not present on the wrapper.** `controlClasses` puts the ring on the control. |
| disabled | **Not present on the wrapper.** `Field` takes no `disabled`; each control passes its own to its element and `controlClasses` styles it. |
| busy | **Not present.** `SelectInput`/`SearchableSelect` set `aria-busy` on their own element. |
| **error** | `error` renders a `role="alert"` span in `status-suspended`, its id enters `describedBy`, and `invalid: true` reaches the control → `aria-invalid` + `border-status-suspended-dot` |
| empty / loading / selected / open / read-only | **Not present.** All belong to the control. |

**Accessibility contract.** One accessible name, from `<label htmlFor>` — **there is deliberately no
`aria-label` on the control**, because when a visible label and an `aria-label` disagree the
accessible name silently wins over the text a sighted user reads. `describedBy` is assembled from
what actually renders (`Field.jsx:47`), never from what might: an `aria-describedby` naming an
absent element makes the whole description empty in several screen readers.

**Held by.** No test file of its own. It is held through its consumers —
`TextInput.test.jsx` (label association, no competing `aria-label`, describedby resolves, hint
suppressed while an error shows, required announced in words), plus `SelectInput.test.jsx`,
`SearchableSelect.test.jsx`, `DateField.test.jsx`, `AddressAutocomplete.test.jsx`, and the
repository-wide `src/test/inkFaintIsNeverText.test.js`.

---

## `TextInput` — `src/components/form/TextInput.jsx`

**What it is for.** Typed text, single line or multiline.

**When rather than a neighbour.** `multiline` gives a `<textarea>`; anything choosing from a list is
a select, not this. For an address use `AddressAutocomplete`; for a date use `DateField` — never
`kind="date"` on a bare input, and there is a repository guard that says so.

**Props.**

| Prop | Type | Default | Effect |
| --- | --- | --- | --- |
| `kind` | `FieldKind` | `'text'` | sets `type`, `inputMode`, `autoComplete`, `enterKeyHint`, `autoCapitalize`, `spellCheck` together — **throws** on an unknown kind |
| `multiline` | `boolean` | `false` | `<textarea>`; the kind attributes are **dropped** rather than passed and silently ignored |
| `rows` | `number` | `3` | multiline only |
| `label`, `error`, `hint`, `required`, `disabled` | | | forwarded to `Field` |
| `value`, `onChange`, `name`, `placeholder` | | | native |

**The `kind` table** is `src/components/form/fieldKinds.js` — fifteen kinds: `text`, `name`, `email`,
`tel`, `number`, `identifier`, `reference`, `money`, `postcode`, `search`, `date`, `password`,
`newPassword`. The measurement it exists for is in the file: across 43 inputs this application
declared **0 `inputmode`, 0 `enterkeyhint` and 1 `autocomplete`**. Three of its decisions are
deliberate and must not be "tidied":

- `identifier` and `reference` set `autoComplete: 'off'` — the automation arc asked for it; a
  browser filling somebody else's membership number is a data-integrity problem.
- `number`/`identifier`/`money` stay `type="text"` and take their keyboard from `inputMode`;
  `type="number"` brings spinners, silent scroll-wheel edits and `''` for anything unparseable.
- `password` and `newPassword` are two kinds, not one — `current-password` offers a saved
  credential, `new-password` refuses to autofill the old one into a sign-up field.
- `date` (`fieldKinds.js:96`) exists **so a call site asking for one gets the right attributes
  rather than silently getting `text`** — but dates go through `DateField`. `CaptureMember.jsx`'s
  private `Field` router (`:843`) is where `type="date"` becomes `<DateField max="today" />`.

**States.**

| State | How |
| --- | --- |
| default | `controlClasses` |
| hover | **Not present.** A text field has no hover treatment in this design. |
| focus-visible | `ring-2 ring-brand-ring`, no outline shift |
| active | **Not present.** Not meaningful for a text field. |
| disabled | `disabled` on the element, `opacity-60`, `cursor-not-allowed` |
| busy | **Not present.** A text field starts no request. |
| **error** | `error` → `aria-invalid`, `border-status-suspended-dot`, announced `role="alert"` |
| empty | **Not present as a component state.** An empty value shows the `placeholder`, in `ink-muted`. |
| loading | **Not present.** |
| selected | **Not present.** |
| open / closed | **Not present.** No popup. |
| read-only | **Not present.** No `readOnly` prop; it would reach the element through `...rest` but nothing in `src/` does that. |

**Accessibility contract.** `<label htmlFor>` from `Field`; `aria-invalid` and `aria-describedby`
handed down; required announced in words as well as with the asterisk.

**Held by.** `src/components/form/TextInput.test.jsx` — the six applicable states, the keyboard, both
themes, and two mutation-checked source scans: every control wears `field-text` with nothing smaller
overriding it, and every control wears `touch-size`.

---

## `SelectInput` — `src/components/form/SelectInput.jsx`

**What it is for.** A native `<select>`, wrapped in `Field` and given the three states a `<select>`
cannot show by itself.

**When rather than `SearchableSelect`.** *Default to this one.* A native select opens the platform's
own wheel or sheet on Android and iOS — a control every user knows, that handles a long list at
390px better than anything we would build, and that a screen reader announces with no ARIA at all.
Reach for `SearchableSelect` only when the list genuinely needs filtering, or when a value that is
not on the list must be accepted (`allowCustom`).

**Props.**

| Prop | Type | Default | Effect |
| --- | --- | --- | --- |
| `options` | `array` | — | `{value,label}` pairs, plain strings, or **records** |
| `optionValue` / `optionLabel` | `string \| fn` | `option.value` / `option.label` | accessors, so a record list needs no `.map()` at the call site |
| `loading` | `boolean` | `false` | placeholder reads "Loading…", control disabled, `aria-busy`, chevron pulses |
| `emptyLabel` | `string` | `'No options available'` | shown when the list arrived and is empty |
| `loadError` | `error \| null` | `null` | the **choices** failed — *not* `error` |
| `onRetryLoad` | `fn` | — | the retry under the failed control |
| `error` | node | — | the **value** is wrong; announced through `Field` |
| `placeholder` | `string` | — | a real non-selectable `<option value="">`, not a CSS trick |
| `onChange` | `(value, option) => void` | — | value first so old call sites keep working; the record second |

`options.js` coerces every value to a string (`asOptionValue`), because a native `<option value>` is
a string and `7 === "7"` is false — which is how a select silently shows its placeholder while
holding a good selection.

**States.**

| State | How |
| --- | --- |
| default | `controlClasses`, `appearance-none`, one lucide chevron (`pr-9`) so there are not two arrows |
| hover | **Not present.** The native control owns its own hover. |
| focus-visible | `ring-2 ring-brand-ring` |
| active | **Not present.** The open list is the platform's. |
| disabled | `disabled`, or derived: `isDisabled = disabled \|\| loading \|\| isEmpty \|\| failed` (`:66`) |
| **busy** | `loading` → `aria-busy`, "Loading…", disabled, chevron `animate-pulse` with `motion-reduce:animate-none` |
| **error (value)** | `error` → `aria-invalid` + `role="alert"` through `Field` |
| **error (choices)** | `loadError` → placeholder reads **"Unavailable"**, every option is dropped, and `LoadFailureFrame` puts a compact `DataState state="error"` with a retry beneath. Before `06.4` a failed list reached this control as an empty one and read "No options available" — F-P54. |
| empty | `emptyLabel`, and the control disables itself rather than opening onto nothing |
| loading | see **busy** |
| selected | the native `<option>`'s own selection; `data-blank` is set when the value is `''` so `Criteria` can grey an unset filter |
| open / closed | **Not a component state.** The platform owns the popup. |
| read-only | **Not present.** A select is disabled, not read-only. |

**Accessibility contract.** A native `<select>` with a real label, `aria-invalid`,
`aria-describedby`, `aria-busy`. The chevron is `aria-hidden`.

**Held by.** `src/components/form/SelectInput.test.jsx` — including the F-P54 case ("failed is not
empty") and the numeric-id round trip. `PositionTreeSelect.test.jsx` exercises it in a cascade.

---

## `SearchableSelect` — `src/components/form/SearchableSelect.jsx`

**What it is for.** A filtering combobox over a list too long to scan.

**When rather than `SelectInput`.** Only when filtering is genuinely needed, or `allowCustom` is
needed. This control is a full ARIA combobox this repository maintains by hand; the native one is
free and correct. Three things were wrong with the implementation this replaced and two of them were
invisible in review: it had `aria-haspopup="listbox"` but **no `role="combobox"`, no
`aria-controls`, no `aria-activedescendant`**; its options were `<li onClick>` with no key handling
at all; and its clear control was an SVG with an `onClick`.

**Props.**

| Prop | Type | Default | Effect |
| --- | --- | --- | --- |
| `options`, `optionValue`, `optionLabel` | | | as `SelectInput` |
| `loading` | `boolean` | `false` | busy, disabled, spinner replaces the chevron |
| `emptyLabel` | `string` | `'No options available'` | the list arrived and is empty |
| `noMatchLabel` | `string` | `'No matches'` | the filter matched nothing — **a different state from `empty`** |
| `loadError` / `onRetryLoad` | | `null` | the choices failed; same `LoadFailureFrame` treatment |
| `clearable` | `boolean` | `true` | a real `<button aria-label="Clear …">` |
| `allowCustom` | `boolean` | **`false`** | a typed value matching no option survives blur, through the **same** `onChange(value, option)` with no option. The default is load-bearing: three screens consume this control, so one changed default is three screens changed in silence. |
| `onChange` | `(value, option) => void` | — | |

`allowCustom` **does not guess.** A near-miss prompt ("did you mean BA2?") belongs at the call site
where the vocabulary is known (`src/utils/jobGradeMatch.js`). A typed value that *does* name an
option resolves to the option, matched case-insensitively against **both** label and value.

**States.**

| State | How |
| --- | --- |
| default | closed, the box shows the selection (`closedDisplay`, `:113`); under `allowCustom` it shows the raw value when no option carries it |
| hover | option rows take `bg-brand-soft text-brand-ink` on `mouseenter` — which also **moves `activeIndex`**, so the pointer and the keyboard share one notion of "active" |
| focus-visible | `ring-2 ring-brand-ring` on the input; options are never focused |
| active | **Not a class.** `activeIndex` is the active *option*, announced by `aria-activedescendant`. |
| disabled | `isDisabled = disabled \|\| loading \|\| isEmpty \|\| failed` (`:185`); the clear button disappears with it |
| **busy** | `loading` → `aria-busy`, "Loading…", `Loader2` in place of the chevron |
| **error (value)** | through `Field` |
| **error (choices)** | `loadError` → "Unavailable" + `LoadFailureFrame` retry |
| empty | `emptyLabel` in the closed box, control disabled — **distinct from `noMatchLabel`**, which is a `role="presentation"` `<li>` (not an option: an unselectable row inside a listbox is announced as a choice, and this one is a message) |
| loading | see **busy** |
| selected | `aria-selected` on the matching option, plus `font-medium text-brand-ink`; `data-blank` when the value is empty |
| **open / closed** | `open` state → `aria-expanded`, `aria-controls` (only while open), chevron `rotate-180`, the `<ul role="listbox">` renders. The **whole field** is the trigger and a second press closes it, as a native select does. Options commit on `pointerdown`, not `click`, because the outside-dismiss handler fires on pointerdown. |
| read-only | **Not present.** Typing is how it filters. |

**Accessibility contract.** `role="combobox"` on the input, `aria-autocomplete="list"`,
`aria-expanded`, `aria-controls`, `aria-activedescendant` pointing at `${listboxId}-option-${index}`;
`<ul role="listbox">` labelled by the field's label; `role="option"` rows with `aria-selected` and
`aria-disabled`. Keyboard: ArrowDown opens, ArrowUp from closed opens at the **end**, arrows wrap,
Home/End jump the ends of the **filtered** list, Enter selects, Escape closes without selecting and
abandons what was typed. Focus never leaves the input. Options carry `touch-size` — the `07.2` sweep
measured them at 39px.

**Held by.** `src/components/form/SearchableSelect.test.jsx` (414 lines) — states, the full keyboard
set, both themes, the 16px and 44px floors, and an `allowCustom` block in which **every custom-value
case is paired with the same interaction and no prop** (the must-pass that catches a guard passing
for the wrong reason).

---

## `DateField` — `src/components/form/DateField.jsx`

**What it is for.** Every date in this application. It replaced ten native `<input type="date">`.

**When rather than a neighbour.** Always, for a date. **Native `<input type="date">` is banned**, and
`npm run lint` fails on one (`no-restricted-syntax` in `eslint.config.js`). The rule is aimed at the
*element*, not at the string `type="date"` — which is live and correct in five places, because
`CaptureMember.jsx`'s and `OrganizerReport.jsx`'s `Field` routers send that kind to this component.
The count was 10 when the arc opened and is 0 today.

**🔴 It is choose-only, by the owner's ruling (2026-09-10):** *"I don't really want to allow dates to
be typed in manually. Clicking on the field either opens or closes the datepicker."* The box is
`readOnly` (`:725`); the **whole field** toggles the calendar — the owner's original complaint about
the control this replaced was that only the icon opened it.

**Props.**

| Prop | Type | Default | Effect |
| --- | --- | --- | --- |
| `value` | `string` (`yyyy-mm-dd`) | — | shown as `yyyy/mm/dd`; the wire value never changes |
| `onChange` | `({target:{name,value}}) => void` | — | **native-shaped**, so `onChange={handleChange("joinDate")}` needs no edit |
| `min` / `max` | `string \| 'today'` | — | `'today'` is resolved at render |
| `hint` | `'age' \| 'ago'` | — | ⚠️ **this is `hintKind`, not `Field`'s hint** — it chooses the calendar readout's second line |
| `helpText` | node | — | this is what reaches `Field`'s `hint` |
| `error` | node | — | |
| `required`, `disabled`, `name`, `placeholder` | | `'Choose a date'` | |

**States.**

| State | How |
| --- | --- |
| default | `controlClasses` + `cursor-pointer pr-10`, a `CalendarDays` icon button on the right |
| hover | the icon lifts `text-ink-faint → text-ink`; calendar cells take `bg-surface-2` |
| focus-visible | one `FOCUS_RING` constant (`:117`) used everywhere in the file — it was written out at ten sites and a change to it was ten edits that had to stay identical |
| active | **Not present.** The press is the toggle. |
| disabled | `disabled` on the input and the icon button; `openPicker` returns early (`:238`); cells carry `disabled:opacity-40` |
| busy | **Not present.** The component fetches nothing. |
| **error** | `message = errorProp \|\| refusal` (`:383`) → `Field`'s `role="alert"` and `aria-invalid`. ⚠️ **`refusal` is dead** — see *Surprises*. |
| empty | no value → the `placeholder`; the calendar's own readout says so in words rather than showing an empty line |
| loading | **Not present.** |
| **selected** | the chosen day is **filled**; today is **ringed** in gold — two different marks, and a test asserts they are different. `aria-selected` on the day, the month and the year. |
| **open / closed** | `open` → `aria-expanded`, `aria-controls`, a portalled `role="dialog"` panel. Three views: `days`, `months` (a 3-column grid), `years` (a 4×5 grid of exactly 20). ≤760px it is a **modal bottom sheet** with a scrim, a Done button and a page scroll-lock (`aria-modal={isSheet}`); above that a popover that **flips above** the field when it would run off the bottom, and does **not** claim to be modal. |
| **read-only** | **the whole point.** `readOnly` on the input, `onMouseDown` prevented (a read-only box has no caret to place), focus moved by hand. |

**Accessibility contract.** Input is `role="combobox"`, `aria-haspopup="dialog"`,
`aria-expanded`, `aria-controls`. Panel is `role="dialog"` with `aria-label` "Choose {label}".
Day grid is `role="grid"` / `role="row"` / `role="columnheader"` / `role="gridcell"` with **exactly
one tab stop**, not 42; each day's `aria-label` is the long date plus ", today" where it applies.
Month and year grids are `role="listbox"` / `role="option"`. Keyboard, binding for binding from the
approved mock: `← → ↑ ↓`, `Home/End`, `PgUp/PgDn`, `Shift+PgUp/PgDn`, `Enter`, `Space`, `Esc`.
ArrowDown from the field **lands on the selected day**, not the first of the month. Moving around
does not change the answer — only Enter or Space does.

**🔴 It portals into the dialog it was opened from** (`:239`), not to the body: on the body, a
calendar opened from a modal was outside that dialog's pointer layer, focus trap and Escape
handling, and no day could be pressed (R2-F1). The target is read from the DOM when the calendar
**opens**, never from a ref during render.

**Held by.** `src/components/form/DateField.test.jsx` (528 lines) — the whole-field trigger, "nothing
can be typed into it", the wire format, bounds, both grids, the readout, Today/Clear, the full
keyboard set, the ARIA-grid single tab stop, the bottom sheet, both themes, and two mutation-checked
scans including *"is not a native date input, which is the thing this unit removes"*. Also held by
the `no-restricted-syntax` lint rule repository-wide, and by `Modal.test.jsx`, which drives it
inside a dialog.

---

## `CheckboxGroup` — `src/components/form/CheckboxGroup.jsx`

**⚠️ This component has no consumer.** It is exported from `src/components/form/index.js:7` and
**imported by nothing in `src/`**, and it has no test. See *Surprises*.

**What it is for.** A set of checkboxes that answer **one** question — `<fieldset>` + `<legend>`.
The pair is not interchangeable with a `<label>`: a label names one control, a legend names a group,
and a group of checkboxes has no single control to name. Written for BR-14, where four hand-rolled
groups in the members lane put a `<label>` with no `htmlFor` above the group (and one rendered its
labels *empty*).

**Props.** `legend` (node), `options` (array), `selected` (array, default `[]`), `onToggle`
(`(next: array) => void`), `labelFor` (`option => node`, default identity), `className`.

**States.**

| State | How |
| --- | --- |
| default | `rounded-lg border border-line bg-surface-2 p-4`, 1 column, 2 from `sm` |
| hover | `hover-hover:hover:bg-surface` on the whole row |
| focus | **Not declared.** The native checkbox's own ring is what shows; no `focus-visible:ring-2 ring-brand-ring` appears in this file — the only form primitive of which that is true. |
| active | **Not present.** |
| disabled | **Not present.** No `disabled` prop, and none is forwarded to the inputs. |
| busy / error / empty / loading | **Not present.** It is not wrapped in `Field` and cannot show an error or a hint. |
| **selected** | `checked` per `selected.includes(option)`, `accent-brand` |
| open / closed | **Not present.** |
| read-only | **Not present.** |

**Accessibility contract.** `<fieldset>` with a `<legend>`; each box is inside its own `<label>`, so
the row is the target — `touch-target` on the row, not on the 16px box.

**Held by.** **Nothing.**

---

## `PositionTreeSelect` — `src/components/form/PositionTreeSelect.jsx`

**What it is for.** A cascade of selects down the structure tree — one level per depth, each level's
options being the previous selection's children.

**When rather than a neighbour.** Only for the structures tree. Two consumers today
(`Recruiters.jsx`, and its own test). It is **restyled, not rebuilt**: the cascade logic is the
incumbent's and is correct; what was broken was presentational and accessible — every level's
`<select>` had **no accessible name** (the caption was a bare `<label>` with no `htmlFor`), so a
sighted user saw "Level 2" and a screen-reader user reached an unnamed combo box. Routing every
level through `SelectInput` fixed that structurally and brought the 16px rule and the load states
with it.

**Props.** `structures` (tree of `{id,name,children}`), `selectedIds` (`number[]`), `onChange`
(`(ids) => void`), `label`, `placeholder` (default `'Select...'`), `levelLabels` (`string[]`),
`loading`, `loadError`, `onRetryLoad`, `disabled`.

**States.**

| State | How |
| --- | --- |
| default | one `SelectInput` per visible level; captions are `levelLabels[n]` → `Within {parent}` → `Level {n+1}` |
| hover / focus / active | **Not its own.** Each level is a `SelectInput`. |
| disabled | `disabled` forwarded to every level |
| busy / loading | `loading` is forwarded **to level 0 only** — the tree is one request |
| **error (choices)** | `loadError` is forwarded **to level 0 only**; it renders the first level with "Unavailable" and a retry. 🔴 Before `06.4` this returned `null` on a failure too, so the Structures field simply vanished from the recruiter dialogs. |
| error (value) | **Not present.** No `error` prop. |
| **empty** | `structures.length === 0 && !loading && !loadError` → renders `null`. *A tree that failed to arrive is not "no tree"* — that is why the three-way condition exists. |
| selected | held in `selectionsByLevel`; choosing at a level **deletes every selection below it** |
| open / closed / read-only | **Not present.** |

**Accessibility contract.** Every level has a real label via `SelectInput` → `Field`. An outer
`<span>` repeats `label` above the cascade — see *Surprises*.

**Held by.** `src/components/form/PositionTreeSelect.test.jsx` — the accessible-name defect, the
empty-vs-loading-vs-failed distinction, cascade reconstruction from a pre-selection, clearing below
a level, both themes, and the `field-text` inheritance.

---

## `AddressAutocomplete` — `src/components/form/AddressAutocomplete.jsx`

**What it is for.** An address field backed by Google Places suggestions.

**When rather than `TextInput`.** Only for a postal or physical address, and only inside a route
wrapped in `MapsScope`. With no API key the provider mounts, `useMapsLibrary` stays `null`, and this
degrades to a plain text field that still accepts and submits a typed address — that is the honest
behaviour for a back office whose network may not reach Google, and it is why nothing throws.

**Props.** `label`, `value`, `onChange` (native-shaped event), `placeholder`, `error`, `hint`,
`disabled`, `className`. It takes **no `required` and no `name`**.

**States.**

| State | How |
| --- | --- |
| default | `controlClasses` + a `Search` icon (`pr-9`) |
| hover | suggestion rows take `bg-brand-soft text-brand-ink` on `mouseenter`, which also moves `highlightIndex` |
| focus-visible | the shared ring; refocus re-opens the list if predictions are already held |
| active | **Not present.** |
| disabled | `disabled` on the input |
| busy | **Not present as a visible state.** The 300ms debounce and the fetch are silent — there is no spinner and no `aria-busy`, unlike `SearchableSelect`. |
| **error (value)** | `error` → `Field`'s `role="alert"` and `aria-invalid` |
| **error (lookup)** | `suggestionsFailed` **replaces the hint** with *"Address suggestions could not be loaded. Type the full address; it is kept as typed."* — which is true: `onChange` carries every keystroke. 🔴 Before `06.4` a failed lookup looked exactly like an address Google did not know: the list simply never opened. |
| empty | **Not distinguished.** Fewer than 3 characters, no matches, and no Maps library all render identically: the list does not open. |
| loading | **Not present.** |
| selected | `aria-selected={index === highlightIndex}` |
| **open / closed** | `listOpen = isOpen && predictions.length > 0` (`:57`) — declared above the effects that read it, because `const` is not hoisted. Dismissed by `pointerdown` outside, and the listener is attached **only while open**. |
| read-only | **Not present.** |

**Accessibility contract.** `role="combobox"`, `aria-autocomplete="list"`, `aria-expanded`,
`aria-controls` (only while open), `aria-activedescendant`; `<ul role="listbox">` labelled
"{label} suggestions"; `role="option"` rows. Keyboard: ArrowDown/ArrowUp wrap, Enter selects the
highlighted prediction, Escape closes. 🔴 The input's id was the literal string
`"address-autocomplete-input"` — `Members.jsx` renders two of these on one page, so the page shipped
duplicate ids and the second field's label focused the first field's input. It is `useId()` now.

**Held by.** `src/components/form/AddressAutocomplete.test.jsx` (the id collision, mutation-checked;
the combobox semantics; the value flowing out unchanged; `field-text`) and
`src/components/form/AddressAutocomplete.suggestions.test.jsx` (the failed lookup, paired with a
must-pass in which the lookup works and the field says nothing of the kind).

---

## `LoadFailureFrame` — `src/components/form/LoadFailureFrame.jsx`

**What it is for.** A choice control **and**, when its list failed to load, the compact error and
retry beneath it. Both select primitives rendered this frame identically.

**When rather than a bare conditional.** Never call it directly — `SelectInput` and
`SearchableSelect` wrap themselves in it. It exists so that while a list is failed, the caller's
`className` belongs to the *frame* rather than to the `Field` (each primitive passes
`failed ? undefined : className` to its `Field`), so the control and its error lay out as one item.

**Props.** `failed` (`boolean`), `className`, `label`, `loadError`, `onRetryLoad`, `children`.

**States.** Two, and only two. `failed === false` → it returns `children` untouched, rendering no
element at all. `failed === true` → a flex column of the control plus
`<DataState state="error" compact … title={"{label} could not be loaded."} />`. None of the other
ten states apply: it holds no value, takes no pointer and no focus.

**Accessibility contract.** Its own: none. `DataState` contributes the `role="alert"`.

**Held by.** No file of its own. `SelectInput.test.jsx` and `SearchableSelect.test.jsx` both assert
the F-P54 case it exists for.

---

## `FilterForm` — `src/components/form/FilterForm.jsx`

**What it is for.** A search form's criteria as a `<form>` — the same row `Criteria` draws, with a
submit.

**When rather than `Criteria`.** `FilterForm` when the row needs to **submit** (Enter in a field runs
the search); `Criteria` when the row filters live and needs no form. They overlap heavily — see
*Surprises*. Two consumers: `Forms.jsx`, and the barrel.

**Props.** `children`, `onSubmit`, `className`.

**States.** None of the twelve. It is a layout wrapper: flex-wrap, items-end, a 160px basis per
child, 10px radii and the resting shadow pushed onto descendant `input`/`select`, and
`:not(:placeholder-shown)` → `text-brand-ink` so a filled criterion reads in the brand's ink.
`no-print`.

**Accessibility contract.** A `<form>` with no label of its own. It carries no `aria-label` —
`Criteria`, its near-twin, does.

**Held by.** **Nothing.**

---

## `MapsScope` — `src/components/form/MapsScope.jsx`

**What it is for.** The Google Maps `APIProvider`, mounted on **the three routes that render
`AddressAutocomplete`** — `/members`, `/members/capture`, `/banks`. It used to be in `main.jsx`
around the whole application, so all 27 routes — the login screen included — downloaded the Maps API
and the browser key.

**When rather than wrapping the component.** Wrapping `AddressAutocomplete` itself would be the
structural answer and does not work: `Members.jsx` renders two of them as **siblings**, so each
would mount its own provider and race to load the same script.

**Props.** `children`. Nothing else.

**States.** None of the twelve. 🔒 `VITE_GOOGLE_API_KEY` ships in the client bundle by design; it
must be referrer- and API-restricted in the Google console (R-SEC-A), which is a console setting
rather than a code change. A missing key is not a crash.

**Held by.** **Nothing.** It is `lazy()`-imported in `src/App.jsx:32`.

---

# Overlays — `src/components/overlay/`

## `Modal` — `src/components/overlay/Modal.jsx`

**What it is for.** The one dialog primitive, over Radix. It replaced **eight hand-rolled overlays in
four files** (`Recruiters.jsx` ×5, `Members.jsx`, `AcbDebits.jsx`, `BankBranchModal.jsx`) — every
one a bare `fixed inset-0` div, **none portalled**, **none trapping focus**, **one** handling
Escape, disagreeing about `z-index` (`z-10 · 20 · 50 · [60]`) and about whether the page behind
scrolled.

**When rather than a neighbour.** DESIGN.md's refusal is the rule: *don't open a modal for a task
that needs neither interruption nor protected focus; the record edits in place.* So —

- **`MemberPanel`** for looking at or editing a record. It is a column, not an interruption.
- **`ConfirmDialog`** when the only content is "are you sure" — it brings the danger button, the
  disabled Cancel and the busy wiring for free.
- **`DialogNotice`** for a failure *inside* a dialog that is already open — never a page notice.
- **`Modal`** for a genuine form or step that must hold focus until it is answered.
- **`Sheet`** is not an overlay at all; it is the white surface a page's content sits on.

**Props.**

| Prop | Type | Default | Effect |
| --- | --- | --- | --- |
| `open` / `onOpenChange` | | | controlled; it renders **no trigger** |
| `title` | node | — | **required — throws in DEV if absent** (`Modal.jsx:90`). Radix warns and renders anyway; an unnamed dialog announces as "dialog" and nothing else. |
| `titleHidden` | `boolean` | `false` | `sr-only` heading — the name still exists |
| `description` | node | — | absent → `aria-describedby: undefined` is **spread conditionally**, because a present key with an `undefined` value removes the attribute while an absent key leaves Radix's own wiring intact |
| `size` | `'sm' \| 'md' \| 'lg' \| 'xl'` | `'md'` | `max-w-sm/lg/2xl/4xl`; unknown value **throws** |
| `footer` | node | — | a fixed footer bar on `surface-2` |
| `dismissible` | `boolean` | `true` | `false` → Escape and outside presses ignored, **and no X at all** |
| **`busy`** | `boolean` | `false` | see below |
| **`initialFocusRef`** | ref | — | which control gets the caret on open; omitted, Radix focuses the first tabbable thing — the header's **Close** |
| `className` | — | — | **does not exist, and that is the point** |

**🔴 `busy` — a write started from this dialog is running (R2-F3).** Every way out is shut until it
answers: Escape, a press outside, the X, and any `onOpenChange(false)` Radix would send
(`changeOn`, `:101`). Nothing moves — the X stays exactly where it is, `disabled`, and only while
the write is in flight. Two reasons it exists: the result or the failure must land on the surface
the user is still looking at, and a second attempt must not be able to overlap the first. Two call
sites use it: `AcbDebits.jsx:320`, `BankBranchModal.jsx:98` and `WelcomeLetter.jsx` (since `10cbe89`), plus
`ConfirmDialog` internally. ⚠️ `busy` shuts every way out **the dialog owns**; a footer button the caller wires
to `onClose` itself is not one of them, and must be `disabled` by the caller while busy — `ConfirmDialog`'s
Cancel and `WelcomeLetter`'s Close both are.

**🔴 `initialFocusRef` — where the caret goes on open (`10.4`, `d8c4070`).** It replaced `autoFocus` on the
two Recruiters search dialogs, and it is **not** the same thing written differently: `autoFocus` worked only by
a timing accident (React focuses at commit; Radix's focus scope runs in a later passive effect and leaves focus
alone if it finds some inside). Remove the attribute without this and the caret lands on the Close button.
`jsx-a11y/no-autofocus` is an error; this is the replacement. Held by `Modal.initialFocus.test.jsx` (with the
must-pass that omitting it leaves Radix's default intact) and `Recruiters.focus.test.jsx`.

**⚠️ There is no `className` prop, deliberately.** It was there, and it made the height cap a lie:
`cn` merges the caller's classes last and `twMerge` resolves `max-h-*` last-wins, so
`<Modal className="max-h-[90vh]">` silently restored the exact bug the cap prevents — and
`max-h-[90vh]` is literally what `Members.jsx` carried before the migration. The `max-h-[85dvh]`
(`:197`) is **not negotiable per call site**; `100vh` on a phone is the viewport with the URL bar
retracted, so a `vh`-sized dialog puts its Save button under the browser chrome.

**States.**

| State | How |
| --- | --- |
| default | `rounded-card`, hairline, `bg-surface`, `shadow-2`; header and footer fixed, **the body scrolls** |
| hover | on the X only — `hover-hover:hover:bg-surface-2` |
| focus-visible | the X takes the brand ring; the content itself is `focus:outline-none` and receives initial focus from Radix |
| active | **Not present.** |
| disabled | **Not a dialog state.** The X is disabled while `busy`. |
| **busy** | `busy` — the four dismissal paths are blocked, the X is `disabled`. No spinner: the content owns that (`ConfirmDialog` passes `loading` to its confirm `Button`). |
| error | **Not present.** A failure inside a dialog is `DialogNotice`'s job. |
| empty / loading / selected / read-only | **Not present.** The dialog is a frame. |
| **open / closed** | `open` drives Radix; `data-[state=open]` → `animate-in fade-in-0 zoom-in-95`, `data-[state=closed]` → the reverse, both `motion-reduce:animate-none`. `dismissible={false}` removes Escape, outside-press and the X together. |

**Accessibility contract.** Portalled to `document.body`; focus-trapped; Escape-cancelling;
scroll-locking. `role="dialog"` with a required accessible name. Scrim is `bg-ink/50 backdrop-blur-xs`.

**🔴 Centred by a grid, never by a transform** (R2-F1). A transformed element is the containing
block for every `position: fixed` inside it, so a calendar could not be placed inside the dialog,
was portalled to the body, and landed outside the trap, the pointer layer and Escape handling. The
wrapper is `pointer-events-none` so a press outside the content still reaches the overlay.

**🔴 Escape belongs to a popup open inside the dialog first** (R2-F1/F2, `:114`). Radix takes Escape
on the document in the **capture** phase, before a calendar or a list inside can hear it — so the
key meant to close the calendar closed the dialog and everything typed into it. While a
`[role="combobox"][aria-expanded="true"]` or `[aria-haspopup][aria-expanded="true"]` is open inside,
that control gets the key. Scoped to popups on purpose: an expanded table row is not one.

**🔴 Focus restore is ours, not Radix's** (`:139`). Radix returns focus to `Dialog.Trigger`; this
primitive renders none, so `triggerRef.current` was always `null` and focus fell to `<body>` on
every dialog in the application — a keyboard user closing a record landed at the top of the
document and re-tabbed the skip link and the whole rail. It restores to `document.activeElement`
captured at open, **only if it is still in the document**. This survived because both halves look
identical from outside and only the second is felt after the dialog has closed.

**Held by.** `src/components/overlay/Modal.test.jsx` (344 lines) — portalling, the trap, focus
restore, Escape, scroll lock, the required name, `titleHidden`, the `dvh` cap and body scroll,
`dismissible={false}`, and a DateField-inside-a-dialog block. `src/components/overlay/Modal.busy.test.jsx`
— the three blocked exits **each paired with a must-pass** proving they work when not busy.
Repository-wide: the `no-restricted-globals` / `no-restricted-properties` lint rules in `eslint.config.js`.

---

## `ConfirmDialog` — `src/components/overlay/Modal.jsx:277` (named export)

**What it is for.** The destructive confirm. It replaced four `window.confirm()` calls
(`RecruitmentChannel.jsx:71`, `Members.jsx:360`, `Recruiters.jsx:294`, `:346`). `window.confirm` is
unstyleable, says "OK"/"Cancel" rather than naming what will happen, blocks the whole browser
thread, and is **suppressible** — a user who ticks "don't show me these again" loses every
confirmation in the application, including the one that deletes a recruiter and sets fifteen members
inactive.

**When rather than `Modal`.** Whenever the whole content is a question. It is `size="sm"`, wires
`busy={loading}` for you, and disables Cancel for the duration.

**🧪 It is load-bearing in the automation suite.** `MemberRegisterPage` and `MemberCapturePage` carry
`page.on('dialog')` handlers, and **the day those native dialogs vanish those steps pass for the
wrong reason** — a handler for an event that never fires is green forever.

**Props.** `open`, `onOpenChange`, `title`, `description`, `confirmLabel` (default `'Confirm'`),
`cancelLabel` (default `'Cancel'`), `onConfirm`, `destructive` (default **`true`**), `loading`
(default `false`), `children`. It does **not** expose `dismissible`, `size` or `titleHidden`.

**States.** Inherits every `Modal` state, and adds:

| State | How |
| --- | --- |
| **busy** | `loading` → `busy` on the Modal (all four exits shut), Cancel `disabled`, and the confirm `Button` in its own `loading` state — spinner, `aria-busy`, label kept |
| default vs. destructive | `destructive` (default true) → `variant="danger"`; false → `variant="primary"` |

**The confirm names the action.** *"Remove recruiter"*, never "OK" — a sentence a user can check
against what they meant to do. A destructive confirm states the consequence in words.

**Held by.** `Modal.test.jsx` (names the action, states the consequence, Cancel does not confirm,
the destructive action looks destructive) and `Modal.busy.test.jsx` (Cancel disabled and the exits
shut while running, with the not-running must-pass).

---

# Shared — `src/components/common/`

## `DataState` — `src/components/common/DataState.jsx`

**What it is for.** The states a fetch can be in, once. Before this file the application carried
**eight separate empty-state copies across six files, three separate spinners, and exactly one error
state** (`Table`'s).

**When rather than a bare conditional.** Always, when a surface renders the outcome of a request.
A convention — *"render an error state"* — is something twenty-seven surfaces have to remember, and
the ones that forgot are how A27 became a class rather than a row. Use `layout="row"` inside a sheet
(icon left, sentence beside it, action right — card.html's `.state`); the centred column is for
panels and fields. `compact` for a field-sized frame, as `LoadFailureFrame` uses it.

**There are four public states, not three, and a fifth the caller cannot ask for.**

| `state` | Meaning |
| --- | --- |
| `loading` | the answer has not arrived |
| `empty` | the answer arrived and was nothing |
| `error` | the request failed |
| **`partial`** | *some* of several sources answered — *"Some of this is missing. What loaded is shown below. The rest did not answer."* This is the state nobody ships. A screen with nine mount fetches (`CaptureMember`) or four (`Members`) has a fourth outcome: rendered as "loaded" it is a quiet lie; rendered as "error" it blanks a screen that is 75% usable. The model is the automation arc's own reporting tool, which prints `DID NOT RUN` rather than zero. |
| *`slow`* | **internal only.** `loading` after `SLOW_NOTICE_MS`, decided here. |

**🔴 The rule the brief asks for, in the code: a request that has not answered is not an empty
answer, and `error` NEVER renders the empty copy.** `PUBLIC_STATES` (`:96`) is a `Set` and an unknown
state **throws** — validated against `PUBLIC_STATES` and *not* against `DEFAULTS`, because `DEFAULTS`
gained a `slow` entry and a check written as `if (!DEFAULTS[state])` would quietly have started
accepting `state="slow"` from a caller.

**Props.**

| Prop | Type | Default | Effect |
| --- | --- | --- | --- |
| `state` | the four above | — | required; unknown **throws** |
| `title` / `message` | node | per-state defaults | ignored once a load has gone slow — the original copy has stopped being true |
| `error` | rejection | `null` | drives `retryable`, `kind`, `status` |
| `onRetry` | `fn` | — | a retry appears **only** when `onRetry` is given *and* `error?.retryable !== false` |
| `compact` | `boolean` | `false` | smaller icon and type, tighter padding |
| `layout` | `'column' \| 'row'` | `'column'` | |
| `action` | node | — | the caller's own control for the empty state (the register's "Clear") |

**🔴 Retry is offered when it can work, not whenever there is an error.** `retryable` is derived once
in `parseApiError`, so no consumer re-derives it from a status code: a 4xx is the server saying the
request was wrong, and a button that cannot work is worse than no button. An error carrying **no**
`retryable` at all — a thrown `TypeError` — is treated as retryable, because the alternative is a
dead end on the failures we understand least.

**States.**

| State | How |
| --- | --- |
| default | — it *is* the state component |
| hover / active | **Not present.** Only its `Button` and its `<summary>` are targets. |
| focus-visible | the `<summary>` takes `focus-visible:outline-2 outline-brand-ring`; the retry is a `Button` |
| disabled | **Not present.** |
| **busy** | `loading` and `slow` → `Loader2` spinning, `motion-reduce:animate-none`, `role="status" aria-live="polite"`, `data-state` on the wrapper |
| **error** | `AlertTriangle` in `status-suspended`, `role="alert"`, the server's own sentence preferred over ours, the retry when honest, and `kind`/`HTTP {status}` **behind a `<details>`** — EH-D12: what happened and what to do next, **no code in the sentence** |
| **empty** | `Inbox` in `ink-faint`, "Nothing here yet", `role="status"`, no error affordance |
| **loading** | see busy; at `SLOW_NOTICE_MS` it becomes *"This is taking longer than expected. Still waiting for the server. It will stop trying shortly."* The threshold is **imported**, not a `5000` typed here — the notice must arrive before the read ceiling or it never renders. The timer is armed **only while loading**, so no stray deadline fires into the next test under fake timers. |
| selected / open / read-only | **Not present.** |
| *offline* | when `error.kind === 'network'` the icon becomes `WifiOff`, read off `kind` and never off the message |

**Accessibility contract.** `role="status"` + `aria-live="polite"` for loading, slow and empty;
`role="alert"` for error and partial — *a result is `status`, a refusal is `alert`* (EH-D6), and the
two are not interchangeable. `data-state` carries the resolved state (including `slow`) for tests
and captures. The `<summary>` wears `touch-size`: measured under touch emulation it offered a
**49×15** target.

**Held by.** `src/components/common/DataState.test.jsx` — an error never carries the empty copy, the
server's sentence wins, retry on 5xx and not on 4xx, retry with no `retryable` at all, codes behind
`<details>`, the offline icon off `kind`, `status` vs `alert`, the four 5s-notice cases, and *"is not
something a CALLER can claim"* / *"refuses an unknown state"*. Also asserted through
`Table.test.jsx` and `src/test/listStates.test.js`.

---

## `Sheet`, `Page`, `PAGE_GUTTER` — `src/components/common/Sheet.jsx`

**What it is for.** `Sheet` is the white surface — card.css's `.card-surface`: `bg-surface`,
`rounded-lg`, the resting shadow, **no border**, children clipped to its corners. Every list in the
application is one, because the mock has exactly one kind of raised surface. `Page` is the page
frame: the 28px gutter (16px below `lg`), 26px above the head, 28px below the last sheet.
`PAGE_GUTTER` is that gutter as one class string, so a page cannot drift.

**When rather than a neighbour.** `Sheet` is not an overlay and not a card-in-a-card — DESIGN.md
refuses nested cards and same-size card grids. Use `grow` when the sheet's footer (the pager) must
sit at the foot of the sheet rather than under the last row. 24 call sites.

**Props.** `Sheet`: `children`, `className`, `grow` (`boolean`, default `false`), plus `...rest`
spread onto the div. `Page`: `children`, `className`.

**States.** **None of the twelve.** Both are pure layout containers: no pointer state, no focus, no
data state. A sheet holding a failed or empty list gets that from `DataState` or `Table` inside it —
see `ArchiveEmpty`, which is exactly that pairing.

**Accessibility contract.** Neither renders a landmark, a role or a label.

**Held by.** No file of its own; `DateField.test.jsx` mounts it incidentally. Its shape is asserted
indirectly through `src/test/registerRows.structural.test.js`.

---

## `StatusChip` — `src/components/common/StatusChip.jsx`

**What it is for.** A member's status as a 24px pill with a 6px dot and small capitals.

**When rather than a neighbour.** Only for the member-status vocabulary. `NavigationCard`
deliberately does **not** use it for "Not yet available" — set as small capitals, nine of them down
the Monthly Menu formed a second column competing with the nine titles.

**Props.** `status` (string), `className`. That is all.

**The vocabulary is five values**: Active, Pending, Suspended, Resigned, Retired — exported as
`KNOWN_STATUSES`. `CANCELLED_STATUSES` (Resigned, Retired) are the two the register strikes through:
nothing disappears, it cancels.

**States.**

| State | How |
| --- | --- |
| default | one of the five `-bg` / text / `-dot` token triples |
| **unknown** | `bg-surface-2 text-ink-muted` + `bg-ink-faint` dot, and the text "Unknown" when `status` is falsy. 🔴 This is the point: `CANCELLED` and `INACTIVE` belong to the *subscription* vocabulary and have no colour here, where the copy this replaced painted them in the **Suspended red**. |
| hover / focus / active / disabled / busy / error / empty / loading / selected / open / read-only | **None present.** It is a `<span>`, not a control. |

The lookup is **case-insensitive** because there are two vocabularies: a member's status is title
case (`Active`), a subscription's is upper case (`ACTIVE`) because the server declares
`SubscriptionsStatus {PENDING, ACTIVE, CANCELLED, INACTIVE}` with no display names, so Jackson puts
the enum name on the wire. `vocabulary.parity.test.js` records that as **accepted**, not as drift.
The value is **rendered as it arrived** — the small capitals are a `text-transform`, so the DOM
still carries what the server sent.

**Accessibility contract.** Plain text in a `<span>`; the dot is an `aria-hidden` `<i>`. The colour
is never the only carrier — the word is always there.

**Held by.** `src/test/vocabulary.parity.test.js` and `src/test/printTokens.structural.test.js`. It
has **no behavioural test of its own**, and the five-value map is the thing worth one.

---

## `Pager` — `src/components/common/Pager.jsx`

**What it is for.** The sheet's foot — "Showing 1–14 of 2 418 · Page 1 of 173" on the left, Previous
and Next on the right, 32px controls.

**When rather than a neighbour.** It is the *footer of a `Sheet`*, not a standalone control. The
middle slot takes `KeyHints`.

**Props.** `page`, `totalPages`, `onPageChange`, `count`, `start`, `end`, `noun` (default `'rows'`),
`loading` (`false`), `error` (`false`), `hint` (node — hidden below `lg`), `children` (extra
controls before Previous), `className`.

**States.**

| State | How |
| --- | --- |
| default | `Showing <b>{start}–{end}</b> of <b>{formatInt(count)}</b> · Page {page} of {totalPages}`, tabular (**The Tabular Rule**) |
| hover / focus / active / disabled | on the two `Button variant="pagination"` controls, which disable at the ends **and** while loading or failed — *no paging over a list that is not there* |
| **busy / loading** | `Loading {noun}…` |
| **error** | `Could not load {noun}` — 🔴 K1: a list that never arrived is not a list in which nothing matched. This foot used to read "No members match" two lines under "The register could not be reached", which is A27's confident wrong answer surviving one component down. |
| **empty** | `No {noun} match` — only when `count === 0` and neither loading nor failed |
| selected / open / read-only | **Not present.** |

**Accessibility contract.** A `<footer>` element; `no-print`. The buttons carry visible text
("Previous", "Next") beside `aria-hidden` chevrons, so neither needs an `aria-label`.

**Held by.** `src/components/common/Pager.test.jsx` (three cases: failed ≠ empty, a genuinely empty
list still says so, no paging over a list that is not there) and
`src/components/common/pagerError.structural.test.js`, which convicts a pager with no `error` prop
and asserts **no page in `src/` renders a pager that cannot say its list failed** — a population
guard, not a spot check.

---

## `Criteria` / `CRITERION` / `CRITERION_WIDE` — `src/components/common/Criteria.jsx`

**What it is for.** The criteria row — labelled fields printed on the page itself, 140px wide and
willing to shrink to 120, radius 10, on the resting shadow, each value in the brand's ink once one
is chosen.

**When rather than `FilterForm`.** `Criteria` when the row filters live and is not submitted; it is
a `<section aria-label>`, which `FilterForm` is not. `CRITERION` is the class a `Field`-based control
takes to join the row — the primitive owns the label, and these reach its control through it.
`CRITERION_WIDE` (200px basis) for a free-text search that needs room to read.

**Props.** `children`, `className`, `label` (default `'Filter criteria'`).

**📐 The section is the container and the list inside it is the row.** A container query can style
only what is *inside* the container, and the narrow tier in `index.css` has to change the row's own
gap — so the row cannot also be the container. Measured at 1280 with a record docked: 558.4px of row
for 596.2px of criteria, More criteria and Clear (**The Container Rule**).

**States.**

| State | How |
| --- | --- |
| default | `criteria-row no-print`, flex-wrap, items-end |
| **blank** | `[&_[data-blank]]:!font-normal !text-ink-muted` — and it comes **last and shouts**, because a `SearchableSelect`'s blank state is a real value ("Any", "All banks"), so `:not(:placeholder-shown)` was true and every unset filter came out teal. Measured at `rgb(9, 87, 96)` on five screens. |
| **filled** | `:not(:placeholder-shown)` and `:not(:has(option:checked[value='']))` → `text-brand-ink` |
| hover / focus / active / disabled / busy / error / empty / loading / selected / open / read-only | **None present on the wrapper.** All belong to the controls inside it. |

**Accessibility contract.** `<section aria-label="Filter criteria">` — a named region, so the row is
reachable in a landmark rota. `data-criteria-items` is a hook for the stylesheet and for captures.

**Held by.** **Nothing.** Ten call sites and no test.

---

## `KeyHints` — `src/components/common/KeyHints.jsx`

**⚠️ One consumer: `Members.jsx:31`.** It is a one-off in practice, presented here as what it is.

**What it is for.** A row of keycaps naming what the keys do on the surface it sits on —
`[['↑','↓'], 'move']`.

**🔴 It used to live inside the record panel's empty state**, which was the wrong home twice over: it
described the *register's* keys while sitting in the *panel*, and it was on screen only while the
panel was empty — so the moment the owner asked for the empty slot to collapse, the one place the
application taught its own keyboard model went with it. It belongs beside the thing it describes:
the register's own foot, in `Pager`'s middle slot, hidden below `lg` because a phone has no arrow
keys to teach.

**Props.** `hints` (`Array<[string[], string]>`), `className`.

**States.** **None of the twelve.** It is a `<p>` of `<kbd>` elements. 📐 The keycap is `border-b-2`
— a heavier bottom edge is what gives a rendered key its depth, and it is why the design detector
flags this file for a thick border on a rounded element. **That finding is a known false positive on
a `<kbd>` and is left standing rather than suppressed.**

**Held by.** Referenced by `src/components/common/pagerError.structural.test.js` only, as a neighbour
of the pager. No test drives it.

---

## `PageTitle` — `src/components/common/PageTitle.jsx`

**What it is for.** The page head of **every** route — card.html's `.pagehead`. Title left, one line
of description under it, the page's actions on the same baseline to the right.

**When rather than a heading of your own.** Always. 🔴 **BR-18 landed here as one change in two
places.** P0 measured the rendered DOM on all 27 routes: 23 already contained exactly one `<h1>` —
the *shell's*, the same string on every page. So the one landmark a screen-reader user navigates by
said nothing about where they were, and promoting this component alone would have given those 23
routes **two** `<h1>`s (the shape `/reports/organizer-report` had, filed as F-D03). The other half is
in `Header.jsx`: **the topbar carries no heading**, because it names the application and an
application name is not a page heading.

**Props.** `title` (node), `subtitle` (node), `actions` (node), `className`.

**States.** **None of the twelve.** It is the page's heading block. Two typographic decisions are
load-bearing: `text-balance` on the `<h1>` and **`text-pretty`, not `text-balance`, on the
subtitle** — `balance` equalises the lines of a block, which is right for a two-line heading, but on
a 100-character sentence it splits it into two short centred-looking lines of ~320px under a 924px
title. `max-w-prose` lives here rather than on each page (R-RESP-O): several subtitles are full
sentences (Finance Houses' runs to 140 characters) and with nothing constraining them they set as
one line across a 1440px window.

**Accessibility contract.** The route's **only** `<h1>`.

**Held by.** `src/test/headingContract.test.jsx` — it mounts **every route** and asserts no route
renders two `<h1>`s, which is what holds both halves of BR-18 together. Also `shell.test.jsx`.

---

## `Breadcrumbs` — `src/components/common/Breadcrumbs.jsx`

**What it is for.** The trail in the topbar — 13px, muted, the current page in ink. One consumer:
`Header.jsx`.

**Props.** **None.** It reads `useLocation()` and `src/config/navigation.js`, with a title table for
segments that are not in `navItems` (`banks` → "Bank / Branch", `apply` → "Member Application",
`organizer-report` → "Organizer's Report", `daily-menu` → "Daily Menu", `miscellaneous` → "Admin
Menu").

**States.**

| State | How |
| --- | --- |
| default | `Home › … › {current}` |
| `/home` | a single `aria-current="page"` span, no links |
| hover | `hover-hover:hover:text-ink` on the links |
| focus-visible | `outline-2 outline-offset-2 outline-brand-ring` |
| active / disabled / busy / error / empty / loading / selected / open / read-only | **None present.** |

**🔑 The shrinking has an order, and it is the opposite of what it used to be.** Every crumb ahead
of the current page carries `shrink` with no floor and `max-w-[18ch]`; the current page carries a
**7ch floor** and `max-w-[22ch]` and gives way last. Measured on the deepest route in the
application — "Home › Admin Menu › Maintenance › Recruitment channel" at 1280, where the leads reach
their floor 15px short. `crumb-lead` is what the topbar's container query drops first.

**Accessibility contract.** `<nav aria-label="Breadcrumb">` — **labelled explicitly because the rail
is also a `<nav>`**, and two unlabelled navigation landmarks are announced identically, which is
worse than one. `aria-current="page"` on the last crumb. The chevrons are `aria-hidden` and are the
only `ink-faint` here (**The Faint-Ink Rule** — the labels beside them are `ink-muted`).

**Held by.** **Nothing directly.** `shell.test.jsx` asserts the landmark count, which includes it.

---

## `NavigationCard` — `src/components/common/NavigationCard.jsx`

**What it is for.** One destination on a menu page — **a row of the wallet, not a card.**

**🔴 It was a grid of same-size icon-disc cards**, which is the scaffold DESIGN.md refuses by name;
the finish review of 2026-09-10 named it as the one material contradiction on Home. card.html has no
menu page, so the menu takes the register's grammar: a sheet of rows, each with its icon in the
brand's ink, its name, what it holds, and a chevron — read down the page the way the register is.

**Props.** `title`, `icon` (a lucide component), `href`, `desc`, `onSelect`, `note` (the caption of a row with no
`href`, default *Not yet available* — month end's listings say *Waits for sign-in*, being built and refusing until
sign-in; keep it short, it never wraps). `NavigationList` takes `cards`, `onSelect`, `wide` and `className` (month end's
page sets its lists under section headings).

**States.**

| State | How |
| --- | --- |
| default | `<Link>` — icon in `text-brand`, title, description, chevron in `ink-faint` |
| hover | `hover-hover:hover:bg-surface-2`; the chevron slides `translate-x-0.5` |
| focus-visible | `ring-2 ring-inset ring-brand-ring` |
| active | **Not present.** |
| **"no route yet"** | 🔴 **`<Link to={undefined}>` renders an `<a href>` pointing at the current URL.** React-router gives no error and no warning, so the row arrived with a pointer cursor, a hover wash, a chevron promising somewhere to go, and a click that did nothing. Measured on the running app, 2026-09-10: **ten such rows** — all nine of Monthly Menu, and Bank/Branch on Maintenance. A row with no `href` now renders a `<div>`: muted ink, no chevron, no hover, no cursor, **out of the tab order**, and the caption "Not yet available" on the right. The decision lives here rather than on each menu page, so the next entry added without a route cannot ship as a lie. |
| disabled | see above — it is not `disabled`, it is *not a control at all* |
| busy / error / empty / loading / selected / open / read-only | **None present.** |

**Accessibility contract.** A real `<a>` when it goes somewhere; a plain `<div>` when it does not.
The icon is `aria-hidden`. The whole row is the target — a row whose only hit area is its title is
the commonest way a touch target ends up under 44px while looking enormous.

**Held by.** No test of its own. `src/pages/admin/MonthlyMenu.test.jsx` drives both branches through the Month end
page: its statistics as links, and its listings as rows that are not links, with their `note`.

---

## `SidebarItem` — `src/components/common/SidebarItem.jsx`

**What it is for.** One row of the rail — 36px, 14px medium, an 18px icon at stroke 1.75, a 14px
chevron for a section, the active row lifted onto `bg-surface` with the resting shadow. Sub-items
are 32px, text only, indented under the parent's icon. It recurses into `item.subItems`.

**Props.** `item` (`{title, href?, icon?, subItems?}`), `isActive`, `expandedItems` (a map keyed by
href), `onToggle`, `onClick`, `selectedCardHref`, `depth` (default `0`).

**States.**

| State | How |
| --- | --- |
| default | `text-nav-ink` |
| hover | `hover-hover:hover:bg-nav-hover hover:text-ink` — a translucent wash **over the rail**, not a surface |
| focus-visible | `outline-2 outline-offset-2 outline-brand` |
| active | **Not present as a press state.** |
| **selected** | `isActive \|\| selectedCardHref === item.href` → `bg-surface font-semibold text-brand-ink shadow-(--shadow-1)`, plus `aria-current="page"` |
| **open / closed** | the disclosure `<button>` carries `aria-expanded` and `aria-controls` (only while expanded), and its chevron rotates 90° over 120ms |
| **"no route yet"** | `item.href` absent → a `<span title="Not available yet">` in `ink-muted`, not a `<Link>` — the same call `NavigationCard` makes |
| disabled / busy / error / empty / loading / read-only | **None present.** |

**🔴 The row was a `<div onClick>` wrapping a `<Link>`, and both halves were wrong.** For a parent
item the div's handler called `onToggle`, so **the only way to expand a section was a mouse**: no tab
stop, no role, no key handler, no `aria-expanded`. A keyboard user reached the `<Link>`, pressed
Enter, and navigated — the sub-items were unreachable unless the route they landed on happened to
expand them. The disclosure is now its own `<button>` beside the link: the link navigates, the
button expands, and each says what it is.

**🔴 The wrapper is `display: contents`.** card.css's `.nav` is a grid with a 2px gap in which
*every* row is a direct child; with this component's wrapper as a real box only the top-level rows
were grid tracks, so parents spread to 73px while sub-rows stayed at 34–36 inside their own box. The
sub-list wrapper is `contents` too, and it stays in the document so `aria-controls` still resolves.

`INDENT` is written out as `["pl-10","pl-14","pl-[4.5rem]"]` rather than computed from `depth`,
because a utility built from a variable is a class Tailwind never generates —
`src/test/dynamicUtilities.test.js` holds that. `item.color` is **gone**: every `color:` in
`navigation.js` sits in a commented-out block, so both branches were unreachable and both would have
re-admitted a palette literal (**The Token-Only Rule**).

**Accessibility contract.** `<Link aria-current="page">` or a `<span>`; the disclosure is a
`<button aria-expanded aria-controls aria-label="{Expand|Collapse} {title}">`. `touch-size` on the
row so the link's target and its disclosure's never land on a neighbour.

**Held by.** `src/components/shell.test.jsx` (*"a navigation section can be expanded without a
mouse"*, the named main navigation) and `src/components/shell.drawer.test.jsx`.

---

## `ThemeToggle` — `src/components/common/ThemeToggle.jsx`

**What it is for.** The theme control — the rail's foot reads "Appearance · Light".

**🔴 It was a `<div onClick>`**, so the only way to change theme was a mouse: no tab stop, no Enter,
no Space, nothing announced. `01.2` had it on its list and shipped without it, and nothing noticed
until the contrast phase's theme check looked for a `<button>` and found none.

**When rather than a second one.** There is exactly one. `04.1` merged `AuthLayout`'s separate theme
button into this: same job, different markup, a different accessible name and no `aria-pressed` at
first. **Two implementations of one control is two places for the next accessibility fix to be
applied to only one of.** The auth screens use `compact`.

**Props.** `variant` (`'nav' | 'compact'`, default `'nav'`), `compact` (`boolean`, default `false` —
**a second spelling of the same thing**; either triggers the compact branch), `className`.

**States.**

| State | How |
| --- | --- |
| default | nav: a 36px full-width rail row with the label; compact: a 40px round `bg-surface` button with an `sr-only` label |
| hover | `hover-hover:hover:bg-nav-hover` (nav) / `bg-surface-2` (compact) |
| focus-visible | `outline-2 outline-offset-2 outline-brand` (nav) / `outline-brand-ring` (compact) |
| **selected / pressed** | `aria-pressed={darkMode}` — **not `role="switch"`**: this toggles a property of the page the user is already on, which is what a toggle button is for. The visible label names the property *and its current value*. |
| active / disabled / busy / error / empty / loading / open / read-only | **None present.** |

**🎬 The pour.** The switch is the one cinematic moment card.html has: the new appearance pours out
of this button across the window (`src/lib/themePour.js`). The DOM change must land **inside** the
view transition's callback, so the React update is flushed with `flushSync` there, and the `.dark`
class is toggled directly as well because `ThemeContext`'s effect would apply it a frame late, after
the new snapshot had been taken.

**Accessibility contract.** A real `<button type="button" aria-pressed>`. Both presentations share
one accessible name — the compact one's `sr-only` span is the same string a sighted user reads on
the rail. `touch-size` / `touch-target`.

**Held by.** `src/components/shell.test.jsx` — *"an application route offers exactly one, and it is a
real button"*, *"an auth route offers exactly one too — the same component"*, and *"changes the
theme from the keyboard, and says so"*.

---

## `DialogNotice`, `noticeTone`, `NoticeIcon` — `src/components/common/DialogNotice.jsx`

**What it is for.** A failure said **inside** the dialog it happened in.

**🔴 Why it exists:** a page-level notice written while a dialog is open renders **under the
dialog's scrim**, and its focus effect pulls focus out of the dialog, which Radix's trap pulls
straight back. The dialog in front of the user says nothing, and the explanation appears only once
they give up and close it.

**When rather than `DataState`.** `DataState` is for the outcome of a *fetch* on a surface;
`DialogNotice` is for the outcome of a *write* inside an open `Modal`. Three consumers — Recruiters
(`06.4`), Recruitment Channel's delete, Manage Debits' banking update — which is the second and
third consumer that triggered the extraction under conventions §11.

**Props.** `notice` (`{tone?: 'ok'|'error', message: string} | null`), `onDismiss`. Renders `null`
when `notice` is null.

`noticeTone(tone)` and `NoticeIcon({tone})` are exported separately because the page's notice and
this one spelt out the same two tones and the same two icons character for character; the *frame*
around each differs and stays with it.

**States.**

| State | How |
| --- | --- |
| **error** (default) | `border-status-suspended-dot bg-status-suspended-bg text-status-suspended`, `AlertCircle`, `role="alert"` |
| **ok** | `status-active` family, `CheckCircle2`, `role="status"` |
| absent | `notice === null` → renders nothing |
| hover / focus / active | on its Dismiss `Button` only |
| disabled / busy / empty / loading / selected / open / read-only | **None present.** |

**Accessibility contract.** `role="alert"` **rather than the page notice's focus move**: the dialog
owns focus, and pulling it out of a form the user is still filling in would be worse than the
silence this replaces.

**Held by.** **Nothing.**

---

## `OfflineBanner` — `src/components/common/OfflineBanner.jsx`

**One consumer: `Main.jsx`.** That is correct — it is a page-level fact and there is one shell.

**What it is for.** Offline is a **state, not a kind of error** (EH-D11). Without it, a user in a
lift gets *"we could not load the banks"*, *"we could not load the recruiters"*, *"the member
register could not be loaded"* — three true sentences, none of them the one fact that explains all
three, and then they retry each panel in turn, from a lift.

**⚠️ `navigator.onLine` is not a connectivity check.** It reports whether the machine has an
interface that thinks it is up: a café wifi behind a login page, a captive portal, a dropped VPN, a
backend that is down — all `onLine: true`. **`false` is reliable; `true` is not.** So the banner is
driven by **both** signals: `navigator.onLine === false` (the browser is certain) **and** a `network`
rejection (a request genuinely failed to reach anyone — the case `onLine` misses, and the common
one). A banner driven by `onLine` alone would stay hidden through exactly the failures users meet.

**Props.** `lastNetworkFailureAt` (`number|null`, default `null`) and `onRetry`. **It is a timestamp
rather than a boolean so that a second network failure after a dismissal re-opens the banner** — a
boolean cannot express "it happened again".

**States.**

| State | How |
| --- | --- |
| hidden | none of the three signals is up → returns `null` |
| **offline** (certain) | `navigator.onLine === false` → `WifiOff`, *"You are offline. Nothing on this page can be saved or refreshed until the connection is back."* |
| **unreachable** | a `network` rejection while the browser says online → `ServerCrash`, *"SASBO Online could not reach its server just now."* 🔴 K2: it used to say "You are offline" here too, to a user on working wifi whose server had refused the connection, above a register that had just loaded. |
| **reconnected** | the `online` event → *"The connection is back. Anything that failed while you were offline can be tried again."* + a Try again button. **The event is a prompt, not a proof**, so it offers a retry rather than silently re-running requests. |
| dismissed | `dismissedAt` — ⚠️ `browserOffline` is **not** cleared by Dismiss: a banner the user can dismiss while genuinely offline is a banner that lies about a fact that is still true. |
| hover / focus-visible | on Try again and Dismiss; both wear `touch-target` |
| busy / error / empty / loading / selected / open / read-only | **None present.** |

**Accessibility contract.** `role="alert"`, **not `status`** — the user is about to act on screens
that cannot save. `data-offline` and `data-testid="offline-banner"` for captures.

**Held by.** `src/components/common/OfflineBanner.test.jsx` — ten cases including both K2 halves (the
rejection-while-online sentence **and** the must-pass that a genuinely offline browser still says
so), the re-open after dismissal, the refusal to be dismissed while offline, and the 44px targets.

---

## `ErrorBoundary` — `src/components/common/ErrorBoundary.jsx`

**What it is for.** The application's only error boundary, and a class component for that reason.
There was none: a throw during render unmounted the **entire** application and left a white page.
`03.1` made that worse before it made it better — every route is `lazy()`, so a chunk that fails to
load (a bad deploy, a stale `index.html` pointing at a hash that no longer exists) throws during
render on a completely healthy application.

**Where it goes.** Inside the shell, **around the `<Suspense>`, keyed by `pathname`**
(`Main.jsx`). A boundary around `<Routes>` would catch the same errors and still blank the header
and the rail, because the layout renders inside the routes. And a boundary holds its error in state
and renders the fallback whatever its children become — so one page that threw left "This page could
not be displayed." on every route the rail could reach, and the fallback's own *"you can carry on
somewhere else"* was false. The key change remounts it (R1-F1).

**Props.** `children`, `onError` (`(error, info) => void`), `fallback`
(`(error, reset) => node` — overrides the designed one).

**States.**

| State | How |
| --- | --- |
| default | renders `children` |
| **error** | `data-testid="route-error"`, `role="alert"`, `AlertTriangle` in `status-suspended`, and **two** recoveries |
| hover / focus-visible | on the two buttons |
| busy / empty / loading / selected / open / read-only / disabled / active | **None present.** |

**🗣️ The automation arc's constraint, verbatim and unprompted:** *"keep the sentence naming what
failed and the one action that recovers it, and never a stack trace or a chunk name."* The sharp
half is "never a chunk name": the most likely error here is *"Failed to fetch dynamically imported
module: /assets/Members-B7fQ2x.js"*, and rendering it shows a member of a trade union a build
artefact's content hash while the action that actually fixes it is not in the sentence anywhere.
So the two recoveries are different actions and both are offered, in the order that costs least —
**Try again** (re-renders the subtree; keeps what the user typed elsewhere) and **Reload the page**
(the only thing that fixes a stale chunk; `Try again` on one of those simply throws again, which is
a loop the user cannot get out of if it is the only button).

**⚠️ Nothing is said about what the user typed.** The plain fallback used to promise *"Nothing you
entered has been sent"*, and this boundary cannot know that. A reassurance that is sometimes false is
worse than none.

It reports through `reportError` — the sink, not `console.error`, because a render error's message
interpolates the offending **value**, which on these screens is a member.

**Held by.** `src/test/errorBoundary.test.jsx`, `src/test/errorBoundaryRoutes.test.jsx`,
`src/App.routes.test.jsx`, `src/pages/bank_branch/BankBranchModal.a29.test.jsx`,
`src/pages/reports/LocationListing.test.jsx`.

---

## `RoutePending` — `src/components/common/RoutePending.jsx`

**What it is for.** What fills the outlet while a route's chunk is arriving. Deliberately near-silent:
a route chunk on a local network resolves in milliseconds and a spinner that flashes for 40ms reads
as a glitch. `DataState` owns the designed loading states; **this is plumbing and it says so.**

**🔴 The `data-testid` is the load-bearing part.** The route smoke test asserts a mounted route
"produced something" — and a Suspense fallback *is* something. Without a way to tell the fallback
from the page, code splitting would silently have converted that guard into one that passes on every
route whose module fails to load. The test waits for it to **go**, so the guard now proves the chunk
resolved *and* the page rendered, where before it only proved the page rendered.

**Props.** None.

**States.** One: present. `role="status" aria-live="polite"` with an `sr-only` "Loading page…" and no
visible content. None of the other eleven applies.

**Held by.** `src/App.routes.test.jsx`.

---

## `Logo` — `src/components/common/Logo.jsx`

**What it is for.** The union's mark. Seven lines. Two consumers: `Sidebar.jsx`, `Auth.jsx`.

**Props.** `className` (default `"h-12 w-auto"`), `alt` (default `"SASBO Logo"`).

**States.** **None of the twelve.** It is an `<img>`.

**Accessibility contract.** A real `alt`. In `Sidebar` it sits inside a `<Link aria-label="SASBO
Online home">`, so the link's name wins and the alt is not read twice.

**Held by.** **Nothing.**

---

# Tables — `src/components/table/`

## `Table` — `src/components/table/Table.jsx`

**What it is for.** The table shell, and what all ten tabular surfaces become. This application has
**three real `<table>`s and seven `grid-cols-N` pseudo-tables** — ten surfaces, each with its own
answer to headers, empty states, overflow and row actions, **and none of which a `<table>` grep can
find** (BR-19).

**When rather than a bare `<table>`.** Always. It brings the scroller, the four states, the column
priority model and the `rowKey` guard, none of which a hand-written table has.

**Props.**

| Prop | Type | Default | Effect |
| --- | --- | --- | --- |
| `columns` | `array` | `[]` | `{key, header, cell?, priority?, fit?, align?, truncate?, nowrap?, width?, className?, headClassName?}` |
| `rows` | `array` | `[]` | |
| **`rowKey`** | `string \| (row,index)=>id` | — | **required; throws in DEV** |
| `loading` / `error` / `errorMessage` / `onRetry` | | | the failure branch |
| `emptyTitle` / `emptyMessage` / `emptyAction` | | `'Nothing here yet'` | the empty branch |
| `total` | `number` | — | how many exist, vs. `rows` on screen |
| `selectedKey` | | — | the selected row |
| `onRowSelect` | `(row)=>void` | — | the **pointer** convenience; also enables ↑/↓ |
| `dense` | `boolean` | `false` | 8px row padding instead of 11/13 |
| `caption` | `string` | — | `sr-only` `<caption>` **and** the scroller's accessible name |
| `enterKey` | any | — | changes → the rows replay their entry stagger (capped at 10) |

**🔴 `rowKey` is required and its absence throws.** Not a warning, not a fallback to the array index.
A table keyed by index looks perfect until the list reorders or a row is deleted, and then React
reuses the DOM node with the old row's state in it: the checkbox you ticked follows the row that took
its place, the open detail panel describes a different member, an edit-in-place lands on the wrong
record. **Nothing errors.** Guarded by `import.meta.env.DEV` so production degrades to the index
rather than blanking a screen somebody is standing in front of.

**📱 The column priority model (R-RESP-K)** — `essential` (always visible) · `secondary` (visible
from `md`, below that it moves *into* the row's detail panel) · `detail` (never a column). An
unknown priority **throws**. **📐 The column ladder** adds `fit` — `wide` / `medium` / `narrow`, the
*register* width below which the column yields, answered by a container query because docking the
record panel narrows the register to 560–740px without the window moving (**The Container Rule**).
**📱 Truncation is only ever in the cell (R-RESP-I)**: `truncate` also puts the full value in the
cell's `title` and in the detail panel.

**States.**

| State | How |
| --- | --- |
| default | `state = loading ? … : error ? … : rows.length === 0 ? 'empty' : 'rows'` (`:239`) — **one named state, not four growing negation chains**, so `error` can never render as `empty` by construction |
| hover | rows take `hover-hover:hover:bg-surface-2` |
| focus-visible | on the controls inside a row and on the expander; the row itself is never focused |
| active | **Not present.** |
| disabled | **Not present.** A table has no disabled state. |
| **busy / loading** | `SKELETON_ROWS = 8` skeleton rows **plus** `<DataState state="loading" layout="row">`, and `aria-busy` on the `<tbody>`. ⚠️ Skeletons are `data-skeleton`, **not** `data-row`: `data-row` means "a row a person can act on", so marking a skeleton as one would make the loading state indistinguishable from the loaded one to everything that asks. Found by a capture that waited for `tr[data-row]`, matched a skeleton, and found no control inside it. |
| **error** | one full-width `<DataState state="error" layout="row">` with a retry, under a header that stays put. A caller may pass a sentence or the rejection itself; a string has no `retryable`, so it is treated as retryable. |
| **empty** | `<DataState state="empty" layout="row">` with `emptyTitle` / `emptyMessage` / `emptyAction` |
| **selected** | `selectedKey === key` → `bg-brand-soft` (**The One Voice Rule**) and `aria-current="true"` |
| **open / closed** (per row) | `expandable` → a per-row `<button aria-expanded aria-controls-free>` toggling a `data-detail` row below `md`; the chevron rotates 180° |
| read-only | **Not present.** |
| *prefix* | `total > rows.length` → *"Showing the first N of M — refine your search"* (R-SCALE-D), so server paging is a drop-in and a truncated list never implies it is complete |

**Accessibility contract.** A real `<table>` with `<thead scope="col">`, a sticky head at `z-10`, an
`sr-only` `<caption>`, and a `<tbody>` carrying `aria-busy`. **A row's action is a control inside
the row** — run 7 found six controls in this application only a mouse could reach, and click-only
table rows were four of them. `onRowSelect` adds the pointer convenience *on top*: the whole row is a
target for a mouse or a finger while the keyboard keeps the real control, and a press that began on
a control inside the row (or on a text selection) is left alone. ↑/↓ are handled on the **body**, not
on each row, so a row action added by any column inherits them; they move to the next row's
`[data-row-action]`, falling back to its first interactive element. Rows carry `touch-size`,
`content-visibility: auto` and `contain-intrinsic-size` (R-PERF-J) — the size hint is what stops the
scrollbar jumping.

**Held by.** `src/components/table/Table.test.jsx` (265 lines) — the four states, the prefix line and
its inverse, the keyboard, *"offers NO row-level click handler"*, both themes in all four states, a
mutation-checked `rowKey` throw, an unknown-priority throw, and truncation. Plus
`src/test/tableScroller.structural.test.js` and `src/test/registerRows.structural.test.js`.

---

## `TableScroller` — `src/components/table/TableScroller.jsx`

**What it is for.** The thing every table scrolls inside.

**When rather than `overflow-x-auto` by hand.** Never by hand. This is contract ⑮ in its original
form, and the failure it prevents is a matter of record: MobiZone's rule was *"admin tables are
wrapped in `overflow-x-auto`"*, carried by memory — honoured in seven files and forgotten in the
eighth, where it clipped the Actions column off the screen entirely. **This repository is already
one file into the same story**: `Members.jsx` and `LocationListing.jsx` wrap their tables,
`Subscriptions.jsx:122` did not. `Table` renders its `<table>` inside this and nowhere else, so the
ten surfaces do not each get a decision.

**Props.** `children`, `label` (the accessible name, which `Table` fills from `caption`), `className`.

**States.**

| State | How |
| --- | --- |
| default | `overflow-x-auto overscroll-x-contain` |
| **scrollable** | measured, not assumed: `scrollWidth - clientWidth > 1` → it becomes `role="region"` with `tabIndex={0}` and `aria-label`. **Only then** — an always-on tab stop over a table that fits is a keyboard user tabbing through nothing on every screen. |
| **edges** | `edges.start` / `edges.end` → a 24px gradient fade on **the side that has content beyond it**, so the cue is true rather than decorative; both `aria-hidden`, because a screen reader reaches the cells regardless |
| focus-visible | `ring-2 ring-brand-ring` on the scrolling region |
| hover / active / disabled / busy / error / empty / loading / selected / open / read-only | **None present.** |

**Accessibility contract.** `tabIndex={0}` and `role="region"` are not decoration: a scrollable
region that cannot be reached by keyboard is a region a keyboard user cannot scroll (WCAG 2.1.1, and
both Firefox and Chrome ship a devtools warning for it). `ResizeObserver` is optional — it is absent
in jsdom, so the affordance is progressive and a test that cannot resize does not need it.

**Held by.** `src/components/table/TableScroller.test.jsx` (*"the `Table` primitive cannot render its
table outside a scrolling container"*, *"is not a keyboard tab stop while there is nothing to
scroll"*, both themes) and `src/test/tableScroller.structural.test.js`, which asserts **no page in
`src/`** renders a table outside one.

---

# Member surfaces — `src/components/members/`

## `MembershipCard` — `src/components/members/MembershipCard.jsx`

**One consumer: `MemberPanel`.** It is a signature object, not a reusable primitive, and it should
stay that way — **The Material-Once Rule** says gradients, gloss, milled edges and foil exist in
exactly one component, and this is it.

**What it is for.** The thesis of the whole design: *"Every member is a card; the register is the
wallet that hands you that card."*

**Props.** `name`, `membershipNumber`, `status` (default `'Active'`), `since`, `employer`.

**States.**

| State | How |
| --- | --- |
| default | six treatments at once, and it is a card rather than a teal rectangle only because all six are present: the ① gradient (`metal-a → metal-b → metal-c` at 135°), ② gloss (radial from top-left), ③ shade (radial into bottom-right), ④ milled edges (inset light on top, dark underneath), ⑤ satin, ⑥ sweep. `aspect-[1.586]` is the **ISO/IEC 7810 ID-1 ratio** — the shape of every bank card already in the member's wallet. |
| **status** | five engraved variants (`ENGRAVED_STATUS`), matched case-insensitively — chips drawn as an inset ring rather than a fill, because a `StatusChip` on metal would be a chip on a card |
| hover / focus / active / disabled / busy / error / loading / selected / open / read-only | **None present.** It is not a control and takes no pointer. |
| empty | **Not a state.** `MemberPanel` passes `'—'` for a missing name; a missing `since` yields an empty tenure string. |
| *arriving* | the sweep and the hinged tilt are driven by `panel-arriving` on the **panel**, not by a prop here |

`min-h-max` is deliberate: a long name pushes the card taller rather than clipping, because a member
whose name does not fit is a member whose card is wrong.

**Accessibility contract.** `aria-label="Membership card"` and `data-testid="membership-card"`. The
crest and its foil are `aria-hidden`. ✍️ **Marcellus appears exactly here and nowhere else** (**The
One Engraving Rule**); the union's own name on the card is Schibsted, as the mock sets it.

**Held by.** No test of its own. `src/pages/members/capturePayload.test.jsx` references it; the
card's tokens are held by `src/test/cardTokens.contrast.test.js`.

---

## `MemberPanel` — `src/components/members/MemberPanel.jsx`

**One consumer: `Members.jsx`.** It is the register's third column, not a general drawer primitive.

**What it is for.** The record panel — the thing DESIGN.md's contract was written around, and the
reason *"a modal for tasks that need neither interruption nor protected focus"* is a refusal: **the
record edits in place.**

**When rather than `Modal`.** Whenever the user is reading or editing a record and the register
behind should stay visible and live. Use `Modal` only for a step that must hold focus.

**📐 It is a column of the shell, not of the page.** The mock's grid is rail | main | panel, so the
panel stands beside the topbar rather than under it. The page renders it wherever it likes; the
component **portals itself into `PANEL_SLOT_ID`** when the shell is there and renders in place when
it is not — the tests mount pages without the shell, and a panel that disappeared in a test would be
a panel nobody could assert on.

**Props.** `member` (`object|null`), `open` (`boolean`), `onClose`, `children`, `footer`.
⚠️ Its own JSDoc also lists **`remarks?: string`**, which the component does not destructure and
never reads — see *Surprises*.

**States.**

| State | How |
| --- | --- |
| **open (docked, ≥ `xl`)** | a real third column at `clamp(424px, 32vw, 448px)`, full height, own scroll, `xl:border-l` |
| **closed (docked)** | 🔴 **the column closes rather than showing an empty slot** — the owner, 2026-09-10: *"Is it not better to rather close that right hand bar instead of showing an empty card slot?"* It **collapses** (`xl:w-0`) rather than unmounting, so the width animates over `--duration-panel`; an instant 448px reflow of the register is a jump this design does not make. It also gives the register that width back: the register's columns come from a container query, so at 1440 it was reading ~680px and dropping to its narrow ladder. |
| **open (drawer, < `xl`)** | `max-xl:translate-x-0` over a `bg-ink/25` scrim, `min(448px,100vw)`, `shadow-2` |
| **closed (drawer)** | `translate-x-full`, and `max-xl:hidden` once there is no member at all — it **slides** now; `hidden` used to cut it out of the layout on the same frame the record was cleared, so the phone's close was the one movement in the application with no motion |
| hover / active | **Not present on the panel.** Its Close is a `Button`. |
| focus-visible | the "Member record" heading is `tabIndex={-1}` with `outline-2 outline-brand-ring` |
| busy / error / empty / loading | **None present.** The panel renders whatever `children` the page gives it; the *page* owns the fetch states. |
| selected | **Not present.** The selection lives on the `Table` row. |
| read-only | **Not present.** |

**🔴 Focus (F-P55).** Opening a record moved no focus and closing it dropped focus to the page: a
keyboard user who chose a member stayed on the row with the record somewhere to the right, a screen
reader was told nothing had opened, and closing lost their place in a register of thousands. Focus
now goes to the record's heading on arrival and **back to whatever held it** on close. The scrim is
`aria-hidden` and `tabIndex={-1}` for the same reason: it was a second "Close member details" button
for a keyboard, beside the real one in the record's head.

**🎬 The arrival.** When a member changes the card is drawn from the wallet and laid on the desk —
580ms, the one animation allowed past the 400ms budget — a band of light sweeps the metal, and the
record's fields rise under it in a stagger **capped at ten**. It plays once per member, never on
re-render: the class is set on the DOM node (as the mock's `arrive()` does) rather than held in
state, so a re-render in that window can neither restart it nor be caused by it. The scroller resets
to the top with it, or opening a second member from halfway down the first drops you into the middle
of a stranger's employment details. **The Reduced-Motion Rule** collapses it to a fade.

**Accessibility contract.** `<aside aria-label="Member details">` when open (a complementary region
the automation arc finds by that name), `aria-hidden="true"` when not. `data-testid="member-panel"`,
`data-open`.

**Held by.** **No test of its own.** It is driven only through `Members.jsx`'s page tests.

---

## `FigureStrip` — `src/components/members/FigureStrip.jsx`

**Two consumers: `Members.jsx`, and the first screen's figures (`src/pages/home/HomeFigures.jsx`, month end `02.3`).**
They are two instruments — the register counts the list it holds, the first screen reads the server's summary — and
one strip draws both (`02.4`, BR9).

**What it is for.** The register's totals as a **ruled figure strip** — a ledger line, not tiles.
DESIGN.md's refusals name the alternative explicitly ("hero-metric tiles, dashboards of same-size
cards, nested cards"), so **the shape is the decision**: a ledger rules a line above and below its
figures and separates them with hairlines, because a box says *"this is a thing"* and a ruled column
says *"this is a reading of one thing"*.

**Props.** `figures` — the figures **as data**, ruled in the order given: `{ label, value, dot?, quiet?,
breakdown?, definition? }`, where `definition` is the figure's second line saying what it counted (ME-10) and a
`value` of `null` reads *not available*, never a nought — `label` (the strip's name for a screen reader, default
*Register summary*), `state` (`'ready'|'loading'|'error'`, default `'ready'`), `className`. **The register's own
props are still accepted** and become that list through the exported `registerFigures`: `total`, `active`, `pending`,
`suspended`, `newThisMonth` and `newDebitOrders` (optional — the month's joiners and how many of them pay by debit
order; absent means that figure is not rendered), `updatedToday` (optional — absent means that figure is not rendered
at all). A `breakdown` or a `definition` is printed only once its figure was read.

**"New this month" (SASBO, 18 Sep 2026).** One quiet figure with a second line, "N by debit order" —
not two figures, because six divide evenly into the stylesheet's three- and two-column modes where
seven leave one figure alone on a row (ruling ③). The second line is the figure's make-up, printed only
once the figure itself was read (K1 holds for it too).

**States.**

| State | How |
| --- | --- |
| default (`ready`) | six figures (fewer when the optional ones are absent), tabular (**The Tabular Rule**), space-separated thousands (`2 418` — en-ZA, a no-break space, so a figure can never wrap mid-number); "New this month" carries its "N by debit order" line |
| **loading** | a shimmer skeleton per figure **plus an `sr-only` "loading"**, and `aria-busy` on the `<dl>` |
| **error** | an `ink-muted` em dash **plus an `sr-only` "not available"** |
| **empty** | a register that really is empty still reads **zero** — that case is tested, and it is the must-pass for the two above |
| hover / focus / active / disabled / busy / selected / open / read-only | **None present.** |

**🔴 K1: a figure nobody read is not a zero.** The counts come from the register the page holds, so
while it loads — and when it could not be reached at all — every one is 0, and the strip printed
"Total 0 · Active 0 · Pending 0 · Suspended 0": the union with no members, over the sentence saying
the server had not answered.

⚠️ The skeleton is sized **inline**, because `.skeleton` is an unlayered rule in `index.css` and
utility classes cannot resize it. `Table`'s skeleton took the same fix for its status pill.

**Accessibility contract.** `<dl aria-label="Register summary">` with `<dt>`/`<dd>` pairs,
`aria-busy` while loading, `data-testid="figure-strip"`. The status dots are `aria-hidden`.
📐 The columns are the stylesheet's — one equal column per figure, then three, then two — answering
**the strip's own width** (**The Container Rule**), which is why the component is a wrapper plus a list.
Measured at 1280×661 with scrollbars shown: six columns of 160 px in one row at full width, 3 + 3 at
534 px with a record docked, no label overflowing. **Each figure is three rows of the strip's own grid** (a
subgrid: label, value, second line), and a label wider than its column takes a second line and sits on its value —
so the first screen's *Members on record* beside the rail moves its whole row down with it, and the values stay level.

**Held by.** `src/components/members/FigureStrip.test.jsx` — the three cases above, with the genuinely
empty register as the must-pass, plus the six-figure arrangement and a caller that passes no month
figure; `src/pages/members/Members.newThisMonth.test.jsx` reads the month against a pinned calendar.
`src/components/members/FigureStrip.data.test.jsx` holds the figures given as data — the order, the definitions, a
`null` that is not a nought, and the register's props drawing the identical strip; `src/pages/Home.figures.test.jsx`
holds the first screen's five states.

---

## `WelcomeLetter` — `src/components/members/WelcomeLetter.jsx`

**One consumer: `CaptureMember.jsx`.** A screen-specific dialog, not a primitive.

**What it is for.** The preview of the Sasbo Welcome Letter, shown after a member is captured, with a
button that asks the backend to email the filled `.docx`.

**🔑 It takes the Card's world by rule.** card.html drew no letter, so the preview uses the world's
materials rather than a page of its own: `surface` on the dialog's ground, ruled with `line`, ink in
`ink` and `ink-muted`, in the application's type, in both themes. The words are the template's,
including its sign-off, so the preview says what the member will receive.

**Props.** `open`, `onClose`, `member`. Renders `null` when `member` is null.

**States.** `sendState` is `idle | sending | sent | error`.

| State | How |
| --- | --- |
| idle | the preview and a Send button |
| **busy** (`sending`) | `Loader2`, the `Modal` in `busy`, **and its footer Close disabled** — no way out mid-send (R2-F3, fixed `10cbe89` after this inventory found it) |
| **sent** | `CheckCircle2`, announced as a `status` |
| **error** | `AlertCircle`, announced as an `alert`, and the letter can be sent again |
| **A51 — reset per member** | `CaptureMember` keeps the dialog mounted and swaps its member, so a state never reset carried "sent" onto the next member captured in the same visit, whose dialog opened with **no Send button**. Reset during render when `member.id` changes, never on a re-render of the same member. |
| **A56 — no address** | a member with no email at all: Send is **not offered**, and the dialog says why. Capture saves members with no email, and the dialog used to read "emailed as an attachment to —" beside an enabled Send that the server refused with a 400. |
| **A52 — no membership number** | nothing assigns one yet (A54, the server's), so the preview used to show "—", which reads as a number to come. It now says, before Send is pressed, that the letter carries no number. |
| hover / focus / active / disabled | on its `Button`s |
| empty / loading / selected / open / read-only | open/closed is the `Modal`'s; the rest are not present |

**Accessibility contract.** Everything `Modal` provides, plus `role="status"` on a sent outcome and
`role="alert"` on a failure — *the outcome of a send is announced, not only drawn.*

**Held by.** `src/components/members/WelcomeLetter.test.jsx` — A50, A51, A52 and A56, **each paired
with its must-pass** (a server failure is still reported; reopening the same member keeps what was
sent; a work-address-only member can be sent the letter; a member who has a number is shown it and
sees no warning) — and R2-F3's pair: while sending, Escape does not close it and **both** Close buttons are
disabled; once the send answers, Escape closes it again.

---

## `ArchiveEmpty` — `src/components/archives/ArchiveEmpty.jsx`

**Three consumers, all archives.** It is a composition, not a primitive: `Sheet` + `DataState`.

**What it is for.** 🔴 A search that matched nothing left the page blank. The list simply stopped
rendering, and the only signal was the "Found 0 …" line above — which itself only appears while a
search is typed, and which reads as a fact about the archive rather than about the query. **A blank
page with a filled search box is the shape a user reads as "broken"**, and there was no way out of it
but to clear the field by hand.

**Props.** `searchQuery`, `onClear`.

**States.** Two, both `DataState state="empty"`:

| State | How |
| --- | --- |
| **empty, no query** | *"Nothing here yet."*, no action |
| **empty, with a query** | *"Nothing matches that search." / "Try fewer words, or clear the search to see everything again."* + a **Clear the search** button |
| every other state | **Not present.** It renders only the empty case; the archives' loading and error states are the pages' own. |

**Held by.** **Nothing.**

---

# The shell — `src/components/`

`src/components/shellIds.js` is the module holding `SIDEBAR_ID`, `MENU_BUTTON_ID`,
`MAIN_CONTENT_ID` and `PANEL_SLOT_ID`. It exists as a module rather than as three exports beside the
elements because `Header` needs the drawer's id, the drawer needs the hamburger's id, and the skip
link needs `<main>`'s id — exporting each from the file that renders it made `Header` import from
`Sidebar` and `Sidebar` import from `Header`, a cycle for two string constants. ⚠️ **Every one of
these relationships fails silently when it breaks**: an `aria-controls` naming an absent element is
simply ignored; `getElementById(...)?.focus()` on a renamed id does nothing; a skip link whose
fragment names nothing moves the caret and not the focus. `shell.test.jsx` asserts each id
**resolves**, not merely that the attribute is present.

---

## `Header` (+ `searchKind`) — `src/components/Header.jsx`

**What it is for.** The topbar, **60px**, `auto | minmax(220px,440px) | 1fr`: **crumbs, search,
tools.** It replaced a **160px** band (`h-40`) carrying a 24px application title, an avatar, the
theme toggle and the breadcrumbs stacked underneath — a sixth of a 1080px screen, on every route,
saying *"Membership Administration System"* to somebody who has been looking at this application all
morning.

**🔑 The search is the point of the row.** It lives in the shell because it is how you get to a
member from anywhere: typing here on any other route navigates to `/members` with the query applied
and keeps focus in the field. ⌘K, Ctrl+K and `/` focus it, as the mock's `kbd` promises.

**Props.** `search` (default `''`), `setSearch`, `sideBarOpen`, `setSideBarOpen`.

**`searchKind(query)`** is exported and pure: it names what the register recognised — Membership
number, I.D. number, Cellphone number, Phone number, Employee number, Email address, I.D. or account
number, Name or surname — so the user knows the search understood them before the rows move.

**States.**

| State | How |
| --- | --- |
| default | crumbs (hidden below 1101px), the search pill, the bell and the account chip |
| hover | on the hamburger and the bell, through `Button` |
| **focus-within** | the search label takes `border-brand`, `bg-surface` and a 3px `brand-ring` glow — the focus treatment is on the **label**, which is also the target |
| **recognised** | `searchKind` non-empty → a `brand-soft` pill beside the field, `aria-live="polite"` |
| **narrow** | below 640px the placeholder shortens to "Search members"; the long form was cut mid-word ("Search members: su"), and a half-word in the most prominent control reads as breakage. A placeholder is an **attribute**, so no media query can swap it and the component has to know the width itself. |
| **disabled** | the bell: 🔴 it had a hover, a focus ring and **no handler**. There is no notifications feature. It is `disabled`, out of the tab order, and its name says what is true — *"Notifications — none yet"*. |
| busy / error / empty / loading / selected / read-only | **None present.** |
| open / closed | the hamburger's `aria-expanded` reports the **drawer's** state; the topbar itself has none |

**Accessibility contract.** `<header>` — and it is a `banner` landmark only because `Main.jsx` fixed
the nesting (see below). The hamburger has `aria-label`, `aria-expanded` and `aria-controls={SIDEBAR_ID}`
— an icon-only `<button>` whose only child is an SVG announces as "button" and nothing else, so the
one control that opens navigation on a phone was unusable by anyone not looking at it. The search has
an `sr-only` "Search members" label. **The account pill is `aria-hidden` and not a control**: there
is no signed-in person for it to belong to (authentication is deferred, R-SEC-C), so it stays as the
shell's anchor as a non-interactive chip assistive technology is not told to believe in.
**This carries no `<h1>`** — see `PageTitle`.

`shrink-0` on the header is load-bearing: it is a flex child of a column that overflows on every long
register, and a flex column squashes its children to their content before it scrolls — measured at
**39px instead of 60** on Members.

**Held by.** `src/components/shell.test.jsx` — the named menu button pointing at a drawer that
exists, the drawer reporting itself, *"the account placeholder is not a control, because there is no
account"*, the landmark count, the skip link, the id resolution, the 44px targets, and BR-18.

---

## `Sidebar` — `src/components/Sidebar.jsx`

**What it is for.** The rail — card.html's `.side`: the quiet second layer, a full step below the
page ground, 256px, the wordmark at the top, the navigation as 36px rows, and a foot carrying
Appearance and Log out. Below `lg` it is the same column **as a drawer** (this application's measured
phone treatment; the mock only ever specified a desk).

**Props.** `sideBarOpen`, `setSideBarOpen`, `selectedCardHref`. It is wrapped in `memo()` because the
topbar's search lives in `AppLayout`, so every keystroke re-renders the shell while the rail's props
never change — **its props must stay stable; an inline callback passed here would quietly undo it.**

**States.**

| State | How |
| --- | --- |
| default (desk, ≥ `lg`) | `lg:static`, always visible, never inert |
| **open (drawer)** | `translate-x-0`, `shadow-2`, a `bg-ink/25` scrim |
| **closed (drawer)** | `-translate-x-64` **and `inert`** — 🔴 R1-F2: a translated element is still focusable, so a keyboard user on a narrow window tabbed through about **twenty stops they could not see** before reaching the page (WCAG 2.4.3 / 2.4.7). `inert` removes the subtree from the tab order and the accessibility tree, applied **only** when the drawer is genuinely off canvas. Pixel-neutral. |
| **focus on open** | 🔴 R1-F2, the other half: opening the drawer moved no focus, so the hamburger stayed focused behind the scrim. Focus goes to the drawer's own close button — via a **ref, not a selector on the label**, because the first spelling found it by `[aria-label="Close navigation menu"]` and bound the behaviour to a copy string 100 lines away. |
| **expanded sections** | the section holding the current route is open when you arrive, including on a deep link from a bookmark. `findAncestors` returns the **shortest** chain, because Members appears twice in the menu and the first match in reading order is the one three deep under Admin Menu → Daily Menu, which opened two sections on every visit to the register. |
| hover / focus-visible / selected | on the rows — see `SidebarItem` |
| busy / error / empty / loading / read-only / disabled | **None present.** |

**⚠️ `BELOW_DESK = "(max-width: 1023px)"` must agree with Tailwind's `lg:`, and nothing enforces
that.** If the two ever disagree the desk rail is `inert` while fully visible — a silent, total
keyboard lockout of the navigation. The constant is named so the pairing is at least visible; one
shared breakpoint source is a job for the arc's closing refactor.

**Accessibility contract.** `<nav id={SIDEBAR_ID} aria-label="Main">` — 🔴 a `<div>` held the entire
navigation, the only way to reach 25 of 27 routes, with no landmark at all. The label is explicit
because `Breadcrumbs` is also a `<nav>`. **Escape closes the drawer and returns focus to the
hamburger** (until `05.4` the drawer had exactly two ways out and both were a mouse: the scrim and
the X — fine on a phone, a one-way door for a keyboard user on a narrow window). The scrim is
`aria-hidden` **because it is truly decorative**, not to satisfy a linter: it is a transparent
full-screen div whose only job is to catch a click aimed at "not the menu", and the keyboard
equivalent is Escape. Measured 2026-09-10: with `aria-hidden` both `jsx-a11y` rules go quiet; with
`role="none"` instead, `role-supports-aria-props` fires.

**Held by.** `src/components/shell.test.jsx` and `src/components/shell.drawer.test.jsx` — the inert
closed drawer, focus on open, the desk-width must-pass (*"at desk width the rail is reachable and
nothing is inert"*), and R1-F4 (the wordmark is a router link, not a document navigation: it was a
raw `<a href="/home">`, so the one thing at the top of every screen **reloaded the whole
application** — the entry chunk re-fetched, the search and any mid-edit record dropped with no
confirm, and on a phone the drawer left open because `setSideBarOpen` never ran).

---

## `Main` — `src/components/Main.jsx`

**What it is for.** The scrolling column under the sticky topbar: `Header`, the offline banner, the
error boundary, the Suspense outlet.

**Props.** `sideBarOpen`, `setSideBarOpen`, `setSelectedCardHref`, `search`, `setSearch` — the last
four are also passed down the router `Outlet` context.

**🔴 The landmarks were nested wrong, and the cost was that there were none.** `<main>` used to be
the outer element with `<Header>` inside it — and HTML maps a `<header>` to the `banner` role **only
when it is not a descendant** of `<main>`, `<article>`, `<aside>`, `<nav>` or `<section>`. So the
application rendered a header and a main region and a screen reader was offered exactly **one**
landmark. The elements were right and the nesting silently voided one.

**States.** None of the twelve are `Main`'s own. What it composes:

- `OfflineBanner` sits **above** the boundary, so it survives a screen that threw — an offline banner
  that disappears with the screen it was explaining is the one moment it was for. Its `onRetry` is a
  reload, and that is honest: every panel owns its own retry, and this one control stands for
  "everything that failed while I was offline", which no single panel can re-run.
- `ErrorBoundary` is **inside the shell and keyed by `pathname`**, wrapping the `<Suspense>`.
- `<main id={MAIN_CONTENT_ID} tabIndex={-1}>` — focusable so the skip link moves **focus** and not
  just the caret.
- The footer band that used to sit under every route is gone with the mock; the application's name is
  in the rail's wordmark.

**Held by.** `src/components/shell.test.jsx`, `src/App.routes.test.jsx`,
`src/test/errorBoundaryRoutes.test.jsx`.

---

## `Footer` — `src/components/Footer.jsx`

**One consumer: `src/layouts/AuthLayout.jsx`.** It is the auth screens' line and nothing else — the
application shell has no footer.

**What it is for.** `© 2026 SASBO — The Finance Union`.

**Props.** None. 🔴 **It had an `absolute` mode and that mode was the bug**: `absolute bottom-0 z-50`
over a translucent ground drew the footer *on top of* the page rather than after it, so any content
tall enough to reach the bottom of the window ran underneath it — which is exactly what the login
card did. Its only caller was `AuthLayout`, so the mode was removed rather than left for the next
layout to reach for: **a footer that can overlap content is not a variant worth keeping.**

**States.** None of the twelve.

**Held by.** **Nothing.**

---

# Gaps, one-offs and overlaps

## Components that are one-offs, and should be read as such

| Component | Consumers | Read as |
| --- | --- | --- |
| `CheckboxGroup` | **0** | dead code — exported, never imported, never tested |
| `KeyHints` | 1 (`Members.jsx`) | a register affordance, not a primitive |
| `MembershipCard` | 1 (`MemberPanel`) | the signature object; **The Material-Once Rule** says keep it so |
| `MemberPanel` | 1 (`Members.jsx`) | the register's third column |
| `FigureStrip` | 2 (`Members.jsx`, `HomeFigures.jsx`) | the register's totals, and the first screen's figures |
| `WelcomeLetter` | 1 (`CaptureMember.jsx`) | a screen-specific dialog |
| `OfflineBanner` | 1 (`Main.jsx`) | correct — a shell-level fact |
| `Footer` | 1 (`AuthLayout.jsx`) | the auth screens' line |
| `Breadcrumbs` | 1 (`Header.jsx`) | shell furniture |
| `MapsScope` | 1 (`App.jsx`, 3 routes) | correct — one provider per page |
| `FilterForm` | 1 (`Forms.jsx`) | overlaps `Criteria` |
| `PositionTreeSelect` | 1 (`Recruiters.jsx`) | structures-tree only |
| `ArchiveEmpty` | 3 archives | a composition, not a primitive |
| `LoadFailureFrame` | 2 (both selects) | internal to the select pair |

## Components with no test at all

`CheckboxGroup` · `Criteria` · `NavigationCard` · `DialogNotice` · `FilterForm` · `Logo` ·
`Footer` · `ArchiveEmpty` · `MapsScope` · `MemberPanel` · `Breadcrumbs`.

`StatusChip` and `MembershipCard` have no *behavioural* test — only token scans.
`Field`, `LoadFailureFrame`, `Sheet`, `SidebarItem`, `ThemeToggle`, `PageTitle`, `RoutePending` and
`Header` have no file of their own but are genuinely driven by named cases elsewhere.

## Repository-wide guards that hold this layer

These fail the suite on a violation anywhere in `src/`, which is why several rules above are facts
rather than conventions.

| Guard | What it holds |
| --- | --- |
| `eslint.config.js` — `no-restricted-syntax` | no native `<input type="date">` — the `DateField` ban *(a lint rule since P10's refactor pass; its canary pair is in `eslintJsxUsage.test.js`)* |
| `eslint.config.js` — `no-restricted-globals` / `-properties` | no `alert`/`confirm`/`prompt`, bare or via `window` — the `ConfirmDialog` ban |
| `src/test/tableScroller.structural.test.js` | no table outside a `TableScroller` |
| `src/test/listStates.test.js` | no page whose only outcome is data |
| `src/components/common/pagerError.structural.test.js` | no pager that cannot say its list failed |
| `src/test/inkFaintIsNeverText.test.js` | **The Faint-Ink Rule** |
| `src/test/paletteLiterals.test.js` | **The Token-Only Rule**, and no `dark:` twin |
| `src/test/viewportUnits.test.js` | no static `vh` |
| `src/test/hoverIntent.test.js` | no bare `hover:` where hover is not real |
| `src/test/dynamicUtilities.test.js` | no Tailwind utility assembled at runtime |
| `src/test/printTokens.structural.test.js` | printing never inherits the dark theme |
| `src/test/headingContract.test.jsx` | one `<h1>` per route |
| `src/test/stateGatedSurfaces.structural.test.js` | every state-gated site in `src/` is driven by a named surface |

Each of these carries a **canary** — the detector is asserted to convict a planted violation and to
acquit an innocent lookalike — because a source scan that stops matching fails blind and green.

---

# Surprises

The things worth acting on, found while reading. Nothing here is padding and nothing is inferred.

1. **`CheckboxGroup` has no consumer at all.** `src/components/form/CheckboxGroup.jsx` is exported
   from the barrel (`form/index.js:7`) and **imported by nothing in `src/`** — there is not a single
   `<CheckboxGroup` in the tree. Its own docblock says it was *"extracted on the second consumer
   (`Members.jsx` and `CaptureMember.jsx` both wrote it in `04.2`), never speculatively"*, and
   neither file uses it now. Meanwhile `src/pages/members/Recruiters.jsx:1366` **hand-rolls its own
   `<fieldset>`/`<legend>`** with a comment reasoning through exactly the argument this component
   was built on. It has no test. Either BR-14's consumers were migrated away and this was left
   behind, or it never landed; either way the barrel is advertising a primitive nothing uses.

2. **`DateField` carries a dead `refusal` state, and a docblock describing a feature that was
   removed.** `refusal` is declared at `:159` and set at `:228` and `:695` — **only ever to `null`**.
   Nothing ever gives it a value, so `message = errorProp || refusal` (`:383`) can never differ from
   `errorProp`. It is the residue of typed entry: the header still documents *"Typed entry accepts
   `15 Jun 1988` · `15/06/1988` · `1988-06-15` · `19880615`… A refusal is a sentence under the field,
   never a silent revert"* (`:84-88`), and the input has been `readOnly` since the owner's
   choose-only ruling (`:711-725`). A future reader hits a 791-line file whose summary describes a
   parser that is not there.

3. **`AddressAutocomplete` still commits its options on `mousedown`, which its sibling documents as
   insufficient.** `SearchableSelect` uses `onPointerDown` and says why in the same commit's
   neighbour: *"`mousedown` alone misses touch on iOS."* `AddressAutocomplete.jsx:251` is
   `onMouseDown={() => handleSelect(prediction)}`. Its option rows also lack `touch-size`, which the
   `07.2` sweep added to `SearchableSelect`'s rows after measuring them at 39px — these are
   `px-4 py-2.5`. Both are the kind of divergence that survives because the two files are never read
   side by side.

4. **`PositionTreeSelect` renders its `label` twice.** The outer `<span>` prints `label`, and
   `captionFor(0)` returns `label || 'Level 1'` when `levelLabels` is absent — which is every call
   site today. So a `<PositionTreeSelect label="Structures">` draws "STRUCTURES" as a caption and
   then "STRUCTURES" again as level 0's field label. `PositionTreeSelect.test.jsx` asserts every
   level *has* a name; it does not assert the name is unique on the page.

5. **`MemberPanel` documents a `remarks` prop it does not accept.** Its JSDoc (`:53-61`) lists
   `remarks?: string`, and the signature at `:62` is
   `({ member, open, onClose, children, footer })`. DESIGN.md names the Remarks slip as the **one**
   `accent-soft` surface in the design — this looks like a slip that was designed, documented and
   then never wired.

6. **`Field` accepts a `labelClassName` that no caller passes.** Harmless, but it is a public prop
   with zero consumers in `src/`, in a file whose whole argument is that a wrapper's surface should
   be the minimum that cannot be got wrong.

7. ✅ **FIXED in `10cbe89`** — *as found:* **`WelcomeLetter` does not use `Modal`'s `busy` prop.** It holds `sendState === 'sending'` and
   passes `onClose` straight through, so while the send is in flight the dialog can still be closed
   by Escape, by the scrim or by the X — the exact failure R2-F3 added `busy` for, in a dialog that
   makes a network write. `ConfirmDialog`, `AcbDebits.jsx:320` and `BankBranchModal.jsx:98` all do
   pass it. This is the one write-bearing dialog in the tree that does not.

8. **`FilterForm` and `Criteria` are the same row, twice.** Both are flex-wrap / items-end rows;
   both push `rounded-[10px] font-medium shadow-(--shadow-1)` onto descendant `input`/`select`; both
   carry `[&_input:not(:placeholder-shown)]:text-brand-ink`. They differ in three things:
   `FilterForm` is a `<form>` with `onSubmit`, `Criteria` is a `<section aria-label>`; `Criteria`
   also carries the `!important` `data-blank` rule that stops an unset `SearchableSelect` reading as
   teal, and `FilterForm` does **not** — so a `SearchableSelect` inside a `FilterForm` has the bug
   `Criteria` was changed to fix. `FilterForm` has one consumer and no test.

9. **`CheckboxGroup` is the only form primitive with no `focus-visible` ring declared**, which
   matters mainly because it is also the only one with no test to notice. Every other control in
   `form/` declares `focus-visible:ring-2 focus-visible:ring-brand-ring` explicitly, either directly
   or through `controlClasses`.

10. **`components.json` points `ui` at `@/components/ui`, which does not exist.** No shadcn registry
    component was ever vendored in. The file is otherwise live (`src/lib/utils.js`'s docblock notes
    that *"every shadcn component imports this by the `@/lib/utils` alias in components.json"*), so
    the alias is a promise the tree does not keep — and a future `npx shadcn add` would drop a
    component with its own colour literals straight past **The Token-Only Rule**.

11. **The brief's premise about `DataState` undercounts it.** It is described as *"the empty/loading
    /error component"* with three states; the component has **four** public states — the fourth,
    `partial`, is used on three real screens (`Members.jsx`, `CaptureMember.jsx`, `BankListing.jsx`)
    — plus a fifth, `slow`, that is internal by design and **throws** if a caller tries to assert it.
