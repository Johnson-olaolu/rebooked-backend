import React from "react";
import ReactDOM from "react-dom/client";
import { FluentProvider, webDarkTheme, webLightTheme } from "@fluentui/react-components";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import "./styles.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter>
      <FluentProvider theme={localStorage.getItem("rebooked-admin-theme") === "dark" ? webDarkTheme : webLightTheme}>
        <App />
      </FluentProvider>
    </BrowserRouter>
  </React.StrictMode>,
);
