import CrudPage, { type FieldDef } from "../components/CrudPage";
import EntityAvatar from "../components/EntityAvatar";
import { merchantsApi, type Merchant } from "../lib/api";

const fields: FieldDef<Merchant>[] = [
    { name: "logoUrl", label: "", type: "text", tableOnly: true, renderCell: (row) => <EntityAvatar url={row.logoUrl} name={row.icon ?? row.name} /> },
    { name: "name", label: "Name", type: "text", required: true },
    { name: "icon", label: "Emoji", type: "emoji" },
    { name: "website", label: "Website", type: "text" },
];

export default function MerchantsPage() {
    return <CrudPage<Merchant> title="Merchants" fields={fields} api={merchantsApi} />;
}
