import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import AppTheme from "./theme/AppTheme";
import App from "./App";
import "./index.css";

const rootElement = document.getElementById("root");
if (rootElement === null) {
    throw new Error("Root element #root not found");
}

createRoot(rootElement).render(
    <StrictMode>
        <BrowserRouter>
            <AppTheme>
                <App />
            </AppTheme>
        </BrowserRouter>
    </StrictMode>,
);
