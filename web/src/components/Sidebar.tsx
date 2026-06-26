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
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import CompareArrowsIcon from "@mui/icons-material/CompareArrows";
import AccountBalanceWalletIcon from "@mui/icons-material/AccountBalanceWallet";
import AccountBalanceIcon from "@mui/icons-material/AccountBalance";
import StorefrontIcon from "@mui/icons-material/Storefront";
import CategoryIcon from "@mui/icons-material/Category";
import FolderIcon from "@mui/icons-material/Folder";
import GavelIcon from "@mui/icons-material/Gavel";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import styles from "./Sidebar.module.css";

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
    { to: "/matches", label: "Matches", icon: <CompareArrowsIcon /> },
    { to: "/duplicates", label: "Duplicates", icon: <ContentCopyIcon /> },
    { to: "/import", label: "Import", icon: <UploadFileIcon /> },
];

interface Props {
    mobile: boolean;
    collapsed: boolean;
    mobileOpen: boolean;
    onClose: () => void;
}

export default function Sidebar({ mobile, collapsed, mobileOpen, onClose }: Props) {
    const location = useLocation();
    // Labels show on mobile (full drawer) and on the expanded desktop drawer.
    const showLabels = mobile || !collapsed;

    const renderNav = (items: NavItem[]) => {
        return items.map((item) => {
            return (
                <Tooltip key={item.to} title={showLabels ? "" : item.label} placement="right">
                    <ListItemButton
                        component={Link}
                        to={item.to}
                        selected={location.pathname === item.to}
                        onClick={mobile ? onClose : undefined}
                        className={styles.navButton}
                    >
                        <ListItemIcon className={styles.navIcon}>
                            {item.icon}
                        </ListItemIcon>
                        {showLabels && <ListItemText primary={item.label} />}
                    </ListItemButton>
                </Tooltip>
            );
        });
    };

    const nav = (
        <>
            <Toolbar />
            <List>{renderNav(MAIN_NAV)}</List>
            <Divider />
            <List>{renderNav(SETUP_NAV)}</List>
        </>
    );

    if (mobile) {
        return (
            <Drawer
                variant="temporary"
                open={mobileOpen}
                onClose={onClose}
                className={styles.mobileDrawer}
                ModalProps={{ keepMounted: true }}
            >
                {nav}
            </Drawer>
        );
    }

    return (
        <Drawer variant="permanent" className={styles.drawer} data-collapsed={collapsed}>
            {nav}
        </Drawer>
    );
}
