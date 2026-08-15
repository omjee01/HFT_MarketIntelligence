import { initThemeToggle } from './theme-toggle.js';
import { getSession, clearSession, apiJson } from './api.js';
import { renderLogin } from './auth.js';
import { renderDashboard } from './views/dashboard.js';
import { renderAdmin } from './views/admin.js';

const mount = document.getElementById('app');
const navEl = document.getElementById('app-nav');
const navAdminLink = document.getElementById('nav-admin');
const userBadge = document.getElementById('user-badge');
const logoutBtn = document.getElementById('logout-btn');

function isAdmin() {
  const session = getSession();
  return !!session && session.roles.includes('ADMIN');
}

function updateChrome() {
  const session = getSession();
  navEl.hidden = !session;
  navAdminLink.hidden = !isAdmin();
  userBadge.hidden = !session;
  userBadge.textContent = session ? session.username : '';
  logoutBtn.hidden = !session;

  const route = (location.hash.replace(/^#\//, '') || 'dashboard').split('/')[0];
  navEl.querySelectorAll('a').forEach(a => {
    a.classList.toggle('is-active', a.dataset.route === route);
  });
}

async function route() {
  const session = getSession();
  const hash = location.hash.replace(/^#\//, '') || 'dashboard';

  if (!session) {
    updateChrome();
    renderLogin(mount, { onAuthenticated: () => { location.hash = '#/dashboard'; route(); } });
    return;
  }

  if (hash === 'admin' && !isAdmin()) {
    location.hash = '#/dashboard';
    return;
  }

  updateChrome();
  if (hash === 'admin') {
    await renderAdmin(mount);
  } else {
    await renderDashboard(mount);
  }
}

logoutBtn.addEventListener('click', () => {
  clearSession();
  location.hash = '#/login';
  route();
});

window.addEventListener('hashchange', route);

async function boot() {
  initThemeToggle();
  const session = getSession();
  if (session) {
    try {
      const me = await apiJson('/api/v1/auth/me');
      // keep roles/username fresh in case they changed server-side
      sessionStorage.setItem('hmip.roles', JSON.stringify(me.roles || session.roles));
      if (me.username) sessionStorage.setItem('hmip.username', me.username);
    } catch {
      clearSession();
    }
  }
  route();
}

boot();
