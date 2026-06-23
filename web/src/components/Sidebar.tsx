import type { ReactNode } from "react";
import { Link, useLocation } from "react-router-dom";
import {
    Divider,
    Drawer,
    List,
    ListItemButton,
    ListItemIcon,
    ListItemText,
    Toolbar,
    Tooltip,
} from "@mui/material";
import DashboardIcon from "@mui/icons-material/Dashboard";
import ReceiptLongIcon from "@mui/icons-material/ReceiptLong";
import AccountBalanceWalletIcon from "@mui/icons-material/AccountBalanceWallet";
import AccountBalanceIcon from "@mui/icons-material/AccountBalance";
import StorefrontIcon from "@mui/icons-material/Storefront";
import CategoryIcon from "@mui/icons-material/Category";
import FolderIcon from "@mui/icons-material/Folder";
import GavelIcon from "@mui/icons-material/Gavel";
import UploadFileIcon from "@mui/icons-material/UploadFile";

export const SIDEBAR_WIDTH_EXPANDED = 224;
export const SIDEBAR_WIDTH_COLLAPSED = 64;

interface NavItem {
    to: string;
    label: string;
    icon: ReactNode;
}

const MAIN_NAV: NavItem[] = [
    { to: "/", label: "Dashboard", icon: <DashboardIcon /> },
    { to: "/transactions", label: "Transactions", icon: <ReceiptLongIcon /> },
    { to: "/budget", label: "Budget", icon: <AccountBalanceWalletIcon /> },
];

const SETUP_NAV: NavItem[] = [
    { to: "/accounts", label: "Accounts", icon: <AccountBalanceIcon /> },
    { to: "/merchants", label: "Merchants", icon: <StorefrontIcon /> },
    { to: "/categories", label: "Categories", icon: <CategoryIcon /> },
    { to: "/category-groups", label: "Category Groups", icon: <FolderIcon /> },
    { to: "/rules", label: "Rules", icon: <GavelIcon /> },
    { to: "/import", label: "Import", icon: <UploadFileIcon /> },
];

interface Props {
    collapsed: boolean;
}

export default function Sidebar({ collapsed }: Props) {
    const location = useLocation();
    const width = collapsed ? SIDEBAR_WIDTH_COLLAPSED : SIDEBAR_WIDTH_EXPANDED;

    const renderNav = (items: NavItem[]) => {
        return items.map((item) => {
            return (
                <Tooltip key={item.to} title={collapsed ? item.label : ""} placement="right">
                    <ListItemButton
                        component={Link}
                        to={item.to}
                        selected={location.pathname === item.to}
                        sx={{ minHeight: 48, px: 2.5, justifyContent: collapsed ? "center" : "initial" }}
                    >
                        <ListItemIcon sx={{ minWidth: 0, mr: collapsed ? 0 : 2.5, justifyContent: "center" }}>
                            {item.icon}
                        </ListItemIcon>
                        {!collapsed && <ListItemText primary={item.label} />}
                    </ListItemButton>
                </Tooltip>
            );
        });
    };

    return (
        <Drawer
            variant="permanent"
            sx={{
                width,
                flexShrink: 0,
                whiteSpace: "nowrap",
                "& .MuiDrawer-paper": {
                    width,
                    boxSizing: "border-box",
                    overflowX: "hidden",
                    transition: (theme) =>
                        theme.transitions.create("width", { duration: theme.transitions.duration.shorter }),
                },
            }}
        >
            <Toolbar />
            <List>{renderNav(MAIN_NAV)}</List>
            <Divider />
            <List>{renderNav(SETUP_NAV)}</List>
        </Drawer>
    );
}
