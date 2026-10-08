import { useCallback, useRef, useState } from 'react';
import Sheet from '../../components/common/Sheet.jsx';
import DialogNotice from '../../components/common/DialogNotice.jsx';
import Table from '../../components/table/Table.jsx';
import Modal, { ModalClose } from '../../components/overlay/Modal.jsx';
import { Button, TextInput } from '../../components/form';
import reportError from '../../lib/reportError.js';
import { acbRefusalOf, acbRunService } from '../../services/acbRunService.js';
import { messageOf, writeMayHaveLanded } from '../../services/parseApiError.js';
import { PagerFor, usePagedRead } from './acbPaging.jsx';
import { LETTER_STATUS, LETTER_TEMPLATE_LABEL, REFUSAL, couldNotGoBecause, monthLabel } from './acbRunRules.js';

const PAGE_SIZE = 50;

/** The server keeps the note to this many characters, and refuses beyond it rather than cutting it (SD3-D). */
export const HANDLED_NOTE_LIMIT = 300;

/**
 * The run's letters that could not go, as a worklist (Q46, `04.1`, EH-E): HELD for want of an address they could
 * go to, or a send that FAILED. Each is SENT once it can go -- again, after a failure -- or MARKED HANDLED with a
 * note of how it was dealt with -- *"no post any longer so someone needs to deal with it"*. Both are writes started from a dialog,
 * and both keep the P9 rules: the failure is said INSIDE the dialog, nothing closes it mid-write, and an
 * answer that never came is never repeated -- the dialog stops offering the write and the list is read
 * again (R4-5).
 *
 * The send is the server's to allow: until administrators sign in to this system it refuses every
 * caller (B1), and the dialog says the server's own sentence.
 */
export default function AcbHeldLetters({ run }) {
  const read = useCallback((page, size) => acbRunService.heldLetters(run.id, page, size), [run.id]);
  const { data, state, goTo, retry } = usePagedRead(read, { size: PAGE_SIZE, label: 'AcbHeldLetters.read' });
  const month = monthLabel(run.actionMonth);

  // One dialog at a time: { letter, action: 'send' | 'handled' }.
  const [open, setOpen] = useState(null);
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);
  const [dialogNotice, setDialogNotice] = useState(null);
  const [unconfirmed, setUnconfirmed] = useState(false);
  const [notice, setNotice] = useState(null);
  const noteRef = useRef(null);

  const start = (letter, action) => {
    setOpen({ letter, action });
    setNote('');
    setDialogNotice(null);
    setUnconfirmed(false);
  };

  const close = () => {
    setOpen(null);
    if (unconfirmed) retry();
  };

  const trimmed = note.trim();
  const why = open?.action !== 'handled' ? null
    : !trimmed ? 'Say how the letter was dealt with first.'
      : trimmed.length > HANDLED_NOTE_LIMIT ? `Keep it to ${HANDLED_NOTE_LIMIT} characters.`
        : null;

  const write = async () => {
    if (!open || why) return;
    const { letter, action } = open;
    setSaving(true);
    setDialogNotice(null);
    try {
      if (action === 'send') await acbRunService.sendLetter(run.id, letter.letterId);
      else await acbRunService.markHandled(run.id, letter.letterId, trimmed);
      setOpen(null);
      setNotice({
        tone: 'ok',
        message: action === 'send'
          ? `${letter.surname}’s letter was sent${letter.status === LETTER_STATUS.FAILED ? ' again' : ''}.`
          : `${letter.surname}’s letter is marked as dealt with by hand. It leaves the list and is never sent.`,
      });
      retry();
    } catch (err) {
      reportError(`AcbHeldLetters.${action}`, err);
      const refusal = acbRefusalOf(err);
      if (refusal?.code === REFUSAL.LETTER_NOT_HELD) {
        setDialogNotice({ message: `This letter is no longer held — ${messageOf(err)} Close this and the list is read again.` });
        setUnconfirmed(true);
      } else if (writeMayHaveLanded(err)) {
        // After a timeout the screen reads again rather than repeating: a letter must never go twice.
        setDialogNotice({
          message: action === 'send'
            ? 'The sending could not be confirmed. The letter may have gone — close this and the list is read again. Do not send it again until it has.'
            : 'This could not be confirmed. It may have been recorded — close this and the list is read again.',
        });
        setUnconfirmed(true);
      } else {
        setDialogNotice({
          message: action === 'send'
            ? `The letter was not sent — ${messageOf(err)}`
            : `Nothing was recorded — ${messageOf(err)}`,
        });
      }
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    {
      key: 'member', header: 'Member', priority: 'essential',
      cell: (letter) => (
        <span>
          {letter.surname || letter.membershipNumber}
          <span className="ml-2 text-xs text-ink-muted">{letter.membershipNumber}</span>
        </span>
      ),
    },
    { key: 'template', header: 'Letter', priority: 'essential',
      cell: (letter) => LETTER_TEMPLATE_LABEL[letter.template] ?? letter.template },
    { key: 'reason', header: 'Held because', priority: 'secondary', cell: couldNotGoBecause },
    {
      key: 'actions', header: 'Deal with it', priority: 'essential',
      cell: (letter) => (
        <div className="flex flex-wrap items-center gap-2">
          <Button onClick={() => start(letter, 'send')} disabled={!letter.canSend}
            aria-label={`Send ${letter.surname || 'the member'}’s letter${letter.status === LETTER_STATUS.FAILED ? ' again' : ''}`}>
            {letter.status === LETTER_STATUS.FAILED ? 'Send again' : 'Send'}
          </Button>
          {!letter.canSend && <span className="text-xs text-ink-muted">No address yet</span>}
          <Button variant="ghost" onClick={() => start(letter, 'handled')}
            aria-label={`Mark ${letter.surname || 'the member'}’s letter as handled`}>
            Mark handled
          </Button>
        </div>
      ),
    },
  ];

  const letter = open?.letter;
  const sending = open?.action === 'send';
  const again = letter?.status === LETTER_STATUS.FAILED;
  return (
    <Sheet className="mt-6" aria-labelledby="acb-held-letters-heading">
      <div className="border-b border-line px-6 py-4">
        <h2 id="acb-held-letters-heading" className="text-base font-semibold text-ink">Letters held — {month}</h2>
        <p className="mt-0.5 text-sm text-ink-muted">
          Letters that could not go — for want of an address, or because sending failed. Send one once it can go, or
          mark it handled and say how.
        </p>
      </div>
      {notice && (
        <div className="px-6 pt-4">
          <DialogNotice notice={notice} onDismiss={() => setNotice(null)} />
        </div>
      )}
      <Table
        caption="Letters that could not go"
        columns={columns}
        rows={data.content}
        rowKey="letterId"
        loading={state === 'loading'}
        error={state === 'error' ? 'We could not load the held letters.' : null}
        onRetry={retry}
        emptyTitle="No letters held"
        emptyMessage={`Every letter of the ${month} run could go, or has been dealt with.`}
        dense
      />
      <PagerFor data={data} state={state} goTo={goTo} noun="letters" size={PAGE_SIZE} />

      <Modal
        open={open != null}
        onOpenChange={(next) => { if (!next) close(); }}
        title={sending ? (again ? 'Send this letter again?' : 'Send this letter?') : 'Mark this letter handled'}
        description={letter ? `${LETTER_TEMPLATE_LABEL[letter.template] ?? letter.template} letter to ${letter.surname}.` : undefined}
        size="md"
        busy={saving}
        initialFocusRef={sending ? undefined : noteRef}
        footer={(
          <>
            {why && !unconfirmed && <p id="acb-handled-why" className="mr-auto text-sm text-ink-muted">{why}</p>}
            <ModalClose>{unconfirmed ? 'Close' : 'Cancel'}</ModalClose>
            {!unconfirmed && (
              <Button variant="primary" onClick={write} loading={saving} disabled={Boolean(why)}
                aria-describedby={why ? 'acb-handled-why' : undefined}>
                {sending ? (again ? 'Send again' : 'Send') : 'Mark handled'}
              </Button>
            )}
          </>
        )}
      >
        <DialogNotice notice={dialogNotice} onDismiss={() => setDialogNotice(null)} />
        {!sending && (
          <TextInput
            ref={noteRef}
            label="How it was dealt with"
            multiline
            rows={3}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            hint={`At most ${HANDLED_NOTE_LIMIT} characters — for example, “posted by hand on 21 September”.`}
            disabled={saving || unconfirmed}
          />
        )}
      </Modal>
    </Sheet>
  );
}
