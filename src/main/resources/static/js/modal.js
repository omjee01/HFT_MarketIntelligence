let overlayEl = null;

function ensureOverlay() {
  if (overlayEl) return overlayEl;
  overlayEl = document.createElement('div');
  overlayEl.className = 'modal-overlay';
  overlayEl.hidden = true;
  overlayEl.innerHTML = `
    <div class="modal" role="dialog" aria-modal="true">
      <button type="button" class="modal__close" aria-label="Close">✕</button>
      <div class="modal__body"></div>
    </div>`;
  document.body.appendChild(overlayEl);

  overlayEl.addEventListener('click', (e) => {
    if (e.target === overlayEl) closeModal();
  });
  overlayEl.querySelector('.modal__close').addEventListener('click', closeModal);
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !overlayEl.hidden) closeModal();
  });
  return overlayEl;
}

export function openModal(html) {
  const overlay = ensureOverlay();
  overlay.querySelector('.modal__body').innerHTML = html;
  overlay.hidden = false;
  document.body.style.overflow = 'hidden';
  return overlay.querySelector('.modal__body');
}

export function closeModal() {
  if (!overlayEl) return;
  overlayEl.hidden = true;
  document.body.style.overflow = '';
}
