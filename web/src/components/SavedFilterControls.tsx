import { useCallback, useEffect, useState } from "react";
import { Box, Chip, IconButton, List, ListItem, ListItemButton, ListItemText, Tooltip, Typography } from "@mui/material";
import BookmarkAddIcon from "@mui/icons-material/BookmarkAdd";
import BookmarksIcon from "@mui/icons-material/Bookmarks";
import DeleteIcon from "@mui/icons-material/Delete";
import { App as AntApp, Input, Modal } from "antd";
import { savedFiltersApi, type SavedFilter, type TransactionFilters } from "../lib/api";
import { errorText } from "../lib/format";

interface Props {
    filters: TransactionFilters;
    loadedId: number | undefined;
    loadedName: string;
    setLoaded: (id: number | undefined, name: string) => void;
    onApply: (filters: TransactionFilters) => void;
}

function toFilters(filter: SavedFilter): TransactionFilters {
    return {
        from: filter.fromDate ?? undefined,
        to: filter.toDate ?? undefined,
        accountIds: filter.accountIds ?? undefined,
        merchantIds: filter.merchantIds ?? undefined,
        categoryIds: filter.categoryIds ?? undefined,
        review: filter.review ?? undefined,
    };
}

export default function SavedFilterControls({ filters, loadedId, loadedName, setLoaded, onApply }: Props) {
    const { message } = AntApp.useApp();
    const [saved, setSaved] = useState<SavedFilter[]>([]);
    const [saveOpen, setSaveOpen] = useState(false);
    const [saveName, setSaveName] = useState("");
    const [pickerOpen, setPickerOpen] = useState(false);

    const reload = useCallback(() => {
        savedFiltersApi.list().then(setSaved).catch(() => setSaved([]));
    }, []);

    useEffect(reload, [reload]);

    const persist = async (name: string, id?: number) => {
        const body = {
            name,
            fromDate: filters.from ?? null,
            toDate: filters.to ?? null,
            accountIds: filters.accountIds ?? [],
            merchantIds: filters.merchantIds ?? [],
            categoryIds: filters.categoryIds ?? [],
            review: filters.review ?? null,
        };
        try {
            const result = id === undefined ? await savedFiltersApi.create(body) : await savedFiltersApi.update(id, body);
            message.success(id === undefined ? "Filter saved" : `Updated “${result.name}”`);
            setLoaded(result.id, result.name);
            reload();
        } catch (error: unknown) {
            message.error(errorText(error));
        }
    };

    const onSave = () => {
        if (loadedId !== undefined) {
            persist(loadedName, loadedId);
        } else {
            setSaveName("");
            setSaveOpen(true);
        }
    };

    const confirmSaveNew = () => {
        if (saveName.trim() !== "") {
            persist(saveName.trim());
            setSaveOpen(false);
        }
    };

    const remove = async (id: number) => {
        try {
            await savedFiltersApi.remove(id);
            if (loadedId === id) {
                setLoaded(undefined, "");
            }
            reload();
        } catch (error: unknown) {
            message.error(errorText(error));
        }
    };

    return (
        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
            {loadedId !== undefined && (
                <Chip color="primary" variant="outlined" label={loadedName} onDelete={() => setLoaded(undefined, "")} />
            )}
            <Tooltip title={loadedId !== undefined ? `Update “${loadedName}”` : "Save current filter"}>
                <IconButton color="primary" onClick={onSave}>
                    <BookmarkAddIcon />
                </IconButton>
            </Tooltip>
            <Tooltip title="Saved filters">
                <IconButton onClick={() => setPickerOpen(true)}>
                    <BookmarksIcon />
                </IconButton>
            </Tooltip>

            <Modal
                open={saveOpen}
                title="Save filter"
                okText="Save"
                onOk={confirmSaveNew}
                onCancel={() => setSaveOpen(false)}
                okButtonProps={{ disabled: saveName.trim() === "" }}
            >
                <Input
                    placeholder="Filter name"
                    value={saveName}
                    onChange={(event) => setSaveName(event.target.value)}
                    onPressEnter={confirmSaveNew}
                />
            </Modal>

            <Modal open={pickerOpen} title="Saved filters" footer={null} onCancel={() => setPickerOpen(false)}>
                {saved.length === 0 ? (
                    <Typography color="text.secondary">No saved filters yet.</Typography>
                ) : (
                    <List>
                        {saved.map((filter) => {
                            return (
                                <ListItem
                                    key={filter.id}
                                    disablePadding
                                    secondaryAction={
                                        <IconButton edge="end" aria-label="delete" onClick={() => remove(filter.id)}>
                                            <DeleteIcon fontSize="small" />
                                        </IconButton>
                                    }
                                >
                                    <ListItemButton
                                        selected={loadedId === filter.id}
                                        onClick={() => {
                                            onApply(toFilters(filter));
                                            setLoaded(filter.id, filter.name);
                                            setPickerOpen(false);
                                        }}
                                    >
                                        <ListItemText primary={filter.name} />
                                    </ListItemButton>
                                </ListItem>
                            );
                        })}
                    </List>
                )}
            </Modal>
        </Box>
    );
}
