/**
 * Abre la ventana, captura una imagen y sale.
 *
 * Sirve para comprobar que la interfaz renderiza de verdad (tipografías incluidas)
 * y para revisar el diseño sin tener que jugar. Los errores de consola del
 * renderer se reenvían aquí, que es donde se esconden los fallos de CSP y las
 * rutas de fuentes mal puestas.
 *
 * Uso:  npx electron tools/preview.mjs [vista]      # play | accounts | settings
 */
import { app, BrowserWindow } from 'electron';
import { writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = path.dirname(fileURLToPath(import.meta.url));
const view = process.argv[2] ?? 'play';
const outDir = path.join(dir, '..', 'preview');
const problems = [];

app.whenReady().then(async () => {
  const win = new BrowserWindow({
    width: 1180,
    height: 720,
    show: false,
    backgroundColor: '#0b1118',
    webPreferences: {
      preload: path.join(dir, '..', 'src', 'preload', 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  });

  win.webContents.on('console-message', (_e, level, message) => {
    if (level >= 2) problems.push(message);
  });
  win.webContents.on('did-fail-load', (_e, code, desc) => problems.push(`carga fallida: ${desc} (${code})`));

  await win.loadFile(path.join(dir, '..', 'src', 'renderer', 'index.html'));
  await win.webContents.executeJavaScript(
    "window.__csp = 0; document.addEventListener('securitypolicyviolation', e => { window.__csp++; console.error('CSP: ' + e.violatedDirective + ' <- ' + e.blockedURI.slice(0,40)); });"
  );

  // Datos de ejemplo: el preview no habla con el proceso real ni con la red.
  // La skin se resuelve en el proceso principal y se inyecta como data URI.
  process.env.LUNA_ROOT ??= path.join(outDir, 'tmp-root');
  const { resolveSkin } = await import('../src/main/core/skin.js');
  const demo = await resolveSkin({ id: 'offline:notch', type: 'offline', name: 'Notch', uuid: '0' });
  await win.webContents.executeJavaScript(`window.__skin = ${JSON.stringify(demo?.dataUri ?? null)};`);

  await win.webContents.executeJavaScript(`
    (async () => {
      const set = (id, text) => { const n = document.getElementById(id); if (n) n.textContent = text; };
      const lamp = (id, state, text) => {
        const n = document.getElementById(id);
        if (!n) return;
        n.dataset.state = state;
        n.querySelector('dd').textContent = text;
      };
      set('stat-pack', '1.7.42');
      set('stat-players', '4/40');
      set('stat-server', 's17.mia.us.tarohosting.lat:33445');
      set('stat-ram', '6 GB');
      set('app-info', 'v1.0.0');
      lamp('lamp-account', 'ok', 'Entrenador_1');
      lamp('lamp-pack', 'ok', 'Versión 1.7.42');
      lamp('lamp-server', 'ok', 'En línea · 4 jugando');
      document.getElementById('who').hidden = false;
      set('who-avatar', 'E'); set('who-name', 'Entrenador_1'); set('who-kind', 'Offline');
      const reqs = [
        { level:'ok',    title:'Windows',              detail:'10.0.19045 · x64' },
        { level:'ok',    title:'Arquitectura',         detail:'64 bits' },
        { level:'error', title:'Visual C++ 2015-2022', detail:'Falta el runtime de Microsoft que necesita Minecraft (vcruntime140.dll). El launcher puede instalarlo: son 25 MB y Windows pedira permiso de administrador.', action:'install-vcredist' },
        { level:'ok',    title:'Memoria del equipo',   detail:'15.4 GB' },
        { level:'warn',  title:'Espacio en disco',     detail:'4.2 GB libres. Va justo; con mundos y capturas se llena.' },
        { level:'ok',    title:'Tarjeta grafica',      detail:'Intel(R) UHD Graphics 620' },
      ];
      const ICON = { ok:'✓', warn:'!', error:'✕' };
      const list = document.getElementById('req-list');
      for (const c of reqs) {
        const li = document.createElement('li');
        li.className = 'req'; li.dataset.level = c.level;
        const ic = document.createElement('span'); ic.className='req-icon'; ic.textContent=ICON[c.level];
        const info = document.createElement('div');
        const t = document.createElement('strong'); t.textContent = c.title;
        const d = document.createElement('span'); d.textContent = c.detail;
        info.append(t, d); li.append(ic, info);
        if (c.action) { const b=document.createElement('button'); b.className='btn'; b.textContent='Instalar ahora'; li.append(b); }
        list.append(li);
      }
      document.querySelector('.nav-item[data-view="requisitos"]').classList.add('has-issue');
      if (window.__skin) {
        const { renderSkin } = await import('./js/skinview.js');
        renderSkin(document.getElementById('skin-stage'), { dataUri: window.__skin });
        document.getElementById('skin-source').textContent = 'Encontrada en Mojang por tu nombre de jugador.';
        document.getElementById('btn-skin-clear').hidden = false;
        document.getElementById('btn-skin').disabled = false;
      }
      const target = document.querySelector('.nav-item[data-view="${view}"]');
      if (target) target.click();
      // Comprobar que las fuentes empaquetadas han cargado de verdad.
      const box = (sel) => { const n = document.querySelector(sel); const b = n.getBoundingClientRect();
        return sel + ' top=' + Math.round(b.top) + ' alto=' + Math.round(b.height); };
      window.__boxes = ['viewport=' + window.innerHeight, box('.device'), box('.stage'), box('.actionbar')];
      return document.fonts.ready.then(() => [...document.fonts].map(f => f.family + ' ' + f.weight + ' ' + f.status));
    })()
  `).then((fonts) => {
    const bad = fonts.filter((f) => !f.endsWith('loaded'));
    if (bad.length) problems.push(`tipografías sin cargar: ${bad.join(', ')}`);
    else console.log(`  tipografías cargadas: ${fonts.length}`);
  });

  await new Promise((r) => setTimeout(r, 600));


  const boxes = await win.webContents.executeJavaScript('window.__boxes');
  console.log('  cajas: ' + boxes.join(' | '));
  const image = await win.webContents.capturePage();
  await mkdir(outDir, { recursive: true });
  const file = path.join(outDir, `${view}.png`);
  await writeFile(file, image.toPNG());
  console.log(`  captura: ${file}`);
  console.log('  diag TRAS captura: ' + JSON.stringify(await win.webContents.executeJavaScript(`
    (() => {
      const stage = document.getElementById('skin-stage');
      const caras = [...stage.querySelectorAll('i')];
      const r = caras.map(c => c.getBoundingClientRect());
      const sr = stage.getBoundingClientRect();
      return {
        n: caras.length,
        extent: r.length ? {
          x0: Math.round(Math.min(...r.map(b => b.left)) - sr.left),
          x1: Math.round(Math.max(...r.map(b => b.right)) - sr.left),
          y0: Math.round(Math.min(...r.map(b => b.top)) - sr.top),
          y1: Math.round(Math.max(...r.map(b => b.bottom)) - sr.top),
        } : null,
        stage: { w: Math.round(sr.width), h: Math.round(sr.height) },
        grandes: r.filter(b => b.width > 20 && b.height > 20).length,
        violaciones: window.__csp || 0,
      };
    })()
  `)));


  if (problems.length) {
    console.log('  PROBLEMAS:');
    for (const p of problems) console.log(`    - ${p}`);
  } else {
    console.log('  sin errores de consola ni de CSP');
  }

  app.exit(problems.length ? 1 : 0);
});
