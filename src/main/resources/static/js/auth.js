import { setSession } from './api.js';

async function postJson(path, body) {
  const res = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    throw new Error((data && data.message) || `Request failed (${res.status})`);
  }
  return data;
}

export function renderLogin(mount, { onAuthenticated }) {
  let mode = 'login';

  function render() {
    mount.innerHTML = `
      <div class="view-grid" style="max-width:380px;margin:40px auto;">
        <div class="card">
          <h2 style="margin-top:0">${mode === 'login' ? 'Log in' : 'Create account'}</h2>
          <form id="auth-form">
            ${mode === 'register' ? `
              <div class="field">
                <label for="email">Email</label>
                <input id="email" type="email" required>
              </div>` : ''}
            <div class="field">
              <label for="username">Username</label>
              <input id="username" type="text" required autocomplete="username">
            </div>
            <div class="field">
              <label for="password">Password</label>
              <input id="password" type="password" required
                     autocomplete="${mode === 'login' ? 'current-password' : 'new-password'}">
            </div>
            <p class="error-text" id="auth-error" hidden></p>
            <button type="submit" class="btn">${mode === 'login' ? 'Log in' : 'Register'}</button>
          </form>
          <p style="margin-bottom:0">
            <a href="#" id="auth-switch">
              ${mode === 'login' ? "Need an account? Register" : 'Have an account? Log in'}
            </a>
          </p>
        </div>
      </div>`;

    mount.querySelector('#auth-switch').addEventListener('click', (e) => {
      e.preventDefault();
      mode = mode === 'login' ? 'register' : 'login';
      render();
    });

    mount.querySelector('#auth-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const errorEl = mount.querySelector('#auth-error');
      errorEl.hidden = true;
      const username = mount.querySelector('#username').value.trim();
      const password = mount.querySelector('#password').value;
      try {
        if (mode === 'register') {
          const email = mount.querySelector('#email').value.trim();
          await postJson('/api/v1/auth/register', { username, email, password });
        }
        const login = await postJson('/api/v1/auth/login', { username, password });
        setSession({
          accessToken: login.accessToken,
          refreshToken: login.refreshToken,
          username: login.username,
          roles: login.roles,
        });
        onAuthenticated();
      } catch (err) {
        errorEl.textContent = err.message;
        errorEl.hidden = false;
      }
    });
  }

  render();
}
