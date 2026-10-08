import { cn } from '../../lib/utils';

/**
 * A search form's criteria, printed on the page -- card.css `.criteria`, as a `<form>`.
 *
 * It used to be a card (radius, border, shadow, a four-column grid). The Card world prints
 * criteria on the page itself, 140px fields on the resting shadow with their values in the
 * brand's ink, and the action on their baseline; a form that wraps them is the same row.
 */
export default function FilterForm({ children, onSubmit, className }) {
  return (
    <form
      onSubmit={onSubmit}
      className={cn(
        'no-print mt-5 flex flex-wrap items-end gap-x-3 gap-y-2.5',
        '[&>*]:min-w-[120px] [&>*]:shrink [&>*]:grow-0 [&>*]:basis-[160px]',
        '[&_input]:rounded-[10px] [&_input]:font-medium [&_input]:shadow-(--shadow-1)',
        '[&_select]:rounded-[10px] [&_select]:font-medium [&_select]:shadow-(--shadow-1)',
        "[&_input:not(:placeholder-shown)]:text-brand-ink",
        className,
      )}
    >
      {children}
    </form>
  );
}
