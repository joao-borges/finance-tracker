import { useState } from "react";
import { Route, Routes } from "react-router-dom";
import { AppBar, Box, IconButton, Toolbar, Typography } from "@mui/material";
import MenuIcon from "@mui/icons-material/Menu";
import SavingsIcon from "@mui/icons-material/Savings";
import Sidebar from "./components/Sidebar";
import ThemeControls from "./theme/ThemeControls";
import Dashboard from "./pages/Dashboard";
import TransactionsPage from "./pages/TransactionsPage";
import BudgetPage from "./pages/BudgetPage";
import AccountsPage from "./pages/AccountsPage";
import MerchantsPage from "./pages/MerchantsPage";
import CategoriesPage from "./pages/CategoriesPage";
import CategoryGroupsPage from "./pages/CategoryGroupsPage";
import RulesPage from "./pages/RulesPage";
import ImportPage from "./pages/ImportPage";

const COLLAPSE_KEY = "ft.sidebar.collapsed";

export default function App() {
    const [collapsed, setCollapsed] = useState<boolean>(() => localStorage.getItem(COLLAPSE_KEY) !== "false");

    const toggle = () => {
        setCollapsed((current) => {
            const next = !current;
            localStorage.setItem(COLLAPSE_KEY, String(next));
            return next;
        });
    };

    return (
        <Box sx={{ display: "flex" }}>
            <AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.drawer + 1 }}>
                <Toolbar>
                    <IconButton color="inherit" edge="start" onClick={toggle} sx={{ mr: 1 }} aria-label="toggle menu">
                        <MenuIcon />
                    </IconButton>
                    <SavingsIcon sx={{ mr: 1 }} />
                    <Typography variant="h6" noWrap sx={{ flexGrow: 1 }}>
                        finance
                    </Typography>
                    <ThemeControls />
                </Toolbar>
            </AppBar>

            <Sidebar collapsed={collapsed} />

            <Box component="main" sx={{ flexGrow: 1, p: 3, minWidth: 0 }}>
                <Toolbar />
                <Routes>
                    <Route path="/" element={<Dashboard />} />
                    <Route path="/transactions" element={<TransactionsPage />} />
                    <Route path="/budget" element={<BudgetPage />} />
                    <Route path="/accounts" element={<AccountsPage />} />
                    <Route path="/merchants" element={<MerchantsPage />} />
                    <Route path="/categories" element={<CategoriesPage />} />
                    <Route path="/category-groups" element={<CategoryGroupsPage />} />
                    <Route path="/rules" element={<RulesPage />} />
                    <Route path="/import" element={<ImportPage />} />
                </Routes>
            </Box>
        </Box>
    );
}
