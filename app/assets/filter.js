(() => {
  'use strict';
  if (window.__calma) window.__calma.destroy();
  const config = window.CALMA_CONFIG;
  let allow = new Set();
  const accepted = new Set();
  const reserved = new Set(['accounts', 'about', 'direct', 'explore', 'reel', 'reels', 'p', 'stories', 'legal', 'privacy', 'developer', 'web', 'challenge']);
  let dead = false, timer = 0, previousPath = location.pathname, running = false;
  let lastOwner = null, allowSource, allowMode, scanAllLinks = true;
  let articleInfo = new WeakMap(), active = window.CALMA_ACTIVE !== false;
  const changedLinks = new Set();
  const css = document.createElement('style');
  css.id = 'calma-style';
  css.textContent = '.calma-hidden,.calma-extra{display:none!important}#calma-end{display:block!important;box-sizing:border-box;margin:16px auto;padding:24px 16px;width:min(100%,480px);border-top:1px solid color-mix(in srgb,CanvasText 12%,Canvas);background:Canvas;color:CanvasText;text-align:center;font:13px/1.5 system-ui,sans-serif}#calma-end strong{display:block;font-size:15px;font-weight:600;margin-bottom:4px}#calma-end span{opacity:.65}';
  (document.head || document.documentElement).appendChild(css);
  css.textContent += 'html[data-calma-home="true"] main,html[data-calma-home="true"] [role="main"],html[data-calma-home="true"] video{visibility:hidden!important}html[data-calma-home="true"] article.calma-approved,html[data-calma-home="true"] article.calma-approved video,html[data-calma-home="true"] #calma-end{visibility:visible!important}';
  css.textContent += 'html[data-calma-home="true"][data-calma-reels="true"] video,html[data-calma-home="true"][data-calma-reels="true"] article.calma-approved video{visibility:hidden!important}html[data-calma-home="true"][data-calma-reels="true"] a[href*="/reel/"]{display:none!important}';
  function routeGuard() {
    const id=window.__calmaRelations?window.__calmaRelations.owner:config.owner;
    const home = location.pathname === '/' && !!id && !document.querySelector('input[type="password"]') ? 'true' : 'false';
    if (document.documentElement.dataset.calmaHome !== home) document.documentElement.dataset.calmaHome = home;
    const reels = String(!!config.reels);
    if (document.documentElement.dataset.calmaReels !== reels) document.documentElement.dataset.calmaReels = reels;
  }
  const originalPush = history.pushState, originalReplace = history.replaceState;
  function routeChange() { routeGuard(); scan(); }
  function notifyRoute() { document.dispatchEvent(new CustomEvent('calma-route', {bubbles: true})); }
  function push(...args) { const result=originalPush.apply(history,args); notifyRoute(); return result; }
  function replace(...args) { const result=originalReplace.apply(history,args); notifyRoute(); return result; }
  history.pushState=push;history.replaceState=replace;
  const marker = document.createElement('div'); marker.id = 'calma-end'; marker.setAttribute('role', 'status');
  const markerTitle = document.createElement('strong'), markerSubtitle = document.createElement('span');
  marker.append(markerTitle, markerSubtitle);
  function path(link) { try { const url = new URL(link.getAttribute('href'), location.href); return /(^|\.)instagram\.com$/.test(url.hostname) ? url.pathname : ''; } catch (_) { return ''; } }
  function reelPath(p) { return /^\/reels?(\/|$)/.test(p); }
  function postInfo(article) {
    if (articleInfo.has(article)) return articleInfo.get(article);
    const links = [...article.querySelectorAll('a[href]')];
    const postPattern = /^\/(?:([a-zA-Z0-9._]{1,30})\/)?(p|reel)\/([^/]+)\/?$/;
    const permalink = links.map(path).find(p => postPattern.test(p));
    if (!permalink) { articleInfo.set(article, null); return null; }
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
    const info = {id: '/' + match[2] + '/' + match[3], authors, photos: mediaImages.length > 0,
      video: !!article.querySelector('video'),
      reel: match[2] === 'reel' || !!article.querySelector('[aria-label="Reel"],[aria-label="Reels"],[aria-label="Clip"]')};
    articleInfo.set(article, info);
    return info;
  }
  function hideDiscovery(main) {
    // Do not prune arbitrary branches: React keeps its pagination sentinels,
    // loading indicators and virtual-feed spacers alongside the posts.
    main.querySelectorAll('aside,[data-calma-discovery],#stories,#discovery').forEach(el => {
      if (!el.querySelector('article') && !el.classList.contains('calma-extra')) el.classList.add('calma-extra');
    });
  }
  function filterLinks(root) {
    if (root.nodeType !== Node.ELEMENT_NODE && root !== document) return;
    const links = root.matches && root.matches('a[href]') ? [root] : [];
    links.push(...root.querySelectorAll('a[href]'));
    for (const link of links) {
      const hide = config.reels && !location.pathname.startsWith('/direct/') && reelPath(path(link));
      link.classList.toggle('calma-hidden', hide);
    }
  }
  function scheduleScan() {
    if (dead || !active || timer) return;
    timer = requestAnimationFrame(() => { timer = 0; scan(); });
  }
  function scan() {
    if (dead || running) return;
    if (!active) { routeGuard(); return; }
    running = true;
    try {
      routeGuard();
      const relations = window.__calmaRelations || {};
      if (lastOwner !== relations.owner) { accepted.clear(); lastOwner = relations.owner; }
      const source = config.mode === 1 ? relations.following : relations.friends;
      if (source !== allowSource || config.mode !== allowMode) {
        allowSource = source; allowMode = config.mode;
        allow = new Set(source || []);
      }
      if (previousPath !== location.pathname) {
        previousPath = location.pathname;
        articleInfo = new WeakMap();
        document.querySelectorAll('.calma-hidden,.calma-extra').forEach(el => el.classList.remove('calma-hidden', 'calma-extra'));
        marker.remove();
        scanAllLinks = true;
      }
      if (config.reels) {
        if (reelPath(location.pathname) && !(window.__calmaReelGate && window.__calmaReelGate.locked())) { location.replace('https://www.instagram.com'+(config.allowedReelPath||'/')); return; }
        if (!location.pathname.startsWith('/direct/')) {
          if (scanAllLinks) filterLinks(document);
          else for (const root of changedLinks) if (root.isConnected) filterLinks(root);
        }
      }
      scanAllLinks = false; changedLinks.clear();
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
      // React may reuse a former suggestion container for the next feed page.
      main.querySelectorAll('.calma-extra').forEach(el => {
        if (el.querySelector('article')) el.classList.remove('calma-extra');
      });
      const rendered = new Set();
      for (const article of articles) {
        const info = postInfo(article);
        const ready = config.mode === 1 ? relations.followingReady : relations.friendsReady;
        const eligible = info && (!config.reels || (!info.reel && (!info.video || info.photos))) && (config.mode === 0 || (ready && [...info.authors].some(author => allow.has(author))));
        let show = false;
        if (eligible && !rendered.has(info.id)) {
          if (accepted.has(info.id)) show = true;
          else if (accepted.size < config.limit) { accepted.add(info.id); show = true; }
        }
        article.classList.toggle('calma-hidden', !show);
        article.classList.toggle('calma-approved', show);
        if ((!info || info.video) && (!show || config.reels)) article.querySelectorAll('video').forEach(video => {
          if (!video.paused) video.pause();
          if (video.hasAttribute('autoplay')) video.removeAttribute('autoplay');
        });
        if (show) rendered.add(info.id);
      }
      hideDiscovery(main);
      // Own status lives outside React's post list, never between a post and its sentinel.
      if (marker.parentElement !== main) main.appendChild(marker);
      setMessage();
    } finally { running = false; }
  }
  function setMessage() {
    let title, subtitle;
    const relations = window.__calmaRelations || {};
    const ready = config.mode === 1 ? relations.followingReady : relations.friendsReady;
    if (config.mode !== 0 && (!ready || (!allow.size && relations.phase === 'syncing'))) {
      title = relations.phase === 'error' ? 'No se pudo sincronizar' : 'Sincronizando';
      subtitle = relations.error || (relations.owner ? 'Cargando tus cuentas.' : 'Inicia sesión para continuar.');
    } else if (config.mode !== 0 && !allow.size) {
      title = config.mode === 1 ? 'Todavía no sigues a ninguna cuenta' : 'Sin seguimiento mutuo';
      subtitle = 'Sincroniza de nuevo desde los ajustes.';
    } else if (accepted.size >= config.limit) {
      title = 'Has llegado al límite de ' + config.limit + ' publicaciones';
      subtitle = 'Puedes iniciar otra sesión desde los ajustes.';
    } else {
      title = 'Más publicaciones';
      subtitle = accepted.size + ' de ' + config.limit + ' · Desliza para cargar más';
    }
    if (markerTitle.textContent !== title) markerTitle.textContent = title;
    if (markerSubtitle.textContent !== subtitle) markerSubtitle.textContent = subtitle;
  }
  function clickBlock(event) {
    const anchor = event.target.closest && event.target.closest('a[href]');
    if (config.reels && anchor && reelPath(path(anchor)) && !location.pathname.startsWith('/direct/')) { event.preventDefault(); event.stopImmediatePropagation(); }
  }
  function pauseReel(event) {
    if (!config.reels || !(event.target instanceof HTMLVideoElement)) return;
    if ((reelPath(location.pathname) && !(window.__calmaReelGate && window.__calmaReelGate.locked())) || event.target.closest('.calma-hidden,.calma-extra') || location.pathname==='/') event.target.pause();
  }
  function relationsChanged() { allowSource = null; scan(); }
  // Observe structure and link changes; ignore our own class writes to avoid loops.
  const observer = new MutationObserver(records => {
    // DM text, reactions and typing indicators never need a feed/link scan.
    // Navigation invalidates article metadata before a feed becomes visible.
    if (location.pathname.startsWith('/direct/')) return;
    let changed = false;
    for (const record of records) {
      if (record.target === marker || marker.contains(record.target)) continue;
      const target = record.target.nodeType === Node.ELEMENT_NODE ? record.target : record.target.parentElement;
      const article = target && target.closest('article');
      if (article) {
        articleInfo.delete(article);
        if (location.pathname === '/' && article.classList.contains('calma-approved')) article.classList.remove('calma-approved');
      }
      if (record.type === 'attributes') {
        if (record.attributeName === 'href') changedLinks.add(target);
      } else {
        for (const node of record.addedNodes) if (node.nodeType === Node.ELEMENT_NODE) changedLinks.add(node);
      }
      changed = true;
    }
    if (changed) scheduleScan();
  });
  function observe() {
    observer.observe(document.documentElement, {childList: true, subtree: true, attributes: true, attributeFilter: ['href', 'aria-label']});
  }
  function visibility(event) {
    active = event.detail ? event.detail.active !== false : window.CALMA_ACTIVE !== false;
    observer.disconnect(); cancelAnimationFrame(timer); timer = 0; changedLinks.clear();
    if (!active) return;
    articleInfo = new WeakMap(); scanAllLinks = true;
    observe(); scan();
  }
  if (active) observe();
  document.addEventListener('click', clickBlock, true);
  document.addEventListener('play', pauseReel, true);
  document.addEventListener('calma-relations', relationsChanged);
  document.addEventListener('calma-route', routeChange);
  document.addEventListener('calma-visibility', visibility);
  window.addEventListener('popstate', notifyRoute);
  window.__calma = {
    destroy() {
      dead = true; observer.disconnect(); cancelAnimationFrame(timer);
      document.removeEventListener('click', clickBlock, true); document.removeEventListener('play', pauseReel, true);
      document.removeEventListener('calma-relations', relationsChanged);
      document.removeEventListener('calma-route', routeChange); window.removeEventListener('popstate', notifyRoute);
      document.removeEventListener('calma-visibility', visibility);
      css.remove(); marker.remove();
      if(history.pushState===push)history.pushState=originalPush;if(history.replaceState===replace)history.replaceState=originalReplace;
      delete document.documentElement.dataset.calmaHome;
      delete document.documentElement.dataset.calmaReels;
      document.querySelectorAll('.calma-approved').forEach(el=>el.classList.remove('calma-approved'));
      document.querySelectorAll('.calma-hidden,.calma-extra').forEach(el => el.classList.remove('calma-hidden', 'calma-extra'));
      delete window.__calma;
    },
    scan() { articleInfo = new WeakMap(); scanAllLinks = true; scan(); },
    count: () => accepted.size
  };
  scan();
})();
