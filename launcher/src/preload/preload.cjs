const { contextBridge, ipcRenderer } = require('electron');

/**
 * Superficie única y cerrada entre la UI y el proceso principal.
 *
 * El renderer no ve `ipcRenderer` ni Node: solo estas funciones. Cada `invoke`
 * devuelve {ok, data|error} desde el main, y aquí se desenvuelve para que la UI
 * pueda usar try/catch normal.
 */
async function call(channel, ...args) {
  const res = await ipcRenderer.invoke(channel, ...args);
  if (!res?.ok) throw new Error(res?.error ?? 'Error desconocido');
  return res.data;
}

/** Registra un listener y devuelve la función para quitarlo. */
function on(channel, handler) {
  const wrapped = (_event, payload) => handler(payload);
  ipcRenderer.on(channel, wrapped);
  return () => ipcRenderer.off(channel, wrapped);
}

contextBridge.exposeInMainWorld('launcher', {
  config: {
    get: () => call('config:get'),
    set: (patch) => call('config:set', patch),
  },
  accounts: {
    list: () => call('accounts:list'),
    addOffline: (name) => call('accounts:addOffline', name),
    remove: (id) => call('accounts:remove', id),
    select: (id) => call('accounts:select', id),
    microsoft: () => call('accounts:microsoft'),
  },
  game: {
    play: () => call('game:play'),
    stop: () => call('game:stop'),
    repair: () => call('game:repair'),
  },
  update: {
    status: () => call('update:status'),
    check: () => call('update:check'),
    install: () => call('update:install'),
  },
  shell: {
    openFolder: (which) => call('shell:openFolder', which),
    openExternal: (url) => call('shell:openExternal', url),
  },
  app: {
    info: () => call('app:info'),
  },
  server: {
    ping: () => call('server:ping'),
  },
  skins: {
    get: () => call('skin:get'),
    choose: () => call('skin:choose'),
    clear: () => call('skin:clear'),
  },
  preflight: {
    check: () => call('preflight:check'),
    fix: (action) => call('preflight:fix', action),
  },
  events: {
    onProgress: (h) => on('progress', h),
    onGameLog: (h) => on('game:log', h),
    onGameExit: (h) => on('game:exit', h),
    onAccountsChanged: (h) => on('accounts:changed', h),
    onMicrosoftDone: (h) => on('microsoft:done', h),
    onUpdate: (h) => on('update:changed', h),
  },
});
