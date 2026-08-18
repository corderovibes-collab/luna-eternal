import { app, BrowserWindow, ipcMain, shell, dialog } from 'electron';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { paths, ensureDirs } from './core/paths.js';
import { loadConfig, saveConfig, loadInstalled } from './core/store.js';
import {
  beginMicrosoftLogin, createOfflineAccount, listAccounts,
  removeAccount, resolveActiveAccount, upsertAccount,
} from './core/auth.js';
import { prepare } from './core/install.js';
import { launchGame } from './core/launch.js';
import { pingServer } from './core/ping.js';
import { fetchManifest } from './core/pack.js';
import { preflight, installVisualCpp } from './core/preflight.js';
import { clearCustomSkin, hasCustomSkin, resolveSkin, setCustomSkin } from './core/skin.js';
import { PAGINA_DESCARGAS, estadoActualizacion, vigilarActualizaciones } from './core/actualizar.js';
import { diagnosticar } from './core/diagnostico.js';

const dir = path.dirname(fileURLToPath(import.meta.url));

/**
 * URL del manifiesto del pack. Se puede sobreescribir en Ajustes o por entorno.
 *
 * Vive en el repositorio PÚBLICO a propósito: el launcher lo pide sin
 * credenciales, así que el repositorio privado del proyecto no puede ser el que
 * lo sirva. Es el mismo sitio del que salen las actualizaciones del launcher.
 */
// ⚠ SE PIDE EL PUNTERO, NO EL MANIFIESTO.
//
// `latest.json` son 250 bytes que dicen cuál es el manifiesto vigente, y sale
// de la RELEASE —el CDN de descargas de GitHub, sin límite por peticiones—, no
// de `raw.githubusercontent`. Esto es lo que arregla dos cosas a la vez:
//
//   el 429 de la primera petición  ·  `raw` limita, la release no
//   la vuelta atrás                ·  publicar mal deja de ser irreversible:
//                                     se reescriben 250 bytes y listo
//
// `fetchManifest` distingue puntero de manifiesto POR EL CONTENIDO, así que la
// URL de abajo sigue valiendo tal cual y no hay que migrar a nadie.
//
// El respaldo en `raw` se queda de segundo: mientras exista un solo launcher
// 1.0.x por ahí, ese fichero se sigue publicando.
//
// OJO con la rama: ese repositorio usa `master`, no `main`. Escribir `main`
// por costumbre deja a TODO el mundo con un 404 y sin pack, y el launcher no
// puede adivinarlo. Se cambia aquí solo si cambia la rama del repositorio.
const PACK_REPO = 'corderovibes-collab/luna-eternal-pack';
const MANIFIESTO_POR_DEFECTO =
  process.env.LUNA_MANIFEST
  ?? `https://github.com/${PACK_REPO}/releases/download/pack-manifest/latest.json`;
const MANIFIESTO_RESPALDO =
  `https://raw.githubusercontent.com/${PACK_REPO}/master/manifest.json`;

let win = null;
let gameProcess = null;
let actualizador = { comprobar: async () => estadoActualizacion(), instalar: () => false };

/**
 * Últimas líneas del log del juego.
 *
 * Se guardan en memoria y acotadas: al morir el proceso, `diagnosticar()` mira
 * aquí para decir por qué. Sin esto, la causa está en un fichero que nadie abre.
 */
const REGISTRO_MAX = 64 * 1024;
let registro = '';

function createWindow() {
  win = new BrowserWindow({
    width: 1120,
    height: 700,
    minWidth: 960,
    minHeight: 620,
    show: false,
    backgroundColor: '#0b0f1d',
    autoHideMenuBar: true,
    title: 'Luna Eternal',
    webPreferences: {
      preload: path.join(dir, '..', 'preload', 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false, // el preload usa contextBridge; el core vive solo en el main
      spellcheck: false,
    },
  });

  win.once('ready-to-show', () => win.show());
  win.loadFile(path.join(dir, '..', 'renderer', 'index.html'));

  // Cualquier enlace externo se abre en el navegador, nunca dentro del launcher.
  win.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: 'deny' };
  });
}

const send = (channel, payload) => win?.webContents.send(channel, payload);

/** Envuelve un handler para que los errores lleguen al renderer como texto legible. */
const handle = (channel, fn) =>
  ipcMain.handle(channel, async (_event, ...args) => {
    try {
      return { ok: true, data: await fn(...args) };
    } catch (err) {
      return { ok: false, error: err?.message ?? String(err) };
    }
  });

handle('config:get', async () => {
  const cfg = await loadConfig();
  const installed = await loadInstalled();
  return {
    ...cfg,
    // ⚠ AQUI VA LA URL SOLA, NO LA LISTA. Esto alimenta el campo de texto de
    //   Ajustes: una lista se pinta como "url1,url2" y `config:set` guardaria
    //   esa cadena con la coma dentro como si fuera una direccion.
    manifestUrl: cfg.manifestUrl ?? MANIFIESTO_POR_DEFECTO,
    installedVersion: installed.version,
  };
});
handle('config:set', (patch) => saveConfig(patch));

handle('accounts:list', listAccounts);
handle('accounts:addOffline', async (name) => {
  await upsertAccount(createOfflineAccount(name));
  return listAccounts();
});
handle('accounts:remove', async (id) => {
  await removeAccount(id);
  return listAccounts();
});
handle('accounts:select', async (id) => saveConfig({ activeAccount: id }));

handle('accounts:microsoft', async () => {
  const cfg = await loadConfig();
  const flow = await beginMicrosoftLogin(cfg.azureClientId);

  // La espera sigue en segundo plano: la ventana muestra el código mientras tanto.
  flow.wait
    .then(async (account) => {
      await upsertAccount(account);
      send('accounts:changed', await listAccounts());
      send('microsoft:done', { ok: true, name: account.name });
    })
    .catch((err) => send('microsoft:done', { ok: false, error: err.message }));

  return { userCode: flow.userCode, verificationUri: flow.verificationUri, expiresIn: flow.expiresIn };
});

/** Prepara e instala. `forzar` es el botón de reparar: comprueba fichero a fichero. */
async function preparar({ forzar = false } = {}) {
  const cfg = await loadConfig();
  return prepare([cfg.manifestUrl ?? MANIFIESTO_POR_DEFECTO, MANIFIESTO_RESPALDO], (p) => send('progress', p), { forzar });
}

handle('game:play', async () => {
  if (gameProcess) throw new Error('El juego ya está abierto.');

  const cfg = await loadConfig();
  const account = await resolveActiveAccount();

  // Mejor parar aquí con un motivo claro que dejar que el juego cierre solo.
  const { blocking } = await preflight({ gpuRenderer: await gpuRenderer() });
  if (blocking.length) {
    throw new Error(`No se puede jugar todavía: ${blocking.map((c) => c.title).join(', ')}. `
      + 'Mira la sección Requisitos.');
  }

  const ready = await preparar();
  registro = '';

  gameProcess = await launchGame({
    java: ready.java,
    versionJson: ready.versionJson,
    fabric: ready.fabric,
    minecraft: ready.minecraft,
    account,
    ramGb: cfg.ramGb,
    onLog: (chunk) => {
      registro = (registro + chunk).slice(-REGISTRO_MAX);
      send('game:log', chunk);
    },
    onExit: async (code) => {
      gameProcess = null;
      // El diagnóstico va CON el evento de cierre, no en un canal aparte: si
      // llegaran por separado, la ventana podría pintar «cerrado» sin motivo y
      // el motivo un instante después.
      send('game:exit', { code, causa: diagnosticar(code, registro) });
      // Releer: el ajuste puede haber cambiado durante la partida.
      if (!(await loadConfig()).keepLauncherOpen) win?.show();
    },
  });

  if (!cfg.keepLauncherOpen) win?.minimize();
  return { launched: true, packVersion: ready.manifest.packVersion, sync: ready.sync };
});

handle('game:stop', () => {
  gameProcess?.kill();
  return true;
});

/**
 * Reparar: comprobar el SHA1 de todo lo instalado y rebajar lo que no cuadre.
 *
 * El camino normal se fía de `installed.json` para no rehashear 4000 ficheros
 * en cada arranque. Eso es lo correcto el 99 % de las veces y es exactamente lo
 * que deja pasar un fichero que se corrompió DESPUÉS de instalarse.
 */
handle('game:repair', async () => {
  if (gameProcess) throw new Error('Cierra el juego antes de reparar.');
  const ready = await preparar({ forzar: true });
  return { packVersion: ready.manifest.packVersion, sync: ready.sync };
});

handle('shell:openFolder', async (which) => {
  await ensureDirs();
  const target = which === 'logs' ? paths.logs : paths.instance;
  await shell.openPath(target);
  return target;
});

handle('shell:openExternal', (url) => shell.openExternal(url));

/** El nombre del renderer solo lo sabe Electron; preflight se mantiene puro. */
async function gpuRenderer() {
  try {
    const info = await app.getGPUInfo('complete');
    return info?.auxAttributes?.glRenderer ?? '';
  } catch {
    return '';
  }
}

handle('preflight:check', async () => preflight({ gpuRenderer: await gpuRenderer() }));

handle('preflight:fix', async (action) => {
  if (action !== 'install-vcredist') throw new Error(`Acción desconocida: ${action}`);
  await installVisualCpp((p) => send('progress', p));
  return preflight({ gpuRenderer: await gpuRenderer() });
});

async function activeAccount() {
  const { accounts, activeAccount: id } = await loadConfig();
  return accounts.find((a) => a.id === id) ?? null;
}

handle('skin:get', async () => {
  const account = await activeAccount();
  if (!account) return null;
  const skin = await resolveSkin(account);
  return { ...skin, account: account.name, custom: await hasCustomSkin(account.id) };
});

handle('skin:choose', async () => {
  const account = await activeAccount();
  if (!account) throw new Error('Elige primero una cuenta.');

  const { canceled, filePaths } = await dialog.showOpenDialog(win, {
    title: 'Elige tu skin',
    filters: [{ name: 'Skin de Minecraft (PNG 64x64)', extensions: ['png'] }],
    properties: ['openFile'],
  });
  if (canceled || !filePaths.length) return null;

  const skin = await setCustomSkin(account.id, filePaths[0]);
  return { ...skin, account: account.name, custom: true };
});

handle('skin:clear', async () => {
  const account = await activeAccount();
  if (!account) throw new Error('Elige primero una cuenta.');
  await clearCustomSkin(account.id);
  const skin = await resolveSkin(account);
  return { ...skin, account: account.name, custom: false };
});

handle('server:ping', async () => {
  const cfg = await loadConfig();
  // El host sale del manifiesto para que cambiar de servidor no obligue a
  // publicar un launcher nuevo.
  const manifest = await fetchManifest([cfg.manifestUrl ?? MANIFIESTO_POR_DEFECTO, MANIFIESTO_RESPALDO]);
  return { ...(await pingServer(manifest.server)), ...manifest.server };
});

handle('app:info', async () => ({
  version: app.getVersion(),
  root: paths.root,
  platform: process.platform,
  paginaDescargas: PAGINA_DESCARGAS,
}));

// --- actualización del propio launcher ------------------------------------

handle('update:status', () => estadoActualizacion());
handle('update:check', () => actualizador.comprobar());
handle('update:install', () => actualizador.instalar());

app.whenReady().then(async () => {
  await ensureDirs();
  createWindow();
  actualizador = vigilarActualizaciones((estado) => send('update:changed', estado));
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

app.on('before-quit', () => gameProcess?.kill());

process.on('unhandledRejection', (err) => {
  dialog.showErrorBox('Error inesperado', String(err?.stack ?? err));
});
