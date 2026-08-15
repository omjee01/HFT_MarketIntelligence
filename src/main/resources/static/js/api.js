const ACCESS_KEY = 'hmip.accessToken';
const REFRESH_KEY = 'hmip.refreshToken';
const USERNAME_KEY = 'hmip.username';
const ROLES_KEY = 'hmip.roles';

export function getSession() {
  const accessToken = sessionStorage.getItem(ACCESS_KEY);
  if (!accessToken) return null;
  return {
    accessToken,
    refreshToken: sessionStorage.getItem(REFRESH_KEY),
    username: sessionStorage.getItem(USERNAME_KEY),
    roles: JSON.parse(sessionStorage.getItem(ROLES_KEY) || '[]'),
  };
}

export function setSession({ accessToken, refreshToken, username, roles }) {
  sessionStorage.setItem(ACCESS_KEY, accessToken);
  if (refreshToken) sessionStorage.setItem(REFRESH_KEY, refreshToken);
  if (username) sessionStorage.setItem(USERNAME_KEY, username);
  if (roles) sessionStorage.setItem(ROLES_KEY, JSON.stringify(roles));
}

export function clearSession() {
  [ACCESS_KEY, REFRESH_KEY, USERNAME_KEY, ROLES_KEY].forEach(k => sessionStorage.removeItem(k));
}

async function raw(path, opts = {}) {
  const session = getSession();
  const headers = { 'Content-Type': 'application/json', ...(opts.headers || {}) };
  if (session) headers.Authorization = `Bearer ${session.accessToken}`;
  const res = await fetch(path, { ...opts, headers });
  return res;
}

let refreshing = null;

async function tryRefresh() {
  const session = getSession();
  if (!session || !session.refreshToken) return false;
  if (!refreshing) {
    refreshing = fetch('/api/v1/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: session.refreshToken }),
    })
      .then(res => (res.ok ? res.json() : null))
      .then(body => {
        if (body && body.accessToken) {
          setSession({ ...session, accessToken: body.accessToken });
          return true;
        }
        return false;
      })
      .catch(() => false)
      .finally(() => { refreshing = null; });
  }
  return refreshing;
}

export async function apiFetch(path, opts = {}) {
  let res = await raw(path, opts);
  if (res.status === 401 && getSession()) {
    const ok = await tryRefresh();
    if (ok) {
      res = await raw(path, opts);
    } else {
      clearSession();
      location.hash = '#/login';
      throw new Error('Session expired');
    }
  }
  return res;
}

export async function apiJson(path, opts = {}) {
  const res = await apiFetch(path, opts);
  const body = await res.json().catch(() => null);
  if (!res.ok) {
    const message = (body && (body.message || body.error?.message)) || `Request failed (${res.status})`;
    throw new Error(message);
  }
  return body;
}
