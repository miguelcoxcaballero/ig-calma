(() => {
  'use strict';
  if (window.__calmaSettings) { window.__calmaSettings.refresh(); return; }
  const paths = new Set(['/accounts/edit', '/accounts/edit/', '/accounts/settings', '/accounts/settings/', '/settings', '/settings/']);
  let section = null, timer = 0, observing = false, configKey = '', draft = null;
  const styles = document.createElement('style'); styles.id = 'calma-settings-style';
  // All controls stay in the settings document. Pickers and switches use the page's theme.
  styles.textContent = `
    #calma-settings{box-sizing:border-box;border-top:1px solid var(--calma-border,#dbdbdb);padding:28px 20px 20px;margin:20px 0 0;font:inherit;color:var(--calma-text,#262626);background:var(--calma-background,#fff);line-height:1.4}
    #calma-settings *{box-sizing:border-box}#calma-settings [hidden]{display:none!important}
    #calma-settings h2{font-size:22px;letter-spacing:-.3px;font-weight:650;margin:0 0 22px}
    #calma-settings h3,#calma-settings legend{font-size:16px;font-weight:600;margin:0;padding:0}
    #calma-settings fieldset{border:0;margin:0;padding:0;min-width:0}
    #calma-settings .calma-group{padding:22px 0;border-bottom:1px solid var(--calma-border,#dbdbdb)}
    #calma-settings .calma-group:first-of-type{padding-top:0}
    #calma-settings .calma-row{display:flex;align-items:center;justify-content:space-between;gap:16px;min-height:52px}
    #calma-settings .calma-copy{display:block;min-width:0;font-size:15px}
    #calma-settings .calma-detail,#calma-settings p{display:block;font-size:12px;line-height:1.5;color:var(--calma-muted,#737373);margin:4px 0 0}
    #calma-settings .calma-choice{cursor:pointer;padding:8px 0;min-height:56px}
    #calma-settings .calma-choice:active{opacity:.65}
    #calma-settings input[type=radio]{appearance:none;-webkit-appearance:none;width:22px;height:22px;flex:none;border:1.5px solid var(--calma-muted,#737373);border-radius:50%;background:transparent;margin:0}
    #calma-settings input[type=radio]:checked{border:6px solid var(--calma-text,#262626)}
    #calma-settings input[type=checkbox]{appearance:none;-webkit-appearance:none;display:grid;align-items:center;width:44px;height:26px;flex:none;border:0;border-radius:20px;background:var(--calma-border,#dbdbdb);margin:0;cursor:pointer;transition:background-color 160ms ease}
    #calma-settings input[type=checkbox]::before{content:'';display:block;width:22px;height:22px;border-radius:50%;background:#fff;box-shadow:0 1px 3px #0002;margin:2px;transform:translateX(0);transition:transform 160ms ease}
    #calma-settings input[type=checkbox]:checked{background:var(--calma-text,#262626)}
    #calma-settings input[type=checkbox]:checked::before{transform:translateX(18px);background:var(--calma-background,#fff)}
    #calma-settings button{appearance:none;-webkit-appearance:none;font:inherit;font-size:14px;font-weight:600;cursor:pointer;border:0;background:transparent;color:var(--calma-text,#262626);padding:0;margin:0;touch-action:manipulation}
    #calma-settings button:disabled{opacity:.35;cursor:default}
    #calma-settings button:active:not(:disabled){opacity:.6}
    #calma-settings :is(button,input):focus-visible{outline:2px solid #0095f6;outline-offset:4px}
    #calma-settings .calma-link{color:#0095f6;min-height:44px;text-align:left}
    #calma-settings .calma-stepper{display:flex;align-items:center;flex:none;border:1px solid var(--calma-border,#dbdbdb);border-radius:10px;overflow:hidden}
    #calma-settings .calma-stepper button{width:36px;height:44px;font-size:23px;font-weight:400}
    #calma-settings #calma-limit{appearance:none;-webkit-appearance:none;width:38px;min-width:0;border:0;padding:10px 0;margin:0;background:transparent;color:inherit;font:inherit;font-size:15px;text-align:center;border-radius:0}
    #calma-settings #calma-limit[aria-invalid=true]{color:#ed4956}
    #calma-settings #calma-limit-error{color:#ed4956}
    #calma-settings .calma-actions{display:flex;align-items:center;gap:16px;padding-top:24px;min-height:64px}
    #calma-settings button[data-action=save]{min-height:44px;border-radius:10px;background:#0095f6;color:#fff;padding:10px 24px}
    #calma-settings [role=status]{font-size:12px;color:var(--calma-muted,#737373)}
    #calma-settings .calma-footer{font-size:11px;margin-top:20px;color:var(--calma-muted,#737373)}
    #calma-settings .calma-chevron{font-size:22px;font-weight:400;color:var(--calma-muted,#737373)}
    #calma-settings .calma-update{width:100%;text-align:left;font-size:15px}
    @media(prefers-reduced-motion:reduce){#calma-settings *{transition:none!important}}
    html[data-calma-motion=reduced] #calma-settings *{transition:none!important}
  `;
  (document.head || document.documentElement).appendChild(styles);
  const markup = '<h2>Tu feed</h2>' +
    '<div class="calma-group"><fieldset id="calma-mode"><legend>Mostrar publicaciones de</legend>' +
    '<label class="calma-row calma-choice"><span class="calma-copy">Todas las cuentas</span><input type="radio" name="calma-feed-mode" value="0"></label>' +
    '<label class="calma-row calma-choice"><span class="calma-copy">Cuentas que sigues</span><input type="radio" name="calma-feed-mode" value="1"></label>' +
    '<label class="calma-row calma-choice"><span class="calma-copy">Amigos<span class="calma-detail">Os seguís mutuamente</span></span><input type="radio" name="calma-feed-mode" value="2"></label></fieldset>' +
    '<div class="calma-row"><span id="calma-sync" role="status" aria-live="polite"></span><button class="calma-link" type="button" data-action="sync">Sincronizar</button></div></div>' +
    '<div class="calma-group"><label class="calma-row" for="calma-reels"><span class="calma-copy">Ocultar Reels</span><input id="calma-reels" type="checkbox" role="switch" aria-describedby="calma-reels-info"></label>' +
    '<p id="calma-reels-info">Puedes abrir los que te envíen tus amigos por DM, sin pasar al siguiente.</p></div>' +
    '<div class="calma-group"><div class="calma-row"><label class="calma-copy" for="calma-limit">Publicaciones por sesión</label>' +
    '<div class="calma-stepper"><button type="button" data-step="-1" aria-label="Ver una publicación menos">−</button><input id="calma-limit" type="text" inputmode="numeric" pattern="[0-9]*" maxlength="3" autocomplete="off" aria-describedby="calma-limit-info calma-limit-error"><button type="button" data-step="1" aria-label="Ver una publicación más">+</button></div></div>' +
    '<p id="calma-limit-info">El inicio termina al alcanzar este límite.</p><p id="calma-limit-error" role="status" hidden>Elige un número entre 1 y 100.</p>' +
    '<button class="calma-link" type="button" data-action="new_session">Empezar otra sesión</button></div>' +
    '<div class="calma-group"><label class="calma-row" for="calma-dm"><span class="calma-copy">Notificaciones de mensajes</span><input id="calma-dm" type="checkbox" role="switch" aria-describedby="calma-dm-info"></label>' +
    '<p id="calma-dm-info">Requiere Instagram oficial con la misma cuenta y sus avisos activados. Puedes recibir notificaciones en ambas apps.</p>' +
    '<div id="calma-permission-row" class="calma-row" hidden><span id="calma-permissions" role="status"></span><button class="calma-link" type="button" data-action="permissions">Dar permiso</button></div></div>' +
    '<div class="calma-actions"><button type="button" data-action="save" disabled>Guardar</button><span id="calma-saved" role="status" aria-live="polite"></span></div>' +
    '<div class="calma-group"><button class="calma-row calma-update" type="button" data-action="update"><span>Actualizar aplicación</span><span class="calma-chevron" aria-hidden="true">›</span></button></div>' +
    '<p class="calma-footer">Calma · 0.3.7</p>';
  function text(selector, value) {
    const element = section.querySelector(selector);
    if (element.textContent !== value) element.textContent = value;
  }
  function preferences() {
    const config = window.CALMA_CONFIG || {};
    return {mode: String(config.mode == null ? 1 : config.mode), limit: String(config.limit || 20), reels: !!config.reels, dm: !!config.dmMirror};
  }
  function fields() {
    return {mode: section.querySelector('#calma-mode input:checked').value, limit: section.querySelector('#calma-limit').value, reels: section.querySelector('#calma-reels').checked, dm: section.querySelector('#calma-dm').checked};
  }
  function fill(values) {
    const radio = section.querySelector('#calma-mode input[value="' + values.mode + '"]');
    (radio || section.querySelector('#calma-mode input[value="1"]')).checked = true;
    section.querySelector('#calma-reels').checked = values.reels;
    section.querySelector('#calma-limit').value = values.limit;
    section.querySelector('#calma-dm').checked = values.dm;
  }
  function controls() {
    const values = fields(), limit = Number(values.limit);
    section.querySelector('[data-step="-1"]').disabled = limit <= 1 && values.limit !== '';
    section.querySelector('[data-step="1"]').disabled = limit >= 100;
    section.querySelector('[data-action="save"]').disabled = JSON.stringify(values) === JSON.stringify(preferences());
    const config = window.CALMA_CONFIG || {}, permitted = !!config.notificationsEnabled && !!config.listenerEnabled;
    section.querySelector('#calma-permission-row').hidden = !values.dm;
    section.querySelector('[data-action="permissions"]').hidden = permitted;
    text('#calma-permissions', permitted ? 'Permiso activado' : 'Falta el acceso a notificaciones');
  }
  function status() {
    if (!section || !section.isConnected) return;
    const state = window.__calmaRelations || {};
    text('#calma-sync', state.phase === 'ready' ? (state.following || []).length + ' siguiendo · ' + (state.friends || []).length + ' amigos'
      : state.phase === 'error' ? (state.error || 'No se pudo sincronizar') : state.phase === 'syncing' ? 'Sincronizando…' : 'Inicia sesión para sincronizar');
    section.querySelector('[data-action="sync"]').disabled = state.phase === 'syncing';
    controls();
  }
  function refresh() {
    const next = JSON.stringify(preferences());
    if (next !== configKey) { configKey = next; draft = null; }
    mount();
    if (!section || !section.isConnected) return;
    if (!draft) fill(preferences());
    status();
  }
  function changed() {
    draft = fields();
    section.querySelector('#calma-limit').removeAttribute('aria-invalid');
    section.querySelector('#calma-limit-error').hidden = true;
    text('#calma-saved', '');
    controls();
  }
  function command(action) {
    if (action === 'sync') { if (window.__calmaRelationsController) window.__calmaRelationsController.sync(); status(); return; }
    const params = new URLSearchParams({calma_action: action});
    if (action === 'save' || action === 'permissions') {
      const values = fields(), limit = Number(values.limit);
      if (!/^\d+$/.test(values.limit) || !Number.isInteger(limit) || limit < 1 || limit > 100) {
        section.querySelector('#calma-limit').setAttribute('aria-invalid', 'true');
        section.querySelector('#calma-limit-error').hidden = false;
        section.querySelector('#calma-limit').focus(); return;
      }
      params.set('mode', values.mode); params.set('limit', String(limit));
      params.set('reels', values.reels ? '1' : '0'); params.set('dm', action === 'permissions' || values.dm ? '1' : '0');
      text('#calma-saved', 'Guardando…');
    }
    // Top-level navigation intercepted and validated by Android, never a JS-native bridge.
    location.assign(location.origin + location.pathname + '?' + params.toString());
  }
  function mount() {
    if (window.CALMA_ACTIVE === false) { observer.disconnect(); observing = false; return; }
    if (!paths.has(location.pathname) || document.querySelector('input[type=password]')) {
      if (section) section.remove(); section = null; draft = null;
      observer.disconnect(); observing = false; return;
    }
    // Only watch panel replacement while settings are open. No observer runs over the feed or DMs.
    if (!observing) { observer.observe(document.body || document.documentElement, {childList: true, subtree: true}); observing = true; }
    const main = document.querySelector('main,[role=main]');
    if (!main) return;
    const form = main.querySelector('form'), host = form ? form.parentElement : main;
    if (section && section.isConnected && section.parentElement === host) return;
    if (section) section.remove();
    section = document.createElement('section'); section.id = 'calma-settings'; section.setAttribute('aria-label', 'Tu feed');
    section.innerHTML = markup;
    section.addEventListener('click', event => {
      const button = event.target.closest('button');
      if (!button || !section.contains(button)) return;
      event.preventDefault(); event.stopPropagation();
      if (button.dataset.step) {
        const input = section.querySelector('#calma-limit'), value = Number(input.value);
        input.value = String(Math.max(1, Math.min(100, (Number.isFinite(value) && input.value !== '' ? value : 20) + Number(button.dataset.step))));
        changed();
      } else if (button.dataset.action) command(button.dataset.action);
    });
    section.addEventListener('input', changed);
    section.addEventListener('change', changed);
    host.appendChild(section);
    fill(draft || preferences()); status();
  }
  function schedule() { clearTimeout(timer); if (window.CALMA_ACTIVE !== false) timer = setTimeout(mount, 60); }
  function visibility(event) {
    if (event.detail && event.detail.active === false) { clearTimeout(timer); observer.disconnect(); observing = false; }
    else schedule();
  }
  function linkNavigation(event) {
    const link = event.target.closest && event.target.closest('a[href]');
    if (!link) return;
    let path; try { path = new URL(link.href, location.href).pathname; } catch (_) { return; }
    if (paths.has(path) || paths.has(location.pathname)) schedule();
  }
  const observer = new MutationObserver(records => {
    if (section && records.every(record => record.target === section || section.contains(record.target))) return;
    schedule();
  });
  document.addEventListener('calma-relations', status);
  document.addEventListener('click', linkNavigation);
  window.addEventListener('calma-route', schedule);
  window.addEventListener('calma-visibility', visibility);
  window.addEventListener('popstate', schedule);
  window.addEventListener('hashchange', schedule);
  window.__calmaSettings = {
    refresh,
    updateStatus: status,
    saved() { if (section && section.isConnected) { draft = null; configKey = JSON.stringify(preferences()); fill(preferences()); status(); text('#calma-saved', 'Guardado'); } },
    destroy() {
      observer.disconnect(); clearTimeout(timer);
      document.removeEventListener('calma-relations', status); document.removeEventListener('click', linkNavigation);
      window.removeEventListener('calma-visibility', visibility); window.removeEventListener('calma-route', schedule); window.removeEventListener('popstate', schedule); window.removeEventListener('hashchange', schedule);
      styles.remove(); if (section) section.remove(); delete window.__calmaSettings;
    }
  };
  refresh();
})();
