import { Button, DatePicker, Select, Space } from "antd";
import dayjs from "dayjs";
import type { Account, Category, Merchant, TransactionFilters } from "../lib/api";

const { RangePicker } = DatePicker;

interface Props {
    filters: TransactionFilters;
    accounts: Account[];
    merchants: Merchant[];
    categories: Category[];
    onChange: (next: Partial<TransactionFilters>) => void;
    onClear: () => void;
}

export default function TransactionFilterBar({ filters, accounts, merchants, categories, onChange, onClear }: Props) {
    const reviewValue = filters.review === undefined ? "all" : filters.review ? "needs" : "reviewed";
    const range: [dayjs.Dayjs, dayjs.Dayjs] | undefined =
        filters.from && filters.to ? [dayjs(filters.from), dayjs(filters.to)] : undefined;

    return (
        <Space wrap style={{ marginBottom: 16 }}>
            <RangePicker
                value={range}
                onChange={(dates) => {
                    if (dates && dates[0] && dates[1]) {
                        onChange({ from: dates[0].format("YYYY-MM-DD"), to: dates[1].format("YYYY-MM-DD") });
                    } else {
                        onChange({ from: undefined, to: undefined });
                    }
                }}
            />
            <Select
                mode="multiple"
                allowClear
                maxTagCount="responsive"
                placeholder="Accounts"
                style={{ minWidth: 180 }}
                value={filters.accountIds}
                onChange={(value: number[]) => onChange({ accountIds: value })}
                options={accounts.map((account) => ({ label: account.name, value: account.id }))}
            />
            <Select
                mode="multiple"
                allowClear
                showSearch
                optionFilterProp="label"
                maxTagCount="responsive"
                placeholder="Merchants"
                style={{ minWidth: 180 }}
                value={filters.merchantIds}
                onChange={(value: number[]) => onChange({ merchantIds: value })}
                options={merchants.map((merchant) => ({
                    label: `${merchant.icon ? merchant.icon + " " : ""}${merchant.name}`,
                    value: merchant.id,
                }))}
            />
            <Select
                mode="multiple"
                allowClear
                maxTagCount="responsive"
                placeholder="Categories"
                style={{ minWidth: 180 }}
                value={filters.categoryIds}
                onChange={(value: number[]) => onChange({ categoryIds: value })}
                options={categories.map((category) => ({
                    label: `${category.icon ? category.icon + " " : ""}${category.name}`,
                    value: category.id,
                }))}
            />
            <Select
                style={{ minWidth: 140 }}
                value={reviewValue}
                onChange={(value: string) => onChange({ review: value === "all" ? undefined : value === "needs" })}
                options={[
                    { label: "All", value: "all" },
                    { label: "Needs review", value: "needs" },
                    { label: "Reviewed", value: "reviewed" },
                ]}
            />
            <Button onClick={onClear}>Clear</Button>
        </Space>
    );
}
