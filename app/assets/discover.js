(() => {
  'use strict';
  if (window.__calmaDiscover) window.__calmaDiscover.destroy();
  const searchSelector = 'input[type="search"],input[placeholder="Buscar"],input[placeholder="Search"],input[aria-label="Buscar"],input[aria-label="Search"]';
  const searchContainer = '[role="dialog"],[role="search"]';
  const postPattern = /^\/(?:([a-z0-9._]{1,30})\/)?(p|reel)\/([A-Za-z0-9_-]{1,24})\/?$/i;
  const reserved = new Set(['accounts','direct','explore','reels','reel','p','stories','about','legal','privacy']);
  let active = window.CALMA_ACTIVE !== false, dead = false, frame = 0, owner = '', epoch = 0, stopped = false;
  let controller = null, busy = false, requests = 0, pace = 0;
  const metadata = new Map(), pending = new Set(), queue = [], scopes = new Set(), filtered = new Set();
  const style = document.createElement('style');
  style.textContent = `
    html[data-calma-discover=true] main img,html[data-calma-discover=true] [role=main] img,html[data-calma-discover=true] main video,html[data-calma-discover=true] [role=main] video,
    html[data-calma-discover=true] main article,html[data-calma-discover=true] [role=main] article,
    html[data-calma-discover=true] main a[href*="/p/"],html[data-calma-discover=true] main a[href*="/reel/"],
    html[data-calma-discover=true] [role=main] a[href*="/p/"],html[data-calma-discover=true] [role=main] a[href*="/reel/"]{visibility:hidden!important}
    html[data-calma-discover=true] main .calma-discover-approved img,html[data-calma-discover=true] [role=main] .calma-discover-approved img,html[data-calma-discover=true] main .calma-discover-approved video,html[data-calma-discover=true] [role=main] .calma-discover-approved video,
    html[data-calma-discover=true] main a.calma-discover-approved,html[data-calma-discover=true] [role=main] a.calma-discover-approved,html[data-calma-discover=true] article.calma-discover-approved{visibility:visible!important}
    [data-calma-search-scope] a[href]{visibility:hidden!important}
    [data-calma-search-scope] a.calma-discover-profile,[data-calma-search-scope] a.calma-discover-approved{visibility:visible!important}
    html[data-calma-home=true] :is(main,[role=main]) [data-calma-search-scope] a.calma-discover-approved{visibility:visible!important}
    .calma-discover-pending{visibility:hidden!important;pointer-events:none!important}
    .calma-discover-rejected{display:none!important}
    html[data-calma-discover=true][data-calma-reels=true] main .calma-discover-approved video,html[data-calma-discover=true][data-calma-reels=true] [role=main] .calma-discover-approved video,html[data-calma-discover=true][data-calma-reels=true] video{visibility:hidden!important}
    #calma-discover-status{color:var(--calma-muted,#737373);font:13px/1.5 system-ui;text-align:center;padding:24px 16px}
    #calma-discover-status[hidden]{display:none!important}
  `;
  (document.head || document.documentElement).appendChild(style);
  const status = document.createElement('p');status.id='calma-discover-status';status.setAttribute('role','status');
  function exploring(){return /^\/explore(?:\/|$)/.test(location.pathname);}
  function path(link){try{const u=new URL(link.href,location.href);return u.origin===location.origin?u.pathname:'';}catch(_){return '';}}
  function guard(){const value=String(exploring() && !document.querySelector('input[type=password]'));if(document.documentElement.dataset.calmaDiscover!==value)document.documentElement.dataset.calmaDiscover=value;}
  function profile(p){const m=p.match(/^\/([a-z0-9._]{1,30})\/?$/i);return m && !reserved.has(m[1].toLowerCase())?m[1].toLowerCase():'';}
  function reset(){epoch++;if(controller)controller.abort();clearTimeout(pace);pace=0;controller=null;busy=false;requests=0;stopped=false;metadata.clear();pending.clear();queue.length=0;}
  function clear(el){el.classList.remove('calma-discover-approved','calma-discover-profile','calma-discover-rejected','calma-discover-pending');}
  function approve(link, allowed, waiting=false){
    filtered.add(link);
    link.classList.toggle('calma-discover-approved',allowed);
    link.classList.toggle('calma-discover-rejected',!allowed && !waiting);
    link.classList.toggle('calma-discover-pending',waiting);
    const card=link.closest('article');
    if(card && postPattern.test(path(link))){filtered.add(card);card.classList.toggle('calma-discover-approved',allowed);card.classList.toggle('calma-discover-rejected',!allowed && !waiting);}
    if(!allowed || window.CALMA_CONFIG.reels) (card || link).querySelectorAll('video').forEach(v=>{if(!v.paused)v.pause();});
  }
  function mediaId(code){
    const alphabet='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
    let value=0n;for(const ch of code)value=value*64n+BigInt(alphabet.indexOf(ch));
    return value.toString();
  }
  const intersection = new IntersectionObserver(entries=>{
    for(const entry of entries){
      if(!entry.isIntersecting || !active)continue;
      const link=entry.target,m=path(link).match(postPattern);
      if(!m || metadata.has(m[3]) || pending.has(m[3]))continue;
      pending.add(m[3]);queue.push(m[3]);
    }
    pump();
  },{rootMargin:'240px'});
  async function pump(){
    if(dead || !active || busy || pace || stopped || !scopes.size || !queue.length || !window.__calmaRelations?.followingReady)return;
    if(requests>=80){stopped=true;scan();return;}
    const code=queue.shift(),token=epoch,account=owner;
    busy=true;requests++;controller=new AbortController();const abort=controller;
    let timedOut=false;const timeout=setTimeout(()=>{timedOut=true;abort.abort();},12000);
    try{
      const response=await fetch('/api/v1/media/'+mediaId(code)+'/info/',{credentials:'same-origin',headers:{'X-IG-App-ID':'936619743392459','Accept':'application/json'},signal:abort.signal});
      if(!response.ok)throw new Error('rejected');
      const data=await response.json(),item=data.items && data.items[0];
      if(!item || (data.status && data.status!=='ok') || (item.code && item.code!==code) || !item.user || !/^[a-z0-9._]{1,30}$/i.test(item.user.username || ''))throw new Error('unexpected');
      const users=[item.user,...(Array.isArray(item.coauthor_producers)?item.coauthor_producers:[])].map(u=>u.username).filter(u=>typeof u==='string' && /^[a-z0-9._]{1,30}$/i.test(u)).map(u=>u.toLowerCase());
      if(token===epoch && account===owner)metadata.set(code,users);
    }catch(error){if(token===epoch && account===owner && (error.name!=='AbortError' || timedOut))stopped=true;}
    finally{
      clearTimeout(timeout);
      if(token===epoch){busy=false;controller=null;pending.delete(code);pace=setTimeout(()=>{pace=0;pump();},450);scan();}
    }
  }
  function scan(){
    if(dead)return;guard();if(!active)return;
    const state=window.__calmaRelations || {};
    if(owner!==(state.owner || '')){owner=state.owner || '';reset();}
    const following=new Set(state.following || []);
    intersection.disconnect();
    for(const scope of scopes)if(scope.hasAttribute('data-calma-search-scope'))scope.removeAttribute('data-calma-search-scope');
    scopes.clear();
    if(exploring() && !document.querySelector('input[type=password]')){
      const main=document.querySelector('main,[role=main]');if(main)scopes.add(main);
    }
    if(exploring() || location.pathname==='/')document.querySelectorAll(searchSelector).forEach(input=>{
      const scope=input.closest('[role=dialog],[role=search]');if(scope){scope.setAttribute('data-calma-search-scope','');scopes.add(scope);}
    });
    for(const el of filtered){if(!el.isConnected || ![...scopes].some(scope=>scope.contains(el))){clear(el);filtered.delete(el);}}
    let shown=0,waiting=false;
    for(const scope of scopes){
      for(const link of scope.querySelectorAll('a[href]')){
        const p=path(link),match=p.match(postPattern),user=profile(p);
        if(!match){
          if(user){const show=!!owner && state.followingReady && following.has(user);link.classList.toggle('calma-discover-profile',show);approve(link,show,!state.followingReady);}
          continue;
        }
        const names=match[1]?[match[1].toLowerCase()]:metadata.get(match[3]);
        const blocked=window.CALMA_CONFIG.reels && match[2].toLowerCase()==='reel';
        const show=!!owner && !!state.followingReady && !blocked && !!names && names.some(name=>following.has(name));
        const checking=!blocked && (!state.followingReady || !names);
        approve(link,show,checking);if(show)shown++;
        if(checking){waiting=true;if(owner && state.followingReady && !names && !stopped)intersection.observe(link);}
      }
    }
    if(exploring() && scopes.size){
      const main=document.querySelector('main,[role=main]');if(main && status.parentElement!==main)main.appendChild(status);
      status.hidden=shown>0;
      const text=stopped?'No se pudo comprobar este contenido.':waiting?'Cargando…':'Sin publicaciones de cuentas que sigues.';
      if(status.textContent!==text)status.textContent=text;
    }else status.remove();
    if(!scopes.size){if(controller)controller.abort();queue.length=0;pending.clear();}
    observe();
  }
  function schedule(){if(active && !frame)frame=requestAnimationFrame(()=>{frame=0;scan();});}
  function route(){guard();scan();}
  function visibility(){active=window.CALMA_ACTIVE!==false;observer.disconnect();cancelAnimationFrame(frame);frame=0;if(!active){intersection.disconnect();if(controller)controller.abort();}else scan();}
  const observer=new MutationObserver(records=>{
    let changed=false;
    const explore=exploring();
    for(const record of records){
      if(record.target===status || status.contains(record.target))continue;
      const inScope=[...scopes].some(scope=>scope.contains(record.target));
      if(record.type==='attributes'){
        if(!explore && !inScope)continue;
        record.target.classList.remove('calma-discover-approved','calma-discover-profile');
        const card=record.target.closest('article');if(card)card.classList.remove('calma-discover-approved');
      }
      if(explore || inScope){changed=true;continue;}
      // On Home only watch for search panels. Incoming feed posts and reactions
      // need no discovery filtering, and DMs have no observer at all.
      if(record.target.closest?.(searchContainer) ||
        [...record.addedNodes].some(n=>n.nodeType===1 && (n.matches(searchContainer) || n.querySelector(searchContainer))) ||
        [...record.removedNodes].some(n=>n.nodeType===1 && [...scopes].some(scope=>n.contains(scope))))changed=true;
    }
    if(changed)schedule();
  });
  function observe(){
    observer.disconnect();
    if(!active || dead || (!exploring() && location.pathname!=='/'))return;
    const options={subtree:true,childList:true};
    if(exploring() || scopes.size){options.attributes=true;options.attributeFilter=['href'];}
    observer.observe(document.documentElement,options);
  }
  function click(event){const link=event.target.closest?.('a[href]');if(link && [...scopes].some(scope=>scope.contains(link)) && (postPattern.test(path(link)) || profile(path(link))) && !link.classList.contains('calma-discover-approved')){event.preventDefault();event.stopImmediatePropagation();}}
  function play(event){const media=event.target;if(media instanceof HTMLMediaElement && [...scopes].some(scope=>scope.contains(media)) && (window.CALMA_CONFIG.reels || !media.closest('.calma-discover-approved')))media.pause();}
  document.addEventListener('play',play,true);document.addEventListener('click',click,true);document.addEventListener('calma-route',route);document.addEventListener('calma-relations',scan);document.addEventListener('calma-visibility',visibility);
  window.__calmaDiscover={destroy(){dead=true;reset();observer.disconnect();intersection.disconnect();cancelAnimationFrame(frame);style.remove();status.remove();document.removeEventListener('play',play,true);document.removeEventListener('click',click,true);document.removeEventListener('calma-route',route);document.removeEventListener('calma-relations',scan);document.removeEventListener('calma-visibility',visibility);delete document.documentElement.dataset.calmaDiscover;document.querySelectorAll('.calma-discover-approved,.calma-discover-profile,.calma-discover-rejected,.calma-discover-pending').forEach(el=>el.classList.remove('calma-discover-approved','calma-discover-profile','calma-discover-rejected','calma-discover-pending'));document.querySelectorAll('[data-calma-search-scope]').forEach(el=>el.removeAttribute('data-calma-search-scope'));delete window.__calmaDiscover;}};
  scan();
})();
