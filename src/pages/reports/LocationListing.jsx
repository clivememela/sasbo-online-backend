import React from 'react';
import TableScroller from "../../components/table/TableScroller.jsx";
import { EMPTY, MONTHS, parseDate } from "../../lib/format.js";
import { cn } from "../../lib/utils";

/**
 * The blank form's worked example. `Forms.jsx` renders `<LocationListing />` with no props
 * to show an operator what a completed listing looks like, so this cannot simply be deleted.
 *
 * ⚠️ SYNTHETIC, AND DELIBERATELY OBVIOUS ABOUT IT. What was here before read as a real
 * branch record: four surnames with initials and titles, employee and membership numbers,
 * street addresses, and a named bank manager at `@absa.africa` — a full name at a real
 * corporate domain, shipped in the client bundle where anyone can read it.
 * `committedSecrets.js` looks for credentials, not for people, so nothing was ever going to
 * catch it. Every value below is invented and lands on `example.invalid`; the lengths are
 * kept so the column widths this file is being measured for do not move.
 *
 * 🔑 HOISTED OUT OF THE COMPONENT so the sample is not rebuilt on every render, and frozen
 * so that the spread below is the only way to vary it — the previous code mutated the
 * object it had just built, which was harmless only because it was rebuilt each time.
 */
const SAMPLE_LISTING = Object.freeze({
    bank: "SYNTHETIC BANK",
    ibtNo: "00000000",
    raNo: "00",
    costCode: "00000000",
    telNo: "0000000000",
    faxNo: "00000000000",
    branch: "SAMPLE BRANCH",
    address: "1 SAMPLE STREET,SAMPLE TOWN,0000",
    members: [
        { memNo: "0000001", name: "SAMPLE ,A, MS", bankAt: "SYNTHETIC BANK,CASH", subscr: "000", empNum: "00000001", email: "Y", id: "Y", cell: "Y", joined: "Jan 2020", susp: "0", label: "Y", alternateAddress: ", , ," },
        { memNo: "0000002", name: "EXAMPLE ,B, MRS", bankAt: "SYNTHETIC BANK,CASH", subscr: "000", empNum: "00000002", email: "Y", id: "Y", cell: "Y", joined: "Feb 2021", susp: "0", label: "Y", alternateAddress: "1 SAMPLE STREET , SAMPLE TOWN , , 0000" },
        { memNo: "0000003", name: "TESTPERSON ,C, MS", bankAt: "SYNTHETIC BANK,CASH", subscr: "000", empNum: "00000003", email: "Y", id: "Y", cell: "Y", joined: "Mar 2022", susp: "0", label: "Y", alternateAddress: "2 SAMPLE STREET , SAMPLE SUBURB , SAMPLE TOWN , 0000" },
        { memNo: "0000004", name: "PLACEHOLDER ,D, MRS", bankAt: "SYNTHETIC BANK,CASH", subscr: "000", empNum: "00000004", email: "Y", id: "Y", cell: "Y", joined: "Apr 2023", susp: "0", label: "Y", alternateAddress: ", , ," },
    ],
    areaRepName: "SAMPLE AREA REP",
    becMembers: "",
    memnetMembers: "",
    bankManager: "SAMPLE MANAGER",
    bankManagerEmail: "branch.manager@example.invalid",
    alternativeContact: "",
    alternativeContactEmail: "",
    complement: "4",
    membership: "4",
    lastVisited: "Mar 2025 , Oct 2024 , Nov 2023 , Nov 2021 , Jul 2021 , Jan 2021 , Jul 2019 , Jun 2017 , Aug 2016 , Nov 2015",
});

/*
 * 🔴 THE SPAN IS A WHOLE CLASS NAME IN THE DATA, BECAUSE TAILWIND READS SOURCE, NOT RUNTIME.
 *
 * This grid used `` `col-span-${span}` `` — a perfectly valid class name the scanner never
 * sees, so `.col-span-3` was never generated and the rule silently did nothing. Confirmed
 * against the built stylesheet: `.col-span-2` is present (something else asks for it
 * literally) and `.col-span-3` is absent, while the class sat in the DOM matching nothing.
 * A style present, spelled correctly, and inert. `src/test/dynamicUtilities.test.js` guards
 * the class of defect.
 *
 * 🔑 AND A `{ 3: 'lg:col-span-3' }` LOOKUP WOULD FIX THE INTERPOLATION WHILE KEEPING THE
 * SILENT DEGRADE ONE KEY AWAY — `SPAN_CLASS[4]` is `undefined`, which interpolates as the
 * literal class name "undefined": inert again, with no error. So the class lives in the row
 * itself, where the scanner can see it, and `cn` simply drops the rows that carry none.
 *
 * `lg:` because the span is only meaningful once there are four columns to span; at
 * `grid-cols-1` it would be asking a phone for three columns it does not have.
 */

/*
 * 🔴 EVERY RESULT CARD PRINTED BLANK -- found by the October build's manual pass (7 Oct 2026). The card read only the
 * sample's own keys (`bank`, `ibtNo`, `raNo` ...), and `Locations` hands it the server's `BankBranch` rows from
 * `GET /api/branches`, whose keys are the entity's (`bankName`, `branchCode`, `randomAccessNumber` ...): not one
 * matched, so every label stood beside nothing. A record is now read in either shape -- the listing's own key first,
 * then the server's -- and what neither carries is the em dash, missing rather than blank.
 *
 * Each label's server field is the one the Bank / Branch screens already show under the same meaning: the branch code
 * is the IBT number; `membersCount` is the membership (at or under the staff complement in 67 of the 68 seeded rows,
 * where `branchMemberComplement` is another figure); `lastVisitedDate` is the last visit, printed as its month as the
 * legacy listing printed its visits. Nothing the server does not send is invented: the cost code, the area
 * representative, the BEC and Memnet counts and the alternative contact have no field there yet, and say so.
 */

/** The branch's address: the listing's own line, else the server's address parts, else its postal address. */
const addressOf = (record) => {
    if (typeof record.address === 'string' && record.address.trim()) return record.address;
    const where = record.physicalAddress;
    if (typeof where === 'string' && where.trim()) return where;
    const street = [where?.streetNumber, where?.streetName].filter(Boolean).join(' ');
    const parts = [where?.complexOrBuilding, street, where?.suburbName ?? where?.suburb?.name,
        where?.cityName ?? where?.city?.name, where?.provinceName ?? where?.province?.name, where?.postalCode]
        .map((part) => String(part ?? '').trim()).filter(Boolean);
    return parts.length ? parts.join(', ') : record.postalAddress;
};

/** A day as the listing prints a visit -- its month -- or the text as it came, where it is not a day. */
const visitedOf = (value) => {
    const date = parseDate(value);
    return date ? `${MONTHS[date.m - 1]} ${date.y}` : value;
};

/** The first value that is there; the em dash where none is. */
const either = (...values) => {
    const found = values.find((value) => value != null && String(value).trim() !== '');
    return found ?? EMPTY;
};

/** A record in the listing's own shape, or a `GET /api/branches` row, as the card prints it. */
const cardOf = (record) => ({
    ...record,
    bank: either(record.bank, record.bankName),
    ibtNo: either(record.ibtNo, record.branchCode),
    raNo: either(record.raNo, record.randomAccessNumber),
    costCode: either(record.costCode),
    telNo: either(record.telNo, record.phoneNumber),
    faxNo: either(record.faxNo, record.fax),
    branch: either(record.branch, record.branchName),
    address: either(addressOf(record)),
    areaRepName: either(record.areaRepName),
    becMembers: either(record.becMembers),
    memnetMembers: either(record.memnetMembers),
    bankManager: either(record.bankManager, record.managerName),
    bankManagerEmail: either(record.bankManagerEmail, record.email),
    alternativeContact: either(record.alternativeContact),
    alternativeContactEmail: either(record.alternativeContactEmail),
    complement: either(record.complement, record.branchStaffComplement),
    membership: either(record.membership, record.membersCount),
    lastVisited: either(visitedOf(record.lastVisited ?? record.lastVisitedDate)),
});

const LocationListing = ({ data, filters }) => {
    // A caller either hands over a record or asks for the worked example. These are NOT
    // merged: filling a real branch's gaps from the sample would print invented members
    // under a real branch heading, which is worse than printing nothing.
    const listingData = data ? cardOf(data) : {
        ...SAMPLE_LISTING,
        bank: filters?.bankName || SAMPLE_LISTING.bank,
        branch: filters?.branch || SAMPLE_LISTING.branch,
        raNo: filters?.randomAccessNumber || SAMPLE_LISTING.raNo,
    };

    // 🔴 THE RECORD IS NOT REQUIRED TO CARRY MEMBERS, AND THIS ROUTE ASSUMED IT DID.
    // `Locations` hands each `/api/branches` result straight here, and a branch row has no
    // `members` key — so `listingData.members.map(...)` threw `Cannot read properties of
    // undefined` and the ErrorBoundary replaced the whole page with "This page could not be
    // displayed." Every successful location search did this. Found by driving the route to
    // the state the width matrix cannot reach: the matrix loads `/reports/locations` and
    // measures the search form, which renders perfectly.
    const members = Array.isArray(listingData.members) ? listingData.members : [];

    return (
        <div className="print-area mt-4 rounded-lg bg-surface p-6 shadow-(--shadow-1)">

            {/* Title */}
            <div className="text-center font-bold text-base tracking-wide mb-5 pb-3 border-b border-line-strong text-ink uppercase">
                SASBO Finance Union — Location Listing
            </div>

            {/* Header info */}
            {/* 📱 FOUR COLUMNS WITH NO BREAKPOINT. At 390px that is roughly 90px per
                column for label-and-value pairs, so every value wrapped or clipped. One
                column on a phone, two on a tablet, four where there is room. */}
            <div className="grid grid-cols-1 gap-x-6 gap-y-1.5 mb-5 p-3 rounded-lg bg-surface-2 border border-line text-sm sm:grid-cols-2 lg:grid-cols-4">
                {[['Bank', listingData.bank], ['IBT No', listingData.ibtNo], ['R/A No', listingData.raNo], ['Cost Code', listingData.costCode],
                  ['Tel No', listingData.telNo], ['Fax No', listingData.faxNo, 'lg:col-span-3'], ['Branch', listingData.branch],
                  ['Address', listingData.address, 'lg:col-span-3']
                ].map(([label, value, span]) => (
                    <div key={label} className={cn('flex gap-1.5 text-ink', span)}>
                        <span className="font-semibold shrink-0 text-ink">{label}:</span>
                        <span>{value}</span>
                    </div>
                ))}
            </div>

            {/* 📱 ELEVEN COLUMNS. `overflow-x-auto` on a bare div scrolls with a mouse
                and with a finger, and with NOTHING ELSE: no tab stop, no accessible name,
                and no indication that there is anything to the right of the fold.
                `TableScroller` makes it a labelled region when -- and only when -- it can
                actually scroll, and fades its edges so the overflow is visible rather than
                discovered. */}
            <TableScroller label="Members at this location" className="mb-5">
                <table className="w-full border-collapse text-sm">
                    <caption className="sr-only">Members at this location</caption>
                    <thead className="sticky top-0 z-10">
                        <tr className="bg-surface-2 text-left text-xs uppercase tracking-wide text-ink-muted border-b border-line-strong">
                            <th className="py-2 px-2 whitespace-nowrap">Mem No</th>
                            <th className="py-2 px-2">Name of Member</th>
                            <th className="py-2 px-2">Bank At</th>
                            <th className="py-2 px-2 text-right">Subscr</th>
                            <th className="py-2 px-2">Emp Num</th>
                            <th className="py-2 px-2 text-center">Email</th>
                            <th className="py-2 px-2 text-center">ID</th>
                            <th className="py-2 px-2 text-center">Cell</th>
                            <th className="py-2 px-2 whitespace-nowrap">Joined</th>
                            <th className="py-2 px-2 text-center">Susp</th>
                            <th className="py-2 px-2 text-center">Label</th>
                        </tr>
                    </thead>
                    <tbody>
                        {/* R-ROB-H: keyed by the MEMBER, not by array position. An index
                            key hands one row's DOM node to another the moment the list
                            reorders, and nothing errors when it does. */}
                        {/* 🔑 AN EMPTY LISTING SAYS SO. A `<tbody>` with nothing in it is
                            indistinguishable from one whose data never arrived — this arc's
                            recurring species, rendered. */}
                        {members.length === 0 && (
                            <tr>
                                <td colSpan="11" className="py-6 px-2 text-center text-sm text-ink-muted">
                                    No members are recorded at this location.
                                </td>
                            </tr>
                        )}
                        {members.map((member, index) => (
                            <React.Fragment key={member.memNo ?? member.id ?? index}>
                                <tr className={`text-sm text-ink ${index % 2 === 0 ? 'bg-surface' : 'bg-surface-2/60'}`}>
                                    <td className="py-1.5 px-2 whitespace-nowrap">{member.memNo}</td>
                                    <td className="py-1.5 px-2 font-semibold whitespace-nowrap">{member.name}</td>
                                    <td className="py-1.5 px-2">{member.bankAt}</td>
                                    <td className="py-1.5 px-2 text-right">{member.subscr}</td>
                                    <td className="py-1.5 px-2">{member.empNum}</td>
                                    <td className="py-1.5 px-2 text-center">{member.email}</td>
                                    <td className="py-1.5 px-2 text-center">{member.id}</td>
                                    <td className="py-1.5 px-2 text-center">{member.cell}</td>
                                    <td className="py-1.5 px-2 whitespace-nowrap">{member.joined}</td>
                                    <td className="py-1.5 px-2 text-center">{member.susp}</td>
                                    <td className="py-1.5 px-2 text-center">{member.label}</td>
                                </tr>
                                <tr className="border-b border-line">
                                    <td />
                                    <td colSpan="10" className="pb-2 px-2 text-xs italic text-ink-muted">
                                        Alt. Address: {member.alternateAddress}
                                    </td>
                                </tr>
                            </React.Fragment>
                        ))}
                    </tbody>
                </table>
            </TableScroller>

            {/* Footer details */}
            <div className="pt-4 border-t border-line space-y-1">
                {[
                    ['Area Rep Name', listingData.areaRepName],
                    ['BEC Members', listingData.becMembers],
                    ['Memnet Members', listingData.memnetMembers],
                    ['Bank Manager', listingData.bankManager],
                    ['Bank Manager Email', listingData.bankManagerEmail],
                    ['Alternative Contact', listingData.alternativeContact],
                    ['Alternative Contact Email', listingData.alternativeContactEmail],
                    // 🗣️ "COMPLEMENT", the staff of the branch -- spelt "Compliments" here until the owner promised
                    // the fix at the 5 Oct Show & Tell. A branch record from `/api/branches` carries it as its staff
                    // complement (`cardOf`).
                    ['Complement', listingData.complement],
                    ['Membership', listingData.membership],
                    ['Last Visited', listingData.lastVisited],
                ].map(([label, value]) => (
                    <div key={label} className="flex gap-2 text-sm text-ink">
                        <span className="font-semibold min-w-[220px] shrink-0 text-ink">{label}:</span>
                        <span>{value}</span>
                    </div>
                ))}
            </div>
        </div>
    );
};

export default LocationListing;
