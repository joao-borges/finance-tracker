import CrudPage, { type FieldDef } from "../components/CrudPage";
import EntityAvatar from "../components/EntityAvatar";
import { institutionsApi, type Institution } from "../lib/api";

const fields: FieldDef<Institution>[] = [
    { name: "logoUrl", label: "", type: "text", tableOnly: true, renderCell: (row) => <EntityAvatar url={row.logoUrl} name={row.name} /> },
    { name: "name", label: "Name", type: "text", required: true },
    { name: "website", label: "Website", type: "text" },
    { name: "offBudget", label: "Off budget (all accounts)", type: "boolean", initial: false },
];

export default function InstitutionsPage() {
    return <CrudPage<Institution> title="Institutions" fields={fields} api={institutionsApi} inlineEdit />;
}
