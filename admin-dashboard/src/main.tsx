import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import LoginScreen from "./components/LoginScreen";
import { useAuth } from "./services/auth";
import "./index.css";

// The dashboard only mounts with an admin session; signing out unmounts it,
// which stops its polling and closes its sockets.
function AuthGate() {
  const { session, notice } = useAuth();
  return session ? <App /> : <LoginScreen notice={notice} />;
}

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <BrowserRouter>
      <AuthGate />
    </BrowserRouter>
  </React.StrictMode>
);
