const ORDER = ['auto', 'light', 'dark'];
const ICON = { auto: '🖥', light: '☀', dark: '🌙' };
const KEY = 'hmip.theme';

function current() {
  const stored = localStorage.getItem(KEY);
  return ORDER.includes(stored) ? stored : 'auto';
}

function apply(mode) {
  if (mode === 'auto') {
    document.documentElement.removeAttribute('data-theme');
    localStorage.removeItem(KEY);
  } else {
    document.documentElement.setAttribute('data-theme', mode);
    localStorage.setItem(KEY, mode);
  }
  const btn = document.getElementById('theme-toggle');
  if (btn) {
    btn.textContent = ICON[mode];
    btn.setAttribute('aria-label', `Theme: ${mode}. Click to change.`);
  }
}

export function initThemeToggle() {
  apply(current());
  const btn = document.getElementById('theme-toggle');
  if (!btn) return;
  btn.addEventListener('click', () => {
    const next = ORDER[(ORDER.indexOf(current()) + 1) % ORDER.length];
    apply(next);
  });
}
