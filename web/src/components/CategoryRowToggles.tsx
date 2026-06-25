import { IconButton, Tooltip } from "@mui/material";
import VisibilityIcon from "@mui/icons-material/Visibility";
import VisibilityOffIcon from "@mui/icons-material/VisibilityOff";
import ArchiveOutlinedIcon from "@mui/icons-material/ArchiveOutlined";
import UnarchiveOutlinedIcon from "@mui/icons-material/UnarchiveOutlined";
import { App as AntApp } from "antd";
import { categoriesApi, type Category } from "../lib/api";
import { errorText } from "../lib/format";

interface Props {
    category: Category;
    reload: () => void;
}

// Inline hidden/archived toggles for a category row (next to the edit pencil).
export default function CategoryRowToggles({ category, reload }: Props) {
    const { message } = AntApp.useApp();

    const toggle = async (patch: Partial<Category>, label: string) => {
        try {
            await categoriesApi.update(category.id, patch);
            message.success(label);
            reload();
        } catch (error: unknown) {
            message.error(errorText(error));
        }
    };

    return (
        <>
            <Tooltip title={category.hidden ? "Unhide from budget" : "Hide from budget"}>
                <IconButton
                    size="small"
                    aria-label="toggle hidden"
                    onClick={() => toggle({ hidden: !category.hidden }, category.hidden ? "Unhidden" : "Hidden")}
                >
                    {category.hidden ? (
                        <VisibilityOffIcon fontSize="small" color="primary" />
                    ) : (
                        <VisibilityIcon fontSize="small" />
                    )}
                </IconButton>
            </Tooltip>
            <Tooltip title={category.archived ? "Unarchive" : "Archive"}>
                <IconButton
                    size="small"
                    aria-label="toggle archived"
                    onClick={() => toggle({ archived: !category.archived }, category.archived ? "Unarchived" : "Archived")}
                >
                    {category.archived ? (
                        <UnarchiveOutlinedIcon fontSize="small" color="primary" />
                    ) : (
                        <ArchiveOutlinedIcon fontSize="small" />
                    )}
                </IconButton>
            </Tooltip>
        </>
    );
}
