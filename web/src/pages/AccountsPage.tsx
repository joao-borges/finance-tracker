import { useEffect, useState } from "react";
import CrudPage, { type FieldDef } from "../components/CrudPage";
import EntityAvatar from "../components/EntityAvatar";
import MergeAccountButton from "../components/MergeAccountButton";
import { accountsApi, institutionsApi, type Account, type Institution } from "../lib/api";
import { formatMoney } from "../lib/format";

const ACCOUNT_TYPES = [
    { label: "Checking", value: "CHECKING" },
    { label: "Savings", value: "SAVINGS" },
    { label: "Credit Card", value: "CREDIT_CARD" },
    { label: "Loan", value: "LOAN" },
    { label: "Cash", value: "CASH" },
];

export default function AccountsPage() {
    const [institutions, setInstitutions] = useState<Institution[]>([]);

    useEffect(() => {
        institutionsApi.list().then(setInstitutions).catch(() => setInstitutions([]));
    }, []);

    const fields: FieldDef<Account>[] = [
        { name: "logoUrl", label: "", type: "text", tableOnly: true, renderCell: (row) => <EntityAvatar url={row.logoUrl} name={row.name} /> },
        { name: "name", label: "Name", type: "text", required: true },
        {
            name: "institutionId",
            label: "Institution",
            type: "select",
            options: [
                { label: "— none —", value: 0 },
                ...institutions.map((institution) => ({ label: institution.name, value: institution.id })),
            ],
            renderCell: (row) => row.institutionName ?? "—",
        },
        { name: "importRef", label: "Import ref", type: "text", tableOnly: true },
        { name: "type", label: "Type", type: "select", required: true, options: ACCOUNT_TYPES },
        { name: "currency", label: "Currency", type: "text", required: true, initial: "CAD" },
        { name: "balance", label: "Balance", type: "number", renderCell: (row) => formatMoney(row.balance, row.currency) },
        { name: "website", label: "Website", type: "text" },
        { name: "offBudget", label: "Off budget", type: "boolean", initial: false },
        { name: "hidden", label: "Hidden", type: "boolean", initial: false },
        { name: "archived", label: "Archived", type: "boolean", initial: false },
    ];

    // Group rows by institution (unassigned last), then by name.
    const groupByInstitution = (rows: Account[]) => {
        return rows.sort((a, b) => {
            const left = a.institutionName ?? "\uffff";
            const right = b.institutionName ?? "\uffff";
            return left.localeCompare(right) || a.name.localeCompare(b.name);
        });
    };

    return (
        <CrudPage<Account>
            title="Accounts"
            fields={fields}
            api={accountsApi}
            inlineEdit
            sortRows={groupByInstitution}
            rowActions={(row, reload) => <MergeAccountButton account={row} reload={reload} />}
        />
    );
}
