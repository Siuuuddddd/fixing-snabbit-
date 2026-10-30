(() => {
  const viewport = document.querySelector('#prototype-carousel');
  if (!viewport) return;
  const cards = [...viewport.querySelectorAll('.prototype-card')];
  const dotsHost = document.querySelector('#carousel-dots');
  const caption = document.querySelector('#carousel-caption');
  const prev = document.querySelector('#carousel-prev');
  const next = document.querySelector('#carousel-next');
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  let active = 0, raf = 0, drag = null;

  function leftFor(index) { return cards[index].offsetLeft; }
  function nearest() {
    let best = 0, distance = Infinity;
    cards.forEach((card, index) => {
      const diff = Math.abs(leftFor(index) - viewport.scrollLeft);
      if (diff < distance) { distance = diff; best = index; }
    });
    return best;
  }
  function render(index) {
    active = index;
    caption.textContent = `${String(index + 1).padStart(2, '0')} · ${cards[index].querySelector('figcaption').textContent}`;
    [...dotsHost.children].forEach((dot, i) => dot.setAttribute('aria-current', String(i === index)));
    prev.disabled = index === 0;
    next.disabled = index === cards.length - 1;
  }
  function go(index, focus = false) {
    index = Math.max(0, Math.min(cards.length - 1, index));
    cancelAnimationFrame(raf);
    const from = viewport.scrollLeft, to = leftFor(index);
    if (reduced.matches || Math.abs(to - from) < 1) viewport.scrollLeft = to;
    else {
      const start = performance.now(), duration = 450;
      const step = now => {
        const t = Math.min(1, (now - start) / duration);
        const eased = 1 - Math.pow(1 - t, 3);
        viewport.scrollLeft = from + (to - from) * eased;
        if (t < 1) raf = requestAnimationFrame(step);
        else raf = 0;
      };
      raf = requestAnimationFrame(step);
    }
    render(index);
    if (focus) viewport.focus({ preventScroll: true });
  }
  cards.forEach((card, i) => {
    const dot = document.createElement('button');
    dot.type = 'button'; dot.className = 'carousel-dot';
    dot.setAttribute('aria-label', `Show prototype screen ${i + 1}: ${card.querySelector('figcaption').textContent}`);
    dot.addEventListener('click', () => go(i));
    dotsHost.append(dot);
    const img = card.querySelector('img');
    img.addEventListener('error', () => {
      img.remove();
      const fallback = document.createElement('div');
      fallback.className = 'missing-image';
      fallback.textContent = `Add assets/prototype/screen-${String(i + 1).padStart(2, '0')}.png`;
      card.prepend(fallback);
    }, { once: true });
  });
  prev.addEventListener('click', () => go(active - 1));
  next.addEventListener('click', () => go(active + 1));
  viewport.addEventListener('keydown', e => {
    if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
      e.preventDefault(); go(active + (e.key === 'ArrowRight' ? 1 : -1));
    }
  });
  viewport.addEventListener('scroll', () => {
    if (drag || raf) return;
    requestAnimationFrame(() => render(nearest()));
  }, { passive: true });
  viewport.addEventListener('pointerdown', e => {
    if (e.pointerType === 'touch') return;
    cancelAnimationFrame(raf); raf = 0;
    drag = { x: e.clientX, scroll: viewport.scrollLeft };
    viewport.setPointerCapture(e.pointerId);
  });
  viewport.addEventListener('pointermove', e => {
    if (drag) viewport.scrollLeft = drag.scroll + drag.x - e.clientX;
  });
  viewport.addEventListener('pointerup', () => { if (drag) { drag = null; go(nearest()); } });
  viewport.addEventListener('pointercancel', () => { drag = null; });
  render(0);
})();
