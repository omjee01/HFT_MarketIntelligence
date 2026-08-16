// Hand-off links only — this app never executes trades or touches brokerage credentials.
// Clicking these opens the broker's own site in a new tab; the user logs in and buys/sells
// there themselves. See HFT_ARCHITECTURE.md §30.

export function brokerFor(market) {
  const isIndian = typeof market === 'string' && market.startsWith('INDIA_');
  return isIndian
    ? { name: 'Zerodha Kite', url: 'https://kite.zerodha.com/', note: 'Log in, then search for the symbol to place your order.' }
    : { name: 'INDmoney', url: 'https://indmoney.com/', note: 'Log in, then search for the symbol to place your order.' };
}

export function brokerButtonHtml(symbol, market) {
  const broker = brokerFor(market);
  return `
    <a class="btn btn--broker" href="${broker.url}" target="_blank" rel="noopener noreferrer">
      Buy / Sell ${symbol} on ${broker.name} ↗
    </a>
    <p class="text-muted" style="margin:6px 0 0;font-size:0.85em;">${broker.note}</p>`;
}
