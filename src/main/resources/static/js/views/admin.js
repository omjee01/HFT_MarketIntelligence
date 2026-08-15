import { apiJson, apiFetch } from '../api.js';

const PROVIDERS = [
  { id: 'NEWSAPI', label: 'NewsAPI.org', fields: [{ key: 'apiKey', label: 'API key' }] },
  { id: 'FRED', label: 'FRED (macro data)', fields: [{ key: 'apiKey', label: 'API key' }] },
  { id: 'REDDIT', label: 'Reddit', fields: [{ key: 'clientId', label: 'Client ID' }, { key: 'clientSecret', label: 'Client secret' }] },
];

function providerCard(def, status) {
  const configured = status?.configured;
  return `
    <div class="card" data-provider="${def.id}">
      <h3 style="margin-top:0">${def.label}
        <span class="badge ${configured ? 'badge--buy' : 'badge--neutral'}">${configured ? 'Configured' : 'Not set'}</span>
      </h3>
      ${status?.updatedAt ? `<p class="text-muted">Updated ${new Date(status.updatedAt).toLocaleString()}${status.updatedBy ? ' by ' + status.updatedBy : ''}</p>` : ''}
      <form class="cred-form">
        ${def.fields.map(f => `
          <div class="field">
            <label>${f.label}</label>
            <input type="password" name="${f.key}" placeholder="${configured ? '••••••••' : 'not set'}" autocomplete="off">
          </div>`).join('')}
        <p class="error-text" hidden></p>
        <div class="form-row">
          <button type="submit" class="btn">Save</button>
          <button type="button" class="btn btn--ghost btn--danger clear-btn" ${configured ? '' : 'disabled'}>Clear</button>
        </div>
      </form>
    </div>`;
}

export async function renderAdmin(mount) {
  mount.innerHTML = `<h2 style="margin-top:0">Admin — Platform API keys</h2>
    <p class="text-muted">These are platform-wide keys used for every user's predictions, not a personal social account.</p>
    <div class="view-grid" id="providers-mount"><p class="text-muted">Loading…</p></div>`;

  const providersMount = mount.querySelector('#providers-mount');

  let statuses = [];
  try {
    statuses = await apiJson('/api/v1/admin/settings/credentials');
  } catch (err) {
    providersMount.innerHTML = `<p class="error-text">${err.message}</p>`;
    return;
  }

  providersMount.innerHTML = PROVIDERS.map(def =>
    providerCard(def, statuses.find(s => s.provider === def.id))
  ).join('');

  PROVIDERS.forEach(def => {
    const card = providersMount.querySelector(`[data-provider="${def.id}"]`);
    const form = card.querySelector('.cred-form');
    const errorEl = form.querySelector('.error-text');

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      errorEl.hidden = true;
      const body = {};
      def.fields.forEach(f => { body[f.key] = form.querySelector(`[name="${f.key}"]`).value; });
      try {
        await apiJson(`/api/v1/admin/settings/credentials/${def.id}`, {
          method: 'PUT',
          body: JSON.stringify(body),
        });
        await renderAdmin(mount);
      } catch (err) {
        errorEl.textContent = err.message;
        errorEl.hidden = false;
      }
    });

    card.querySelector('.clear-btn').addEventListener('click', async () => {
      errorEl.hidden = true;
      try {
        const res = await apiFetch(`/api/v1/admin/settings/credentials/${def.id}`, { method: 'DELETE' });
        if (!res.ok && res.status !== 204) throw new Error(`Failed to clear (${res.status})`);
        await renderAdmin(mount);
      } catch (err) {
        errorEl.textContent = err.message;
        errorEl.hidden = false;
      }
    });
  });
}
