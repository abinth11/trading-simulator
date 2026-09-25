import { useSyncExternalStore } from "react";

export interface AdminSession {
  accessToken: string;
  refreshToken: string;
  user: {
    id: string;
    email: string;
    username: string;
    role: string;
  };
}

interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: AdminSession["user"];
}

interface AuthState {
  session: AdminSession | null;
  // Why the user was signed out, shown on the login screen
  notice: string;
}

const STORAGE_KEY = "admin-dashboard-session";

let state: AuthState = { session: readStoredSession(), notice: "" };
const listeners = new Set<() => void>();
let refreshInFlight: Promise<boolean> | null = null;

function readStoredSession(): AdminSession | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as AdminSession) : null;
  } catch {
    return null;
  }
}

function setState(next: AuthState): void {
  state = next;
  try {
    if (next.session) localStorage.setItem(STORAGE_KEY, JSON.stringify(next.session));
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    // Storage unavailable: the session still lasts for this page load
  }
  listeners.forEach((listener) => listener());
}

function toSession(response: AuthResponse): AdminSession {
  return { accessToken: response.accessToken, refreshToken: response.refreshToken, user: response.user };
}

export function useAuth(): AuthState {
  return useSyncExternalStore(
    (listener) => {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    () => state
  );
}

export function getAccessToken(): string | null {
  return state.session?.accessToken ?? null;
}

export async function login(email: string, password: string): Promise<void> {
  const response = await fetch("/user-api/api/v1/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password })
  });

  if (response.status === 401 || response.status === 400) {
    throw new Error("Incorrect email or password.");
  }
  if (!response.ok) {
    throw new Error(`Sign-in failed (${response.status}). Is user-service running?`);
  }

  const auth = (await response.json()) as AuthResponse;
  if (auth.user.role !== "ADMIN") {
    throw new Error("This account doesn't have admin access.");
  }
  setState({ session: toSession(auth), notice: "" });
}

export function logout(notice = ""): void {
  setState({ session: null, notice });
}

// Concurrent 401s share one refresh request
function refreshSession(): Promise<boolean> {
  const refreshToken = state.session?.refreshToken;
  if (!refreshToken) return Promise.resolve(false);

  refreshInFlight ??= (async () => {
    try {
      const response = await fetch("/user-api/api/v1/auth/refresh", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken })
      });
      if (!response.ok) return false;

      const auth = (await response.json()) as AuthResponse;
      if (auth.user.role !== "ADMIN") return false;
      setState({ session: toSession(auth), notice: "" });
      return true;
    } catch {
      return false;
    } finally {
      refreshInFlight = null;
    }
  })();

  return refreshInFlight;
}

/**
 * fetch() with the admin access token attached. On 401 it refreshes the token once and retries;
 * if that fails, or the server says the account isn't an admin (403), the session ends.
 */
export async function authorizedFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const send = () => {
    const headers = new Headers(init.headers);
    const token = getAccessToken();
    if (token) headers.set("Authorization", `Bearer ${token}`);
    return fetch(path, { ...init, headers });
  };

  let response = await send();

  if (response.status === 401 && (await refreshSession())) {
    response = await send();
  }

  if (response.status === 401) {
    logout("Your session expired. Sign in again.");
  } else if (response.status === 403) {
    logout("This account no longer has admin access.");
  }

  return response;
}
