(() => {
  'use strict';
  if (window.__calmaSettings) { window.__calmaSettings.refresh(); return; }
  const paths = new Set(['/accounts/edit', '/accounts/edit/', '/accounts/settings/', '/settings', '/settings/']);
  let section = null, timer;
  const styles = document.createElement('style'); styles.id = 'calma-settings-style';
  // Normal document flow only. No fixed controls, overlays or replacement of native settings.
  styles.textContent = '#calma-settings{box-sizing:border-box;border-top:1px solid var(--calma-border,#dbdbdb);padding:24px;margin:16px 0;font:inherit;color:inherit}#calma-settings h2{font-size:20px;font-weight:600;margin:0 0 20px}#calma-settings h3{font-size:16px;margin:24px 0 12px}#calma-settings .calma-row{display:flex;align-items:center;justify-content:space-between;gap:16px;margin:16px 0}#calma-settings label{font-size:14px}#calma-settings select,#calma-settings input[type=number]{font:inherit;color:inherit;background:transparent;border:1px solid var(--calma-border,#dbdbdb);border-radius:8px;padding:8px;max-width:100%;box-sizing:border-box}#calma-settings select{width:100%}#calma-settings input[type=checkbox]{width:20px;height:20px;accent-color:#0095f6}#calma-settings p{font-size:12px;line-height:1.6;margin:10px 0;color:inherit;opacity:.75}#calma-settings button{font:inherit;font-size:14px;cursor:pointer;padding:8px 16px;margin:6px 8px 6px 0;border:1px solid var(--calma-border,#dbdbdb);border-radius:8px;background:transparent;color:inherit}#calma-settings button[data-action=save]{background:#0095f6;color:white;border-color:#0095f6}#calma-settings [role=status]{font-size:13px;line-height:1.5;margin:12px 0}';
  (document.head || document.documentElement).appendChild(styles);
  const markup = '<h2>Ajustes adicionales</h2>' +
    '<label for="calma-mode">Contenido del inicio</label><div class="calma-row"><select id="calma-mode"><option value="0">Todas las cuentas</option><option value="1">Solo cuentas que sigo</option><option value="2">Solo amigos (seguimiento mutuo)</option></select></div>' +
    '<div id="calma-sync" role="status"></div><button type="button" data-action="sync">Sincronizar ahora</button>' +
    '<p>Los seguidos se consultan desde tu sesión. Amigos significa que tú les sigues y ellos te siguen. Instagram puede limitar la sincronización.</p>' +
    '<div class="calma-row"><label for="calma-reels">Desactivar Reels</label><input id="calma-reels" type="checkbox"></div>' +
    '<p>Oculta los vídeos del inicio. Los Reels compartidos en DM por amigos de seguimiento mutuo pueden abrirse de uno en uno, sin desplazarte al siguiente.</p>' +
    '<div class="calma-row"><label for="calma-limit">Publicaciones por sesión</label><input id="calma-limit" type="number" min="1" max="100" step="1" required aria-describedby="calma-limit-info"></div>' +
    '<p id="calma-limit-info">Entre 1 y 100. El feed se termina al llegar al límite. Puedes iniciar otra sesión.</p>' +
    '<h3>Notificaciones de mensajes</h3><div class="calma-row"><label for="calma-dm">Avisos de DM desde Instagram oficial</label><input id="calma-dm" type="checkbox"></div>' +
    '<p>Mantén Instagram oficial instalado con la misma cuenta y sus avisos de mensajes activados. Se copian sus notificaciones reconocidas como DM; pueden aparecer ambas. Android requiere acceso a notificaciones, que puedes revocar desde sus ajustes.</p>' +
    '<div id="calma-permissions" role="status"></div><button type="button" data-action="permissions">Activar permisos de notificaciones</button>' +
    '<div><button type="button" data-action="save">Guardar</button><button type="button" data-action="new_session">Nueva sesión</button></div><div id="calma-saved" role="status" aria-live="polite"></div>' +
    '<p>Instagram Calma 0.3.1 · Cliente independiente basado en Instagram web. Estos ajustes se guardan en esta app y no cambian los ajustes de tu cuenta en otros dispositivos.</p>';
  function status() {
    if (!section || !section.isConnected) return;
    const state = window.__calmaRelations || {}, config = window.CALMA_CONFIG;
    section.querySelector('#calma-sync').textContent = state.phase === 'ready' ? state.following.length + ' seguidos · ' + state.friends.length + ' amigos mutuos'
      : state.phase === 'error' ? state.error : state.phase === 'syncing' ? 'Sincronizando seguidos y seguidores…' : 'Inicia sesión para sincronizar tu cuenta.';
    section.querySelector('#calma-permissions').textContent = config.notificationsEnabled && config.listenerEnabled ? 'Permisos de notificaciones activados.' : 'Falta activar los permisos de notificaciones en Android.';
  }
  function refresh() {
    mount();
    if (!section || !section.isConnected) return;
    const config = window.CALMA_CONFIG;
    section.querySelector('#calma-mode').value = String(config.mode);
    section.querySelector('#calma-reels').checked = !!config.reels;
    section.querySelector('#calma-limit').value = String(config.limit);
    section.querySelector('#calma-dm').checked = !!config.dmMirror;
    status();
  }
  function command(action) {
    if (action === 'sync') { if (window.__calmaRelationsController) window.__calmaRelationsController.sync(); status(); return; }
    const params = new URLSearchParams({calma_action: action});
    if (action === 'save' || action === 'permissions') {
      const limit = section.querySelector('#calma-limit');
      if (!limit.reportValidity()) return;
      params.set('mode', section.querySelector('#calma-mode').value);
      params.set('limit', limit.value);
      params.set('reels', section.querySelector('#calma-reels').checked ? '1' : '0');
      params.set('dm', action === 'permissions' || section.querySelector('#calma-dm').checked ? '1' : '0');
      section.querySelector('#calma-saved').textContent = 'Guardando…';
    }
    // Top-level navigation intercepted and validated by Android, never a JS-native bridge.
    location.assign(location.origin + location.pathname + '?' + params.toString());
  }
  function mount() {
    if (!paths.has(location.pathname) || document.querySelector('input[type=password]')) { if (section) section.remove(); return; }
    const main = document.querySelector('main,[role=main]');
    if (!main) return;
    const form = main.querySelector('form');
    const host = form ? form.parentElement : main;
    if (section && section.isConnected && section.parentElement === host) return;
    if (section) section.remove();
    section = document.createElement('section'); section.id = 'calma-settings'; section.setAttribute('aria-label', 'Ajustes adicionales');
    section.innerHTML = markup;
    section.addEventListener('click', event => {
      const button = event.target.closest('button[data-action]');
      if (button && section.contains(button)) { event.preventDefault(); event.stopPropagation(); command(button.dataset.action); }
    });
    host.appendChild(section);
    refresh();
  }
  const observer = new MutationObserver(records => {
    if (section && records.every(r => r.target === section || section.contains(r.target))) return;
    clearTimeout(timer); timer = setTimeout(mount, 100);
  });
  observer.observe(document.documentElement, {childList: true, subtree: true});
  const interval = setInterval(() => { mount(); status(); }, 1600);
  document.addEventListener('calma-relations', status);
  window.__calmaSettings = {
    refresh,
    updateStatus: status,
    saved() { if (section && section.isConnected) section.querySelector('#calma-saved').textContent = 'Cambios guardados.'; },
    destroy() { observer.disconnect(); clearTimeout(timer); clearInterval(interval); document.removeEventListener('calma-relations', status); styles.remove(); if (section) section.remove(); delete window.__calmaSettings; }
  };
  refresh();
})();
