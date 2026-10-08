import { clsx } from 'clsx'
import { twMerge } from 'tailwind-merge'

/**
 * Join class names, and let the LAST conflicting utility win.
 *
 * `clsx` handles the conditionals; `tailwind-merge` handles the part that bites --
 * `cn('px-4', 'px-2')` is `px-2`, not both. Without it a primitive that accepts a
 * `className` prop can only be overridden by luck of source order, which is how
 * "why is my padding not applying" becomes a `!important`.
 *
 * Every shadcn component imports this by the `@/lib/utils` alias in components.json;
 * it is the one file the registry assumes exists.
 */
export function cn(...inputs) {
  return twMerge(clsx(inputs))
}

/**
 * Look a variant up in its map, and REFUSE an unknown key in development.
 *
 * 🔴 THE INCONSISTENCY THIS CLOSES. `fieldKinds.js` spends a paragraph on why an unknown
 * field kind must throw rather than degrade -- *"a typo'd kind that silently behaves like
 * plain text is the failure this whole module exists to stop, and it would never be
 * noticed"* -- and `Table` throws the same way on an unknown column priority. Two files
 * over, `SIZES[size] ?? SIZES.md` and `VARIANTS[variant] ?? VARIANTS.default` did exactly
 * the silent degrade those paragraphs argue against: `<Button variant="primry">` rendered
 * a neutral button and nobody found out.
 *
 * Guarded by DEV so a production render degrades rather than blanking a screen somebody is
 * standing in front of -- the same trade `Table`'s `rowKey` guard makes.
 */
export function pick(map, key, what) {
  const found = map[key]
  if (found !== undefined) return found

  const fallback = Object.values(map)[0]
  if (import.meta.env?.DEV) {
    throw new Error(
      `Unknown ${what} "${key}". Known: ${Object.keys(map).join(', ')}. Falling back would ` +
        `render something that looks deliberate and is not, which is how a typo ships.`,
    )
  }
  return fallback
}
