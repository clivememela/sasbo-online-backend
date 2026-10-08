import { useCallback, useRef, useState } from 'react';
import Sheet from '../../components/common/Sheet.jsx';
import DialogNotice from '../../components/common/DialogNotice.jsx';
import Table from '../../components/table/Table.jsx';
import Modal, { ModalClose } from '../../components/overlay/Modal.jsx';
import { Button, TextInput } from '../../components/form';
import reportError from '../../lib/reportError.js';
import { formatZar } from '../../lib/format.js';
import { acbRefusalOf, acbRunService } from '../../services/acbRunService.js';
import { messageOf, writeMayHaveLanded } from '../../services/parseApiError.js';
import { PagerFor, usePagedRead } from './acbPaging.jsx';
import { REFUSAL, monthLabel } from './acbRunRules.js';

const PAGE_SIZE = 50;

/** The server keeps the note to this many characters, and refuses beyond it rather than cutting it (SD3-D). */
export const PERMISSION_NOTE_LIMIT = 300;

/**
 * The re-debits the next run holds over (D32, `04.1`): a member's SECOND unpaid to be presented again in
 * one run goes on the same file (Q38) only with the member's permission -- who agreed, when and how. Each
 * is listed with *Record permission*, whose confirm stays disabled, and says why, until the note is
 * written (R4-4). A recorded permission is taken by the run when it is prepared again; otherwise the
 * re-debit is offered again next month.
 *
 * 🔒 The note is sent and kept; the list says only THAT permission is on record, never what it says.
 */
export default function AcbHeldOver({ run }) {
  const read = useCallback((page, size) => acbRunService.heldOver(run.id, page, size), [run.id]);
  const { data, state, goTo, retry } = usePagedRead(read, { size: PAGE_SIZE, label: 'AcbHeldOver.read' });
  const month = monthLabel(run.actionMonth);

  const [asking, setAsking] = useState(null);
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);
  const [dialogNotice, setDialogNotice] = useState(null);
  const [unconfirmed, setUnconfirmed] = useState(false);
  const [notice, setNotice] = useState(null);
  const noteRef = useRef(null);

  const open = (held) => {
    setAsking(held);
    setNote('');
    setDialogNotice(null);
    setUnconfirmed(false);
  };

  const close = () => {
    setAsking(null);
    // An unconfirmed write: whatever happened, the list shows it as it now stands.
    if (unconfirmed) retry();
  };

  const trimmed = note.trim();
  const tooLong = trimmed.length > PERMISSION_NOTE_LIMIT;
  const why = !trimmed ? 'Write who agreed, when and how first.'
    : tooLong ? `Keep it to ${PERMISSION_NOTE_LIMIT} characters.`
      : null;

  const record = async () => {
    if (why || !asking) return;
    setSaving(true);
    setDialogNotice(null);
    try {
      await acbRunService.permit(asking.runId, asking.lineId, trimmed);
      setAsking(null);
      setNotice({
        tone: 'ok',
        message: `Permission recorded for ${asking.surname}. The ${month} run takes this re-debit when it is prepared again; `
          + 'otherwise next month’s does.',
      });
      retry();
    } catch (err) {
      reportError('AcbHeldOver.permit', err);
      const refusal = acbRefusalOf(err);
      if (refusal?.code === REFUSAL.UNPAID_ALREADY_RECORDED || refusal?.code === REFUSAL.RUN_NOT_IN_STATE) {
        setDialogNotice({ message: `This re-debit can no longer take a permission — ${messageOf(err)} Nothing was recorded.` });
        setUnconfirmed(true);
      } else if (writeMayHaveLanded(err)) {
        // After a timeout the screen reads again rather than sending the same permission twice.
        setDialogNotice({ message: 'The permission could not be confirmed. It may have been recorded — close this and the list is read again.' });
        setUnconfirmed(true);
      } else {
        setDialogNotice({ message: `The permission was not recorded. Nothing was written — ${messageOf(err)}` });
      }
    } finally {
      setSaving(false);
    }
  };

  const columns = [
    {
      key: 'member', header: 'Member', priority: 'essential',
      cell: (held) => (
        <span>
          {held.surname || held.membershipNumber}
          <span className="ml-2 text-xs text-ink-muted">{held.membershipNumber}</span>
        </span>
      ),
    },
    { key: 'month', header: 'Unpaid for', priority: 'essential', cell: (held) => monthLabel(held.month) },
    { key: 'amount', header: 'Amount', priority: 'secondary', cell: (held) => formatZar(held.amount) },
    {
      key: 'permission', header: 'Permission', priority: 'essential',
      cell: (held) => (held.permitted ? (
        <span className="text-sm text-ink">On record — prepare the run again to take it</span>
      ) : (
        <Button onClick={() => open(held)} aria-label={`Record ${held.surname || 'the member'}’s permission`}>
          Record permission
        </Button>
      )),
    },
  ];

  return (
    <Sheet className="mt-6" aria-labelledby="acb-held-over-heading">
      <div className="border-b border-line px-6 py-4">
        <h2 id="acb-held-over-heading" className="text-base font-semibold text-ink">Second re-debits held over — {month}</h2>
        <p className="mt-0.5 text-sm text-ink-muted">
          A member’s second unpaid presented again in one run goes on the same file only with their permission.
        </p>
      </div>
      {notice && (
        <div className="px-6 pt-4">
          <DialogNotice notice={notice} onDismiss={() => setNotice(null)} />
        </div>
      )}
      <Table
        caption="Re-debits held over for the member's permission"
        columns={columns}
        rows={data.content}
        rowKey="lineId"
        loading={state === 'loading'}
        error={state === 'error' ? 'We could not load the re-debits held over.' : null}
        onRetry={retry}
        emptyTitle="Nothing held over"
        emptyMessage={`Every re-debit due in the ${month} run is on its file.`}
        dense
      />
      <PagerFor data={data} state={state} goTo={goTo} noun="re-debits" size={PAGE_SIZE} />

      <Modal
        open={asking != null}
        onOpenChange={(next) => { if (!next) close(); }}
        title="The member’s permission"
        description={asking ? `${asking.surname}’s ${monthLabel(asking.month)} unpaid would be a second re-debit in the ${month} run.` : undefined}
        size="md"
        busy={saving}
        initialFocusRef={noteRef}
        footer={(
          <>
            {why && !unconfirmed && <p id="acb-permission-why" className="mr-auto text-sm text-ink-muted">{why}</p>}
            <ModalClose>{unconfirmed ? 'Close' : 'Cancel'}</ModalClose>
            {!unconfirmed && (
              <Button variant="primary" onClick={record} loading={saving} disabled={Boolean(why)}
                aria-describedby={why ? 'acb-permission-why' : undefined}>
                Record permission
              </Button>
            )}
          </>
        )}
      >
        <DialogNotice notice={dialogNotice} onDismiss={() => setDialogNotice(null)} />
        <TextInput
          ref={noteRef}
          label="Who agreed, when, and how"
          multiline
          rows={3}
          value={note}
          onChange={(e) => setNote(e.target.value)}
          hint={`At most ${PERMISSION_NOTE_LIMIT} characters — for example, “the member, by phone, 21 September”.`}
          disabled={saving || unconfirmed}
        />
      </Modal>
    </Sheet>
  );
}
