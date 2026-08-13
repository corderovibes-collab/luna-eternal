import { execFile } from 'node:child_process';
import { access, mkdir, readdir, rm } from 'node:fs/promises';
import { constants } from 'node:fs';
import path from 'node:path';
import { promisify } from 'node:util';
import { download, getJson } from './net.js';
import { paths } from './paths.js';
import {
  ARCH_ADOPTIUM, ES_WINDOWS, adoptium, homeDentroDelPaquete,
  javaConConsola, javaSinConsola,
} from './plataforma.js';
import { extractZip } from './zip.js';

const run = promisify(execFile);
const REQUIRED_MAJOR = 21;

// `javaw` solo existe en Windows; en Mac y Linux `java` no abre consola igual.
const exe = javaSinConsola;
const exeConsole = javaConConsola;

/**
 * Descomprime lo que baja Adoptium.
 *
 * Windows recibe un `.zip` y se usa el extractor propio. Mac y Linux reciben un
 * `.tar.gz`, que `extractZip` no sabe leer — pero los dos traen `tar` de serie,
 * y el de macOS (BSD tar) descomprime gzip sin ayuda. Se delega en el sistema
 * en vez de meter una dependencia solo para esto.
 */
async function descomprimirRuntime(archivo, destino) {
  if (ES_WINDOWS) {
    await extractZip(archivo, destino, { strip: 1 });
    return;
  }
  await mkdir(destino, { recursive: true });
  await run('tar', ['-xzf', archivo, '-C', destino, '--strip-components=1']);
}

async function majorVersionOf(javaExe) {
  try {
    // `java -version` escribe en stderr; el formato es `openjdk version "21.0.4"`.
    const { stderr, stdout } = await run(javaExe, ['-version']);
    const m = `${stderr}${stdout}`.match(/version "(\d+)(?:\.(\d+))?/);
    if (!m) return null;
    const major = Number(m[1]);
    return major === 1 ? Number(m[2]) : major; // "1.8" -> 8
  } catch {
    return null;
  }
}

/** JRE ya descargado por el launcher en una ejecución anterior. */
async function findManaged() {
  try {
    for (const dir of await readdir(paths.runtime)) {
      // En Mac el JAVA_HOME real cuelga de `Contents/Home` dentro del paquete.
      const home = homeDentroDelPaquete(path.join(paths.runtime, dir));
      if (await majorVersionOf(exeConsole(home)) === REQUIRED_MAJOR) return home;
    }
  } catch { /* runtime/ aún no existe */ }
  return null;
}

/** Java del sistema, si por casualidad ya es la versión correcta. */
async function findSystem() {
  const candidates = [process.env.JAVA_HOME].filter(Boolean);
  for (const home of candidates) {
    try {
      await access(exeConsole(home), constants.X_OK);
      if (await majorVersionOf(exeConsole(home)) === REQUIRED_MAJOR) return home;
    } catch { /* siguiente */ }
  }
  return null;
}

/**
 * Devuelve la ruta a un Java 21, descargándolo de Adoptium si hace falta.
 *
 * Minecraft 1.21.1 necesita Java 21 exacto: con 17 no arranca y con 25 fallan
 * mixins de varios mods del pack. Por eso no se acepta "cualquier Java nuevo".
 */
export async function ensureJava(onProgress = () => {}) {
  const existing = (await findManaged()) ?? (await findSystem());
  if (existing) return { home: existing, javaw: exe(existing), java: exeConsole(existing) };

  onProgress({ phase: 'java', message: 'Descargando Java 21…', progress: 0 });

  // Adoptium nombra las arquitecturas distinto que Node.
  if (!ARCH_ADOPTIUM) throw new Error(`No hay un Java 21 de Adoptium para ${process.arch}`);
  const { os: sistema } = adoptium();
  const api = 'https://api.adoptium.net/v3/assets/latest/21/hotspot'
    + `?architecture=${ARCH_ADOPTIUM}&image_type=jre&os=${sistema}&vendor=eclipse`;
  const assets = await getJson(api);
  const asset = assets.find((a) => a.binary?.package?.link);
  if (!asset) {
    throw new Error(`Adoptium no devolvió ningún JRE 21 para ${sistema} ${ARCH_ADOPTIUM}`);
  }

  const pkg = asset.binary.package;
  const zipPath = path.join(paths.cache, pkg.name);
  let done = 0;

  await download(pkg.link, zipPath, {
    // Adoptium publica SHA-256, no SHA-1 como Modrinth o Mojang.
    sha256: pkg.checksum,
    size: pkg.size,
    onChunk: (n) => {
      done += n;
      onProgress({
        phase: 'java',
        message: 'Descargando Java 21…',
        progress: pkg.size ? done / pkg.size : 0,
      });
    },
  });

  onProgress({ phase: 'java', message: 'Instalando Java 21…', progress: 1 });
  // Los paquetes de Adoptium traen todo bajo `jdk-21.../`: se quita ese nivel.
  const paquete = path.join(paths.runtime, 'jre21');
  await rm(paquete, { recursive: true, force: true });
  await descomprimirRuntime(zipPath, paquete);
  await rm(zipPath, { force: true });
  const home = homeDentroDelPaquete(paquete);

  if (await majorVersionOf(exeConsole(home)) !== REQUIRED_MAJOR) {
    throw new Error('El Java descargado no responde como versión 21');
  }
  return { home, javaw: exe(home), java: exeConsole(home) };
}
