(() => {
  'use strict';
  if(window.__calmaReelGate)window.__calmaReelGate.destroy();
  const config=window.CALMA_CONFIG;
  let ticket=null, primary=null, primaryCard=null, selected=false, activePath='', observing=false, queued=false, destroyed=false;
  const marked=new Set(), root=document.documentElement;
  const normalize=p=>p.endsWith('/')?p:p+'/';
  const active=()=>window.CALMA_ACTIVE!==false && !document.hidden;
  function reel(p){return /^\/reel\/[^/]+\/?$/.test(p);}
  function locked(){return !!config.reels && !!config.allowedReelPath && normalize(location.pathname)===normalize(config.allowedReelPath);}
  function linkPath(a){try{const u=new URL(a.href,location.href);return /(^|\.)instagram\.com$/.test(u.hostname)?u.pathname:'';}catch(_){return '';}}
  function username(a){const m=linkPath(a).match(/^\/([a-z0-9._]{1,30})\/?$/i);return m?m[1].toLowerCase():'';}
  function sender(anchor){
    const message=anchor.closest('[data-message-id],[role="row"],article');
    const explicit=message && [...new Set([...message.querySelectorAll('a[href]')].map(username).filter(Boolean))];
    if(explicit && explicit.length===1)return explicit[0];
    // One-to-one thread header only. Ambiguous/group messages are never assumed to be friends.
    const headers=[...document.querySelectorAll('main header a[href],[role="main"] header a[href]')].map(username).filter(Boolean);
    const unique=[...new Set(headers)];return unique.length===1?unique[0]:'';
  }
  function click(event){
    if(!active() || !config.reels)return;
    const anchor=event.target.closest && event.target.closest('a[href]');
    if(!anchor)return;
    const path=linkPath(anchor);
    if(!reel(path))return;
    if(location.pathname.startsWith('/direct/t/')){
      const state=window.__calmaRelations||{}, author=sender(anchor);
      if(author && state.friendsReady && state.friends.includes(author))ticket={path:normalize(path),until:Date.now()+30000};
      else ticket=null;
      event.preventDefault();event.stopImmediatePropagation();
      location.assign(new URL(anchor.href,location.href).href);
    }else if(locked() && normalize(path)!==normalize(config.allowedReelPath)){event.preventDefault();event.stopImmediatePropagation();}
  }
  function block(event){
    if(!active() || !locked())return;
    if(event.type==='keydown'){
      if(!['ArrowDown','ArrowUp','PageDown','PageUp','Home','End'].includes(event.key))return;
      const target=event.target;
      if(target.closest && target.closest('input,textarea,[contenteditable="true"],button,[role="button"]'))return;
    }
    event.preventDefault();event.stopImmediatePropagation();
  }
  function resetSelection(){
    if(primary)primary.removeAttribute('data-calma-primary-reel');
    primary=null;primaryCard=null;selected=false;
    marked.forEach(element=>element.classList.remove('calma-other-reel'));marked.clear();
  }
  function mark(element){if(!marked.has(element)){element.classList.add('calma-other-reel');marked.add(element);}}
  function enforce(){
    if(destroyed || !active() || !locked())return;
    const main=document.querySelector('main,[role="main"]');if(!main)return;
    const videos=[...main.querySelectorAll('video')];
    if(!primary || !primary.isConnected){
      // A removed first video must never promote Instagram's next recommendation.
      // React may replace the player inside the same post; that replacement is safe.
      const replacement=selected ? videos.find(video=>primaryCard && primaryCard.isConnected && primaryCard.contains(video)) : videos[0];
      if(replacement){
        if(primary)primary.removeAttribute('data-calma-primary-reel');
        primary=replacement;primaryCard=primary.closest('article');selected=true;
        primary.classList.remove('calma-other-reel');marked.delete(primary);
        primary.setAttribute('data-calma-primary-reel','true');
      }
    }
    videos.forEach(video=>{
      if(video===primary)return;
      video.pause();mark(video);
      const card=video.closest('article');if(card && !card.contains(primary))mark(card);
    });
  }
  const style=document.createElement('style');style.textContent='html[data-calma-reel-locked="true"],html[data-calma-reel-locked="true"] body,html[data-calma-reel-locked="true"] main{overflow:hidden!important;overscroll-behavior:none!important}html[data-calma-reel-locked="true"] .calma-other-reel{display:none!important}html[data-calma-reel-locked="true"] :is(main,[role="main"]) video:not([data-calma-primary-reel]){visibility:hidden!important}';
  (document.head||root).appendChild(style);
  const observer=new MutationObserver(records=>{
    if(queued || !records.some(record=>[...record.addedNodes,...record.removedNodes].some(node=>node.nodeType===1 && (node.matches('video,main,[role="main"]') || node.querySelector('video,main,[role="main"]')))))return;
    queued=true;queueMicrotask(()=>{queued=false;enforce();});
  });
  const options={capture:true,passive:false};
  function observe(enabled){
    if(observing===enabled)return;
    observing=enabled;
    if(enabled){
      observer.observe(root,{childList:true,subtree:true});
      document.addEventListener('wheel',block,options);document.addEventListener('touchmove',block,options);document.addEventListener('keydown',block,true);
    }else{
      observer.disconnect();
      document.removeEventListener('wheel',block,true);document.removeEventListener('touchmove',block,true);document.removeEventListener('keydown',block,true);
    }
  }
  function route(){
    if(config.reels && config.allowedReelPath && reel(location.pathname) && normalize(location.pathname)!==normalize(config.allowedReelPath)){location.replace('https://www.instagram.com'+normalize(config.allowedReelPath));return;}
    const isLocked=locked(), path=isLocked?normalize(location.pathname):'';
    if(activePath!==path){resetSelection();activePath=path;}
    const state=String(isLocked);if(root.dataset.calmaReelLocked!==state)root.dataset.calmaReelLocked=state;
    if(!location.pathname.startsWith('/direct/t/'))ticket=null;
    observe(isLocked && active());
    if(!active() && primary)primary.pause();
    if(isLocked && active())enforce();
  }
  function play(event){
    if(!(event.target instanceof HTMLVideoElement) || !locked())return;
    if(!active() || event.target!==primary)event.target.pause();
  }
  document.addEventListener('click',click,true);document.addEventListener('play',play,true);
  document.addEventListener('calma-route',route);document.addEventListener('calma-visibility',route);document.addEventListener('visibilitychange',route);window.addEventListener('popstate',route);
  window.__calmaReelGate={locked,consume(path){const ok=!!ticket && location.pathname.startsWith('/direct/t/') && ticket.path===normalize(path) && ticket.until>=Date.now();ticket=null;return ok;},
    destroy(){destroyed=true;observe(false);document.removeEventListener('click',click,true);document.removeEventListener('play',play,true);document.removeEventListener('calma-route',route);document.removeEventListener('calma-visibility',route);document.removeEventListener('visibilitychange',route);window.removeEventListener('popstate',route);style.remove();resetSelection();delete root.dataset.calmaReelLocked;delete window.__calmaReelGate;}
  };
  route();
})();
