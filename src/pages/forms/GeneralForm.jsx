import { useState } from 'react';
import TextInput from '../../components/form/TextInput';
import Button from '../../components/form/Button';

export default function GeneralForm({ filters }) {
    const [nonMembers, setNonMembers] = useState(
        Array.from({ length: 54 }, (_, i) => ({ id: i + 1, name: '', indicator: '' }))
    );

    /*
     * ✅ A60, CLOSED (MO22, with A.1): the Bank and R/A No the Forms screen asks for SEED this form, read once as it
     * mounts; `Forms.jsx` remounts it with a `key` when it opens it for other criteria. Never an effect -- it would
     * overwrite what somebody had typed each time a criterion changed.
     *
     * 🔴 AND THE `'ABSA BANK'` AND `'94'` THAT STOOD HERE ARE GONE. They were not defaults behind a filter but
     * hardcoded values wearing a filter's clothes, printed on the form whatever was entered: a criterion left empty is
     * an empty box now, to be filled in, never somebody else's bank.
     */
    const [bankDetails, setBankDetails] = useState(() => ({
        bank: filters?.bankName || '',
        ibtNo: '',
        raNo: filters?.randomAccessNumber || '',
        costCode: '',
        telNo: ''
    }));

    const handleNonMemberChange = (index, field, value) => {
        const updated = [...nonMembers];
        updated[index][field] = value;
        setNonMembers(updated);
    };

    return (
        <div className="mt-5 rounded-lg bg-surface p-6 text-ink shadow-(--shadow-1)">
            <div className="mb-6 border-b border-line pb-4 text-center">
                {/* 🔴 An <h1> used as a `text-sm` caption -- N8's other half, and the
                    same shape as `OrganizerReport`. This component renders INSIDE a page
                    that already has its own title, so it was giving that route two <h1>s
                    the moment `PageTitle` was promoted. */}
                <h2 className="text-xs font-semibold uppercase tracking-[0.12em] text-ink-muted">
                    SASBO — The Finance Union — General Form
                </h2>
            </div>

            <div className="mb-6 rounded-lg bg-accent-soft px-3 py-2.5 text-sm leading-relaxed text-ink">
                <span className="mb-0.5 block text-[11px] font-semibold uppercase tracking-[0.08em] text-accent-ink">How to complete this form</span>
                Please indicate below the title, initials and surname of every non-member of SASBO at the branch / department, as well as, after attempting to recruit the individual, provide one of the following indicators in the application column:{' '}
                {/* `not-italic` went with the italics it was undoing, and the emphasis is
                    the world's semibold rather than bold. */}
                <span className="font-semibold text-accent-ink">
                    &lsquo;J&rsquo; = Joined, &lsquo;NI&rsquo; = Not Interested, &lsquo;NA&rsquo; = Not Available, &lsquo;NT&rsquo; = No Time to See.
                </span>
            </div>

            <div className="grid grid-cols-2 md:grid-cols-5 gap-4 mb-8 p-4 border border-line rounded-lg bg-surface-2">
                {[
                    { label: 'Bank',      field: 'bank' },
                    { label: 'IBT No',    field: 'ibtNo' },
                    { label: 'R/A No',    field: 'raNo' },
                    { label: 'Cost Code', field: 'costCode' },
                    { label: 'Tel No',    field: 'telNo' },
                ].map(({ label, field }) => (
                    <TextInput
                        key={field}
                        label={label}
                        value={bankDetails[field]}
                        onChange={(e) => setBankDetails(prev => ({ ...prev, [field]: e.target.value }))}
                    />
                ))}
            </div>

            <div className="hidden lg:grid lg:grid-cols-3 gap-8 mb-2 px-2 text-xs font-semibold uppercase tracking-wide text-ink-muted">
                {[0, 1, 2].map(i => (
                    <div key={i} className={`flex gap-4 ${i > 0 ? 'border-l border-line-strong pl-4' : ''}`}>
                        <span className="w-8">#</span>
                        <span className="flex-1">Name of Non-Member</span>
                        <span className="w-20">Indicator</span>
                    </div>
                ))}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-x-8 gap-y-1">
                {nonMembers.map((member, idx) => (
                    <div key={member.id} className="flex items-center gap-2 py-1 border-b border-line hover-hover:hover:bg-surface-2/30 transition-colors">
                        <span className="w-8 text-xs font-medium text-ink-muted shrink-0">{member.id}.</span>
                        {/* 🔑 SIXTY CONTROLS WHOSE ONLY LABEL WAS A PLACEHOLDER. A
                            placeholder disappears the moment anything is typed, and it is
                            not an accessible name -- so this roster announced as sixty
                            identical unnamed text boxes. The row NUMBER is what
                            distinguishes them on screen, so it is what distinguishes them
                            here too. */}
                        <input
                            type="text"
                            placeholder="Name"
                            aria-label={`Name of non-member ${member.id}`}
                            className="flex-1 bg-transparent border-none focus:outline-none field-text py-1 text-ink placeholder:text-ink-muted"
                            value={member.name}
                            onChange={(e) => handleNonMemberChange(idx, 'name', e.target.value)}
                        />
                        <input
                            type="text"
                            placeholder="Ind."
                            maxLength={2}
                            aria-label={`Indicator for non-member ${member.id}`}
                            className="w-14 bg-transparent border-none focus:outline-none field-text py-1 text-center text-ink placeholder:text-ink-muted uppercase"
                            value={member.indicator}
                            onChange={(e) => handleNonMemberChange(idx, 'indicator', e.target.value)}
                        />
                    </div>
                ))}
            </div>

            <div className="mt-10 flex justify-end gap-3 pt-4 border-t border-line">
                <Button variant="default">Save Draft</Button>
                <Button variant="primary">Submit Form</Button>
            </div>
        </div>
    );
}
