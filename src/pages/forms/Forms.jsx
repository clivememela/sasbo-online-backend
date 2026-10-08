import {useState} from "react";
import {OrganizerReport} from "../reports/OrganizerReport.jsx";
import GeneralForm from "./GeneralForm.jsx";
import LocationListing from "../reports/LocationListing.jsx";
import MembershipApplication from "./MembershipApplication.jsx";
import {TextInput, Button, FilterForm, SearchableSelect} from "../../components/form";
import { fieldValue } from "../../lib/fieldValue.js";
import { FileSearch } from "lucide-react";
import PageTitle from '../../components/common/PageTitle';
import Sheet, { Page } from '../../components/common/Sheet.jsx';
import DataState from '../../components/common/DataState.jsx';

/** The four criteria a form is opened for -- what A60 found discarded. */
const criteriaOf = ({ bankName, branch, area, randomAccessNumber }) => ({ bankName, branch, area, randomAccessNumber });

export function Forms(){
    const [filters, setFilters] = useState({
        bankName: "",
        branch: "",
        area: "",
        randomAccessNumber: "",
        formType: "",
    });
    /*
     * ✅ A60 (MO22, with A.1): THE CRITERIA REACH THE FORM -- the criteria the open form was OPENED for. Choosing a form
     * type opens it for the criteria as they stand, and "Search Form" opens it again for the criteria as they stand
     * then: another workplace, so it is remounted (its `key`) and seeded afresh. Changing a criterion alone does
     * nothing to a form already open -- never an effect that writes the form from the criteria, which would overwrite
     * what somebody had typed into it each time one changed (the register's warning). Search Form with the criteria
     * unchanged keeps the same key, and so everything typed.
     */
    const [opened, setOpened] = useState(() => criteriaOf(filters));

    const formTypeOptions = [
        { label: "Select Form Type", value: "" },
        { label: "Membership Application Form", value: "Membership Application Form" },
        { label: "Branch Form (online)", value: "Branch Form (online)" },
        { label: "Branch Form (offline)", value: "Branch Form (offline)" },
        { label: "Location Listing", value: "Location Listing" },
        { label: "Bulk Listing", value: "Bulk Listing" },
        { label: "General Form", value: "General Form" }
    ];

    /*
     * 🔴 R2-F5 (FIX-NOW, found in P9's execution): THIS CRASHED THE WHOLE SCREEN THE MOMENT A FORM TYPE WAS CHOSEN.
     *
     * `SearchableSelect` reports a choice as `onChange(value, option)` -- a VALUE, not an event -- and this read
     * `event.target.value`, so picking anything threw and the route fell to the error boundary. The picker is the
     * only control here, so the entire Forms feature was unreachable in the built app. `fieldValue` is that rule,
     * in one place: P9's refactor pass found these same three lines written twice.
     */
    const handleInputChange = (field) => (eventOrValue) => {
        setFilters((prev) => ({ ...prev, [field]: fieldValue(eventOrValue) }));
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        setOpened(criteriaOf(filters));
    };

    const renderForm = () => {
        const key = JSON.stringify(opened);
        switch (filters.formType) {
            // 🔴 `embedded`, BECAUSE THIS PICKER RENDERS WHOLE PAGES INSIDE A PAGE.
            // Both of these are also routes of their own, so each brought its own page gutter
            // and its own <h1> in here -- two <h1>s on `/reports/forms` the moment a form type
            // was chosen, and 28px of padding inside 28px of padding. Embedded they are only
            // their sheet, captioned by an <h2>, which is what `GeneralForm` and
            // `LocationListing` in the same switch already were.
            case "Membership Application Form":
                return <MembershipApplication embedded />;
            case "Branch Form (online)":
            case "Branch Form (offline)":
                return <OrganizerReport key={key} filters={opened} embedded />;
            case "Location Listing":
            case "Bulk Listing":
                return <LocationListing />;
            case "General Form":
                return <GeneralForm key={key} filters={opened} />;
            default:
                return null;
        }
    };

    return (
        <Page>
            <PageTitle title="Forms" subtitle="Search and complete Forms" />
            <FilterForm onSubmit={handleSubmit}>
                <TextInput
                    label="Bank"
                    value={filters.bankName}
                    onChange={handleInputChange("bankName")}
                    placeholder="Any"
                />
                <TextInput
                    label="Branch"
                    value={filters.branch}
                    onChange={handleInputChange("branch")}
                    placeholder="Any"
                />
                <TextInput
                    label="Area"
                    value={filters.area}
                    onChange={handleInputChange("area")}
                    placeholder="Any"
                />
                <TextInput
                    label="Random access number"
                    value={filters.randomAccessNumber}
                    onChange={handleInputChange("randomAccessNumber")}
                    placeholder="Any"
                />
                <div className="lg:col-span-2 basis-[220px]!">
                    <SearchableSelect
                        label="Form Type"
                        value={filters.formType}
                        onChange={(value) => {
                            handleInputChange("formType")(value);
                            setOpened(criteriaOf(filters));
                        }}
                        options={formTypeOptions}
                    />
                </div>

                <div className="flex items-end basis-auto!">
                    <Button type="submit" variant="primary">
                        <FileSearch size={16} strokeWidth={2} />
                        Search Form
                    </Button>
                </div>
            </FilterForm>
            {/* 🔴 `mx-auto` CENTRED THE FORM UNDER A LEFT-ALIGNED PAGE HEAD, so the
                title sat at the page gutter and the sheet it introduced started 150px
                to its right. Centring a body column under a left-aligned title is the
                one typographic thing the mock never does. `max-w-4xl` is the width the
                four things this switch can render already set for themselves. */}
            {/* 🔴 BELOW THE CRITERIA THE PAGE WAS BARE GROUND. With no form type chosen --
                which is how every visit starts -- a "Search Form" button sat above 650px of
                empty `--bg` with no heading, no sentence and no way forward, and a page that
                renders nothing reads as a page that failed. The mock never leaves the work
                area empty: its sheet is always there and carries its own state. So this one
                does too, and the resting state says the one thing the visitor needs. */}
            <div className="mt-6 max-w-4xl">
                {filters.formType ? renderForm() : (
                    <Sheet>
                        <DataState
                            state="empty"
                            title="Choose a form to begin."
                            message="Pick a form type above and it opens here, ready to complete or print."
                        />
                    </Sheet>
                )}
            </div>
        </Page>
    )
}
