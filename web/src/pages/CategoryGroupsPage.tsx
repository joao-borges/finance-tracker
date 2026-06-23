import CrudPage, { type FieldDef } from "../components/CrudPage";
import { categoryGroupsApi, type CategoryGroup } from "../lib/api";

const fields: FieldDef<CategoryGroup>[] = [
    { name: "name", label: "Name", type: "text", required: true },
    { name: "sortOrder", label: "Sort order", type: "number", initial: 0 },
    { name: "icon", label: "Icon", type: "text" },
    { name: "color", label: "Color", type: "text" },
];

export default function CategoryGroupsPage() {
    return <CrudPage<CategoryGroup> title="Category Groups" fields={fields} api={categoryGroupsApi} />;
}
