import { useState, type FormEvent } from "react";
import { login } from "../services/auth";

interface LoginScreenProps {
  notice: string;
}

export default function LoginScreen({ notice }: LoginScreenProps) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      await login(email.trim(), password);
    } catch (loginError) {
      setError(loginError instanceof Error ? loginError.message : "Sign-in failed.");
      setSubmitting(false);
    }
  }

  return (
    <main className="login-shell">
      <form className="panel login-card" onSubmit={(event) => void handleSubmit(event)}>
        <div className="brand-block">
          <div className="brand-mark">TS</div>
          <div>
            <h1>Trading Simulator</h1>
            <p>Operator Workstation</p>
          </div>
        </div>

        {notice && !error ? (
          <div className="dashboard-notice warning-notice" role="status">{notice}</div>
        ) : null}
        {error ? (
          <div className="dashboard-notice error-notice" role="alert">{error}</div>
        ) : null}

        <label className="login-field">
          <span>Email</span>
          <input
            className="table-search"
            type="email"
            autoComplete="username"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </label>

        <label className="login-field">
          <span>Password</span>
          <input
            className="table-search"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </label>

        <button className="primary-button" type="submit" disabled={submitting}>
          {submitting ? "Signing in…" : "Sign in"}
        </button>

        <p className="login-hint">Admin accounts only.</p>
      </form>
    </main>
  );
}
