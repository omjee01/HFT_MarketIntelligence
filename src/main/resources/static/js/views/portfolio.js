import { apiJson, apiFetch } from '../api.js';

function fmt(n) { return n == null ? '—' : Number(n).toFixed(2); }

function pnlClass(v) {
  if (v == null) return '';
  return Number(v) >= 0 ? 'badge--buy' : 'badge--sell';
}

const ALERT_LABEL = {
  TARGET_HIT: 'Target reached',
  STOP_LOSS_HIT: 'Stop-loss hit',
  SIGNAL_DETERIORATED: 'Outlook worsened',
};

function alertBanner(alert, onAck) {
  const el = document.createElement('div');
  el.className = `alert-banner alert-banner--${alert.suggestedAction === 'SELL' ? 'sell' : 'review'}`;
  el.innerHTML = `
    <div>
      <strong>${ALERT_LABEL[alert.alertType] || alert.alertType} — ${alert.symbol}</strong>
      <p style="margin:4px 0 0">${alert.message}</p>
    </div>
    <button type="button" class="btn btn--ghost">Dismiss</button>`;
  el.querySelector('button').addEventListener('click', () => onAck(alert.id));
  return el;
}

function positionRow(p, onClose, onDelete) {
  const el = document.createElement('div');
  el.className = 'card';
  const isOpen = p.status === 'OPEN';
  el.innerHTML = `
    <div class="reco-row" style="border:none;padding:0;">
      <div class="reco-row__meta">
        <span class="reco-row__symbol">${p.symbol} <span class="badge badge--neutral">${p.status}</span></span>
        <span class="text-muted">${p.companyName || ''} · ${p.market} · qty ${p.quantity}</span>
      </div>
      <div class="reco-row__numbers">
        <div>Avg ${fmt(p.avgBuyPrice)} → Now ${fmt(p.currentPrice)}</div>
        <div><span class="badge ${pnlClass(isOpen ? p.unrealizedPnl : p.realizedPnl)}">
          ${isOpen ? fmt(p.unrealizedPnl) + ' (' + fmt(p.unrealizedPnlPercent) + '%)' : fmt(p.realizedPnl)}
        </span></div>
      </div>
    </div>
    <div class="text-muted" style="font-size:0.85em;margin-top:6px;">
      Target ${fmt(p.targetPrice)} · Stop-loss ${fmt(p.stopLossPrice)}
    </div>
    <div class="form-row" style="margin-top:10px;" id="pos-actions-${p.id}"></div>`;

  const actions = el.querySelector(`#pos-actions-${p.id}`);
  if (isOpen) {
    const closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.className = 'btn btn--ghost';
    closeBtn.textContent = 'I sold this →';
    const formMount = document.createElement('div');
    closeBtn.addEventListener('click', () => {
      if (formMount.innerHTML) { formMount.innerHTML = ''; return; }
      formMount.innerHTML = `
        <form class="form-row" style="margin-top:8px;">
          <div class="field" style="flex:1;min-width:100px;">
            <label>Sold at</label>
            <input type="number" step="0.0001" min="0.0001" required value="${p.currentPrice ?? ''}">
          </div>
          <button type="submit" class="btn">Confirm</button>
        </form>`;
      formMount.querySelector('form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const exitPrice = formMount.querySelector('input').value;
        await onClose(p.id, exitPrice);
      });
    });
    actions.appendChild(closeBtn);
    actions.appendChild(formMount);
  }
  const deleteBtn = document.createElement('button');
  deleteBtn.type = 'button';
  deleteBtn.className = 'btn btn--ghost btn--danger';
  deleteBtn.textContent = 'Remove';
  deleteBtn.addEventListener('click', () => onDelete(p.id));
  actions.appendChild(deleteBtn);

  return el;
}

export async function renderPortfolio(mount) {
  mount.innerHTML = `
    <h2 style="margin-top:0">Portfolio</h2>
    <div id="alerts-mount"></div>
    <h3>Open positions</h3>
    <div id="open-mount" class="view-grid"><p class="text-muted">Loading…</p></div>
    <h3>Closed positions</h3>
    <div id="closed-mount" class="view-grid"><p class="text-muted">Loading…</p></div>`;

  const alertsMount = mount.querySelector('#alerts-mount');
  const openMount = mount.querySelector('#open-mount');
  const closedMount = mount.querySelector('#closed-mount');

  async function loadAlerts() {
    try {
      const body = await apiJson('/api/v1/portfolio/alerts?unacknowledgedOnly=true');
      alertsMount.innerHTML = '';
      (body.data || []).forEach(alert => {
        alertsMount.appendChild(alertBanner(alert, async (id) => {
          await apiJson(`/api/v1/portfolio/alerts/${id}/acknowledge`, { method: 'POST' });
          loadAlerts();
        }));
      });
    } catch (err) {
      alertsMount.innerHTML = `<p class="error-text">${err.message}</p>`;
    }
  }

  async function closePosition(id, exitPrice) {
    await apiJson(`/api/v1/portfolio/positions/${id}/close`, {
      method: 'POST',
      body: JSON.stringify({ exitPrice }),
    });
    await loadPositions();
  }

  async function deletePosition(id) {
    const res = await apiFetch(`/api/v1/portfolio/positions/${id}`, { method: 'DELETE' });
    if (res.ok) await loadPositions();
  }

  async function loadPositions() {
    try {
      const [openBody, closedBody] = await Promise.all([
        apiJson('/api/v1/portfolio/positions?status=OPEN'),
        apiJson('/api/v1/portfolio/positions?status=CLOSED'),
      ]);
      openMount.innerHTML = '';
      const openPositions = openBody.data || [];
      if (openPositions.length === 0) {
        openMount.innerHTML = '<p class="text-muted">No open positions — buy something from the Dashboard and record it here.</p>';
      } else {
        openPositions.forEach(p => openMount.appendChild(positionRow(p, closePosition, deletePosition)));
      }

      closedMount.innerHTML = '';
      const closedPositions = closedBody.data || [];
      if (closedPositions.length === 0) {
        closedMount.innerHTML = '<p class="text-muted">No closed positions yet.</p>';
      } else {
        closedPositions.forEach(p => closedMount.appendChild(positionRow(p, closePosition, deletePosition)));
      }
    } catch (err) {
      openMount.innerHTML = `<p class="error-text">${err.message}</p>`;
    }
  }

  await Promise.all([loadAlerts(), loadPositions()]);
}
