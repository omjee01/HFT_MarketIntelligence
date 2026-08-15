import { apiJson } from '../api.js';

const MARKETS = ['US_NASDAQ', 'US_NYSE', 'US_AMEX', 'INDIA_NSE', 'INDIA_BSE', 'INDIA_MCX'];

function signalBadgeClass(signal) {
  if (!signal) return 'badge--neutral';
  if (signal.includes('BUY')) return 'badge--buy';
  if (signal.includes('SELL')) return 'badge--sell';
  if (signal === 'HOLD' || signal === 'WATCH') return 'badge--hold';
  return 'badge--neutral';
}

function recoRow(r) {
  return `
    <div class="reco-row">
      <div class="reco-row__meta">
        <span class="reco-row__symbol">${r.symbol} <span class="badge ${signalBadgeClass(r.signal)}">${r.signal}</span></span>
        <span class="text-muted">${r.companyName || ''} · ${r.market || ''}</span>
      </div>
      <div class="reco-row__numbers">
        <div>Entry ${r.entryPrice ?? '—'} → Target ${r.targetPrice ?? '—'}</div>
        <div class="text-muted">${r.expectedProfitPercent != null ? r.expectedProfitPercent.toFixed(1) + '%' : '—'}
          · conf ${r.confidencePercent != null ? Math.round(r.confidencePercent) : '—'}%</div>
      </div>
    </div>`;
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
    <div class="view-grid">
      <div class="card">
        <h3 style="margin-top:0">Quick quote</h3>
        <form id="quote-form" class="form-row">
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
      </div>

      <div class="card" style="grid-column:1/-1;">
        <h3 style="margin-top:0">Today's top recommendations</h3>
        <p class="text-muted" id="reco-summary">Loading…</p>
        <div id="reco-mount"></div>
      </div>
    </div>`;

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

  const summaryEl = mount.querySelector('#reco-summary');
  const recoMount = mount.querySelector('#reco-mount');
  try {
    const body = await apiJson('/api/v1/recommendations/daily?topN=10');
    if (body.success && body.data) {
      summaryEl.textContent = body.data.marketSummary || `${body.data.totalRecommendations} recommendations`;
      recoMount.innerHTML = (body.data.topBuys || []).map(recoRow).join('') || '<p class="text-muted">No active recommendations right now.</p>';
    } else {
      summaryEl.textContent = body.message || 'No data available';
    }
  } catch (err) {
    summaryEl.textContent = '';
    summaryEl.classList.add('error-text');
    summaryEl.textContent = err.message;
  }
}
