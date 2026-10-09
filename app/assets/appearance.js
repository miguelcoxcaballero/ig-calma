(() => {
  'use strict';
  if (window.__calmaAppearance) { window.__calmaAppearance.refresh(); return; }
  const root = document.documentElement;
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  let previousPath = location.pathname, frame = 0, activeAnimation = null, pressed = null;
  const style = document.createElement('style'); style.id = 'calma-appearance-style';
  style.textContent = `
    html[data-calma-theme="light"],html[data-calma-theme="light"] body {
      --ig-primary-background:255,255,255!important;--ig-secondary-background:250,250,250!important;
      --ig-elevated-background:255,255,255!important;--ig-highlight-background:239,239,239!important;
      --ig-primary-text:38,38,38!important;--ig-secondary-text:115,115,115!important;
      --ig-tertiary-text:199,199,199!important;--ig-separator:219,219,219!important;
      --ig-stroke:219,219,219!important;--ig-elevated-separator:219,219,219!important;
      --calma-background:#fff;--calma-text:#262626;--calma-muted:#737373;--calma-border:#dbdbdb;--calma-field:#fafafa;
    }
    html[data-calma-theme="dark"],html[data-calma-theme="dark"] body {
      --ig-primary-background:0,0,0!important;--ig-secondary-background:18,18,18!important;
      --ig-elevated-background:38,38,38!important;--ig-highlight-background:38,38,38!important;
      --ig-primary-text:245,245,245!important;--ig-secondary-text:168,168,168!important;
      --ig-tertiary-text:115,115,115!important;--ig-separator:38,38,38!important;
      --ig-stroke:54,54,54!important;--ig-elevated-separator:54,54,54!important;
      --calma-background:#000;--calma-text:#f5f5f5;--calma-muted:#a8a8a8;--calma-border:#363636;--calma-field:#121212;
    }
    html[data-calma-theme],html[data-calma-theme] body {
      background-color:var(--calma-background)!important;color:var(--calma-text)!important;
      -webkit-text-size-adjust:100%;overscroll-behavior-y:none;
    }
    html[data-calma-theme] body {margin:0;-webkit-tap-highlight-color:transparent}
    html[data-calma-theme] :is(button,[role="button"],a){touch-action:manipulation}
    #calma-settings {color:var(--calma-text);background:var(--calma-background)}
    #calma-settings :is(button,input):focus-visible{outline:2px solid #0095f6;outline-offset:3px}
    #calma-end{background:var(--calma-background)!important;color:var(--calma-text)!important;border-color:var(--calma-border)!important}
    html[data-calma-motion="full"] .calma-pressed{opacity:.72!important}
    html[data-calma-motion="full"] #calma-settings :is(button,input){transition:background-color 140ms ease,border-color 140ms ease,opacity 100ms ease}
    html[data-calma-motion="reduced"] #calma-settings *,html[data-calma-motion="reduced"] #calma-settings *::before,html[data-calma-motion="reduced"] #calma-settings *::after{transition:none!important;animation:none!important}
    @media(prefers-reduced-motion:reduce){#calma-settings *,#calma-settings *::before,#calma-settings *::after{transition:none!important;animation:none!important}.calma-pressed{opacity:1!important}}
  `;
  (document.head || root).appendChild(style);
  function isActive() { return window.CALMA_ACTIVE !== false && !document.hidden; }
  function motionOff() { return !!(window.CALMA_APPEARANCE || {}).reduceMotion || reduced.matches; }
  function theme() {
    const dark = !!(window.CALMA_APPEARANCE || {}).dark, name = dark ? 'dark' : 'light';
    if (root.dataset.calmaTheme !== name) root.dataset.calmaTheme = name;
    if (root.style.colorScheme !== name) root.style.colorScheme = name;
    if (root.classList.contains('__fb-dark-mode') !== dark) root.classList.toggle('__fb-dark-mode', dark);
    if (root.classList.contains('__fb-light-mode') === dark) root.classList.toggle('__fb-light-mode', !dark);
    const motion = motionOff() ? 'reduced' : 'full';
    if (root.dataset.calmaMotion !== motion) root.dataset.calmaMotion = motion;
    if (motion === 'reduced') { release(); if (activeAnimation) activeAnimation.cancel(); }
  }
  function animateNavigation() {
    const path = location.pathname;
    if (path === previousPath) return;
    previousPath = path;
    if (motionOff() || !isActive() || document.querySelector('input[type="password"]')) return;
    const main = document.querySelector('main,[role="main"]');
    const focused = document.activeElement;
    if (!main || !main.animate || (focused && focused.matches('input,textarea,[contenteditable="true"]'))) return;
    if (activeAnimation) activeAnimation.cancel();
    // Instagram already moves its panels. Only fade once per route; never move the
    // entire feed or replay a transition as messages and photos arrive.
    activeAnimation = main.animate([{opacity:.9},{opacity:1}], {duration:110,easing:'ease-out'});
  }
  function refresh() { theme(); animateNavigation(); }
  function schedule() { if (isActive() && !frame) frame = requestAnimationFrame(() => { frame = 0; refresh(); }); }
  function visibility() {
    if (!isActive()) {
      observer.disconnect(); cancelAnimationFrame(frame); frame = 0;
      release(); if (activeAnimation) activeAnimation.cancel();
      return;
    }
    observeTheme(); refresh();
  }
  function release() { if (pressed) pressed.classList.remove('calma-pressed'); pressed = null; }
  function press(event) {
    release();
    if (motionOff() || !event.isPrimary || event.button !== 0) return;
    const target = event.target.closest && event.target.closest('button,[role="button"],a');
    if (!target || target.matches(':disabled,[aria-disabled="true"],input,textarea,select,[contenteditable="true"]') || target.querySelector('img,video,input,textarea,[contenteditable="true"]')) return;
    pressed = target; target.classList.add('calma-pressed');
  }
  const observer = new MutationObserver(schedule);
  function observeTheme() { observer.observe(root, {attributes:true,attributeFilter:['class','style','data-calma-theme']}); }
  if (isActive()) observeTheme();
  document.addEventListener('pointerdown', press, true);
  document.addEventListener('pointerup', release, true);
  document.addEventListener('pointercancel', release, true);
  window.addEventListener('blur', release); window.addEventListener('popstate', schedule);
  document.addEventListener('calma-route', schedule);
  document.addEventListener('calma-visibility', visibility);
  document.addEventListener('visibilitychange', visibility);
  reduced.addEventListener('change', schedule);
  window.__calmaAppearance = {
    refresh,
    destroy() {
      observer.disconnect(); cancelAnimationFrame(frame);
      document.removeEventListener('pointerdown',press,true); document.removeEventListener('pointerup',release,true);document.removeEventListener('pointercancel',release,true);
      window.removeEventListener('blur',release);window.removeEventListener('popstate',schedule);reduced.removeEventListener('change',schedule);
      document.removeEventListener('calma-route',schedule);document.removeEventListener('calma-visibility',visibility);document.removeEventListener('visibilitychange',visibility);
      release();if(activeAnimation)activeAnimation.cancel();style.remove();delete window.__calmaAppearance;
    }
  };
  refresh();
})();
