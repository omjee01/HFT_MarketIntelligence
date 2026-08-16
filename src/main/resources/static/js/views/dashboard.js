import { apiJson } from '../api.js';
import { showStockDetail, showIpoDetail } from './detail.js';

const MARKETS = ['US_NASDAQ', 'US_NYSE', 'US_AMEX', 'INDIA_NSE', 'INDIA_BSE', 'INDIA_MCX'];

const TIER_LABEL = { MEGA: 'Mega Cap', LARGE: 'Large Cap', MID: 'Mid Cap', SMALL: 'Small Cap', MICRO: 'Micro Cap' };
const IPO_GROUP_ORDER = ['APPLY_STRONG', 'APPLY', 'RISKY', 'AVOID'];
const IPO_GROUP_LABEL = {
  APPLY_STRONG: 'Strong Apply', APPLY: 'Apply', RISKY: 'Risky', AVOID: 'Avoid',
};

function signalBadgeClass(signal) {
  if (!signal) return 'badge--neutral';
  if (signal.includes('BUY')) return 'badge--buy';
  if (signal.includes('SELL')) return 'badge--sell';
  if (signal === 'HOLD' || signal === 'WATCH') return 'badge--hold';
  return 'badge--neutral';
}

function stockCard(r, onOpen) {
  const el = document.createElement('button');
  el.type = 'button';
  el.className = 'reco-card';
  el.innerHTML = `
    <div class="reco-row" style="border:none;padding:0;">
      <div class="reco-row__meta">
        <span class="reco-row__symbol">${r.symbol} <span class="badge ${signalBadgeClass(r.signal)}">${r.signal}</span></span>
        <span class="text-muted">${r.companyName || ''}</span>
      </div>
      <div class="reco-row__numbers">
        <div>Entry ${r.entryPrice ?? '—'} → Target ${r.targetPrice ?? '—'}</div>
        <div class="text-muted">${r.expectedProfitPercent != null ? r.expectedProfitPercent.toFixed(1) + '%' : '—'}
          · confidence ${r.confidencePercent != null ? Math.round(r.confidencePercent) : '—'}%</div>
      </div>
    </div>`;
  el.addEventListener('click', () => onOpen(r));
  return el;
}

function ipoCard(ipo, onOpen) {
  const el = document.createElement('button');
  el.type = 'button';
  el.className = 'reco-card';
  el.innerHTML = `
    <div class="reco-row" style="border:none;padding:0;">
      <div class="reco-row__meta">
        <span class="reco-row__symbol">${ipo.symbol} <span class="badge ${signalBadgeClass(ipo.recommendation === 'AVOID' ? 'SELL' : ipo.recommendation)}">${ipo.recommendation}</span></span>
        <span class="text-muted">${ipo.companyName || ''} · ${ipo.status || ''}</span>
      </div>
      <div class="reco-row__numbers">
        <div>${ipo.predictedListingGainPercent != null ? ipo.predictedListingGainPercent.toFixed(1) + '% predicted gain' : '—'}</div>
        <div class="text-muted">confidence ${ipo.listingGainConfidence != null ? Math.round(ipo.listingGainConfidence) : '—'}%</div>
      </div>
    </div>`;
  el.addEventListener('click', () => onOpen(ipo));
  return el;
}

function tierSection(label, items, cardFn, onOpen) {
  const section = document.createElement('div');
  section.className = 'cap-tier';
  const header = document.createElement('h3');
  header.className = 'cap-tier__header';
  header.textContent = `${label} (${items.length})`;
  section.appendChild(header);
  const grid = document.createElement('div');
  grid.className = 'reco-card-grid';
  items.forEach(item => grid.appendChild(cardFn(item, onOpen)));
  section.appendChild(grid);
  return section;
}

async function renderBoard(el, market) {
  el.innerHTML = '<p class="text-muted">Loading…</p>';
  try {
    const body = await apiJson(`/api/v1/recommendations/board?market=${market}`);
    if (!body.success || !body.data || Object.keys(body.data).length === 0) {
      el.innerHTML = `<p class="text-muted">${body.message || 'No actionable recommendations right now.'}</p>`;
      return;
    }
    el.innerHTML = '';
    for (const [tier, items] of Object.entries(body.data)) {
      el.appendChild(tierSection(TIER_LABEL[tier] || tier, items, stockCard, showStockDetail));
    }
  } catch (err) {
    el.innerHTML = `<p class="error-text">${err.message}</p>`;
  }
}

async function renderIpoBoard(el) {
  el.innerHTML = '<p class="text-muted">Loading…</p>';
  try {
    const body = await apiJson('/api/v1/ipo/recommendations');
    if (!body.success || !body.data || body.data.length === 0) {
      el.innerHTML = `<p class="text-muted">${body.message || 'No IPO data available right now.'}</p>`;
      return;
    }
    el.innerHTML = '';
    for (const group of IPO_GROUP_ORDER) {
      const items = body.data
        .filter(i => i.recommendation === group)
        .sort((a, b) => (b.listingGainConfidence ?? 0) - (a.listingGainConfidence ?? 0));
      if (items.length) el.appendChild(tierSection(IPO_GROUP_LABEL[group], items, ipoCard, showIpoDetail));
    }
  } catch (err) {
    el.innerHTML = `<p class="error-text">${err.message}</p>`;
  }
}

function quoteCard(q) {
  const changeClass = (q.change ?? 0) >= 0 ? 'badge--buy' : 'badge--sell';
  return `
    <div class="card" id="quote-result">
      <div class="reco-row" style="border:none;padding:0;">
        <div class="reco-row__meta">
          <span class="reco-row__symbol">${q.symbol}</span>
          <span class="text-muted">${q.companyName || ''} · ${q.sector || ''}</span>
        </div>
        <div class="reco-row__numbers">
          <div>${q.price ?? '—'} <span class="badge ${changeClass}">${q.changePercent != null ? q.changePercent.toFixed(2) + '%' : '—'}</span></div>
          <div class="text-muted">${q.isMarketOpen ? 'Market open' : 'Market closed'} · ${q.dataSource || ''}</div>
        </div>
      </div>
    </div>`;
}

export async function renderDashboard(mount) {
  mount.innerHTML = `
    <div class="tabs" id="market-tabs">
      <button type="button" class="tab is-active" data-tab="us">US Markets</button>
      <button type="button" class="tab" data-tab="india">Indian Markets</button>
      <button type="button" class="tab" data-tab="ipo">IPOs</button>
    </div>
    <div id="board-mount"><p class="text-muted">Loading…</p></div>

    <details class="card" style="margin-top:20px;">
      <summary style="cursor:pointer;font-weight:600;">Quick quote lookup</summary>
      <form id="quote-form" class="form-row" style="margin-top:12px;">
        <div class="field" style="flex:1;min-width:120px;">
          <label for="quote-symbol">Symbol</label>
          <input id="quote-symbol" type="text" placeholder="AAPL" required>
        </div>
        <div class="field">
          <label for="quote-market">Market</label>
          <select id="quote-market">
            ${MARKETS.map(m => `<option value="${m}">${m}</option>`).join('')}
          </select>
        </div>
        <button type="submit" class="btn">Get quote</button>
      </form>
      <p class="error-text" id="quote-error" hidden></p>
      <div id="quote-mount"></div>
    </details>`;

  const boardMount = mount.querySelector('#board-mount');
  const tabsEl = mount.querySelector('#market-tabs');

  async function showTab(tab) {
    tabsEl.querySelectorAll('.tab').forEach(t => t.classList.toggle('is-active', t.dataset.tab === tab));
    if (tab === 'us') await renderBoard(boardMount, 'US_NASDAQ');
    else if (tab === 'india') await renderBoard(boardMount, 'INDIA_NSE');
    else await renderIpoBoard(boardMount);
  }

  tabsEl.addEventListener('click', (e) => {
    const btn = e.target.closest('.tab');
    if (btn) showTab(btn.dataset.tab);
  });

  mount.querySelector('#quote-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const symbol = mount.querySelector('#quote-symbol').value.trim().toUpperCase();
    const market = mount.querySelector('#quote-market').value;
    const errorEl = mount.querySelector('#quote-error');
    const quoteMount = mount.querySelector('#quote-mount');
    errorEl.hidden = true;
    quoteMount.innerHTML = '';
    if (!symbol) return;
    try {
      const body = await apiJson(`/api/v1/market/quote/${encodeURIComponent(symbol)}?market=${market}`);
      if (!body.success || !body.data) {
        errorEl.textContent = body.message || 'Quote not available';
        errorEl.hidden = false;
        return;
      }
      quoteMount.innerHTML = quoteCard(body.data);
    } catch (err) {
      errorEl.textContent = err.message;
      errorEl.hidden = false;
    }
  });

  await showTab('us');
}
