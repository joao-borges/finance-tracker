import { useCallback, useEffect, useState, type ReactNode } from "react";
import {
    Box,
    Button,
    Chip,
    IconButton,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import EditIcon from "@mui/icons-material/Edit";
import { App as AntApp, Form, Input, InputNumber, Modal, Select, Switch } from "antd";
import EmojiField from "./EmojiField";
import type { Crud } from "../lib/api";

export interface SelectOption {
    label: string;
    value: string | number;
}

export interface FieldDef<T> {
    name: string;
    label: string;
    type: "text" | "number" | "boolean" | "select" | "emoji";
    options?: SelectOption[];
    required?: boolean;
    formOnly?: boolean;
    tableOnly?: boolean;
    initial?: unknown;
    renderCell?: (row: T) => ReactNode;
}

interface CrudPageProps<T> {
    title: string;
    fields: FieldDef<T>[];
    api: Crud<T>;
    beforeSubmit?: (values: Record<string, unknown>, editing: T | null) => Record<string, unknown>;
    rowActions?: (row: T, reload: () => void) => ReactNode;
}

function errorMessage(error: unknown): string {
    return error instanceof Error ? error.message : String(error);
}

export default function CrudPage<T extends { id: number }>({ title, fields, api, beforeSubmit, rowActions }: CrudPageProps<T>) {
    const { message } = AntApp.useApp();
    const [rows, setRows] = useState<T[]>([]);
    const [open, setOpen] = useState(false);
    const [editing, setEditing] = useState<T | null>(null);
    const [form] = Form.useForm();

    const reload = useCallback(() => {
        api.list()
            .then(setRows)
            .catch((error: unknown) => message.error(errorMessage(error)));
    }, [api, message]);

    useEffect(() => {
        reload();
    }, [reload]);

    const openCreate = () => {
        setEditing(null);
        form.resetFields();
        const initial: Record<string, unknown> = {};
        fields.forEach((field) => {
            if (field.initial !== undefined) {
                initial[field.name] = field.initial;
            }
        });
        form.setFieldsValue(initial);
        setOpen(true);
    };

    const openEdit = (row: T) => {
        setEditing(row);
        form.resetFields();
        form.setFieldsValue(row as Record<string, unknown>);
        setOpen(true);
    };

    const submit = async () => {
        const values = (await form.validateFields()) as Record<string, unknown>;
        const body = beforeSubmit ? beforeSubmit(values, editing) : values;
        try {
            if (editing) {
                await api.update(editing.id, body as Partial<T>);
                message.success(`${title} updated`);
            } else {
                await api.create(body as Partial<T>);
                message.success(`${title} created`);
            }
            setOpen(false);
            reload();
        } catch (error: unknown) {
            message.error(errorMessage(error));
        }
    };

    const tableFields = fields.filter((field) => !field.formOnly);
    const formFields = fields.filter((field) => !field.tableOnly);

    const cell = (field: FieldDef<T>, row: T): ReactNode => {
        if (field.renderCell) {
            return field.renderCell(row);
        }
        const value = (row as Record<string, unknown>)[field.name];
        if (field.type === "boolean") {
            return <Chip size="small" label={value ? "Yes" : "No"} color={value ? "success" : "default"} />;
        }
        if (field.type === "select" && field.options) {
            const option = field.options.find((candidate) => candidate.value === value);
            return option ? option.label : "—";
        }
        return value === null || value === undefined || value === "" ? "—" : String(value);
    };

    const control = (field: FieldDef<T>) => {
        if (field.type === "number") {
            return <InputNumber style={{ width: "100%" }} />;
        }
        if (field.type === "boolean") {
            return <Switch />;
        }
        if (field.type === "select") {
            return <Select allowClear options={field.options} />;
        }
        if (field.type === "emoji") {
            return <EmojiField />;
        }
        return <Input />;
    };

    return (
        <Box>
            <Box sx={{ display: "flex", alignItems: "center", mb: 2 }}>
                <Typography variant="h5" sx={{ flex: 1 }}>
                    {title}
                </Typography>
                <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>
                    New
                </Button>
            </Box>

            <TableContainer component={Paper}>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            {tableFields.map((field) => {
                                return <TableCell key={field.name}>{field.label}</TableCell>;
                            })}
                            <TableCell align="right">Actions</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {rows.map((row) => {
                            return (
                                <TableRow key={row.id} hover>
                                    {tableFields.map((field) => {
                                        return <TableCell key={field.name}>{cell(field, row)}</TableCell>;
                                    })}
                                    <TableCell align="right">
                                        {rowActions?.(row, reload)}
                                        <IconButton size="small" onClick={() => openEdit(row)} aria-label="edit">
                                            <EditIcon fontSize="small" />
                                        </IconButton>
                                    </TableCell>
                                </TableRow>
                            );
                        })}
                        {rows.length === 0 && (
                            <TableRow>
                                <TableCell colSpan={tableFields.length + 1}>
                                    <Typography color="text.secondary">No {title.toLowerCase()} yet.</Typography>
                                </TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
            </TableContainer>

            <Modal
                open={open}
                title={editing ? `Edit ${title}` : `New ${title}`}
                onOk={submit}
                onCancel={() => setOpen(false)}
                okText="Save"
            >
                <Form form={form} layout="vertical">
                    {formFields.map((field) => {
                        return (
                            <Form.Item
                                key={field.name}
                                name={field.name}
                                label={field.label}
                                valuePropName={field.type === "boolean" ? "checked" : "value"}
                                rules={field.required ? [{ required: true, message: `${field.label} is required` }] : undefined}
                            >
                                {control(field)}
                            </Form.Item>
                        );
                    })}
                </Form>
            </Modal>
        </Box>
    );
}
