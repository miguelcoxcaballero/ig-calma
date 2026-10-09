(() => {
  'use strict';
  if(window.__calmaReelGate)window.__calmaReelGate.destroy();
  const config=window.CALMA_CONFIG;
  let ticket=null, primary=null;
  const normalize=p=>p.endsWith('/')?p:p+'/';
  function reel(p){return /^\/reel\/[^/]+\/?$/.test(p);}
  function locked(){return !!config.reels && !!config.allowedReelPath && normalize(location.pathname)===config.allowedReelPath;}
  function linkPath(a){try{const u=new URL(a.href,location.href);return /(^|\.)instagram\.com$/.test(u.hostname)?u.pathname:'';}catch(_){return '';}}
  function username(a){const m=linkPath(a).match(/^\/([a-z0-9._]{1,30})\/?$/i);return m?m[1].toLowerCase():'';}
  function sender(anchor){
    const message=anchor.closest('[data-message-id],[role="row"],article');
    const explicit=message && [...message.querySelectorAll('a[href]')].map(username).filter(Boolean);
    if(explicit && explicit.length===1)return explicit[0];
    // One-to-one thread header only. Ambiguous/group messages are never assumed to be friends.
    const headers=[...document.querySelectorAll('main header a[href],[role="main"] header a[href]')].map(username).filter(Boolean);
    const unique=[...new Set(headers)];return unique.length===1?unique[0]:'';
  }
  function click(event){
    if(!config.reels)return;
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
    }else if(locked() && normalize(path)!==config.allowedReelPath){event.preventDefault();event.stopImmediatePropagation();}
  }
  function block(event){
    if(!locked())return;
    if(event.type==='keydown' && !['ArrowDown','ArrowUp','PageDown','PageUp','Home','End'].includes(event.key))return;
    event.preventDefault();event.stopImmediatePropagation();
  }
  function enforce(){
    if(config.reels && config.allowedReelPath && reel(location.pathname) && normalize(location.pathname)!==config.allowedReelPath){location.replace('https://www.instagram.com'+config.allowedReelPath);return;}
    const isLocked=locked();document.documentElement.dataset.calmaReelLocked=String(isLocked);
    if(!isLocked){primary=null;return;}
    const main=document.querySelector('main,[role="main"]');if(!main)return;
    const videos=[...main.querySelectorAll('video')];
    if(!primary || !primary.isConnected)primary=videos[0]||null;
    videos.forEach(video=>{
      if(video===primary)return;
      video.pause();video.classList.add('calma-other-reel');
      const card=video.closest('article');if(card && !card.contains(primary))card.classList.add('calma-other-reel');
    });
  }
  const style=document.createElement('style');style.textContent='html[data-calma-reel-locked="true"],html[data-calma-reel-locked="true"] body,html[data-calma-reel-locked="true"] main{overflow:hidden!important;overscroll-behavior:none!important}html[data-calma-reel-locked="true"] .calma-other-reel{display:none!important}';
  (document.head||document.documentElement).appendChild(style);
  const observer=new MutationObserver(enforce);observer.observe(document.documentElement,{childList:true,subtree:true});
  const interval=setInterval(enforce,400);
  function play(event){if(locked() && event.target instanceof HTMLVideoElement && event.target!==primary)event.target.pause();}
  document.addEventListener('click',click,true);
  document.addEventListener('play',play,true);
  const options={capture:true,passive:false};document.addEventListener('wheel',block,options);document.addEventListener('touchmove',block,options);document.addEventListener('keydown',block,true);
  window.__calmaReelGate={locked,consume(path){const ok=!!ticket && location.pathname.startsWith('/direct/t/') && ticket.path===path && ticket.until>=Date.now();ticket=null;return ok;},
    destroy(){observer.disconnect();clearInterval(interval);document.removeEventListener('click',click,true);document.removeEventListener('play',play,true);document.removeEventListener('wheel',block,true);document.removeEventListener('touchmove',block,true);document.removeEventListener('keydown',block,true);style.remove();document.querySelectorAll('.calma-other-reel').forEach(e=>e.classList.remove('calma-other-reel'));delete document.documentElement.dataset.calmaReelLocked;delete window.__calmaReelGate;}
  };
  enforce();
})();
