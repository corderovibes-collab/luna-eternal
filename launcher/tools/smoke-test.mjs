/**
 * Prueba de humo del núcleo del launcher, sin Electron y sin interfaz.
 *
 * Cubre lo que de verdad puede romperse en silencio: el lector de ZIP escrito a
 * mano, el filtrado de librerías por reglas de sistema operativo, el UUID offline
 * (si no coincide con el del servidor, los jugadores pierden el progreso) y que
 * los metadatos reales de Mojang y Fabric se resuelvan.
 *
 * Uso:  node tools/smoke-test.mjs
 */
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, rm, readdir, stat, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';

process.env.LUNA_ROOT ??= path.join(os.tmpdir(), 'luna-smoke');

const { extractZip } = await import('../src/main/core/zip.js');
const { resolveLibraries, allowed, fetchVersionJson } = await import('../src/main/core/minecraft.js');
const { fetchFabricProfile } = await import('../src/main/core/fabric.js');
const { offlineUuid, createOfflineAccount } = await import('../src/main/core/auth.js');
const { download } = await import('../src/main/core/net.js');
const { diagnosticar } = await import('../src/main/core/diagnostico.js');
const { syncPack } = await import('../src/main/core/pack.js');
const { paths } = await import('../src/main/core/paths.js');

let passed = 0;
let failed = 0;

async function test(name, fn) {
  try {
    await fn();
    console.log(`  OK    ${name}`);
    passed++;
  } catch (err) {
    console.log(`  FALLO ${name}\n        ${err.message}`);
    failed++;
  }
}

console.log('\n== UUID offline ==');
await test('coincide con la fórmula del servidor de Minecraft', () => {
  // UUID v3 sobre "OfflinePlayer:<nombre>". Contrastado con una implementación
  // independiente (uuid.UUID(bytes=md5(...), version=3) de Python).
  assert.equal(offlineUuid('Notch'), 'b50ad385-829d-3141-a216-7e7d7539ba7f');
  assert.equal(offlineUuid('jeb_'), 'a762f560-4fce-3236-812a-b80efff0b62b');
  assert.equal(offlineUuid('Ash'), '4491e473-c7c9-3195-a8de-330c79a24db4');
});

await test('es estable y sensible al nombre', () => {
  assert.equal(offlineUuid('Ash'), offlineUuid('Ash'));
  assert.notEqual(offlineUuid('Ash'), offlineUuid('ash'));
});

await test('rechaza nombres inválidos', () => {
  assert.throws(() => createOfflineAccount('a'));
  assert.throws(() => createOfflineAccount('nombre con espacios'));
  assert.throws(() => createOfflineAccount('demasiado_largo_para_minecraft'));
  assert.equal(createOfflineAccount('Entrenador_1').name, 'Entrenador_1');
});

console.log('\n== Requisitos del equipo ==');
const { preflight } = await import('../src/main/core/preflight.js');
const REAL_GPU = 'ANGLE (Intel, Intel(R) UHD Graphics 620 (0x00003EA0) Direct3D11 vs_5_0, D3D11-31.0)';

await test('comprueba las seis cosas que pueden faltar', async () => {
  const report = await preflight({ gpuRenderer: REAL_GPU });
  assert.equal(report.checks.length, 6);
  const ids = report.checks.map((c) => c.id);
  assert.deepEqual(ids, ['so', 'arch', 'vcredist', 'ram', 'disco', 'gpu']);
  assert.ok(report.checks.every((c) => c.title && c.detail));
});

await test('el renderizado por software avisa pero no impide jugar', async () => {
  // Avisa, no bloquea: lo que se lee es el renderizador de Chromium, y cae a software
  // en equipos donde Minecraft si funciona. Bloquear dejaba fuera a gente que podia jugar.
  for (const renderer of ['Google SwiftShader', 'Microsoft Basic Render Driver', 'llvmpipe (LLVM 15)']) {
    const report = await preflight({ gpuRenderer: renderer });
    assert.ok(report.warnings.some((c) => c.id === 'gpu'), `deberia avisar con ${renderer}`);
    assert.ok(!report.blocking.some((c) => c.id === 'gpu'), `no deberia bloquear con ${renderer}`);
  }
});

await test('una gráfica real no bloquea y se muestra con nombre legible', async () => {
  const report = await preflight({ gpuRenderer: REAL_GPU });
  const gpu = report.checks.find((c) => c.id === 'gpu');
  assert.equal(gpu.level, 'ok');
  assert.match(gpu.detail, /Intel\(R\) UHD Graphics 620/);
  assert.ok(!gpu.detail.includes('ANGLE'), 'no deberia enseñar la envoltura de ANGLE');
});

await test('el arreglo de Visual C++ se ofrece solo cuando falta', async () => {
  const vc = (await preflight({ gpuRenderer: REAL_GPU })).checks.find((c) => c.id === 'vcredist');
  // En esta máquina está instalado, así que no debe ofrecer acción.
  if (vc.level === 'ok') assert.equal(vc.action, undefined);
  else assert.equal(vc.action, 'install-vcredist');
});

console.log('\n== Reglas de sistema operativo ==');
await test('sin reglas se permite', () => assert.equal(allowed(undefined), true));
await test('deniega lo que no es Windows', () => {
  assert.equal(allowed([{ action: 'allow', os: { name: 'osx' } }]), false);
  assert.equal(allowed([{ action: 'allow', os: { name: 'windows' } }]), true);
});
await test('gana la última regla que encaja', () => {
  assert.equal(allowed([{ action: 'allow' }, { action: 'disallow', os: { name: 'windows' } }]), false);
});
await test('las features se respetan', () => {
  const rules = [{ action: 'allow', features: { is_demo_user: true } }];
  assert.equal(allowed(rules, { is_demo_user: false }), false);
  assert.equal(allowed(rules, { is_demo_user: true }), true);
});

console.log('\n== Metadatos reales de Mojang / Fabric ==');
let versionJson;
await test('descarga el JSON de Minecraft 1.21.1', async () => {
  versionJson = await fetchVersionJson('1.21.1');
  assert.equal(versionJson.id, '1.21.1');
  assert.equal(versionJson.javaVersion.majorVersion, 21, 'el pack exige Java 21');
  assert.ok(versionJson.downloads.client.url);
});

await test('resuelve librerías y natives para Windows', () => {
  const { classpath, natives } = resolveLibraries(versionJson.libraries);
  assert.ok(classpath.length > 20, `classpath demasiado corto: ${classpath.length}`);
  assert.ok(natives.length > 0, 'no se encontró ningún native de LWJGL');
  assert.ok(classpath.every((c) => c.url && c.dest));
  // Ninguna librería de otro sistema debe colarse.
  assert.ok(!classpath.some((c) => /natives-(linux|macos)/.test(c.dest)));
});

await test('el perfil de Fabric 0.18.4 trae la clase principal', async () => {
  const profile = await fetchFabricProfile('1.21.1', '0.18.4');
  assert.match(profile.mainClass, /KnotClient/);
  const { classpath } = resolveLibraries(profile.libraries);
  assert.ok(classpath.length >= 3);
  assert.ok(classpath.every((c) => c.url.startsWith('http')));
});

console.log('\n== Descarga + verificación SHA1 ==');
const tmp = await mkdtemp(path.join(os.tmpdir(), 'cv-test-'));
let jar;
await test('descarga un jar y valida su SHA1', async () => {
  const lib = resolveLibraries(versionJson.libraries).classpath.find((c) => c.sha1 && c.size < 3e6);
  jar = path.join(tmp, 'lib.jar');
  await download(lib.url, jar, { sha1: lib.sha1, size: lib.size });
  assert.equal((await stat(jar)).size, lib.size);
});

await test('un SHA1 incorrecto hace fallar la descarga', async () => {
  const lib = resolveLibraries(versionJson.libraries).classpath.find((c) => c.sha1 && c.size < 3e6);
  await assert.rejects(
    download(lib.url, path.join(tmp, 'malo.jar'), { sha1: '0'.repeat(40) }),
    /SHA1 no coincide/,
  );
});

console.log('\n== Lector de ZIP propio ==');
await test('extrae un jar real y descomprime bien', async () => {
  const out = path.join(tmp, 'extraido');
  const count = await extractZip(jar, out, { filter: (n) => !n.startsWith('META-INF/') });
  assert.ok(count > 0, 'no extrajo nada');
  const entries = await readdir(out);
  assert.ok(entries.length > 0);
});

await test('un fichero OCULTO no tumba la extraccion (Windows)', async () => {
  // ⚠ ESTA PRUEBA FIJA UN FALLO QUE YA LLEGO A UN JUGADOR.
  //
  //   `EPERM: operation not permitted, open '.../euphoria_patcher/.data.json'`
  //
  // En Windows, `writeFile` sobre un fichero con el atributo HIDDEN falla con
  // EPERM: `CreateFile` con CREATE_ALWAYS se niega si el que ya esta ahi esta
  // oculto. No es un permiso del usuario ni un antivirus, y el mensaje no dice
  // en ningun momento que el problema sea que el fichero esta oculto -- por eso
  // costo llegar hasta la causa.
  //
  // El pack trae ese fichero y el mod EuphoriaPatcher lo vuelve a crear OCULTO
  // en el PC del jugador, asi que toda actualizacion moria al 99 %.
  const out = path.join(tmp, 'oculto');
  await extractZip(jar, out, { filter: (n) => n.endsWith('.class'), strip: 1 });

  const dentro = (await readdir(out, { recursive: true, withFileTypes: true }))
    .filter((d) => d.isFile());
  if (!dentro.length) return; // el jar de prueba no dejo ningun fichero suelto

  const victima = path.join(dentro[0].parentPath ?? dentro[0].path, dentro[0].name);
  if (process.platform === 'win32') {
    const { execFile } = await import('node:child_process');
    const { promisify } = await import('node:util');
    await promisify(execFile)('attrib', ['+h', victima]);
  }

  // Sin el arreglo, esto lanza EPERM en Windows y aqui se ve como un fallo.
  await extractZip(jar, out, { filter: (n) => n.endsWith('.class'), strip: 1 });
  assert.ok((await stat(victima)).size >= 0);
});

await test('el filtro y `strip` funcionan', async () => {
  const out = path.join(tmp, 'filtrado');
  const count = await extractZip(jar, out, { filter: (n) => n.endsWith('.class'), strip: 1 });
  assert.ok(count >= 0);
});

console.log('\n== Perfiles: jugador vs constructor ==');

/** Manifiesto mínimo con un mod común y uno solo de constructor. */
const MANIFIESTO = {
  packVersion: 'test',
  files: [
    { path: 'mods/cobblemon.jar', sha1: 'a'.repeat(40), size: 1, url: 'http://x/1' },
    { path: 'mods/axiom.jar', sha1: 'b'.repeat(40), size: 1, url: 'http://x/2',
      profiles: ['constructor'] },
  ],
};

await test('el jugador normal no descarga las herramientas de construcción', async () => {
  const plan = await syncPack(MANIFIESTO, { perfil: 'jugador' });
  const rutas = plan.toDownload.map((d) => d.file.path);
  assert.ok(rutas.includes('mods/cobblemon.jar'));
  assert.ok(!rutas.includes('mods/axiom.jar'),
    'Axiom no puede colarse en el pack de jugador: son 46 MB que no usa');
});

await test('el constructor descarga las dos cosas', async () => {
  const plan = await syncPack(MANIFIESTO, { perfil: 'constructor' });
  assert.equal(plan.toDownload.length, 2);
});

await test('el estado que se guardará solo anota lo del perfil', async () => {
  // `applySync` guarda `plan.nextState`. Antes guardaba `manifest.files`
  // entero, así que un jugador normal quedaba con Axiom anotado como instalado
  // sin haberlo descargado jamás: el fichero de estado mentía.
  const plan = await syncPack(MANIFIESTO, { perfil: 'jugador' });
  assert.deepEqual(Object.keys(plan.nextState), ['mods/cobblemon.jar'],
    'anotar como instalado algo que no se ha bajado engaña al atajo del '
    + 'siguiente arranque');
});

await test('el atajo se fía de installed.json; reparar no', async () => {
  // Se deja en disco un fichero del tamaño correcto pero con contenido que NO
  // corresponde a su SHA1, y `installed.json` diciendo que está bien.
  const sha1 = 'c'.repeat(40);
  const M = {
    packVersion: 't',
    files: [{ path: 'mods/x.jar', sha1, size: 4, url: 'http://x/9' }],
  };
  const jar = path.join(paths.instance, 'mods', 'x.jar');
  await mkdir(path.dirname(jar), { recursive: true });
  await writeFile(jar, 'AAAA');
  await writeFile(paths.state, JSON.stringify({ files: { 'mods/x.jar': sha1 } }));

  try {
    const rapido = await syncPack(M, { perfil: 'jugador' });
    assert.equal(rapido.toDownload.length, 0,
      'el arranque normal no puede releer y resumir los 185 MB del pack');

    const reparar = await syncPack(M, { perfil: 'jugador', forzar: true });
    assert.equal(reparar.toDownload.length, 1,
      'reparar existe justo para cazar un jar corrupto del tamaño correcto');
  } finally {
    // Las pruebas siguientes cuentan con que no hay nada instalado.
    await writeFile(paths.state, JSON.stringify({ version: null, files: {} }));
    await rm(jar, { force: true });
  }
});

console.log('\n== Los ajustes del jugador son SUYOS ==');

/**
 * Es el fallo que el launcher anterior tuvo de verdad: actualizar el pack le
 * borraba a la gente sus controles y sus opciones de vídeo. Aquí no puede
 * pasar porque el manifiesto no declara ninguno de esos ficheros — y esta
 * prueba existe para que siga siendo así el día que alguien añada `config/`
 * sin pensarlo.
 */
const AJUSTES_DEL_JUGADOR = [
  'options.txt', 'optionsof.txt', 'servers.dat', 'usercache.json',
  'config/', 'saves/', 'screenshots/', 'schematics/', 'shaderpacks/',
];

await test('ningún fichero del manifiesto pisa los ajustes del jugador', async () => {
  const manifiesto = await (await import('../src/main/core/pack.js'))
    .fetchManifest('https://raw.githubusercontent.com/corderovibes-collab/'
      + 'luna-eternal-pack/master/manifest.json');

  for (const f of manifiesto.files) {
    const esDelJugador = AJUSTES_DEL_JUGADOR.some(
      (p) => f.path === p || f.path.startsWith(p));
    if (!esDelJugador) continue;
    // Se puede declarar, pero SOLO con una de las dos garantías:
    //
    //   `once`         fichero suelto: se escribe si falta y no se toca más
    //   `keepExisting` carpeta en un zip: se extrae SIN pisar lo que ya exista
    //
    // La segunda apareció al pasar la base a CobbleVerse: su configuración son
    // 155 ficheros, y sueltos eran 155 peticiones a raw.githubusercontent, que
    // contesta 429. Da la MISMA garantía fichero a fichero — y además arregla
    // lo que `once` hacía mal: un fichero de configuración NUEVO en una versión
    // posterior sí llega, porque todavía no existe.
    assert.equal(f.once === true || f.keepExisting === true, true,
      `"${f.path}" es un ajuste del jugador y no está protegido: `
      + 'actualizar el pack se lo borraría');
  }
});

await test('un fichero no gestionado nunca se marca para borrar', async () => {
  // `stale` solo puede tocar mods/, config/, resourcepacks/, shaderpacks/ y
  // datapacks/. Si alguna vez alcanzara options.txt o saves/, borraría mundos.
  const plan = await syncPack({ packVersion: 't', files: [] }, { perfil: 'jugador' });
  assert.deepEqual(plan.stale, [],
    'sin nada instalado no puede haber nada que borrar');
});

console.log('\n== Interfaz: lo oculto tiene que estar oculto ==');
await test('el atributo `hidden` gana sobre cualquier display propio', async () => {
  // Fallo real, visto en una captura del usuario: `.updatebar { display: flex }`
  // pisa el `[hidden] { display: none }` de la hoja del navegador por
  // especificidad, así que el aviso de actualización salía SIEMPRE con su texto
  // de plantilla. Ningún `show(node, false)` funcionaba en esos elementos, y no
  // da ningún error: solo se ve mal.
  const { readFile } = await import('node:fs/promises');
  const css = await readFile(
    new URL('../src/renderer/css/app.css', import.meta.url), 'utf8');
  assert.match(css, /\[hidden\]\s*\{[^}]*display:\s*none\s*!important/,
    'Falta la regla global [hidden] { display: none !important }');
});

console.log('\n== Diagnóstico de cierres ==');
await test('una salida limpia no inventa un problema', () => {
  assert.equal(diagnosticar(0, 'todo bien'), null);
});

await test('reconoce un jar corrupto y ofrece reparar', () => {
  // El caso real de este proyecto: el stack estaba lleno de netty y la causa
  // era un jar que cambió bajo los pies del proceso.
  const causa = diagnosticar(1, 'java.util.zip.ZipException: ZipFile invalid LOC header');
  assert.equal(causa.accion, 'reparar');
});

await test('la línea normal de la GPU no dispara ninguna alarma', () => {
  // Fallo real, visto en el launcher del usuario: saltaba
  // "LA TARJETA GRÁFICA NO ARRANCA EL JUEGO" en cada arranque correcto porque
  // el patrón llevaba `OpenGL 3.2` a secas, y esta línea de ÉXITO lo contiene.
  const log = '[19:36:10] [Render thread/INFO]: GPU: Intel(R) UHD Graphics 620 '
    + '(Supports OpenGL 3.2.0 - Build 31.0.101.2135)';
  assert.equal(diagnosticar(0, log), null,
    'un diagnostico que se equivoca siempre ensena a ignorar los avisos');
});

await test('sí reconoce una GPU que de verdad no llega', () => {
  const causa = diagnosticar(1,
    'GLFW error 65543: WGL: Driver does not support OpenGL version 3.2');
  assert.ok(causa && /gráfica/i.test(causa.titulo), 'esto si es un fallo de verdad');
});

await test('reconoce quedarse sin memoria y manda a Ajustes', () => {
  assert.equal(diagnosticar(1, 'java.lang.OutOfMemoryError: Java heap space').accion, 'ram');
});

await test('el registro manda sobre el código de salida', () => {
  // Un código conocido NO puede tapar una causa que el log dice a las claras.
  const causa = diagnosticar(-1073741819, 'Caused by: java.lang.OutOfMemoryError');
  assert.equal(causa.accion, 'ram');
});

await test('un cierre desconocido explica que no se sabe, en vez de callar', () => {
  const causa = diagnosticar(99, 'nada reconocible');
  assert.ok(causa.titulo.includes('99'));
  assert.equal(causa.accion, 'ninguna');
});

// ---------------------------------------------------------------------------
// Distribucion: espejos y puntero
//
// Todo esto se prueba contra un servidor LOCAL y no contra GitHub. No es por
// velocidad: es que los casos que importan son "el origen contesta 404" y "el
// manifiesto llega manipulado", y esos no se pueden provocar a voluntad contra
// un CDN de verdad. Un fallo aqui tiene que significar que el codigo esta mal,
// nunca que GitHub tenia un mal dia.
console.log('\n== Distribucion: espejos y puntero ==');

const { createServer } = await import('node:http');
const { createHash } = await import('node:crypto');

/** Servidor de usar y tirar. `rutas` es {ruta: string | {status, cuerpo}}. */
async function servidor(rutas) {
  const pedidas = [];
  const srv = createServer((req, res) => {
    pedidas.push(req.url);
    const r = rutas[req.url];
    if (r === undefined) { res.writeHead(404); res.end('no'); return; }
    const { status = 200, cuerpo = r } = typeof r === 'object' ? r : {};
    res.writeHead(status, { 'Content-Type': 'application/json' });
    res.end(typeof r === 'string' ? r : cuerpo);
  });
  await new Promise((ok) => srv.listen(0, '127.0.0.1', ok));
  return { base: `http://127.0.0.1:${srv.address().port}`, pedidas,
           cerrar: () => new Promise((ok) => srv.close(ok)) };
}

const MINI = { packVersion: '9.9.9', files: [{ path: 'mods/x.jar', sha1: 'aa', size: 1, url: 'http://x/y' }] };
const MINI_TXT = JSON.stringify(MINI);
const MINI_SHA = createHash('sha1').update(MINI_TXT).digest('hex');

await test('un 404 en el primario NO tumba la descarga: tira del espejo', async () => {
  const s = await servidor({ '/bueno.txt': 'contenido' });
  try {
    const destino = path.join(tmp, 'espejo.txt');
    // El primario ni siquiera existe como ruta: 404 duro, que antes era `fatal`
    // y abortaba sin llegar a preguntarle al segundo origen.
    await download([`${s.base}/no-existe.txt`, `${s.base}/bueno.txt`], destino);
    assert.equal((await stat(destino)).size, 'contenido'.length);
  } finally { await s.cerrar(); }
});

await test('sin espejo, un 404 sigue fallando en seco', async () => {
  const s = await servidor({});
  try {
    await assert.rejects(() => download(`${s.base}/nada`, path.join(tmp, 'nada.txt')));
  } finally { await s.cerrar(); }
});

const { fetchManifest } = await import('../src/main/core/pack.js');

await test('el puntero lleva al manifiesto vigente', async () => {
  const s = await servidor({ '/latest.json': null, '/m.json': MINI_TXT });
  s.pedidas.length = 0;
  const s2 = await servidor({
    '/latest.json': JSON.stringify({ manifest: `${s.base}/m.json`, sha1: MINI_SHA }),
  });
  try {
    const m = await fetchManifest(`${s2.base}/latest.json`);
    assert.equal(m.packVersion, '9.9.9');
    assert.equal(m.files.length, 1);
  } finally { await s.cerrar(); await s2.cerrar(); }
});

await test('un manifiesto que no cuadra con su huella SE RECHAZA', async () => {
  // ⚠ ESTA ES LA PRUEBA QUE IMPORTA DE LAS CUATRO.
  //   El manifiesto elige de que URL salen los 185 MB que se instalan, o sea lo
  //   que acaba EJECUTANDOSE en la maquina del jugador. Sin la comprobacion de
  //   huella, cualquiera que pueda colocarle un JSON --un DNS envenenado, el
  //   proxy de un wifi publico-- le elige los mods.
  const s = await servidor({ '/m.json': JSON.stringify({ ...MINI, packVersion: 'manipulado' }) });
  const s2 = await servidor({
    '/latest.json': JSON.stringify({ manifest: `${s.base}/m.json`, sha1: MINI_SHA }),
  });
  try {
    await assert.rejects(() => fetchManifest(`${s2.base}/latest.json`), /huella/i);
  } finally { await s.cerrar(); await s2.cerrar(); }
});

await test('un manifiesto directo sigue valiendo (launchers 1.0.x)', async () => {
  // Compatibilidad hacia atras: quien tenga la URL vieja guardada en su
  // configuracion recibe el manifiesto entero, sin puntero de por medio.
  const s = await servidor({ '/manifest.json': MINI_TXT });
  try {
    assert.equal((await fetchManifest(`${s.base}/manifest.json`)).packVersion, '9.9.9');
  } finally { await s.cerrar(); }
});

await rm(tmp, { recursive: true, force: true });
await rm(process.env.LUNA_ROOT, { recursive: true, force: true });

console.log(`\n${failed === 0 ? 'TODO CORRECTO' : 'HAY FALLOS'} — ${passed} pasadas, ${failed} fallidas\n`);
process.exit(failed === 0 ? 0 : 1);
