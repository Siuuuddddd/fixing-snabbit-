(() => {
  const textNodes = [];
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  while (walker.nextNode()) if (/\bSnabbit\b/i.test(walker.currentNode.nodeValue) && !walker.currentNode.parentElement.closest('.brand')) textNodes.push(walker.currentNode);
  textNodes.forEach(node => {
    const fragment = document.createDocumentFragment();
    node.nodeValue.split(/(Snabbit)/gi).forEach(part => {
      if (/^Snabbit$/i.test(part)) { const span = document.createElement('span'); span.className = 'brand'; span.textContent = part; fragment.append(span); }
      else fragment.append(document.createTextNode(part));
    });
    node.replaceWith(fragment);
  });
  document.querySelectorAll('.carousel-viewport').forEach(setupCarousel);
})();

function setupCarousel(viewport) {
  const root = viewport.closest('.prototype-stage, .people-carousel');
  const cards = [...viewport.querySelectorAll('.prototype-card')];
  const dotsHost = root.querySelector('.carousel-dots');
  const caption = root.querySelector('.carousel-caption');
  const prev = root.querySelector('.carousel-button:first-child');
  const next = root.querySelector('.carousel-button:last-child');
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
    viewport.style.scrollSnapType = 'none';
    if (reduced.matches || Math.abs(to - from) < 1) {
      viewport.scrollLeft = to;
      viewport.style.scrollSnapType = '';
      raf = 0;
    }
    else {
      const start = performance.now(), duration = 450;
      const step = now => {
        const t = Math.min(1, (now - start) / duration);
        const eased = 1 - Math.pow(1 - t, 3);
        viewport.scrollLeft = from + (to - from) * eased;
        if (t < 1) raf = requestAnimationFrame(step);
        else { viewport.scrollLeft = to; viewport.style.scrollSnapType = ''; raf = 0; }
      };
      raf = requestAnimationFrame(step);
    }
    render(index);
    if (focus) viewport.focus({ preventScroll: true });
  }
  cards.forEach((card, i) => {
    const dot = document.createElement('button');
    dot.type = 'button'; dot.className = 'carousel-dot';
    dot.setAttribute('aria-label', `Show ${card.querySelector('figcaption').textContent}`);
    dot.addEventListener('click', () => go(i));
    dotsHost.append(dot);
    const img = card.querySelector('img');
    img.addEventListener('error', () => {
      img.remove();
      const fallback = document.createElement('div');
      fallback.className = 'missing-image';
      fallback.textContent = `Add ${img.getAttribute('src').replace(/^\.\//, '')}`;
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
    viewport.style.scrollSnapType = 'none';
    drag = { x: e.clientX, scroll: viewport.scrollLeft };
    viewport.setPointerCapture(e.pointerId);
  });
  viewport.addEventListener('pointermove', e => {
    if (drag) viewport.scrollLeft = drag.scroll + drag.x - e.clientX;
  });
  viewport.addEventListener('pointerup', () => { if (drag) { drag = null; go(nearest()); } });
  viewport.addEventListener('pointercancel', () => { drag = null; viewport.style.scrollSnapType = ''; });
  render(0);
}

const loomVideo = document.querySelector('.loom-video');
if (loomVideo) {
  const frame = loomVideo.querySelector('iframe');
  const button = loomVideo.querySelector('.loom-play-button');
  let ready = false;
  let requested = false;
  const play = () => {
    if (!ready || !requested) return;
    loomVideo.classList.add('playing');
    frame.contentWindow?.postMessage({ context: 'player.js', method: 'play' }, 'https://www.loom.com');
  };
  const markReady = () => setTimeout(() => { ready = true; play(); }, 1800);
  if (frame.dataset.loaded) markReady();
  else frame.addEventListener('load', markReady, { once: true });
  button.addEventListener('click', () => {
    requested = true;
    if (!ready) button.querySelector('span').textContent = '…';
    play();
  });
}
