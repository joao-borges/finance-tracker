import CrudPage, { type FieldDef } from "../components/CrudPage";
import EntityAvatar from "../components/EntityAvatar";
import MergeAccountButton from "../components/MergeAccountButton";
import { accountsApi, type Account } from "../lib/api";
import { formatMoney } from "../lib/format";

const ACCOUNT_TYPES = [
    { label: "Checking", value: "CHECKING" },
    { label: "Savings", value: "SAVINGS" },
    { label: "Credit Card", value: "CREDIT_CARD" },
    { label: "Loan", value: "LOAN" },
    { label: "Cash", value: "CASH" },
];

const fields: FieldDef<Account>[] = [
    { name: "logoUrl", label: "", type: "text", tableOnly: true, renderCell: (row) => <EntityAvatar url={row.logoUrl} name={row.name} /> },
    { name: "name", label: "Name", type: "text", required: true },
    { name: "importRef", label: "Import ref", type: "text", tableOnly: true },
    { name: "type", label: "Type", type: "select", required: true, options: ACCOUNT_TYPES },
    { name: "currency", label: "Currency", type: "text", required: true, initial: "CAD" },
    { name: "balance", label: "Balance", type: "number", renderCell: (row) => formatMoney(row.balance, row.currency) },
    { name: "website", label: "Website", type: "text" },
    { name: "hidden", label: "Hidden", type: "boolean", initial: false },
    { name: "archived", label: "Archived", type: "boolean", initial: false },
];

export default function AccountsPage() {
    return (
        <CrudPage<Account>
            title="Accounts"
            fields={fields}
            api={accountsApi}
            rowActions={(row, reload) => <MergeAccountButton account={row} reload={reload} />}
        />
    );
}
