(() => {
  'use strict';
  if (window.__calma) window.__calma.destroy();
  const config = window.CALMA_CONFIG;
  let allow = new Set();
  const accepted = new Set();
  const reserved = new Set(['accounts', 'about', 'direct', 'explore', 'reel', 'reels', 'p', 'stories', 'legal', 'privacy', 'developer', 'web', 'challenge']);
  let dead = false, timer, previousPath = location.pathname, running = false;
  let lastOwner = null;
  const css = document.createElement('style');
  css.id = 'calma-style';
  css.textContent = '.calma-hidden,.calma-extra{display:none!important}#calma-end{display:block!important;box-sizing:border-box;margin:24px auto;padding:24px 20px;width:min(92%,480px);border:1px solid #dbdbdb;border-radius:8px;background:Canvas;color:CanvasText;text-align:center;font:14px/1.6 system-ui,sans-serif}#calma-end strong{display:block;font-size:18px}';
  (document.head || document.documentElement).appendChild(css);
  css.textContent += 'html[data-calma-home="true"] main,html[data-calma-home="true"] [role="main"],html[data-calma-home="true"] video{visibility:hidden!important}html[data-calma-home="true"] article.calma-approved,html[data-calma-home="true"] article.calma-approved video,html[data-calma-home="true"] #calma-end{visibility:visible!important}';
  css.textContent += 'html[data-calma-home="true"][data-calma-reels="true"] video,html[data-calma-home="true"][data-calma-reels="true"] article.calma-approved video{visibility:hidden!important}html[data-calma-home="true"][data-calma-reels="true"] a[href*="/reel/"]{display:none!important}';
  function routeGuard() { const id=window.__calmaRelations?window.__calmaRelations.owner:config.owner;document.documentElement.dataset.calmaHome = location.pathname === '/' && !!id && !document.querySelector('input[type="password"]') ? 'true' : 'false';document.documentElement.dataset.calmaReels=String(!!config.reels); }
  const originalPush = history.pushState, originalReplace = history.replaceState;
  function push(...args) { const result=originalPush.apply(history,args); routeGuard(); scan(); return result; }
  function replace(...args) { const result=originalReplace.apply(history,args); routeGuard(); scan(); return result; }
  history.pushState=push;history.replaceState=replace;
  const marker = document.createElement('div'); marker.id = 'calma-end'; marker.setAttribute('role', 'status');
  function path(link) { try { const url = new URL(link.getAttribute('href'), location.href); return /(^|\.)instagram\.com$/.test(url.hostname) ? url.pathname : ''; } catch (_) { return ''; } }
  function reelPath(p) { return /^\/reels?(\/|$)/.test(p); }
  function postInfo(article) {
    const links = [...article.querySelectorAll('a[href]')];
    const postPattern = /^\/(?:([a-zA-Z0-9._]{1,30})\/)?(p|reel)\/([^/]+)\/?$/;
    const permalink = links.map(path).find(p => postPattern.test(p));
    if (!permalink) return null;
    const match = permalink.match(postPattern);
    const header = article.querySelector('header');
    // Instagram also uses a div-based heading and username-prefixed permalinks.
    // Stop at the first media/permalink so commenters are never mistaken for authors.
    const mediaImages = [...article.querySelectorAll('img')].filter(img => {
      if (img.closest('header')) return false;
      const anchor = img.closest('a[href]');
      return !anchor || !/^\/(?:stories\/)?[a-zA-Z0-9._]{1,30}\/?$/.test(path(anchor));
    });
    const boundary = mediaImages[0] || article.querySelector('video') || links.find(a => path(a) === permalink);
    const candidateLinks = [...new Set([...(header ? header.querySelectorAll('a[href]') : []),
      ...links.filter(a => a === boundary || !!(a.compareDocumentPosition(boundary) & Node.DOCUMENT_POSITION_FOLLOWING))])];
    const authors = new Set(match[1] ? [match[1].toLowerCase()] : []);
    for (const link of candidateLinks) {
      const profile = path(link).match(/^\/(?:stories\/)?([a-zA-Z0-9._]{1,30})\/?$/);
      if (profile && !reserved.has(profile[1].toLowerCase())) authors.add(profile[1].toLowerCase());
    }
    return {id: '/' + match[2] + '/' + match[3], authors, photos: mediaImages.length > 0,
      reel: match[2] === 'reel' || !!article.querySelector('[aria-label="Reel"],[aria-label="Reels"],[aria-label="Clip"]')};
  }
  function hideDiscovery(main) {
    // Do not prune arbitrary branches: React keeps its pagination sentinels,
    // loading indicators and virtual-feed spacers alongside the posts.
    main.querySelectorAll('aside,[data-calma-discovery],#stories,#discovery').forEach(el => {
      if (!el.querySelector('article')) el.classList.add('calma-extra');
    });
  }
  function scan() {
    if (dead || running) return;
    running = true;
    try {
      routeGuard();
      const relations = window.__calmaRelations || {};
      if (lastOwner !== relations.owner) { accepted.clear(); lastOwner = relations.owner; }
      allow = new Set(config.mode === 1 ? relations.following || [] : relations.friends || []);
      if (previousPath !== location.pathname) {
        previousPath = location.pathname;
        document.querySelectorAll('.calma-hidden,.calma-extra').forEach(el => el.classList.remove('calma-hidden', 'calma-extra'));
        marker.remove();
      }
      if (config.reels) {
        if (reelPath(location.pathname) && !(window.__calmaReelGate && window.__calmaReelGate.locked())) { location.replace('https://www.instagram.com'+(config.allowedReelPath||'/')); return; }
        document.querySelectorAll('a[href]').forEach(a => {
          if (reelPath(path(a)) && !location.pathname.startsWith('/direct/')) a.classList.add('calma-hidden');
        });
      }
      // Account allowlists apply only to the home feed, never to login forms or messages.
      if (location.pathname !== '/' || document.querySelector('input[type="password"]')) { marker.remove(); return; }
      const main = document.querySelector('main,[role="main"]');
      if (!main) return;
      const articles = [...main.querySelectorAll('article')];
      if (!articles.length) {
        if (marker.parentElement !== main) main.appendChild(marker);
        setMessage();
        return;
      }
      // Restore projected branches before recomputing when React replaces feed nodes.
      main.querySelectorAll('.calma-extra').forEach(el => el.classList.remove('calma-extra'));
      const rendered = new Set();
      for (const article of articles) {
        const info = postInfo(article);
        const ready = config.mode === 1 ? relations.followingReady : relations.friendsReady;
        const eligible = info && (!config.reels || (!info.reel && (!article.querySelector('video') || info.photos))) && (config.mode === 0 || (ready && [...info.authors].some(author => allow.has(author))));
        let show = false;
        if (eligible && !rendered.has(info.id)) {
          if (accepted.has(info.id)) show = true;
          else if (accepted.size < config.limit) { accepted.add(info.id); show = true; }
        }
        article.classList.toggle('calma-hidden', !show);
        article.classList.toggle('calma-approved', show);
        if (!show || config.reels) article.querySelectorAll('video').forEach(video => { video.pause(); video.removeAttribute('autoplay'); });
        if (show) rendered.add(info.id);
      }
      hideDiscovery(main);
      // Own status lives outside React's post list, never between a post and its sentinel.
      if (marker.parentElement !== main) main.appendChild(marker);
      setMessage();
    } finally { running = false; }
  }
  function setMessage() {
    marker.replaceChildren();
    const title = document.createElement('strong');
    const subtitle = document.createElement('span');
    const relations = window.__calmaRelations || {};
    const ready = config.mode === 1 ? relations.followingReady : relations.friendsReady;
    if (config.mode !== 0 && (!ready || (!allow.size && relations.phase === 'syncing'))) {
      title.textContent = relations.phase === 'error' ? 'No se pudo sincronizar' : 'Sincronizando tu cuenta';
      subtitle.textContent = relations.error || (relations.owner ? 'Consultando seguidos y seguidores para filtrar tu feed.' : 'Inicia sesión para detectar automáticamente a quién sigues.');
    } else if (config.mode !== 0 && !allow.size) {
      title.textContent = config.mode === 1 ? 'Todavía no sigues a ninguna cuenta' : 'Sin seguimiento mutuo';
      subtitle.textContent = 'Puedes volver a sincronizar desde los ajustes adicionales.';
    } else if (accepted.size >= config.limit) {
      title.textContent = 'Esta tanda está completa';
      subtitle.textContent = 'Has llegado al límite de ' + config.limit + ' publicaciones. Puedes cerrar la app o iniciar una Nueva sesión desde Ajustes.';
    } else {
      title.textContent = 'Buscando más publicaciones';
      subtitle.textContent = accepted.size + ' de ' + config.limit + ' publicaciones en esta sesión. Sigue bajando para cargar más; esta tanda termina al alcanzar el límite.';
    }
    marker.append(title, subtitle);
  }
  function clickBlock(event) {
    const anchor = event.target.closest && event.target.closest('a[href]');
    if (config.reels && anchor && reelPath(path(anchor)) && !location.pathname.startsWith('/direct/')) { event.preventDefault(); event.stopImmediatePropagation(); }
  }
  function pauseReel(event) {
    if (!config.reels || !(event.target instanceof HTMLVideoElement)) return;
    if ((reelPath(location.pathname) && !(window.__calmaReelGate && window.__calmaReelGate.locked())) || event.target.closest('.calma-hidden,.calma-extra') || location.pathname==='/') event.target.pause();
  }
  // Observe structure and link changes; ignore our own class writes to avoid loops.
  const observer = new MutationObserver(records => {
    if (records.every(record => record.target === marker || marker.contains(record.target))) return;
    for (const record of records) {
      if (record.type === 'attributes') {
        const article = record.target.closest('article');
        if (article) article.classList.remove('calma-approved');
      }
    }
    cancelAnimationFrame(timer); timer = requestAnimationFrame(scan);
  });
  observer.observe(document.documentElement, {childList: true, subtree: true, attributes: true, attributeFilter: ['href']});
  document.addEventListener('click', clickBlock, true);
  document.addEventListener('play', pauseReel, true);
  document.addEventListener('calma-relations', scan);
  const interval = setInterval(scan, 1600);
  window.__calma = {
    destroy() {
      dead = true; observer.disconnect(); cancelAnimationFrame(timer); clearInterval(interval);
      document.removeEventListener('click', clickBlock, true); document.removeEventListener('play', pauseReel, true);
      document.removeEventListener('calma-relations', scan);
      css.remove(); marker.remove();
      if(history.pushState===push)history.pushState=originalPush;if(history.replaceState===replace)history.replaceState=originalReplace;
      delete document.documentElement.dataset.calmaHome;
      delete document.documentElement.dataset.calmaReels;
      document.querySelectorAll('.calma-approved').forEach(el=>el.classList.remove('calma-approved'));
      document.querySelectorAll('.calma-hidden,.calma-extra').forEach(el => el.classList.remove('calma-hidden', 'calma-extra'));
      delete window.__calma;
    },
    scan,
    count: () => accepted.size
  };
  scan();
})();
