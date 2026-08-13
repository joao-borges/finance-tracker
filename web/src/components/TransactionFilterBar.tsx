import { useState } from "react";
import { Button, DatePicker, Select } from "antd";
import FilterListIcon from "@mui/icons-material/FilterList";
import dayjs from "dayjs";
import type { Account, Category, Merchant, TransactionFilters } from "../lib/api";
import styles from "./TransactionFilterBar.module.css";

const { RangePicker } = DatePicker;

interface Props {
    filters: TransactionFilters;
    accounts: Account[];
    merchants: Merchant[];
    categories: Category[];
    tagOptions?: string[];
    onChange: (next: Partial<TransactionFilters>) => void;
    onClear: () => void;
}

// Drives the count on the mobile "Filters" button, so a collapsed bar still
// tells you how many filters are narrowing the list.
function activeCount(filters: TransactionFilters): number {
    let count = 0;
    if (filters.from && filters.to) {
        count += 1;
    }
    if (filters.accountIds && filters.accountIds.length > 0) {
        count += 1;
    }
    if (filters.merchantIds && filters.merchantIds.length > 0) {
        count += 1;
    }
    if (filters.categoryIds && filters.categoryIds.length > 0) {
        count += 1;
    }
    if (filters.review !== undefined) {
        count += 1;
    }
    return count;
}

export default function TransactionFilterBar({ filters, accounts, merchants, categories, tagOptions, onChange, onClear }: Props) {
    const [open, setOpen] = useState(false);
    const reviewValue = filters.review === undefined ? "all" : filters.review ? "needs" : "reviewed";
    const range: [dayjs.Dayjs, dayjs.Dayjs] | undefined =
        filters.from && filters.to ? [dayjs(filters.from), dayjs(filters.to)] : undefined;
    const active = activeCount(filters);

    return (
        <div className={styles.bar}>
            <Button
                className={styles.toggle}
                icon={<FilterListIcon fontSize="small" />}
                onClick={() => setOpen((current) => !current)}
            >
                {active > 0 ? `Filters (${active})` : "Filters"}
            </Button>

            <div className={styles.controls} data-open={open}>
                <RangePicker
                    className={styles.control}
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
                    className={styles.control}
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
                    className={styles.control}
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
                    className={styles.control}
                    value={filters.categoryIds}
                    onChange={(value: number[]) => onChange({ categoryIds: value })}
                    options={categories.map((category) => ({
                        label: `${category.icon ? category.icon + " " : ""}${category.name}`,
                        value: category.id,
                    }))}
                />
                <Select
                    mode="multiple"
                    allowClear
                    maxTagCount="responsive"
                    placeholder="Tags"
                    className={styles.control}
                    value={filters.tags}
                    onChange={(value: string[]) => onChange({ tags: value.length > 0 ? value : undefined })}
                    options={(tagOptions ?? []).map((tag) => ({ label: tag, value: tag }))}
                />
                <Select
                    className={styles.control}
                    value={reviewValue}
                    onChange={(value: string) => onChange({ review: value === "all" ? undefined : value === "needs" })}
                    options={[
                        { label: "All", value: "all" },
                        { label: "Needs review", value: "needs" },
                        { label: "Reviewed", value: "reviewed" },
                    ]}
                />
                <Button className={styles.control} onClick={onClear}>
                    Clear
                </Button>
            </div>
        </div>
    );
}
