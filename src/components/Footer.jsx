/**
 * The union's line at the foot of the auth screens.
 *
 * 🔴 IT HAD AN `absolute` MODE, AND THAT MODE WAS THE BUG. `absolute bottom-0 z-50` over a
 * translucent ground drew the footer ON TOP of the page rather than after it, so any content
 * tall enough to reach the bottom of the window ran underneath it -- which is exactly what
 * the login card did. Its only caller was `AuthLayout`, so the mode is removed rather than
 * left for the next layout to reach for: a footer that can overlap content is not a variant
 * worth keeping.
 */
export default function Footer() {
  return (
    <footer className="mt-auto border-t border-line bg-surface/80 p-4 text-center text-sm text-ink-muted backdrop-blur-sm">
      <p>© 2026 SASBO — The Finance Union</p>
    </footer>
  );
}
