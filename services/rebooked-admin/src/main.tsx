import React, { useEffect, useState } from "react";
import ReactDOM from "react-dom/client";
import { createDarkTheme, createLightTheme, FluentProvider } from "@fluentui/react-components";
import type { BrandVariants } from "@fluentui/react-components";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import "./styles.css";

const brandRamp: BrandVariants = {
  10: "#f4fbfa",
  20: "#e2f4f1",
  30: "#cce9e5",
  40: "#a9d8d1",
  50: "#83c4bb",
  60: "#64aea4",
  70: "#43988e",
  80: "#2b8278",
  90: "#16605a",
  100: "#13534e",
  110: "#104741",
  120: "#0d3a35",
  130: "#092e29",
  140: "#06221e",
  150: "#031613",
  160: "#010b0a",
};

const lightTheme = createLightTheme(brandRamp);
const darkTheme = createDarkTheme(brandRamp);

function AdminApp() {
  const [dark, setDark] = useState(() => localStorage.getItem("rebooked-admin-theme") === "dark");
  useEffect(() => {
    const updateTheme = () => setDark(localStorage.getItem("rebooked-admin-theme") === "dark");
    window.addEventListener("rebooked-admin-theme-change", updateTheme);
    return () => window.removeEventListener("rebooked-admin-theme-change", updateTheme);
  }, []);

  return (
    <BrowserRouter basename={import.meta.env.PROD ? "/api/admin-dashboard" : undefined}>
      <FluentProvider theme={dark ? darkTheme : lightTheme}>
        <App />
      </FluentProvider>
    </BrowserRouter>
  );
}

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <AdminApp />
  </React.StrictMode>,
);
