import { apiJson } from '../api.js';
import { openModal, closeModal } from '../modal.js';
import { brokerButtonHtml } from '../broker-links.js';

function fmt(n, digits = 2) {
  return n == null ? '—' : Number(n).toFixed(digits);
}

function pct(n, digits = 1) {
  return n == null ? '—' : `${Number(n).toFixed(digits)}%`;
}

function scoreBar(label, value) {
  const v = value == null ? null : Math.max(0, Math.min(100, value));
  return `
    <div class="score-bar">
      <div class="score-bar__label"><span>${label}</span><span>${v == null ? '—' : Math.round(v)}</span></div>
      <div class="score-bar__track"><div class="score-bar__fill" style="width:${v ?? 0}%"></div></div>
    </div>`;
}

function list(items, emptyText) {
  if (!items || items.length === 0) return `<p class="text-muted">${emptyText}</p>`;
  return `<ul class="detail-list">${items.map(i => `<li>${i}</li>`).join('')}</ul>`;
}

// ─── Stock/ETF/IPO-post-listing recommendation detail ─────────────────────────

function stockDetailHtml(r) {
  const stopDistance = r.entryPrice && r.stopLossPrice
    ? Math.abs(((r.stopLossPrice - r.entryPrice) / r.entryPrice) * 100) : null;

  return `
    <div class="detail">
      <div class="detail__header">
        <div>
          <h2 style="margin:0">${r.symbol} <span class="badge badge--${signalTone(r.signal)}">${r.signal}</span></h2>
          <p class="text-muted" style="margin:4px 0 0">${r.companyName || ''} · ${r.market || ''} · ${r.sector || ''}
            ${r.marketCapTier ? ' · ' + r.marketCapTier + ' Cap' : ''}</p>
        </div>
      </div>

      <div class="detail__grid">
        <div class="stat"><span class="stat__label">Current / Entry</span><span class="stat__value">${fmt(r.currentPrice)} / ${fmt(r.entryPrice)}</span></div>
        <div class="stat"><span class="stat__label">Target</span><span class="stat__value">${fmt(r.targetPrice)}</span></div>
        <div class="stat"><span class="stat__label">Stop-loss</span><span class="stat__value">${fmt(r.stopLossPrice)}${stopDistance != null ? ` <span class="text-muted">(-${stopDistance.toFixed(1)}%)</span>` : ''}</span></div>
        <div class="stat"><span class="stat__label">Expected profit</span><span class="stat__value">${pct(r.expectedProfitPercent)}</span></div>
      </div>
      <p class="text-muted" style="font-size:0.85em">Stop-loss is set 2× ATR below entry when volatility data is available
        (a wider buffer in choppier stocks, a tighter one in calm ones), or a flat 7% fallback otherwise.</p>

      <h3>Success rate &amp; risk</h3>
      <div class="detail__grid">
        <div class="stat"><span class="stat__label">Model confidence</span><span class="stat__value">${pct(r.confidencePercent, 0)}</span></div>
        <div class="stat"><span class="stat__label">Risk level</span><span class="stat__value">${r.riskLevel || '—'}</span></div>
        <div class="stat"><span class="stat__label">Max loss if stopped out</span><span class="stat__value">${pct(r.maxRiskPercent)}</span></div>
        <div class="stat"><span class="stat__label">Risk : reward</span><span class="stat__value">${r.riskRewardRatio != null ? r.riskRewardRatio.toFixed(2) + ' : 1' : '—'}</span></div>
      </div>
      <p class="text-muted" style="font-size:0.85em">"Model confidence" is this model's own confidence in the call, not a
        backtested historical win rate — treat it as a relative ranking signal, not a guarantee.</p>

      <h3>Score breakdown</h3>
      ${scoreBar('Technical', r.technicalScore)}
      ${scoreBar('Fundamental', r.fundamentalScore)}
      ${scoreBar('Sentiment', r.sentimentScore)}
      ${scoreBar('Macro', r.macroScore)}
      ${scoreBar('ML', r.mlScore)}

      <h3>Why</h3>
      ${list(r.keyReasons, 'No specific reasons recorded.')}

      <h3>Risks</h3>
      ${list(r.keyRisks, 'No specific risks recorded.')}

      ${r.relatedNews && r.relatedNews.length ? `<h3>Related news</h3>${list(r.relatedNews, '')}` : ''}

      <h3>Buy / Sell</h3>
      ${brokerButtonHtml(r.symbol, r.market)}
      <button type="button" class="btn btn--ghost" id="open-position-toggle" style="margin-top:10px">I already bought this →</button>
      <div id="open-position-form-mount"></div>
    </div>`;
}

function signalTone(signal) {
  if (!signal) return 'neutral';
  if (signal.includes('BUY')) return 'buy';
  if (signal.includes('SELL')) return 'sell';
  return 'hold';
}

// ─── IPO detail ─────────────────────────────────────────────────────────────

function ipoDetailHtml(ipo) {
  return `
    <div class="detail">
      <div class="detail__header">
        <div>
          <h2 style="margin:0">${ipo.symbol} <span class="badge badge--${ipoTone(ipo.recommendation)}">${ipo.recommendation}</span></h2>
          <p class="text-muted" style="margin:4px 0 0">${ipo.companyName || ''} · ${ipo.market || ''} · ${ipo.sector || ''} · ${ipo.status || ''}</p>
        </div>
      </div>

      <div class="detail__grid">
        <div class="stat"><span class="stat__label">Price band</span><span class="stat__value">${fmt(ipo.issuePriceLow)} – ${fmt(ipo.issuePriceHigh)}</span></div>
        <div class="stat"><span class="stat__label">Lot size</span><span class="stat__value">${ipo.lotSize ?? '—'}</span></div>
        <div class="stat"><span class="stat__label">GMP</span><span class="stat__value">${pct(ipo.gmpPercent)}</span></div>
        <div class="stat"><span class="stat__label">Overall subscription</span><span class="stat__value">${ipo.overallSubscriptionTimes != null ? ipo.overallSubscriptionTimes.toFixed(1) + 'x' : '—'}</span></div>
      </div>

      <h3>Success rate &amp; prediction</h3>
      <div class="detail__grid">
        <div class="stat"><span class="stat__label">Predicted listing gain</span><span class="stat__value">${pct(ipo.predictedListingGainPercent)}</span></div>
        <div class="stat"><span class="stat__label">Confidence</span><span class="stat__value">${pct(ipo.listingGainConfidence, 0)}</span></div>
        <div class="stat"><span class="stat__label">PE at issue vs industry</span><span class="stat__value">${fmt(ipo.peAtIssuePrice, 1)} / ${fmt(ipo.industryPeAvg, 1)}</span></div>
        <div class="stat"><span class="stat__label">Valuation</span><span class="stat__value">${ipo.valuationVerdict || '—'}</span></div>
      </div>

      <h3>Why</h3>
      <p>${ipo.recommendationReason || 'No detail recorded.'}</p>

      <h3>Key dates</h3>
      <div class="detail__grid">
        <div class="stat"><span class="stat__label">Subscription</span><span class="stat__value">${ipo.subscriptionOpenDate || '—'} → ${ipo.subscriptionCloseDate || '—'}</span></div>
        <div class="stat"><span class="stat__label">Allotment / Listing</span><span class="stat__value">${ipo.allotmentDate || '—'} / ${ipo.listingDate || '—'}</span></div>
      </div>

      <h3>Apply / Buy</h3>
      ${brokerButtonHtml(ipo.symbol, ipo.market)}
      <button type="button" class="btn btn--ghost" id="open-position-toggle" style="margin-top:10px">I already got an allotment / bought this →</button>
      <div id="open-position-form-mount"></div>
    </div>`;
}

function ipoTone(rec) {
  if (!rec) return 'neutral';
  if (rec.includes('APPLY')) return 'buy';
  if (rec === 'AVOID') return 'sell';
  return 'hold';
}

// ─── "I bought this" inline form ───────────────────────────────────────────

function openPositionFormHtml(defaults) {
  return `
    <form id="open-position-form" class="card" style="margin-top:10px;background:var(--bg-elevated)">
      <div class="form-row">
        <div class="field" style="flex:1;min-width:100px;">
          <label for="pos-qty">Quantity</label>
          <input id="pos-qty" type="number" step="0.0001" min="0.0001" required>
        </div>
        <div class="field" style="flex:1;min-width:100px;">
          <label for="pos-price">Price you paid</label>
          <input id="pos-price" type="number" step="0.0001" min="0.0001" required value="${defaults.price ?? ''}">
        </div>
      </div>
      <p class="error-text" id="open-position-error" hidden></p>
      <button type="submit" class="btn">Save to portfolio</button>
    </form>`;
}

function wireOpenPositionForm(mount, { symbol, market, assetType, companyName, recommendationId, targetPrice, stopLossPrice, defaultPrice }) {
  const toggle = mount.querySelector('#open-position-toggle');
  const formMount = mount.querySelector('#open-position-form-mount');
  toggle.addEventListener('click', () => {
    if (formMount.innerHTML) { formMount.innerHTML = ''; return; }
    formMount.innerHTML = openPositionFormHtml({ price: defaultPrice });
    const form = formMount.querySelector('#open-position-form');
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const errorEl = form.querySelector('#open-position-error');
      errorEl.hidden = true;
      const quantity = form.querySelector('#pos-qty').value;
      const avgBuyPrice = form.querySelector('#pos-price').value;
      try {
        await apiJson('/api/v1/portfolio/positions', {
          method: 'POST',
          body: JSON.stringify({
            symbol, market, assetType, companyName, quantity, avgBuyPrice,
            recommendationId: recommendationId || null,
            targetPrice: recommendationId ? null : (targetPrice ?? null),
            stopLossPrice: recommendationId ? null : (stopLossPrice ?? null),
          }),
        });
        formMount.innerHTML = '<p class="text-muted">Saved — see it under Portfolio.</p>';
      } catch (err) {
        errorEl.textContent = err.message;
        errorEl.hidden = false;
      }
    });
  });
}

export function showStockDetail(r) {
  const mount = openModal(stockDetailHtml(r));
  wireOpenPositionForm(mount, {
    symbol: r.symbol, market: r.market, assetType: r.assetType || 'STOCK', companyName: r.companyName,
    recommendationId: r.id, defaultPrice: r.entryPrice,
  });
}

export function showIpoDetail(ipo) {
  const mount = openModal(ipoDetailHtml(ipo));
  wireOpenPositionForm(mount, {
    symbol: ipo.symbol, market: ipo.market, assetType: 'IPO', companyName: ipo.companyName,
    recommendationId: null, targetPrice: null, stopLossPrice: null,
    defaultPrice: ipo.issuePriceHigh,
  });
}

export { closeModal };
