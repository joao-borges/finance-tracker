import { BottomNavigation, BottomNavigationAction, Paper } from "@mui/material";
import DashboardIcon from "@mui/icons-material/Dashboard";
import AccountBalanceWalletIcon from "@mui/icons-material/AccountBalanceWallet";
import AccountBalanceIcon from "@mui/icons-material/AccountBalance";
import { useLocation, useNavigate } from "react-router-dom";
import styles from "./BottomNav.module.css";

// Mobile-only quick access to the main pages.
const ITEMS = [
    { to: "/", label: "Home", icon: <DashboardIcon /> },
    { to: "/budget", label: "Budget", icon: <AccountBalanceWalletIcon /> },
    { to: "/accounts", label: "Accounts", icon: <AccountBalanceIcon /> },
];

export default function BottomNav() {
    const location = useLocation();
    const navigate = useNavigate();
    const current = ITEMS.findIndex((item) => item.to === location.pathname);

    return (
        <Paper className={styles.root} elevation={3}>
            <BottomNavigation
                showLabels
                value={current === -1 ? false : current}
                onChange={(_event, index) => navigate(ITEMS[index].to)}
            >
                {ITEMS.map((item) => {
                    return <BottomNavigationAction key={item.to} label={item.label} icon={item.icon} />;
                })}
            </BottomNavigation>
        </Paper>
    );
}
