import { access, statfs, rm } from 'node:fs/promises';
import { execFile } from 'node:child_process';
import os from 'node:os';
import path from 'node:path';
import { promisify } from 'node:util';
import { download } from './net.js';
import { paths } from './paths.js';

const run = promisify(execFile);

/**
 * Comprobación de requisitos del equipo antes de instalar o jugar.
 *
 * El launcher se trae Java, Minecraft y Fabric, pero hay cosas que no puede
 * traerse y que son la causa habitual de "no me arranca" en un Windows recién
 * formateado: el runtime de Visual C++ que necesitan los natives de LWJGL,
 * drivers de gráfica de verdad y sitio en disco. Vale más decirlo con nombre y
 * apellidos que dejar que el juego se cierre con un error incomprensible.
 *
 * Este módulo no importa Electron a propósito (la info de GPU se le pasa desde
 * fuera), para poder probarlo con node a secas.
 */

// Windows 10 1809: es el mínimo que soportan Electron 43 y Minecraft 1.21.
const MIN_BUILD = 17763;
const NEEDED_GB = 3;
const COMFY_GB = 6;

const VC_DLLS = ['vcruntime140.dll', 'vcruntime140_1.dll', 'msvcp140.dll'];
const VC_REDIST_URL = 'https://aka.ms/vs/17/release/vc_redist.x64.exe';

const ok = (id, title, detail) => ({ id, level: 'ok', title, detail });
const warn = (id, title, detail, action) => ({ id, level: 'warn', title, detail, action });
const bad = (id, title, detail, action) => ({ id, level: 'error', title, detail, action });

async function checkWindows() {
  if (process.platform !== 'win32') {
    return bad('so', 'Sistema operativo', 'Este launcher es solo para Windows.');
  }
  const build = Number(os.release().split('.')[2] ?? 0);
  if (build && build < MIN_BUILD) {
    return bad('so', 'Versión de Windows',
      `Necesitas Windows 10 (build ${MIN_BUILD}) o superior. El tuyo es ${os.release()}.`);
  }
  return ok('so', 'Windows', `${os.release()} · ${process.arch}`);
}

function checkArch() {
  if (process.arch === 'x64') return ok('arch', 'Arquitectura', '64 bits');
  if (process.arch === 'arm64') {
    return warn('arch', 'Arquitectura ARM',
      'Windows ARM funciona por emulación, pero irá más lento y algún mod puede fallar.');
  }
  return bad('arch', 'Arquitectura',
    'Minecraft 1.21.1 necesita un Windows de 64 bits y el tuyo es de 32.');
}

/** Los natives de LWJGL enlazan contra el runtime de Visual C++; sin él, el juego cierra al abrir. */
async function checkVisualCpp() {
  const system32 = path.join(process.env.SystemRoot ?? process.env.windir ?? 'C:\\Windows', 'System32');
  const missing = [];
  for (const dll of VC_DLLS) {
    try {
      await access(path.join(system32, dll));
    } catch {
      missing.push(dll);
    }
  }
  if (!missing.length) return ok('vcredist', 'Visual C++ 2015-2022', 'Instalado');

  return bad('vcredist', 'Visual C++ 2015-2022',
    `Falta el runtime de Microsoft que necesita Minecraft (${missing.join(', ')}). `
    + 'El launcher puede instalarlo: son 25 MB y Windows pedirá permiso de administrador.',
    'install-vcredist');
}

async function checkDisk() {
  try {
    // En la primera ejecución la carpeta del launcher aún no existe: se mide la
    // unidad donde va a vivir, que siempre está.
    const target = await access(paths.root).then(() => paths.root, () => path.parse(paths.root).root);
    const stats = await statfs(target);
    const freeGb = (stats.bavail * stats.bsize) / 1024 ** 3;
    const detail = `${freeGb.toFixed(1)} GB libres`;
    if (freeGb < NEEDED_GB) {
      return bad('disco', 'Espacio en disco',
        `${detail}. Hacen falta al menos ${NEEDED_GB} GB para Minecraft y el modpack.`);
    }
    if (freeGb < COMFY_GB) {
      return warn('disco', 'Espacio en disco', `${detail}. Va justo; con mundos y capturas se llena.`);
    }
    return ok('disco', 'Espacio en disco', detail);
  } catch {
    return ok('disco', 'Espacio en disco', 'No se ha podido comprobar');
  }
}

function checkMemory() {
  const gb = os.totalmem() / 1024 ** 3;
  const detail = `${gb.toFixed(1)} GB`;
  if (gb < 6) {
    // Tambien aviso: con 4-6 GB el pack va mal, pero arranca. Quien tenga un equipo
    // justo prefiere jugar a tirones antes que no jugar, y esa decision es suya.
    return warn('ram', 'Memoria del equipo',
      `${detail}. Es poco para este modpack: va a ir a tirones. Baja la RAM asignada en `
      + 'Ajustes a 3 GB y cierra el navegador antes de jugar.');
  }
  if (gb < 8) {
    return warn('ram', 'Memoria del equipo', `${detail}. Justo: asigna 4 GB al juego y cierra todo lo demás.`);
  }
  return ok('ram', 'Memoria del equipo', detail);
}

/**
 * Detecta si Windows está tirando de renderizado por software.
 *
 * Pasa en equipos recién formateados sin los drivers de la gráfica: Minecraft
 * necesita OpenGL 3.2 de verdad y con el adaptador básico ni abre.
 */
function checkGpu(renderer) {
  if (!renderer) return ok('gpu', 'Tarjeta gráfica', 'No se ha podido leer');
  if (/swiftshader|software|basic (render|display)|llvmpipe/i.test(renderer)) {
    // Aviso, no bloqueo. Lo que se lee aqui es el renderizador de **Chromium**, no
    // el de Minecraft, y Chromium cae a software por motivos que al juego no le
    // afectan: graficas antiguas que lleva en lista negra, aceleracion desactivada
    // por politica, o drivers que el navegador no acepta pero OpenGL si. Minecraft
    // habla con OpenGL directamente, asi que en la mayoria de estos casos arranca.
    // Bloquear aqui dejaba fuera a gente cuyo equipo si podia jugar.
    return warn('gpu', 'Tarjeta gráfica',
      'El launcher no detecta aceleración por hardware. Puede ser que falten los drivers '
      + '(se instalan desde la web de NVIDIA, AMD o Intel), o simplemente que tu gráfica sea '
      + 'antigua para el navegador pero válida para Minecraft. Puedes intentar jugar: si el '
      + 'juego no abre, entonces sí son los drivers.');
  }
  // Chromium lo envuelve así: "ANGLE (<fabricante>, <modelo> (0x1234) Direct3D11 ..., <driver>)".
  // Se trocea por campos en vez de con una regex: el modelo lleva paréntesis
  // propios ("Intel(R) UHD Graphics 620") y cualquier patrón goloso se atraganta.
  const inner = renderer.match(/^ANGLE \((.*)\)$/s)?.[1];
  if (!inner) return ok('gpu', 'Tarjeta gráfica', renderer.trim());

  const fields = inner.split(', ');
  const model = (fields[1] ?? fields[0]).replace(/\s*\(0x[0-9a-f]+\).*$/i, '').trim();
  return ok('gpu', 'Tarjeta gráfica', model || renderer.trim());
}

export async function preflight({ gpuRenderer } = {}) {
  const checks = [
    await checkWindows(),
    checkArch(),
    await checkVisualCpp(),
    checkMemory(),
    await checkDisk(),
    checkGpu(gpuRenderer),
  ];
  return {
    checks,
    blocking: checks.filter((c) => c.level === 'error'),
    warnings: checks.filter((c) => c.level === 'warn'),
  };
}

/**
 * Instala el redistribuible oficial de Microsoft.
 *
 * Se descarga de aka.ms (dominio de Microsoft) y se lanza en modo pasivo: el
 * usuario ve la barra de progreso y el aviso de administrador, nada silencioso.
 */
export async function installVisualCpp(onProgress) {
  const installer = path.join(paths.cache, 'vc_redist.x64.exe');
  onProgress?.({ phase: 'requisitos', message: 'Descargando Visual C++…', progress: 0 });
  await download(VC_REDIST_URL, installer, {
    onChunk: () => onProgress?.({ phase: 'requisitos', message: 'Descargando Visual C++…' }),
  });

  onProgress?.({ phase: 'requisitos', message: 'Instalando Visual C++…', progress: 0.7 });
  try {
    await run(installer, ['/install', '/passive', '/norestart']);
  } catch (err) {
    // 1638 = ya hay una versión igual o más nueva; 3010 = instalado, pide reinicio.
    if (![1638, 3010].includes(err.code)) {
      throw new Error('No se pudo instalar Visual C++. Descárgalo a mano desde ' + VC_REDIST_URL);
    }
  }
  await rm(installer, { force: true });

  const result = await checkVisualCpp();
  if (result.level !== 'ok') {
    throw new Error('Visual C++ se instaló pero Windows aún no lo ve. Reinicia el equipo y vuelve a probar.');
  }
  return result;
}
