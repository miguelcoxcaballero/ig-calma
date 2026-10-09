(() => {
  'use strict';
  if (window.__calma) window.__calma.destroy();
  const config = window.CALMA_CONFIG;
  let allow = new Set();
  const accepted = new Set();
  const reserved = new Set(['accounts', 'about', 'direct', 'explore', 'reel', 'reels', 'p', 'stories', 'legal', 'privacy', 'developer', 'web', 'challenge']);
  let dead = false, timer, previousPath = location.pathname, running = false;
  let lastRoot = null;
  let lastOwner = null;
  const css = document.createElement('style');
  css.id = 'calma-style';
  css.textContent = '.calma-hidden,.calma-extra{display:none!important}#calma-end{display:block!important;box-sizing:border-box;margin:24px auto;padding:24px 20px;width:min(92%,480px);border:1px solid #dbdbdb;border-radius:8px;background:Canvas;color:CanvasText;text-align:center;font:14px/1.6 system-ui,sans-serif}#calma-end strong{display:block;font-size:18px}';
  (document.head || document.documentElement).appendChild(css);
  const marker = document.createElement('div'); marker.id = 'calma-end'; marker.setAttribute('role', 'status');
  function path(link) { try { const url = new URL(link.getAttribute('href'), location.href); return /(^|\.)instagram\.com$/.test(url.hostname) ? url.pathname : ''; } catch (_) { return ''; } }
  function reelPath(p) { return /^\/reels?(\/|$)/.test(p); }
  function postInfo(article) {
    const links = [...article.querySelectorAll('a[href]')];
    const permalink = links.map(path).find(p => /^\/(p|reel)\/[^/]+/.test(p));
    if (!permalink) return null;
    const header = article.querySelector('header');
    const candidateLinks = header ? [...header.querySelectorAll('a[href]')] : links.slice(0, links.findIndex(a => path(a) === permalink));
    const authorLink = candidateLinks.find(a => {
      const match = path(a).match(/^\/([a-zA-Z0-9._]{1,30})\/?$/);
      return match && !reserved.has(match[1].toLowerCase());
    });
    const author = authorLink ? path(authorLink).split('/')[1].toLowerCase() : null;
    return {id: permalink.replace(/\/$/, ''), author, reel: links.some(a => reelPath(path(a))) || !!article.querySelector('[aria-label="Reel"],[aria-label="Reels"],[aria-label="Clip"]')};
  }
  function hideOtherBranches(node, articles) {
    for (const child of [...node.children]) {
      if (child === marker || articles.includes(child) || child.matches('[role="dialog"],script,style')) continue;
      const descendants = articles.filter(article => child.contains(article));
      if (descendants.length) hideOtherBranches(child, descendants);
      else child.classList.add('calma-extra');
    }
  }
  function scan() {
    if (dead || running) return;
    running = true;
    try {
      const relations = window.__calmaRelations || {};
      if (lastOwner !== relations.owner) { accepted.clear(); lastOwner = relations.owner; }
      allow = new Set(config.mode === 1 ? relations.following || [] : relations.friends || []);
      if (previousPath !== location.pathname) {
        previousPath = location.pathname;
        document.querySelectorAll('.calma-hidden,.calma-extra').forEach(el => el.classList.remove('calma-hidden', 'calma-extra'));
        marker.remove();
      }
      if (config.reels) {
        if (reelPath(location.pathname)) { location.replace('https://www.instagram.com/'); return; }
        document.querySelectorAll('a[href]').forEach(a => {
          if (reelPath(path(a))) a.classList.add('calma-hidden');
        });
      }
      // Account allowlists apply only to the home feed, never to login forms or messages.
      if (location.pathname !== '/' || document.querySelector('input[type="password"]')) { marker.remove(); return; }
      const main = document.querySelector('main,[role="main"]');
      if (!main) return;
      const articles = [...main.querySelectorAll('article')];
      if (!articles.length) {
        if (lastRoot === main && marker.isConnected) setMessage();
        return;
      }
      lastRoot = main;
      // Restore projected branches before recomputing when React replaces feed nodes.
      main.querySelectorAll('.calma-extra').forEach(el => el.classList.remove('calma-extra'));
      const visible = [];
      for (const article of articles) {
        const info = postInfo(article);
        const ready = config.mode === 1 ? relations.followingReady : relations.friendsReady;
        const eligible = info && (!config.reels || !info.reel) && (config.mode === 0 || (ready && info.author && allow.has(info.author)));
        let show = false;
        if (eligible) {
          if (accepted.has(info.id)) show = true;
          else if (accepted.size < config.limit) { accepted.add(info.id); show = true; }
        }
        article.classList.toggle('calma-hidden', !show);
        if (!show) article.querySelectorAll('video').forEach(video => { video.pause(); video.removeAttribute('autoplay'); });
        else visible.push(article);
      }
      // Keep only post branches in home: remove stories, suggestions and trailing discovery panels.
      hideOtherBranches(main, visible);
      if (visible.length) {
        const last = visible[visible.length - 1];
        if (last.nextElementSibling !== marker) last.after(marker);
      } else if (marker.parentElement !== main) main.appendChild(marker);
      setMessage();
    } finally { running = false; }
  }
  function setMessage() {
    marker.replaceChildren();
    const title = document.createElement('strong');
    const subtitle = document.createElement('span');
    const relations = window.__calmaRelations || {};
    const ready = config.mode === 1 ? relations.followingReady : relations.friendsReady;
    if (config.mode !== 0 && !ready) {
      title.textContent = relations.phase === 'error' ? 'No se pudo sincronizar' : 'Sincronizando tu cuenta';
      subtitle.textContent = relations.error || (relations.owner ? 'Consultando seguidos y seguidores para filtrar tu feed.' : 'Inicia sesión para detectar automáticamente a quién sigues.');
    } else if (config.mode !== 0 && !allow.size) {
      title.textContent = config.mode === 1 ? 'Todavía no sigues a ninguna cuenta' : 'Sin seguimiento mutuo';
      subtitle.textContent = 'Puedes volver a sincronizar desde los ajustes adicionales.';
    } else if (accepted.size >= config.limit) {
      title.textContent = 'Por hoy, esta tanda está completa';
      subtitle.textContent = 'Has llegado al límite de ' + config.limit + ' publicaciones. Puedes cerrar la app o iniciar una Nueva sesión desde Ajustes.';
    } else {
      title.textContent = accepted.size ? 'Fin de lo cargado' : 'Sin publicaciones permitidas';
      subtitle.textContent = accepted.size + ' de ' + config.limit + ' publicaciones en esta sesión. Instagram puede cargar más al desplazarte; si no aparecen, vuelve más tarde.';
    }
    marker.append(title, subtitle);
  }
  function clickBlock(event) {
    const anchor = event.target.closest && event.target.closest('a[href]');
    if (config.reels && anchor && reelPath(path(anchor))) { event.preventDefault(); event.stopImmediatePropagation(); }
  }
  function pauseReel(event) {
    if (!config.reels || !(event.target instanceof HTMLVideoElement)) return;
    if (reelPath(location.pathname) || event.target.closest('.calma-hidden,.calma-extra')) event.target.pause();
  }
  // Observe structure only: own class changes must not cause an observer loop.
  const observer = new MutationObserver(records => {
    if (records.every(record => record.target === marker || marker.contains(record.target))) return;
    clearTimeout(timer); timer = setTimeout(scan, 120);
  });
  observer.observe(document.documentElement, {childList: true, subtree: true});
  document.addEventListener('click', clickBlock, true);
  document.addEventListener('play', pauseReel, true);
  document.addEventListener('calma-relations', scan);
  const interval = setInterval(scan, 1600);
  window.__calma = {
    destroy() {
      dead = true; observer.disconnect(); clearTimeout(timer); clearInterval(interval);
      document.removeEventListener('click', clickBlock, true); document.removeEventListener('play', pauseReel, true);
      document.removeEventListener('calma-relations', scan);
      css.remove(); marker.remove();
      document.querySelectorAll('.calma-hidden,.calma-extra').forEach(el => el.classList.remove('calma-hidden', 'calma-extra'));
      delete window.__calma;
    },
    scan,
    count: () => accepted.size
  };
  scan();
})();
