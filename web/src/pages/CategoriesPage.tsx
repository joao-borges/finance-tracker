import { useEffect, useState } from "react";
import CrudPage, { type FieldDef } from "../components/CrudPage";
import { categoriesApi, categoryGroupsApi, type Category, type CategoryGroup } from "../lib/api";

export default function CategoriesPage() {
    const [groups, setGroups] = useState<CategoryGroup[]>([]);

    useEffect(() => {
        categoryGroupsApi.list().then(setGroups).catch(() => setGroups([]));
    }, []);

    const fields: FieldDef<Category>[] = [
        { name: "name", label: "Name", type: "text", required: true },
        {
            name: "groupId",
            label: "Group",
            type: "select",
            options: groups.map((group) => ({ label: group.name, value: group.id })),
        },
        { name: "income", label: "Income", type: "boolean", initial: false },
        { name: "sortOrder", label: "Sort order", type: "number", initial: 0 },
        { name: "icon", label: "Icon", type: "emoji" },
        { name: "archived", label: "Archived", type: "boolean", initial: false },
    ];

    return <CrudPage<Category> title="Categories" fields={fields} api={categoriesApi} />;
}
