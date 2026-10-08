import { cn } from '../../lib/utils';
import DataState from '../common/DataState';

/**
 * A choice control, and -- when its list failed to load -- the compact error and retry beneath it. `06.4`, F-P54.
 *
 * Both select primitives rendered this frame identically. While the list is failed the caller's `className` belongs
 * to the frame rather than the field (each primitive passes `failed ? undefined : className` to its `Field`), so the
 * control and its error are laid out as one item.
 */
export default function LoadFailureFrame({ failed, className, label, loadError, onRetryLoad, children }) {
  if (!failed) return children;
  return (
    <div className={cn('flex flex-col gap-1', className)}>
      {children}
      <DataState state="error" compact error={loadError} onRetry={onRetryLoad} title={`${label} could not be loaded.`} />
    </div>
  );
}
