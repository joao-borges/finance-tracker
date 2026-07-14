import { useEffect, useState } from "react";
import { IconButton, Tooltip } from "@mui/material";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutlined";
import { App as AntApp, Popconfirm } from "antd";
import CrudPage, { type FieldDef } from "../components/CrudPage";
import { categoriesApi, merchantsApi, rulesApi, type Category, type Merchant, type Rule } from "../lib/api";
import { errorText } from "../lib/format";

export default function RulesPage() {
    const { message } = AntApp.useApp();
    const [categories, setCategories] = useState<Category[]>([]);
    const [merchants, setMerchants] = useState<Merchant[]>([]);

    const removeRule = async (rule: Rule, reload: () => void) => {
        try {
            await rulesApi.remove(rule.id);
            message.success(`Rule "${rule.name}" deleted`);
            reload();
        } catch (error: unknown) {
            message.error(errorText(error));
        }
    };

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

    return (
        <CrudPage<Rule>
            title="Rules"
            fields={fields}
            api={rulesApi}
            rowActions={(row, reload) => (
                <Popconfirm
                    title={`Delete rule "${row.name}"?`}
                    description="Already-categorized transactions keep their category."
                    okText="Delete"
                    okButtonProps={{ danger: true }}
                    onConfirm={() => removeRule(row, reload)}
                >
                    <Tooltip title="Delete rule">
                        <IconButton size="small" color="error">
                            <DeleteOutlineIcon fontSize="small" />
                        </IconButton>
                    </Tooltip>
                </Popconfirm>
            )}
        />
    );
}
