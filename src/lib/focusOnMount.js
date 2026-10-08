/**
 * A callback ref that puts the caret in the element the moment it mounts: `<input ref={focusOnMount} />`.
 *
 * 🔑 FOR AN INPUT THAT APPEARS BECAUSE THE USER ASKED FOR IT -- an inline "add" row, a rename cell -- outside any
 * dialog. Inside a `Modal`, use its `initialFocusRef` instead: there Radix owns initial focus, and this would race it.
 *
 * WHY IT REPLACED `autoFocus` AND THEN A REF-AND-EFFECT PAIR (`10.4`). `jsx-a11y/no-autofocus` is an error, because
 * the declarative attribute can fire on page load and take the caret from someone who never asked. The first
 * replacement was a `useRef` plus an effect keyed on the revealing state, one pair per input. But these inputs
 * MOUNT exactly when they are revealed -- and unmount when they are hidden, or when the rename moves to another
 * row -- so "focus when revealed" is simply "focus on mount". A callback ref says that with no dependency array to
 * get wrong, and focuses in the commit, before paint, which is when `autoFocus` did.
 *
 * ⚠️ IT MUST STAY A MODULE-LEVEL FUNCTION. An inline arrow is a new ref every render; React 19 would call it again on
 * each one, and pull the caret back from wherever the user had moved it -- the Save button, say.
 */
export const focusOnMount = (element) => {
  element?.focus()
}

export default focusOnMount
