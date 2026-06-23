import { useEffect, useState } from "react";
import CrudPage, { type FieldDef } from "../components/CrudPage";
import { categoriesApi, merchantsApi, rulesApi, type Category, type Merchant, type Rule } from "../lib/api";

export default function RulesPage() {
    const [categories, setCategories] = useState<Category[]>([]);
    const [merchants, setMerchants] = useState<Merchant[]>([]);

    useEffect(() => {
        categoriesApi.list().then(setCategories).catch(() => setCategories([]));
        merchantsApi.list().then(setMerchants).catch(() => setMerchants([]));
    }, []);

    const fields: FieldDef<Rule>[] = [
        { name: "name", label: "Name", type: "text", required: true },
        { name: "merchantMatch", label: "Merchant contains", type: "text", required: true },
        {
            name: "categoryId",
            label: "Category",
            type: "select",
            required: true,
            options: categories.map((category) => ({ label: category.name, value: category.id })),
        },
        {
            name: "merchantId",
            label: "Merchant",
            type: "select",
            options: merchants.map((merchant) => ({ label: merchant.name, value: merchant.id })),
        },
        { name: "newMerchantName", label: "…or new merchant", type: "text", formOnly: true },
        { name: "autoApprove", label: "Auto-approve", type: "boolean", initial: false },
        { name: "priority", label: "Priority", type: "number", initial: 0 },
        { name: "enabled", label: "Enabled", type: "boolean", initial: true },
        { name: "matchCount", label: "Matches", type: "number", tableOnly: true },
    ];

    return <CrudPage<Rule> title="Rules" fields={fields} api={rulesApi} />;
}
