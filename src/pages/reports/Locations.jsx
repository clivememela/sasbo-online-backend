import {useState} from "react";
import { Link } from "react-router-dom";
import {Search, X, Printer} from "lucide-react";
import LocationListing from "./LocationListing.jsx";
import {locationService} from "../../services/locationService.js";
import { TextInput, Button } from "../../components/form";
import PageTitle from '../../components/common/PageTitle';
import Criteria, { CRITERION, CRITERION_WIDE } from '../../components/common/Criteria.jsx';
import DataState from '../../components/common/DataState.jsx';
import Sheet, { Page } from '../../components/common/Sheet.jsx';
import reportError from '../../lib/reportError.js';

export function Locations() {
    const [filters, setFilters] = useState({
        bankName: "",
        branch: "",
        area: "",
        randomAccessNumber: "",
    });
    const [results, setResults] = useState([]);
    const [searched, setSearched] = useState(false);

    const handleInputChange = (field) => (event) => {
        setFilters((prev) => ({ ...prev, [field]: event.target.value }));
    };

    /**
     * 🔴 FOUND BY `listStates.test.js` ON ITS FIRST RUN, and it is the third instance of
     * A27's class after the member register and two more in `04.3`.
     *
     * 🔑 AND IT FAILED MORE QUIETLY THAN THE OTHER TWO. Those rendered an EMPTY state
     * against a down backend, which is a wrong answer. This rendered NOTHING: a rejection
     * left `searched` false, so the screen stayed exactly as it was before the button was
     * pressed. Somebody presses Search, the page does not move, and there is no way to
     * tell "the request failed" from "the click did not land" -- so the natural response
     * is to press it again.
     */
    const [searchState, setSearchState] = useState('idle');

    const handleSearch = () => {
        setSearchState('loading');
        locationService.search(filters)
            .then((data) => {
                setResults(data ?? []);
                setSearched(true);
                setSearchState('ready');
            })
            .catch((err) => {
                reportError('Locations.search', err);
                setSearched(true);
                setSearchState('error');
            });
    };

    const handleClear = () => {
        setFilters({ bankName: "", branch: "", area: "", randomAccessNumber: "" });
        setResults([]);
        setSearched(false);
        setSearchState('idle');
    };

    return (
        <Page>
            <PageTitle
                title="Location Listings"
                subtitle="View and manage branch location records, member listings, and contact details across all financial institutions."
            />
            {/* card.html's arrangement by rule: the criteria printed on the page, the
                action on their baseline, the results as sheets beneath. */}
            <Criteria className="mt-5">
                <TextInput className={CRITERION_WIDE} label="Bank" value={filters.bankName} onChange={handleInputChange("bankName")} placeholder="Any" />
                <TextInput className={CRITERION} label="Branch" value={filters.branch} onChange={handleInputChange("branch")} placeholder="Any" />
                <TextInput className={CRITERION} label="Area" value={filters.area} onChange={handleInputChange("area")} placeholder="Any" />
                <TextInput className={CRITERION} label="Random access number" kind="identifier" value={filters.randomAccessNumber} onChange={handleInputChange("randomAccessNumber")} placeholder="Any" />
                {/* Its visible text IS its name, so it needs no aria-label -- what it
                    was missing was the primitive's hit area and focus ring. */}
                <Button variant="primary" onClick={handleSearch}>
                    <Search size={16} strokeWidth={1.75} aria-hidden="true" />
                    Search
                </Button>
                {searched && (
                    <Button variant="ghost" onClick={handleClear}>
                        <X size={14} strokeWidth={1.75} aria-hidden="true" /> Clear
                    </Button>
                )}
            </Criteria>
            {/* October build (5 Oct Show & Tell): the organisers' batch print -- every R/A number at once. */}
            <p className="no-print mt-3 text-sm text-ink-muted">
                Printing for several branches?{' '}
                <Link to="/reports/locations/print" className="font-medium text-brand-ink underline">
                    Type all their R/A numbers at once
                </Link>{' '}
                and print every listing together, each bank on a new page.
            </p>

            {searched && searchState === 'ready' && results.length > 0 && (
                <p className="mt-4 flex items-center gap-2 text-sm text-ink-muted">
                    <b className="font-semibold tabular-nums text-ink">{results.length}</b> location{results.length !== 1 ? "s" : ""} found
                    <Button size="icon" variant="ghost" onClick={() => window.print()} aria-label="Print these location results" className="no-print">
                        <Printer size={16} strokeWidth={1.75} aria-hidden="true" />
                    </Button>
                </p>
            )}

            {searchState === 'loading' && (
                <Sheet className="mt-4">
                    <DataState state="loading" layout="row" title="Searching locations…" />
                </Sheet>
            )}

            {/* 🔴 THE ERROR BRANCH IS SEPARATE FROM THE EMPTY ONE, and never falls through
                to it. "No locations match your search criteria" against a down backend is
                a statement about the union's branches; this is a statement about the
                server, and they are different facts. */}
            {searchState === 'error' && (
                <Sheet className="mt-4">
                    <DataState state="error" layout="row" title="We could not run that search." onRetry={handleSearch} />
                </Sheet>
            )}

            {searchState === 'ready' && searched && results.length === 0 && (
                <Sheet className="mt-4">
                    <DataState
                        state="empty"
                        layout="row"
                        title="No locations match these criteria"
                        message="Try a different bank, branch, area or random access number."
                        action={<Button onClick={handleClear}>Clear</Button>}
                    />
                </Sheet>
            )}

            {searchState === 'ready' && results.map((location, index) => (
                <LocationListing key={index} data={location} />
            ))}
        </Page>
    );
}
