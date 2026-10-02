'use strict';
const menu = document.querySelector('.menu-toggle');
menu?.addEventListener('click', () => {
  const expanded = menu.getAttribute('aria-expanded') !== 'true';
  menu.setAttribute('aria-expanded', String(expanded));
  menu.setAttribute('aria-label', expanded ? 'Cerrar navegación' : 'Abrir navegación');
  document.getElementById('mobile-nav').hidden = !expanded;
});
const wikiToggle = document.querySelector('.wiki-toggle');
wikiToggle?.addEventListener('click', () => {
  const expanded = document.querySelector('.wiki-sidebar').classList.toggle('expanded');
  wikiToggle.setAttribute('aria-expanded', String(expanded));
});
let toastTimer;
function announce(text) {
  const toast = document.querySelector('.toast');
  toast.textContent = text;
  toast.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('show'), 3500);
}
document.querySelectorAll('[data-copy]').forEach(button => button.addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText(button.dataset.copy);
    announce('Dirección copiada. ¡Nos vemos en PokeReport!');
  } catch {
    announce('Dirección del servidor: ' + button.dataset.copy);
  }
}));
const input = document.getElementById('wiki-search');
const results = document.getElementById('search-results');
if (input && results) {
  let indexPromise;
  let generation = 0;
  const normalize = value => value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
  async function search() {
    const current = ++generation;
    const query = normalize(input.value.trim());
    if (!query) { results.hidden = true; results.replaceChildren(); return; }
    try {
      indexPromise ||= fetch('/portal-assets/search.json', {cache:'no-cache'}).then(response => {
        if (!response.ok) throw new Error('Search index unavailable');
        return response.json();
      }).catch(error => { indexPromise = null; throw error; });
      const data = await indexPromise;
      if (current !== generation) return;
      const matches = data.filter(a => normalize(a.title + ' ' + a.summary + ' ' + a.category).includes(query));
      results.replaceChildren();
      for (const article of matches) {
        const link = document.createElement('a');
        link.href = '/wiki/' + article.slug + '/';
        link.textContent = article.title;
        const desc = document.createElement('small');
        desc.textContent = article.summary;
        link.append(desc); results.append(link);
      }
      if (!matches.length) {
        const message = document.createElement('p');
        message.textContent = 'No encontramos una guía. Prueba con «crianza», «PokéPad» o «primer acceso».';
        results.append(message);
      }
      results.hidden = false;
    } catch {
      results.replaceChildren();
      const message = document.createElement('p');
      message.textContent = 'La búsqueda no está disponible. Puedes usar el índice de guías.';
      results.append(message); results.hidden = false;
    }
  }
  input.addEventListener('input', search);
  input.addEventListener('focus', () => { if (input.value.trim()) search(); });
  input.addEventListener('keydown', event => {
    if (event.key === 'Escape') { results.hidden = true; input.blur(); }
    if (event.key === 'ArrowDown') { event.preventDefault(); results.querySelector('a')?.focus(); }
    if (event.key === 'Enter') results.querySelector('a')?.click();
  });
  results.addEventListener('keydown', event => {
    const links = [...results.querySelectorAll('a')];
    const at = links.indexOf(document.activeElement);
    if (event.key === 'ArrowDown') { event.preventDefault(); links[(at + 1) % links.length]?.focus(); }
    if (event.key === 'ArrowUp') { event.preventDefault(); at > 0 ? links[at - 1].focus() : input.focus(); }
    if (event.key === 'Escape') { results.hidden = true; input.focus(); }
  });
  document.addEventListener('click', event => { if (!event.target.closest('.wiki-search')) results.hidden = true; });
  document.addEventListener('keydown', event => {
    if (event.key === '/' && !event.ctrlKey && !event.metaKey && !['INPUT','TEXTAREA'].includes(document.activeElement.tagName)) {
      event.preventDefault(); input.focus();
    }
  });
}
if ('IntersectionObserver' in window) {
  const observer = new IntersectionObserver(entries => {
    for (const entry of entries) {
      if (entry.isIntersecting) {
        document.querySelectorAll('.article-toc a').forEach(link => link.classList.toggle('active', link.hash === '#' + entry.target.id));
      }
    }
  }, {rootMargin:'-100px 0px -55% 0px'});
  document.querySelectorAll('.article-section').forEach(section => observer.observe(section));
}
