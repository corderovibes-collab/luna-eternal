/**
 * Prueba visual de la interfaz con Playwright, en un Chromium de verdad.
 *
 * Existe porque `capturePage` de Electron no compone las capas 3D del visor del
 * personaje: el DOM sale correcto pero la imagen sale vacia. Playwright renderiza
 * de verdad, asi que sirve para distinguir un fallo del codigo de un fallo del
 * arnes de captura, y para comprobar que el muñeco se dibuja.
 *
 * Uso:  node tools/visual-test.mjs
 */
import { chromium } from 'playwright';
import { createReadStream } from 'node:fs';
import { mkdir, writeFile, stat } from 'node:fs/promises';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = path.dirname(fileURLToPath(import.meta.url));
const outDir = path.join(dir, '..', 'preview');
process.env.LUNA_ROOT ??= path.join(os.tmpdir(), 'luna-visual-test');

const { resolveSkin } = await import('../src/main/core/skin.js');

/**
 * Servidor estatico para la carpeta del renderer.
 *
 * Chromium prohibe importar modulos ES desde `file://` (CORS), asi que la interfaz
 * se sirve por HTTP. De paso es mas parecido a como la carga Electron.
 */
const TIPOS = { '.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript',
                '.png': 'image/png', '.woff2': 'font/woff2' };
const raiz = path.join(dir, '..', 'src', 'renderer');

const server = http.createServer(async (req, res) => {
  const rel = decodeURIComponent(req.url.split('?')[0]).replace(/^\//, '') || 'index.html';
  const file = path.join(raiz, rel);
  if (path.relative(raiz, file).startsWith('..')) { res.writeHead(403).end(); return; }
  try {
    await stat(file);
    res.writeHead(200, { 'Content-Type': TIPOS[path.extname(file)] ?? 'application/octet-stream' });
    createReadStream(file).pipe(res);
  } catch {
    res.writeHead(404).end();
  }
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const base = `http://127.0.0.1:${server.address().port}/`;

/** Cuenta cuantos pixeles del recorte no son el fondo: si es ~0, no se pinto nada. */
async function pixelesPintados(page, selector) {
  return page.evaluate(async (sel) => {
    const el = document.querySelector(sel);
    const r = el.getBoundingClientRect();
    return { x: Math.round(r.x), y: Math.round(r.y), width: Math.round(r.width), height: Math.round(r.height) };
  }, selector);
}

const problemas = [];
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1180, height: 760 }, deviceScaleFactor: 1 });

page.on('console', (m) => { if (m.type() === 'error') problemas.push(m.text()); });
page.on('pageerror', (e) => problemas.push(`pageerror: ${e.message}`));

// app.js espera el puente del preload; sin el, revienta al arrancar y ensucia
// la prueba con un error que no es del codigo que se quiere comprobar.
await page.addInitScript(() => {
  const nada = async () => null;
  window.launcher = {
    config: { get: async () => ({ ramGb: 6, autoConnect: true, extraOptimization: true,
                                  keepLauncherOpen: false, manifestUrl: '', azureClientId: '' }), set: nada },
    accounts: { list: async () => ({ accounts: [], activeAccount: null }), addOffline: nada,
                remove: nada, select: nada, microsoft: nada },
    game: { play: nada, stop: nada },
    shell: { openFolder: nada, openExternal: nada },
    app: { info: async () => ({ version: '1.2.0', root: 'C:\test', platform: 'win32' }) },
    server: { ping: async () => ({ online: true, players: 4, maxPlayers: 40, host: 'demo', port: 1 }) },
    skins: { get: nada, choose: nada, clear: nada },
    preflight: { check: async () => ({ checks: [], blocking: [], warnings: [] }), fix: nada },
    events: Object.fromEntries(['onProgress','onGameLog','onGameExit','onAccountsChanged','onMicrosoftDone']
      .map((k) => [k, () => () => {}])),
  };
});

await page.goto(base);

// La UI real habla por IPC; aqui no existe, asi que se simula lo justo para pintar.
const skin = await resolveSkin({ id: 'offline:notch', type: 'offline', name: 'Notch', uuid: '0' });

await page.evaluate(async ({ dataUri }) => {
  document.querySelector('.nav-item[data-view="accounts"]').click();
  const { renderSkin } = await import('./js/skinview.js');
  renderSkin(document.getElementById('skin-stage'), { dataUri });
  document.getElementById('skin-source').textContent = 'Encontrada en Mojang por tu nombre de jugador.';
}, { dataUri: skin.dataUri });

await page.waitForTimeout(900);

const caja = await pixelesPintados(page, '#skin-stage');
await mkdir(outDir, { recursive: true });

// Recorte del marco del personaje: es lo unico que interesa comprobar.
const recorte = await page.screenshot({ clip: caja });
await writeFile(path.join(outDir, 'skin-playwright.png'), recorte);

await writeFile(path.join(outDir, 'accounts-playwright.png'), await page.screenshot());

// Comparar contra el mismo marco vacio para saber si de verdad se dibujo algo.
await page.evaluate(() => document.getElementById('skin-stage').replaceChildren());
await page.waitForTimeout(200);
const vacio = await page.screenshot({ clip: caja });

const distintos = Buffer.compare(recorte, vacio) !== 0;
console.log(`  marco del personaje: ${caja.width}x${caja.height}`);
console.log(`  se dibujo algo dentro: ${distintos ? 'SI' : 'NO'}`);
console.log(`  captura: ${path.join(outDir, 'skin-playwright.png')}`);

await page.evaluate(() => document.querySelector('.nav-item[data-view="play"]').click());
await page.waitForTimeout(300);
await writeFile(path.join(outDir, 'play-playwright.png'), await page.screenshot());

if (problemas.length) {
  console.log('  PROBLEMAS:');
  for (const p of problemas) console.log(`    - ${p.slice(0, 160)}`);
}

await browser.close();
server.close();
process.exit(distintos && !problemas.length ? 0 : 1);
