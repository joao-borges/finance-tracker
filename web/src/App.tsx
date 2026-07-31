import { useState } from "react";
import { Route, Routes } from "react-router-dom";
import { AppBar, Box, IconButton, Toolbar, useMediaQuery, useTheme } from "@mui/material";
import MenuIcon from "@mui/icons-material/Menu";
import SavingsIcon from "@mui/icons-material/Savings";
import Sidebar from "./components/Sidebar";
import BottomNav from "./components/BottomNav";
import UserMenu from "./components/UserMenu";
import ThemeControls from "./theme/ThemeControls";
import Dashboard from "./pages/Dashboard";
import TransactionsPage from "./pages/TransactionsPage";
import BudgetPage from "./pages/BudgetPage";
import AccountsPage from "./pages/AccountsPage";
import InstitutionsPage from "./pages/InstitutionsPage";
import MerchantsPage from "./pages/MerchantsPage";
import CategoriesPage from "./pages/CategoriesPage";
import CategoryGroupsPage from "./pages/CategoryGroupsPage";
import RulesPage from "./pages/RulesPage";
import MatchesPage from "./pages/MatchesPage";
import DuplicatesPage from "./pages/DuplicatesPage";
import ImportPage from "./pages/ImportPage";
import styles from "./App.module.css";

const COLLAPSE_KEY = "ft.sidebar.collapsed";

export default function App() {
    const theme = useTheme();
    const mobile = useMediaQuery(theme.breakpoints.down("sm"));
    const [collapsed, setCollapsed] = useState<boolean>(() => localStorage.getItem(COLLAPSE_KEY) !== "false");
    const [mobileOpen, setMobileOpen] = useState(false);

    // On mobile the menu icon opens the temporary drawer; on desktop it collapses
    // the permanent one.
    const onMenu = () => {
        if (mobile) {
            setMobileOpen((open) => !open);
            return;
        }
        setCollapsed((current) => {
            const next = !current;
            localStorage.setItem(COLLAPSE_KEY, String(next));
            return next;
        });
    };

    return (
        <Box className={styles.root}>
            <AppBar position="fixed" className={styles.appBar}>
                <Toolbar>
                    <IconButton color="inherit" edge="start" onClick={onMenu} className={styles.menuButton} aria-label="menu">
                        <MenuIcon />
                    </IconButton>
                    <SavingsIcon className={styles.logo} />
                    <Box className={styles.spacer} />
                    <ThemeControls />
                    <UserMenu />
                </Toolbar>
            </AppBar>

            <Sidebar mobile={mobile} collapsed={collapsed} mobileOpen={mobileOpen} onClose={() => setMobileOpen(false)} />

            <Box component="main" className={styles.main}>
                <Toolbar />
                <Routes>
                    <Route path="/" element={<Dashboard />} />
                    <Route path="/transactions" element={<TransactionsPage />} />
                    <Route path="/budget" element={<BudgetPage />} />
                    <Route path="/accounts" element={<AccountsPage />} />
                    <Route path="/institutions" element={<InstitutionsPage />} />
                    <Route path="/merchants" element={<MerchantsPage />} />
                    <Route path="/categories" element={<CategoriesPage />} />
                    <Route path="/category-groups" element={<CategoryGroupsPage />} />
                    <Route path="/rules" element={<RulesPage />} />
                    <Route path="/matches" element={<MatchesPage />} />
                    <Route path="/duplicates" element={<DuplicatesPage />} />
                    <Route path="/import" element={<ImportPage />} />
                </Routes>
            </Box>

            {mobile && <BottomNav />}
        </Box>
    );
}
