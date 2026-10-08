import { useMemo, useState } from "react";
import { bankService } from "../../services/bankService.js";
import { writeFailure, writeMayHaveLanded } from "../../services/parseApiError.js";
import reportError from "../../lib/reportError.js";
import { TextInput, Button } from "../../components/form";
import AddressAutocomplete from "../../components/form/AddressAutocomplete";
import Modal, { closeOn } from "../../components/overlay/Modal.jsx";
import { validateEmail } from "../../utils/validateSAID.js";
import { toBranchForm, toBranchPayload, toBranchRecord } from "./branchForm.js";

const normalizeText = (value) => (value === null || value === undefined ? "" : String(value)).trim();

/**
 * The fields a refusal named, always an object. Kept here rather than imported as `parseApiError`'s
 * `validationErrorsOf`: nothing else uses that export, so importing it would ship it in the entry chunk, which the
 * bundle budget holds flat (OB17).
 */
const fieldErrorsOf = (err) =>
  err?.validationErrors && typeof err.validationErrors === "object" ? err.validationErrors : {};

/**
 * What a failed save says. A refusal names its reasons -- the server's sentence, or each field it refused -- and says
 * nothing was written; a timeout, a lost connection or a 5xx may have saved, so it says to check (R3-F2).
 */
const saveFailure = (err, what) => {
  const fields = Object.values(fieldErrorsOf(err));
  return !writeMayHaveLanded(err) && fields.length > 0
    ? `${what} was not saved. ${fields.join(" ")}`
    : writeFailure(err, what, "the branch list");
};

export default function BankBranchModal({ onClose, onCreated, onUpdated, selectedBank }) {
  const [isEditing, setIsEditing] = useState(false);
  const isReadOnlyView = Boolean(selectedBank) && !isEditing;
  const isEditMode = Boolean(selectedBank) && isEditing;
  // 🔴 A29: the record converted to the form's strings, never used as the form (see `branchForm.js`).
  const [form, setForm] = useState(() => toBranchForm(selectedBank));
  // The form as the record opened it: what a save compares the address against, to send it only when changed.
  const opened = useMemo(() => toBranchForm(selectedBank), [selectedBank]);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const [loading, setLoading] = useState(false);

  /*
   * 🔴 A NEW `selectedBank` RESEEDS THE FORM -- DURING RENDER, GUARDED BY THE LAST ONE SEEN (`10.4`).
   *
   * This was an effect keyed on `selectedBank`, which `react-hooks/set-state-in-effect` makes an error this
   * phase. The render-phase form is React's documented one, and it runs on exactly the same trigger: an
   * IDENTITY change of `selectedBank`.
   *
   * ⚠️ A `key` ON THE MODAL WOULD HAVE BEEN THE WRONG TOOL, and it is the fix that looks right. The open path
   * is already a fresh mount (`BankBranch.jsx` renders two conditionally-mounted instances), so the only time
   * this ever did work is AFTER A SAVE: `onUpdated` hands back a NEW object with the SAME id. A
   * `key={selectedBank.id}` would not remount on that, and would silently drop the reset. Identity is the
   * right thing to compare, which is what this does.
   */
  const [seenBank, setSeenBank] = useState(selectedBank);
  if (selectedBank !== seenBank) {
    setSeenBank(selectedBank);
    setForm(toBranchForm(selectedBank));
    setIsEditing(false);
    setFieldErrors({});
  }

  const set = (field) => (e) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }));
    if (field === "email") {
      const err = validateEmail(e.target.value);
      setFieldErrors((prev) => ({ ...prev, email: err || undefined }));
    } else if (fieldErrors[field]) {
      // A field the server refused is cleared of its message the moment it is changed.
      setFieldErrors((prev) => ({ ...prev, [field]: undefined }));
    }
  };

  const handleCancel = () => {
    setError(null);
    if (isEditMode) {
      setForm(toBranchForm(selectedBank));
      setFieldErrors({});
      setIsEditing(false);
      return;
    }
    onClose();
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (isReadOnlyView) return;
    const bankName = normalizeText(form.bankName);
    const branch = normalizeText(form.branch);

    if (!bankName || !branch) {
      setError("Bank Name and Branch are required.");
      return;
    }
    if (fieldErrors.email) {
      setError("Please fix the errors before saving.");
      return;
    }
    setLoading(true);
    setError(null);
    try {
      if (isEditMode) {
        const payload = toBranchPayload(form, opened);
        const answer = await bankService.updateBank(selectedBank.id, payload);
        const record = toBranchRecord(answer, selectedBank, payload);
        setForm(toBranchForm(record));
        setIsEditing(false);
        onUpdated?.(record);
      } else {
        const created = await bankService.createBank(toBranchPayload(form));
        onCreated?.(created);
      }
    } catch (err) {
      reportError(isEditMode ? "BankBranchModal.update" : "BankBranchModal.create", err);
      setFieldErrors((prev) => ({ ...prev, ...fieldErrorsOf(err) }));
      setError(saveFailure(err, isEditMode ? "The change to this branch" : "This branch"));
    } finally {
      setLoading(false);
    }
  };

  return (
    // 🔴 `03.4`: was a bare `fixed inset-0` overlay with its own hand-rolled header, its
    // own close button, and `max-h-[90vh]` -- a `vh` cap, which on a phone puts the Save
    // button under the browser chrome (conventions §6). `Modal` portals it, traps focus,
    // handles Escape, locks the page, and caps in `dvh`; the close button is its own.
    <Modal
      open
      onOpenChange={closeOn(onClose)}
      title={isReadOnlyView ? "Bank Details" : isEditMode ? "Edit Bank/Branch Details" : "Add Bank/Branch Details"}
      size="xl"
      // R2-F3: closed mid-update, `BankBranch`'s `onUpdated` reopened this dialog when the write landed.
      busy={loading}
      footer={
        <>
          {/* 🔴 was `variant="secondary"`, which Button has never had -- the old
              `VARIANTS[variant] ?? VARIANTS.default` silently rendered `default` for it. That
              is the exact silent degrade `pick()` now refuses, and this call site is what it
              found. Naming the variant it was ALREADY rendering: no pixel changes. */}
          <Button type="button" onClick={isReadOnlyView ? onClose : handleCancel} variant="default" disabled={loading}>{isReadOnlyView ? "Close" : "Cancel"}</Button>
          {/* 🔴 A29: TWO BUTTONS, NEVER ONE THAT CHANGES ITS TYPE -- hence the keys. Unkeyed, React kept the pressed
              Edit `<button>` and made it Save Changes; a browser applies that update before the click's default
              action, so pressing Edit submitted the form it had just opened, unchanged, and the answer shut the form
              again (the merged gate's run 37680224612). */}
          {isReadOnlyView ? (
            <Button key="edit" type="button" variant="primary" onClick={() => setIsEditing(true)}>Edit</Button>
          ) : (
            <Button key="save" type="submit" form="add-bank-form" variant="primary" loading={loading}>
              {isEditMode ? "Save Changes" : "Save"}
            </Button>
          )}
        </>
      }
    >
        {isReadOnlyView ? (
          <div className="space-y-6">
            <section className="space-y-4">
              <h4 className="border-b border-line pb-2 text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted">General Info</h4>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                <DetailItem label="Bank Name" value={form.bankName} />
                <DetailItem label="Abbreviation" value={form.bankAbbreviation} />
                <DetailItem label="Branch" value={form.branch} />
                <DetailItem label="Branch Code" value={form.branchCode} />
                <DetailItem label="Area" value={form.area} />
                <DetailItem label="Random Access Number" value={form.randomAccessNumber} />
              </div>
            </section>

            <section className="space-y-4">
              <h4 className="border-b border-line pb-2 text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted">Contact & Management</h4>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                <DetailItem label="Phone Number" value={form.phoneNumber} />
                <DetailItem label="Fax" value={form.fax} />
                <DetailItem label="Email" value={form.email} />
                <DetailItem label="Manager Name" value={form.managerName} />
              </div>
            </section>

            <section className="space-y-4">
              <h4 className="border-b border-line pb-2 text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted">Address</h4>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <DetailItem label="Physical Address" value={form.physicalAddress} />
                <DetailItem label="Postal Address" value={form.postalAddress} />
              </div>
            </section>

            <section className="space-y-4">
              <h4 className="border-b border-line pb-2 text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted">Operations & Codes</h4>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                <DetailItem label="Province Committee Code" value={form.provinceCommitteeCode} />
                <DetailItem label="Province Committee Name" value={form.provinceCommitteeName} />
                <DetailItem label="SASBO Area Code" value={form.sasboAreaCode} />
                <DetailItem label="Bank Department Code" value={form.bankDepartmentCode} />
                <DetailItem label="Table Code" value={form.tableCode} />
                <DetailItem label="Subs Value Branch" value={form.subsValueBranch} />
                <DetailItem label="Staff Complement" value={form.branchStaffComplement} />
                <DetailItem label="Member Complement" value={form.branchMemberComplement} />
              </div>
            </section>
          </div>
        ) : (
          <form id="add-bank-form" onSubmit={handleSubmit} className="space-y-6">
          <section className="space-y-4">
            <h4 className="border-b border-line pb-2 text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted">General Info</h4>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              <TextInput label="Bank Name *" value={form.bankName} onChange={set("bankName")} placeholder="e.g. Standard Bank" error={fieldErrors.bankName} />
              <TextInput label="Abbreviation" value={form.bankAbbreviation} onChange={set("bankAbbreviation")} placeholder="e.g. STD" />
              <TextInput label="Branch *" value={form.branch} onChange={set("branch")} placeholder="e.g. Sandton" error={fieldErrors.branch} />
              <TextInput label="Branch Code" value={form.branchCode} onChange={set("branchCode")} placeholder="6-digit code" error={fieldErrors.branchCode} />
              <TextInput label="Area" value={form.area} onChange={set("area")} placeholder="e.g. Gauteng North" />
              <TextInput label="Random Access Number" value={form.randomAccessNumber} onChange={set("randomAccessNumber")} placeholder="RAN" />
            </div>
          </section>

          <section className="space-y-4">
            <h4 className="border-b border-line pb-2 text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted">Contact & Management</h4>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              <TextInput label="Phone Number" value={form.phoneNumber} onChange={set("phoneNumber")} placeholder="Phone" />
              <TextInput label="Fax" value={form.fax} onChange={set("fax")} placeholder="Fax" />
              <TextInput label="Email" value={form.email} onChange={set("email")} placeholder="Email" type="email" error={fieldErrors.email} />
              <TextInput label="Manager Name" value={form.managerName} onChange={set("managerName")} placeholder="Manager" />
            </div>
          </section>

          <section className="space-y-4">
            <h4 className="border-b border-line pb-2 text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted">Address</h4>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <AddressAutocomplete label="Physical Address" value={form.physicalAddress} onChange={set("physicalAddress")} placeholder="Start typing a physical address..." />
              <AddressAutocomplete label="Postal Address" value={form.postalAddress} onChange={set("postalAddress")} placeholder="Start typing a postal address..." />
            </div>
          </section>

          <section className="space-y-4">
            <h4 className="border-b border-line pb-2 text-xs font-semibold uppercase tracking-[0.08em] text-ink-muted">Operations & Codes</h4>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              <TextInput label="Province Committee Code" value={form.provinceCommitteeCode} onChange={set("provinceCommitteeCode")} placeholder="e.g. GP" />
              <TextInput label="Province Committee Name" value={form.provinceCommitteeName} onChange={set("provinceCommitteeName")} placeholder="e.g. Gauteng" />
              <TextInput label="SASBO Area Code" value={form.sasboAreaCode} onChange={set("sasboAreaCode")} placeholder="Area code" />
              <TextInput label="Bank Department Code" value={form.bankDepartmentCode} onChange={set("bankDepartmentCode")} placeholder="Dept code" />
              <TextInput label="Table Code" value={form.tableCode} onChange={set("tableCode")} placeholder="Table code" type="number" />
              <TextInput label="Subs Value Branch" value={form.subsValueBranch} onChange={set("subsValueBranch")} placeholder="0.00" type="number" />
              <TextInput label="Staff Complement" value={form.branchStaffComplement} onChange={set("branchStaffComplement")} placeholder="0" type="number" />
              <TextInput label="Member Complement" value={form.branchMemberComplement} onChange={set("branchMemberComplement")} placeholder="0" type="number" />
            </div>
          </section>

          {error && <p role="alert" className="text-sm text-status-suspended">{error}</p>}
          </form>
        )}

    </Modal>
  );
}

/**
 * One field of the read-only view. Every value is already the form's string (`toBranchForm`), the address included:
 * the object-formatting this view used to carry was the second address formatter A29 hid behind, and it would have
 * printed a nested suburb (`{id, name}`) as "[object Object]".
 */
function DetailItem({ label, value }) {
  return (
    <div>
      <label className="text-[10px] uppercase tracking-wider text-ink-muted font-bold">
        {label}
      </label>
      <p className="font-semibold text-ink text-lg break-words">
        {String(value ?? "").trim() || "N/A"}
      </p>
    </div>
  );
}
