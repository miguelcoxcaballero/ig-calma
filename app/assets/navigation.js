(() => {
  'use strict';
  if (window.__calmaNavigation) return;
  function click(event) {
    if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
    const link = event.target.closest && event.target.closest('a[href]');
    if (!link || link.hasAttribute('download') || (link.target && link.target !== '_self')) return;
    let url;
    try { url = new URL(link.href, location.href); } catch (_) { return; }
    if (url.origin !== location.origin || url.search || url.hash) return;
    const tab = url.pathname === '/direct/inbox/' || url.pathname === '/direct/inbox' ? 'direct' : url.pathname === '/' ? 'home' : '';
    if (!tab || tab === window.CALMA_TAB || !(window.__calmaRelations ? window.__calmaRelations.owner : window.CALMA_CONFIG.owner)) return;
    event.preventDefault(); event.stopImmediatePropagation();
    // A validated main-frame navigation is handled by Android; no native bridge on Instagram.
    const command = new URL(location.href); command.search = ''; command.hash = '';
    command.searchParams.set('calma_tab', tab);
    location.assign(command.href);
  }
  function visibility() {
    if (window.CALMA_ACTIVE === false) {
      document.querySelectorAll('video,audio').forEach(media => media.pause());
      const focused = document.activeElement;
      if (focused && focused.matches('input,textarea,[contenteditable="true"]')) focused.blur();
    }
  }
  document.addEventListener('click', click, true);
  document.addEventListener('calma-visibility', visibility);
  window.__calmaNavigation = {destroy() {
    document.removeEventListener('click', click, true);
    document.removeEventListener('calma-visibility', visibility);
    delete window.__calmaNavigation;
  }};
})();
