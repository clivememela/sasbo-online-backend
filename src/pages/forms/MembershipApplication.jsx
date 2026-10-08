import { Download } from 'lucide-react';

import PageTitle from '../../components/common/PageTitle';
import Sheet, { Page } from '../../components/common/Sheet.jsx';
import { Button } from '../../components/form';
// 🔴 IMPORTED, NOT A HAND-WRITTEN `/src/...` PATH. The URL was the literal string
// `"/src/assets/documents/Membership Application.pdf"`, which the DEV server happens to serve
// because Vite serves `src/` in development -- and which does not exist in a BUILD, where the
// file is emitted under `/assets/` with a content hash. So the one page whose entire job is to
// show a PDF would have shown an empty frame in production, and nothing in the build would have
// said so. The import makes Vite emit the file and hand back its real URL, in both modes --
// proved by building: `dist/assets/Membership Application-CMgf2ofC.pdf`.
import applicationForm from '../../assets/documents/Membership Application.pdf';

/**
 * THE APPLICATION FORM. A reading page, not a register: one document, shown whole.
 *
 * 🗣️ The owner's instruction is that card.html is the whole application's world, so this page
 * is built from the same three objects as every other -- `Page`, `PageTitle`, `Sheet` -- rather
 * than a bare `<h1>` over a raw frame. What used to sit here was a centred paragraph under a
 * left-aligned title, which is the one typographic thing the mock never does.
 *
 * 🔴 THE FRAME ASKS THE VIEWER TO PUT ITS FURNITURE AWAY. A PDF in an `<iframe>` is drawn
 * by the browser's own viewer, which brings a black toolbar, a zoom stepper, rotate, print
 * and kebab controls and a thumbnail rail -- a second application's chrome, in a second
 * application's dark, inside our white sheet. None of it can be styled. The PDF Open
 * Parameters are the one lever that reaches it: `toolbar=0&navpanes=0&scrollbar=0` takes the
 * furniture away and `view=FitH` fits the page across the sheet, so what is left is the
 * document. The controls that mattered are ours already -- "Download the form" in the page
 * head does what the viewer's download button did.
 *
 * 🔴 `embedded` EXISTS BECAUSE THIS COMPONENT HAS TWO HOMES, and one of them already has a
 * page head. It is a route (`/members/apply`) AND one of the five things `Forms` renders when
 * you pick a form type -- and inside `Forms` a second `<Page>` doubled the gutter and a second
 * `<h1>` gave that route two of them, which is the very defect `headingContract.test.jsx` was
 * written for and which it cannot see, because reaching it takes a change of select. Embedded,
 * the component is only its document, captioned by an `<h2>` -- the same shape `GeneralForm`
 * already uses two files away, so the four things `Forms` can show now agree with each other.
 */
export default function MembershipApplication({ embedded = false }) {
  const frame = (
    <iframe
      // 🔴 THE VIEWER'S CHROME, NOT OURS. See the note above the component.
      src={`${applicationForm}#toolbar=0&navpanes=0&scrollbar=0&view=FitH`}
      title="Membership application form"
      className="w-full flex-1 border-0 bg-surface-2"
    />
  );

  if (embedded) {
    return (
      // A height rather than `grow`: inside `Forms` the surrounding column is not a bounded
      // flex context, so a `flex-1` sheet would resolve to nothing and the frame would vanish.
      <Sheet className="mt-5 min-h-[34rem] p-1.5">
        <div className="mb-1.5 border-b border-line px-4 pb-3 pt-2.5 text-center">
          <h2 className="text-xs font-semibold uppercase tracking-[0.12em] text-ink-muted">
            SASBO — The Finance Union — Membership Application
          </h2>
        </div>
        {frame}
      </Sheet>
    );
  }

  return (
    <Page>
      <PageTitle
        title="Membership Application"
        subtitle="Read the form here, or download it to complete by hand."
        actions={
          <Button href={applicationForm} download="SASBO Membership Application.pdf" variant="primary">
            <Download size={16} strokeWidth={1.75} aria-hidden="true" />
            Download the form
          </Button>
        }
      />

      {/* 📐 THE SHEET TAKES THE REMAINING PAGE, `h-dvh` DID NOT.
          The frame asked for a whole viewport inside a column that had already spent one on the
          topbar and the page head, so the document was always taller than the window by that
          much and every visit began with its foot below the fold. `grow` is the same device the
          register's sheet uses -- the surface ends where the page ends -- and the `min-h` floor
          keeps the document readable on a short window.

          The document goes edge to edge and the sheet is its frame: a padded ring would put a
          white margin around Chrome's own dark PDF chrome, which reads as a mistake rather than
          as a surface. The sheet's radius and `overflow-hidden` clip the frame instead. */}
      <Sheet grow className="mt-5 min-h-[26rem] max-w-4xl">
        {frame}
      </Sheet>
    </Page>
  );
}
