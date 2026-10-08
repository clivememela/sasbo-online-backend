import { useCallback, useMemo, useState, useEffect } from "react";
import { Plus, Printer } from "lucide-react";
import { bankService } from "../../services/bankService.js";
import { TextInput, SearchableSelect, Button } from "../../components/form";
import { fieldValue } from "../../lib/fieldValue.js";
import BankBranchModal from "./BankBranchModal.jsx";
import PageTitle from '../../components/common/PageTitle';
import Criteria, { CRITERION, CRITERION_WIDE } from '../../components/common/Criteria.jsx';
import Pager from '../../components/common/Pager.jsx';
import Sheet, { Page } from '../../components/common/Sheet.jsx';
import Table from '../../components/table/Table.jsx';
import reportError from '../../lib/reportError.js';

const PAGE_SIZE = bankService.getPageSize();

export default function BankBranch() {
  const [banks, setBanks] = useState([]);

  /**
   * 🔴 THIS WAS `bankService.getBanks().then(setBanks)` WITH NO `.catch` AT ALL. A
   * failed request became an unhandled rejection and the screen rendered "No banks match
   * your search" -- a confident wrong answer with not even a console line behind it. A27
   * is the same defect on the member register, and the plan predicted this: "A27 is
   * probably one instance of a class."
   */
  const [listState, setListState] = useState('loading');

  /*
   * 🔴 THE LOADING RESET LIVES ON THE RETRY PATH, NOT INSIDE THE FETCH (`10.4`). Same change, and the same
   * reason, as `AcbDebits.jsx`, whose comment carries the full account: the reset was a no-op on mount, so
   * the fetch owns its outcome and `retryBanks` owns the intent to reload.
   */
  const loadBanks = useCallback(() => bankService.getBanks()
    .then((data) => { setBanks(data ?? []); setListState('ready'); })
    .catch((err) => { reportError('BankBranch.load', err); setListState('error'); }), []);

  const retryBanks = useCallback(() => { setListState('loading'); loadBanks(); }, [loadBanks]);

  useEffect(() => { loadBanks(); }, [loadBanks]);
  const [filters, setFilters] = useState({
    bankName: "",
    branch: "",
    area: "",
    randomAccessNumber: "",
  });
  const [page, setPage] = useState(1);
  const [selectedBank, setSelectedBank] = useState(null);
  const [showAddBank, setShowAddBank] = useState(false);

  const filteredBanks = useMemo(() => {
    const bankNameQuery = filters.bankName.trim().toLowerCase();
    const branchQuery = filters.branch.trim().toLowerCase();
    const areaQuery = filters.area.trim().toLowerCase();
    const ranQuery = filters.randomAccessNumber.trim().toLowerCase();

    return banks.filter((bank) => {
      if (bankNameQuery && !bank.bankName?.toLowerCase().includes(bankNameQuery)) return false;
      if (branchQuery && !bank.branchName?.toLowerCase().includes(branchQuery)) return false;
      if (areaQuery && !bank.area?.toLowerCase().includes(areaQuery)) return false;
      return !(ranQuery && !bank.randomAccessNumber?.toLowerCase().includes(ranQuery));
    });
  }, [filters, banks]);

  const totalPages = Math.max(1, Math.ceil(filteredBanks.length / PAGE_SIZE));
  const startIndex = (page - 1) * PAGE_SIZE;
  const endIndex = Math.min(startIndex + PAGE_SIZE, filteredBanks.length);
  const visibleBanks = filteredBanks.slice(startIndex, endIndex);

  /*
   * 🔴 R2-F5's SECOND SITE (FIX-NOW, found in P9's execution): choosing a bank threw and took the register with it.
   * `SearchableSelect` reports a VALUE, `TextInput` an event, and this read `event.target.value` from both.
   * `fieldValue` states that rule once -- see its own file for what it cost to learn.
   */
  const handleInputChange = (field) => (eventOrValue) => {
    setFilters((prev) => ({ ...prev, [field]: fieldValue(eventOrValue) }));
    setPage(1);
  };

  /**
   * 🔴 NINE COLUMNS, IN A `grid-cols-9`, WITH `<div onClick>` ROWS. The header was nine
   * `<div>`s with no relationship to the cells beneath them, so a screen-reader user got
   * eighty-one unlabelled values in a row; and opening a branch to edit it was mouse-only.
   *
   * 📱 R-RESP-K IS DOING REAL WORK HERE. Nine columns at 390px is roughly 40px each --
   * every value truncated to nothing. Two are essential and the other seven drop into the
   * row's detail panel, so a phone shows a usable list instead of a wall of ellipses.
   */
  const bankColumns = useMemo(() => [
    {
      key: 'bankName',
      header: 'Bank Name',
      priority: 'essential',
      truncate: true,
      cell: (bank) => (
        <button
          type="button"
          onClick={() => setSelectedBank(bank)}
          aria-label={`${bank.bankName || 'This bank'} \u2014 open the ${bank.branchName || 'branch'} record`}
          className="touch-target rounded-sm text-left font-medium text-brand-ink underline-offset-2 hover-hover:hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-ring"
        >
          {bank.bankName}
        </button>
      ),
    },
    { key: 'bankAbbreviation', header: 'Abbreviation', priority: 'essential', cell: (b) => b.bankAbbreviation || '\u2014' },
    { key: 'branchName', header: 'Branch', priority: 'secondary', truncate: true, cell: (b) => b.branchName || '\u2014' },
    { key: 'area', header: 'Area', priority: 'secondary', truncate: true, cell: (b) => b.area || '\u2014' },
    { key: 'randomAccessNumber', header: 'Random Access Number', priority: 'detail', cell: (b) => b.randomAccessNumber || '\u2014' },
    { key: 'phoneNumber', header: 'Phone', priority: 'detail', cell: (b) => b.phoneNumber || '\u2014' },
    { key: 'managerName', header: 'Manager', priority: 'detail', cell: (b) => b.managerName || '\u2014' },
    { key: 'fax', header: 'Fax', priority: 'detail', cell: (b) => b.fax || '\u2014' },
    { key: 'email', header: 'Email', priority: 'detail', truncate: true, cell: (b) => b.email || '\u2014' },
  ], []);

  return (
    <Page>
      <PageTitle
        title="Bank / Branch"
        subtitle="Every bank branch Sasbo members work from, with its area and random access number."
        actions={
          <>
            <Button onClick={() => globalThis.print()} title="Print the listing, or save it as a PDF">
              <Printer size={16} strokeWidth={1.75} aria-hidden="true" /> Print
            </Button>
            <Button variant="primary" onClick={() => setShowAddBank(true)}>
              <Plus size={16} strokeWidth={1.75} aria-hidden="true" /> Add Bank / Branch
            </Button>
          </>
        }
      />

      <Criteria className="mt-5">
        <SearchableSelect
          className={CRITERION_WIDE}
          label="Bank"
          value={filters.bankName}
          onChange={handleInputChange("bankName")}
          placeholder="All banks"
          options={[{ value: '', label: 'All banks' }, ...([...new Set(banks.map(b => b.bankName).filter(Boolean))].sort().map(b => ({ value: b, label: b })))]}
        />
        <TextInput
          className={CRITERION}
          label="Branch"
          value={filters.branch}
          onChange={handleInputChange("branch")}
          placeholder="Any"
        />
        <TextInput
          className={CRITERION}
          label="Area"
          value={filters.area}
          onChange={handleInputChange("area")}
          placeholder="Any"
        />
        <TextInput
          className={CRITERION}
          label="Random access number"
          kind="identifier"
          value={filters.randomAccessNumber}
          onChange={handleInputChange("randomAccessNumber")}
          placeholder="Any"
        />
      </Criteria>

      <Sheet className="mt-4">
        <div className="print-area">
          <Table
            caption="Bank branches"
            columns={bankColumns}
            rows={visibleBanks}
            rowKey="id"
            loading={listState === 'loading'}
            error={listState === 'error' ? 'We could not load the bank branches.' : null}
            onRetry={retryBanks}
            emptyTitle="No branches match these criteria"
            emptyMessage="Try a different bank, branch or area."
            dense
          />
        </div>
        <Pager
          page={page}
          totalPages={totalPages}
          onPageChange={setPage}
          count={filteredBanks.length}
          error={listState === 'error'}
          start={startIndex + 1}
          end={endIndex}
          noun="branches"
          loading={listState === 'loading'}
        />
      </Sheet>

      {selectedBank && (
        <BankBranchModal
          selectedBank={selectedBank}
          onClose={() => setSelectedBank(null)}
          onUpdated={(updatedBank) => {
            setBanks((prev) => prev.map((bank) => (bank.id === updatedBank.id ? updatedBank : bank)));
            setSelectedBank(updatedBank);
          }}
        />
      )}

      {showAddBank && (
        <BankBranchModal
          onClose={() => setShowAddBank(false)}
          onCreated={(newBank) => { setBanks((prev) => [...prev, newBank]); setShowAddBank(false); }}
        />
      )}

    </Page>
  );
}
