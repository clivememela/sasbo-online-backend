import { useState } from 'react';

import { DERIVATION, derivationNote } from '../utils/derivation';
import { workplaceFill } from '../utils/workplaces';

/**
 * THE WORKPLACE A FORM NAMES, AND THE OFFER IT MAKES -- one rule for capture and the record (`01.3`).
 *
 * The FORM holds only `workplaceId`: that is all a save may name (BR66), and on capture it is the one key the closed
 * payload gains (BR46). The row that was chosen -- its name, R/A number, area and committee -- is held here, beside it,
 * and shown only while the form still names it. The record passes `named`, so its form also carries the name and R/A
 * number its register row shows, and its save confirmation can say which workplace became which.
 *
 * 🔑 THE OFFER (`workplaceFill`) IS ASKED, NEVER DONE: accepting fills *Bank Where Employed* and *Bank Branch* through
 * the page's own `settle` -- the same rules a person's choice of bank or branch goes through (a job grade belongs to a
 * bank; IBT No. and the bank's address follow the branch) -- and marks each with the derivation state it then has,
 * DERIVED, naming the workplace it came from. A field stays DERIVED only while it still holds what was filled: the
 * moment anybody chooses another value it is theirs again, and no later offer may overwrite it (S4). Declining leaves
 * both exactly as they are, and the offer does not come back for that workplace.
 *
 * @param {{ form: object, setForm: Function, setFieldErrors: Function, banks: string[], named?: boolean,
 *           branchesFor: (bank: string) => string[], settle: (prev: object, next: object) => object }} page
 */
export default function useWorkplaceChoice({ form, setForm, setFieldErrors, banks, branchesFor, settle, named }) {
  const [workplace, setWorkplace] = useState(null);
  const [filled, setFilled] = useState({});
  const [declined, setDeclined] = useState(null);

  const chosen = workplace?.id === form.workplaceId ? workplace : null;
  // The workplace a field was filled from, while it still holds what was filled -- else null: the value is somebody's.
  const from = (field) => (form[field] && filled[field]?.value === form[field] ? filled[field].from : null);
  const fill = chosen && declined !== chosen.id
    ? workplaceFill(chosen, {
      banks,
      branchesFor,
      form,
      free: { bankName: !form.bankName || from('bankName'), branch: !form.branch || from('branch') },
    })
    : {};
  const dropErrors = (...fields) =>
    setFieldErrors((prev) => Object.fromEntries(Object.entries(prev).filter(([field]) => !fields.includes(field))));

  return {
    workplace: chosen,
    fill,
    /** The note under a field the offer filled -- "Filled in from INVENTED HEAD OFFICE." -- else nothing. */
    note: (field) => from(field) && derivationNote(DERIVATION.DERIVED, { source: from(field) }),
    /** A found workplace chosen -- or, on capture, `null`: no workplace, which a member may have. */
    choose: (row) => {
      setWorkplace(row);
      setForm((prev) => ({
        ...prev,
        workplaceId: row ? row.id : null,
        ...(named && { workplaceName: row.name, workplaceRaNumber: row.raNumber }),
      }));
      dropErrors('workplaceId');
    },
    accept: () => {
      setFilled((prev) => ({
        ...prev,
        ...Object.fromEntries(Object.entries(fill).map(([field, value]) => [field, { value, from: chosen.name }])),
      }));
      setForm((prev) => settle(prev, { ...prev, ...fill }));
      dropErrors('bankName', 'branch');
    },
    decline: () => setDeclined(chosen.id),
    /** A new form (capture's next member, the record's next edit): nothing it holds was filled by an offer. */
    reset: () => setFilled({}),
  };
}
