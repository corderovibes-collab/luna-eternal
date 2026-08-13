import { renderSkin } from './skinview.js';

const { launcher } = window;

const $ = (id) => document.getElementById(id);
const show = (node, visible) => { node.hidden = !visible; };

const el = {
  ball: $('ball'),
  navItems: document.querySelectorAll('.nav-item'),
  views: document.querySelectorAll('.view'),
  lampAccount: $('lamp-account'),
  lampPack: $('lamp-pack'),
  lampServer: $('lamp-server'),
  appInfo: $('app-info'),

  statPack: $('stat-pack'),
  statPlayers: $('stat-players'),
  statServer: $('stat-server'),
  statRam: $('stat-ram'),

  play: $('btn-play'),
  playLabel: $('play-label'),
  stop: $('btn-stop'),
  playError: $('play-error'),
  consoleOut: $('console-out'),

  progress: $('progress'),
  progressMsg: $('progress-message'),
  progressPct: $('progress-percent'),
  progressBar: $('progress-bar'),

  who: $('who'),
  whoAvatar: $('who-avatar'),
  whoName: $('who-name'),
  whoKind: $('who-kind'),

  accountList: $('account-list'),
  formOffline: $('form-offline'),
  offlineName: $('offline-name'),
  offlineError: $('offline-error'),

  ram: $('ram'),
  ramOut: $('ram-out'),
  extra: $('opt-extra'),
  keepOpen: $('opt-keepopen'),
  manifestUrl: $('manifest-url'),
  btnFolder: $('btn-folder'),
  btnLogs: $('btn-logs'),
  steps: document.querySelectorAll('.step'),
  reqList: $('req-list'),
  reqError: $('req-error'),
  skinStage: $('skin-stage'),
  skinSource: $('skin-source'),
  btnSkin: $('btn-skin'),
  btnSkinClear: $('btn-skin-clear'),
  skinError: $('skin-error'),

  updatebar: $('updatebar'),
  updateText: $('update-text'),
  btnUpdate: $('btn-update'),
  btnCheckUpdate: $('btn-check-update'),

  perfiles: document.querySelectorAll('input[name="perfil"]'),

  crash: $('crash'),
  crashTitle: $('crash-title'),
  crashDetail: $('crash-detail'),
  btnCrashAction: $('btn-crash-action'),
  btnCrashLogs: $('btn-crash-logs'),
  btnCrashClose: $('btn-crash-close'),

  btnRepair: $('btn-repair'),
  repairMsg: $('repair-msg'),
};

const SKIN_ORIGEN = {
  custom: 'La que has elegido tú.',
  'mojang-name': 'Encontrada en Mojang por tu nombre de jugador.',
  default: 'Sin skin todavía. Elige un PNG de 64x64 y se verá aquí.',
};

const ICON = { ok: '✓', warn: '!', error: '✕' };

const state = { config: null, accounts: [], activeAccount: null, playing: false };

// ------------------------------------------------------------------ helpers

function notice(node, message) {
  node.textContent = message ?? '';
  show(node, Boolean(message));
}

function lamp(node, level, text) {
  node.dataset.state = level;
  node.querySelector('dd').textContent = text;
}

function setBusy(busy, label) {
  el.play.disabled = busy;
  el.playLabel.textContent = label;
  el.ball.classList.toggle('is-busy', busy);
}

/** Recorta el registro: una partida larga escupe megas y no cabe en el DOM. */
function appendLog(text) {
  const merged = el.consoleOut.textContent + text;
  el.consoleOut.textContent = merged.length > 60_000 ? merged.slice(-60_000) : merged;
  el.consoleOut.scrollTop = el.consoleOut.scrollHeight;
}

// --------------------------------------------------------------- navegación

el.navItems.forEach((item) => {
  item.addEventListener('click', () => {
    el.navItems.forEach((other) => {
      const active = other === item;
      other.classList.toggle('is-active', active);
      other.setAttribute('aria-selected', String(active));
    });
    el.views.forEach((view) => {
      view.classList.toggle('is-active', view.id === `view-${item.dataset.view}`);
    });
  });
});

// ------------------------------------------------------------------ render

function renderAccounts() {
  el.accountList.replaceChildren();

  if (!state.accounts.length) {
    const empty = document.createElement('li');
    empty.className = 'dim';
    empty.textContent = 'Todavía no hay ninguna cuenta. Añade una arriba.';
    el.accountList.append(empty);
  }

  for (const account of state.accounts) {
    const li = document.createElement('li');
    li.classList.toggle('is-active', account.id === state.activeAccount);

    const avatar = document.createElement('span');
    avatar.className = 'who-avatar';
    avatar.textContent = account.name.charAt(0).toUpperCase();

    const info = document.createElement('div');
    info.className = 'grow';
    const name = document.createElement('strong');
    name.textContent = account.name;
    const kind = document.createElement('span');
    kind.textContent = 'Offline · el progreso va con el nombre';
    info.append(name, kind);

    const use = document.createElement('button');
    use.className = 'btn btn-quiet';
    use.textContent = account.id === state.activeAccount ? 'En uso' : 'Usar';
    use.disabled = account.id === state.activeAccount;
    use.addEventListener('click', async () => {
      await launcher.accounts.select(account.id);
      state.activeAccount = account.id;
      renderAccounts();
      renderWho();
      refreshSkin();
    });

    const drop = document.createElement('button');
    drop.className = 'btn btn-quiet';
    drop.textContent = 'Quitar';
    drop.addEventListener('click', async () => {
      Object.assign(state, await launcher.accounts.remove(account.id));
      renderAccounts();
      renderWho();
      refreshSkin();
    });

    li.append(avatar, info, use, drop);
    el.accountList.append(li);
  }
}

function renderWho() {
  const account = state.accounts.find((a) => a.id === state.activeAccount);
  show(el.who, Boolean(account));

  if (!account) {
    lamp(el.lampAccount, 'warn', 'Sin elegir');
    return;
  }
  el.whoAvatar.textContent = account.name.charAt(0).toUpperCase();
  el.whoName.textContent = account.name;
  el.whoKind.textContent = 'Offline';
  lamp(el.lampAccount, 'ok', account.name);
}

function renderConfig() {
  const cfg = state.config;
  el.ram.value = cfg.ramGb;
  el.ramOut.textContent = `${cfg.ramGb} GB`;
  el.statRam.textContent = `${cfg.ramGb} GB`;
  el.extra.checked = cfg.extraOptimization;
  el.keepOpen.checked = cfg.keepLauncherOpen;
  el.manifestUrl.value = cfg.manifestUrl ?? '';
  el.perfiles.forEach((r) => { r.checked = r.value === (cfg.perfil ?? 'jugador'); });

  const version = cfg.installedVersion;
  el.statPack.textContent = version ?? 'Sin instalar';
  lamp(el.lampPack, version ? 'ok' : 'warn', version ? `Versión ${version}` : 'Se instala al jugar');
}

async function refreshServer() {
  lamp(el.lampServer, '', 'Comprobando…');
  try {
    const info = await launcher.server.ping();
    el.statServer.textContent = `${info.host}:${info.port}`;
    if (info.online) {
      el.statPlayers.textContent = `${info.players}/${info.maxPlayers}`;
      lamp(el.lampServer, 'ok', `En línea · ${info.players} jugando`);
    } else {
      el.statPlayers.textContent = '—';
      lamp(el.lampServer, 'bad', 'No responde');
    }
  } catch {
    el.statPlayers.textContent = '—';
    lamp(el.lampServer, 'bad', 'Sin conexión');
  }
}

function renderRequirements(report) {
  el.reqList.replaceChildren();

  for (const check of report.checks) {
    const li = document.createElement('li');
    li.className = 'req';
    li.dataset.level = check.level;

    const icon = document.createElement('span');
    icon.className = 'req-icon';
    icon.textContent = ICON[check.level];

    const info = document.createElement('div');
    const title = document.createElement('strong');
    title.textContent = check.title;
    const detail = document.createElement('span');
    detail.textContent = check.detail;
    info.append(title, detail);

    li.append(icon, info);

    if (check.action) {
      const fix = document.createElement('button');
      fix.className = 'btn';
      fix.textContent = 'Instalar ahora';
      fix.addEventListener('click', async () => {
        notice(el.reqError, '');
        fix.disabled = true;
        fix.textContent = 'Instalando…';
        show(el.progress, true);
        try {
          renderRequirements(await launcher.preflight.fix(check.action));
        } catch (err) {
          notice(el.reqError, err.message);
          fix.disabled = false;
          fix.textContent = 'Instalar ahora';
        }
      });
      li.append(fix);
    }

    el.reqList.append(li);
  }

  // Solo se avisa cuando hay algo que arreglar; si va todo bien no se molesta.
  const nav = document.querySelector('.nav-item[data-view="requisitos"]');
  nav.classList.toggle('has-issue', report.blocking.length > 0);
}

let disposeSkin = () => {};

async function refreshSkin() {
  notice(el.skinError, '');
  const skin = await launcher.skins.get().catch(() => null);

  if (!skin) {
    disposeSkin();
    el.skinStage.replaceChildren();
    el.skinSource.textContent = 'Elige una cuenta para ver tu personaje.';
    el.btnSkin.disabled = true;
    show(el.btnSkinClear, false);
    return;
  }

  el.btnSkin.disabled = false;
  el.skinSource.textContent = SKIN_ORIGEN[skin.source] ?? '';
  show(el.btnSkinClear, skin.custom);

  // Soltar los listeners del muñeco anterior antes de dibujar el nuevo.
  disposeSkin();
  disposeSkin = renderSkin(el.skinStage, skin);
}

el.btnSkin.addEventListener('click', async () => {
  notice(el.skinError, '');
  try {
    if (await launcher.skins.choose()) await refreshSkin();
  } catch (err) {
    notice(el.skinError, err.message);
  }
});

el.btnSkinClear.addEventListener('click', async () => {
  notice(el.skinError, '');
  try {
    await launcher.skins.clear();
    await refreshSkin();
  } catch (err) {
    notice(el.skinError, err.message);
  }
});

// ------------------------------------------------------------------- jugar

el.play.addEventListener('click', async () => {
  notice(el.playError, '');

  if (!state.activeAccount) {
    notice(el.playError, 'Elige una cuenta en la sección Cuentas y vuelve a darle a Jugar.');
    return;
  }

  setBusy(true, 'PREPARANDO');
  show(el.progress, true);
  el.consoleOut.textContent = '';

  try {
    const result = await launcher.game.play();
    state.playing = true;
    el.statPack.textContent = result.packVersion;
    lamp(el.lampPack, 'ok', `Versión ${result.packVersion}`);
    setBusy(true, 'JUGANDO');
    show(el.stop, true);
  } catch (err) {
    notice(el.playError, err.message);
    setBusy(false, 'JUGAR');
    el.progressBar.classList.remove('is-active');
  }
});

el.stop.addEventListener('click', () => launcher.game.stop());

/** Orden real de las fases de `prepare()`: al entrar en una, las previas quedan hechas. */
const PHASES = ['java', 'minecraft', 'fabric', 'pack'];

function markPhase(phase) {
  const current = PHASES.indexOf(phase);
  if (current < 0 && phase !== 'done') return;
  el.steps.forEach((step) => {
    const index = PHASES.indexOf(step.dataset.phase);
    if (phase === 'done' || index < current) step.dataset.state = 'done';
    else if (index === current) step.dataset.state = 'doing';
    else step.dataset.state = '';
  });
}

launcher.events.onProgress(({ phase, message, progress }) => {
  markPhase(phase);
  const pct = Math.round((progress ?? 0) * 100);
  el.progressMsg.textContent = message;
  el.progressPct.textContent = `${pct}%`;
  el.progressBar.style.width = `${pct}%`;
  el.progressBar.classList.toggle('is-active', pct > 0 && pct < 100);
});

launcher.events.onGameLog(appendLog);

launcher.events.onGameExit(({ code, causa }) => {
  state.playing = false;
  setBusy(false, 'JUGAR');
  show(el.stop, false);
  el.progressBar.classList.remove('is-active');
  el.steps.forEach((step) => { step.dataset.state = ''; });
  el.progressMsg.textContent = causa
    ? 'El juego se cerró sin querer.'
    : 'El juego se ha cerrado.';
  mostrarCausa(causa);
  refreshServer();
});

// ------------------------------------------------------- por qué se cerró

/**
 * Enseña la causa del último cierre, con su botón de arreglo si lo hay.
 *
 * Va a la vista de Jugar y no a un `alert` a propósito: un diálogo se cierra de
 * un clic sin leerlo, y el jugador se queda igual que estaba. Aquí el motivo se
 * queda en pantalla hasta que se resuelve o se descarta.
 */
function mostrarCausa(causa) {
  if (!causa) {
    show(el.crash, false);
    return;
  }
  el.crashTitle.textContent = causa.titulo;
  el.crashDetail.textContent = causa.detalle;
  el.btnCrashAction.dataset.accion = causa.accion;
  el.btnCrashAction.textContent =
    causa.accion === 'ram' ? 'Ir a Ajustes' : 'Reparar instalación';
  show(el.btnCrashAction, causa.accion !== 'ninguna');
  show(el.crash, true);
  document.querySelector('.nav-item[data-view="play"]').click();
}

el.btnCrashAction.addEventListener('click', () => {
  if (el.btnCrashAction.dataset.accion === 'ram') {
    document.querySelector('.nav-item[data-view="settings"]').click();
    el.ram.focus();
    return;
  }
  document.querySelector('.nav-item[data-view="settings"]').click();
  el.btnRepair.click();
});

el.btnCrashLogs.addEventListener('click', () => launcher.shell.openFolder('logs'));
el.btnCrashClose.addEventListener('click', () => show(el.crash, false));

// ----------------------------------------------------------------- cuentas

launcher.events.onAccountsChanged((data) => {
  Object.assign(state, data);
  renderAccounts();
  renderWho();
  refreshSkin();
});

el.formOffline.addEventListener('submit', async (event) => {
  event.preventDefault();
  notice(el.offlineError, '');
  try {
    Object.assign(state, await launcher.accounts.addOffline(el.offlineName.value));
    el.offlineName.value = '';
    renderAccounts();
    renderWho();
    refreshSkin();
  } catch (err) {
    notice(el.offlineError, err.message);
  }
});

// ----------------------------------------------------------------- ajustes

el.ram.addEventListener('input', () => {
  el.ramOut.textContent = `${el.ram.value} GB`;
  el.statRam.textContent = `${el.ram.value} GB`;
});
el.ram.addEventListener('change', () => launcher.config.set({ ramGb: Number(el.ram.value) }));

const bindToggle = (node, key) =>
  node.addEventListener('change', () => launcher.config.set({ [key]: node.checked }));

bindToggle(el.extra, 'extraOptimization');
bindToggle(el.keepOpen, 'keepLauncherOpen');

el.manifestUrl.addEventListener('change', async () => {
  await launcher.config.set({ manifestUrl: el.manifestUrl.value.trim() });
  refreshServer();
});

el.btnFolder.addEventListener('click', () => launcher.shell.openFolder('instance'));
el.btnLogs.addEventListener('click', () => launcher.shell.openFolder('logs'));

el.perfiles.forEach((radio) => {
  radio.addEventListener('change', async () => {
    if (!radio.checked) return;
    await launcher.config.set({ perfil: radio.value });
    el.progressMsg.textContent = radio.value === 'constructor'
      ? 'Perfil de constructor. Al jugar se instalarán Axiom y WorldEdit CUI.'
      : 'Perfil de jugador. Al jugar se quitarán las herramientas de construcción.';
    show(el.progress, true);
  });
});

// ---------------------------------------------------------------- reparar

el.btnRepair.addEventListener('click', async () => {
  notice(el.repairMsg, '');
  el.btnRepair.disabled = true;
  el.btnRepair.textContent = 'Comprobando…';
  show(el.progress, true);
  try {
    const res = await launcher.game.repair();
    notice(el.repairMsg, res.sync.downloaded
      ? `Listo: ${res.sync.downloaded} fichero(s) vueltos a bajar.`
      : 'Todo estaba correcto. No hacía falta bajar nada.');
    show(el.crash, false);
  } catch (err) {
    notice(el.repairMsg, err.message);
  } finally {
    el.btnRepair.disabled = false;
    el.btnRepair.textContent = 'Reparar instalación';
  }
});

// ------------------------------------------ actualización del propio launcher

/**
 * Pinta el estado del actualizador.
 *
 * Solo aparece cuando hay algo que decir. Mientras comprueba o está al día no
 * se enseña nada: un aviso permanente se vuelve parte del decorado.
 */
function renderUpdate(estado) {
  if (!estado || ['inactivo', 'comprobando', 'al-dia', 'desarrollo', 'error'].includes(estado.fase)) {
    show(el.updatebar, false);
    return;
  }

  if (estado.fase === 'descargando') {
    el.updateText.textContent = `Descargando la versión ${estado.version ?? 'nueva'}… `
      + `${Math.round((estado.progreso ?? 0) * 100)}%`;
    show(el.btnUpdate, false);
  } else if (estado.fase === 'lista') {
    el.updateText.textContent = `La versión ${estado.version ?? 'nueva'} está lista.`;
    el.btnUpdate.textContent = 'Reiniciar e instalar';
    show(el.btnUpdate, true);
  } else if (estado.fase === 'disponible-mac') {
    // En Mac no se puede instalar sin firma: se manda a la página de descargas.
    el.updateText.textContent = `Hay una versión nueva (${estado.version ?? ''}). `
      + 'En Mac hay que bajarla a mano.';
    el.btnUpdate.textContent = 'Abrir descargas';
    show(el.btnUpdate, true);
  }
  show(el.updatebar, true);
}

el.btnUpdate.addEventListener('click', async () => {
  if (el.btnUpdate.textContent === 'Abrir descargas') {
    const info = await launcher.app.info();
    launcher.shell.openExternal(info.paginaDescargas);
    return;
  }
  await launcher.update.install();
});

el.btnCheckUpdate.addEventListener('click', async () => {
  notice(el.repairMsg, 'Comprobando si hay una versión nueva del launcher…');
  const estado = await launcher.update.check();
  renderUpdate(estado);
  notice(el.repairMsg, {
    'al-dia': 'El launcher ya está en la última versión.',
    desarrollo: 'En modo desarrollo no se comprueban actualizaciones.',
    error: `No se pudo comprobar: ${estado.error ?? 'sin detalle'}`,
  }[estado.fase] ?? 'Hay una versión nueva. Mira el aviso de arriba.');
});

launcher.events.onUpdate(renderUpdate);

// ------------------------------------------------------------------ arranque

(async function init() {
  const [config, accounts, info] = await Promise.all([
    launcher.config.get(),
    launcher.accounts.list(),
    launcher.app.info(),
  ]);

  state.config = config;
  Object.assign(state, accounts);

  renderConfig();
  renderAccounts();
  renderWho();
  refreshSkin();
  el.appInfo.textContent = `v${info.version}`;

  refreshServer();
  // Mientras no se esté jugando, refrescar el estado del servidor de vez en cuando.
  setInterval(() => { if (!state.playing) refreshServer(); }, 60_000);

  try {
    const report = await launcher.preflight.check();
    renderRequirements(report);
    // Si falta algo imprescindible, llevar directo a Requisitos en vez de dejar
    // que el jugador descubra el problema al pulsar Jugar.
    if (report.blocking.length) {
      document.querySelector('.nav-item[data-view="requisitos"]').click();
    }
  } catch (err) {
    notice(el.reqError, err.message);
  }
})();
