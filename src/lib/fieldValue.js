/**
 * What a form control's `onChange` first argument MEANS, written once.
 *
 * 🔴 THE REASON THIS EXISTS IS R2-F5, WHICH TOOK TWO SCREENS DOWN. This application's form primitives report a change
 * in two shapes, both deliberate and both documented: `TextInput` forwards the DOM event, and `SelectInput` /
 * `SearchableSelect` report `onChange(value, option)` — a value, because a listbox has no `event.target.value` to
 * give. A screen whose one handler feeds both kinds of control therefore has to know the rule, and two screens did
 * not: they read `event.target.value` off a string, threw during render, and fell to the error boundary. On
 * `/reports/forms` that made the entire Forms feature unreachable.
 *
 * P9's refactor pass found the same three lines written twice more, character for character, in the two screens that
 * had just been fixed — so the next control that reports a new shape would be fixed in one file and not the others,
 * which is exactly how R2-F5 happened. One rule, one home.
 *
 * ⚠️ IT DOES NOT GUESS. An event is recognised by the `target` it must have; anything else is the value itself, and a
 * missing value is the empty string, never `undefined` — a filter holding `undefined` is a filter no comparison can
 * read.
 *
 * @param {Event | string | null | undefined} eventOrValue
 * @returns {string}
 */
export function fieldValue(eventOrValue) {
  if (eventOrValue && typeof eventOrValue === 'object' && 'target' in eventOrValue) {
    return eventOrValue.target.value;
  }
  return eventOrValue ?? '';
}

export default fieldValue;
