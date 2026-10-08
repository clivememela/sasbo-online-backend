import Button from '../form/Button.jsx';
import { cn } from '../../lib/utils';

/**
 * A CHOICE OF HOW A PAGE LAYS OUT THE SAME THINGS -- Home's modules as cards, a list or a table (the 5 Oct Show & Tell).
 *
 * Toggle buttons in a named group, each carrying `aria-pressed`: Tab reaches every one, Enter or Space chooses it, and
 * the one chosen says so to a screen reader as well as by its fill. 🔑 NOT A RADIOGROUP, for the reason `Auth.jsx` gives
 * for the tablist it refused: that pattern promises arrow keys and a single tab stop, and three plain buttons promise
 * only what they do. The `Button` primitive brings the focus ring and the 44px coarse-pointer target, and its own
 * variants are the skin -- the chosen one the raised control, the others ghosts on the quiet fill -- so the segmented
 * control `Auth.jsx` wears costs the stylesheet nothing new.
 *
 * @param {{ label: string, value: string, onChange: (value: string) => void, className?: string,
 *   options: Array<{ value: string, label: string }> }} props
 */
export default function ViewSwitch({ label, options, value, onChange, className }) {
  return (
    <div role="group" aria-label={label} className={cn('flex rounded-lg bg-surface-2 p-1', className)}>
      {options.map(({ value: option, label: name }) => {
        const chosen = option === value;
        return (
          <Button
            key={option}
            size="sm"
            variant={chosen ? 'default' : 'ghost'}
            aria-pressed={chosen}
            onClick={() => onChange(option)}
            className={cn('rounded-md', chosen && 'border-transparent text-brand-ink hover-hover:hover:bg-surface')}
          >
            {name}
          </Button>
        );
      })}
    </div>
  );
}
