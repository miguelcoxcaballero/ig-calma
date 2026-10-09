(() => {
  'use strict';
  if (window.__calmaRelationsController) {
    window.__calmaRelationsController.refreshIdentity(window.CALMA_CONFIG.owner || '');
    return;
  }
  let owner = '', generation = 0, active = null;
  const empty = () => ({owner, following: [], friends: [], followingReady: false, friendsReady: false, phase: 'waiting', error: '', updated: 0});
  window.__calmaRelations = empty();
  const notify = () => {
    document.dispatchEvent(new Event('calma-relations'));
  };
  const validUser = user => user && /^\d+$/.test(String(user.pk)) && /^[a-z0-9._]{1,30}$/i.test(user.username || '');
  async function request(url, token) {
    const csrf = document.cookie.split(';').map(x => x.trim()).find(x => x.startsWith('csrftoken='));
    const headers = {'X-IG-App-ID': '936619743392459', 'Accept': 'application/json'};
    if (csrf) headers['X-CSRFToken'] = decodeURIComponent(csrf.slice('csrftoken='.length));
    const timeout = setTimeout(() => token.controller.abort(), 15000);
    try {
      const response = await fetch(url, {credentials: 'same-origin', headers, signal: token.controller.signal});
      if (generation !== token.generation) throw new Error('account_changed');
      if (!response.ok) throw new Error('http_' + response.status);
      const data = await response.json();
      if (data.status && data.status !== 'ok') throw new Error('instagram_rejected');
      return data;
    } finally { clearTimeout(timeout); }
  }
  async function list(kind, token) {
    const users = new Map(), cursors = new Set();
    let cursor = '';
    for (let page = 0; page < 100; page++) {
      if (generation !== token.generation) throw new Error('account_changed');
      const url = '/api/v1/friendships/' + token.owner + '/' + kind + '/?count=100' + (cursor ? '&max_id=' + encodeURIComponent(cursor) : '');
      const data = await request(url, token);
      if (!Array.isArray(data.users) || !data.users.every(validUser)) throw new Error('unexpected_response');
      data.users.forEach(user => users.set(String(user.pk), user.username.toLowerCase()));
      const next = data.next_max_id == null ? '' : String(data.next_max_id);
      if (!next) {
        if (data.big_list === true && data.users.length === 100) throw new Error('incomplete_list');
        return users;
      }
      if (cursors.has(next)) throw new Error('pagination_loop');
      cursors.add(next); cursor = next;
      // Serial, paced requests; never retry a challenge or rate limit.
      await new Promise(resolve => setTimeout(resolve, 650));
    }
    throw new Error('too_many_pages');
  }
  async function sync(force = false) {
    if (!owner || active) return;
    const state = window.__calmaRelations;
    if (!force && state.friendsReady && Date.now() - state.updated < 24 * 60 * 60 * 1000) return;
    const token = {owner, generation, controller: new AbortController()};
    active = token; state.phase = 'syncing'; state.error = ''; notify();
    try {
      const following = await list('following', token);
      if (generation !== token.generation) return;
      state.following = [...following.values()]; state.followingReady = true;
      // A fresh following snapshot invalidates the old mutual snapshot until both finish.
      state.friends = []; state.friendsReady = false; notify();
      const followers = await list('followers', token);
      if (generation !== token.generation) return;
      state.friends = [...following].filter(([id]) => followers.has(id)).map(([, username]) => username);
      state.friendsReady = true; state.phase = 'ready'; state.updated = Date.now();
      try { localStorage.setItem('calma-relations-v2-' + owner, JSON.stringify(state)); } catch (_) {}
    } catch (error) {
      if (generation !== token.generation) return;
      state.phase = 'error';
      state.error = error.message === 'http_429' ? 'Instagram ha limitado la consulta. Espera antes de volver a sincronizar.'
        : /http_401|http_403|instagram_rejected/.test(error.message) ? 'Instagram no permite consultar las relaciones en esta sesión. Revisa el inicio de sesión o las comprobaciones de seguridad.'
        : 'No se pudo completar la sincronización. Comprueba la conexión o inténtalo más tarde.';
      // Never publish a partial mutual list or retry automatically after a rejection.
    } finally {
      if (active === token) active = null;
      if (generation === token.generation) notify();
    }
  }
  function refreshIdentity(id) {
    id = /^\d+$/.test(String(id)) ? String(id) : '';
    if (id === owner) {
      const state = window.__calmaRelations;
      if (owner && state.phase === 'ready' && Date.now() - state.updated >= 24 * 60 * 60 * 1000) sync();
      return;
    }
    generation++; if (active) active.controller.abort(); active = null; owner = id;
    window.__calmaRelations = empty();
    if (id) {
      try {
        const cache = JSON.parse(localStorage.getItem('calma-relations-v2-' + id));
        if (cache && cache.owner === id && cache.followingReady === true && cache.friendsReady === true
            && Array.isArray(cache.following) && Array.isArray(cache.friends)
            && cache.following.concat(cache.friends).every(name => typeof name === 'string' && /^[a-z0-9._]{1,30}$/.test(name))
            && Number.isFinite(cache.updated) && cache.updated <= Date.now()) {
          window.__calmaRelations = {...cache, phase: 'ready', error: ''};
        }
      } catch (_) {}
      sync();
    }
    notify();
  }
  window.__calmaRelationsController = {refreshIdentity, sync: () => sync(true)};
  refreshIdentity(window.CALMA_CONFIG.owner || '');
})();
