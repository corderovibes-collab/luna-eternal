import { rm, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { download, getJson, isIntact, pool } from './net.js';
import { paths } from './paths.js';
import { loadInstalled, saveInstalled } from './store.js';
import { extractZip } from './zip.js';

const CONCURRENCY = 10;

/** Carpetas que administra el launcher: solo dentro de ellas puede borrar. */
const MANAGED = ['mods', 'config', 'resourcepacks', 'shaderpacks', 'datapacks'];

const isManaged = (rel) => MANAGED.some((dir) => rel === dir || rel.startsWith(`${dir}/`));

/** Bloquea rutas que se escapen de la instancia (`../`, absolutas, unidades). */
function safeJoin(rel) {
  const target = path.join(paths.instance, rel);
  const check = path.relative(paths.instance, target);
  if (check.startsWith('..') || path.isAbsolute(check)) {
    throw new Error(`Ruta no permitida en el manifiesto: ${rel}`);
  }
  return target;
}

export async function fetchManifest(url) {
  const manifest = await getJson(url, { headers: { 'Cache-Control': 'no-cache' } });
  if (!Array.isArray(manifest.files)) throw new Error('El manifiesto no trae lista de ficheros');
  return manifest;
}

/**
 * ¿Este fichero pertenece al perfil elegido?
 *
 * Un fichero sin `profiles` es de todos. Es lo que permite tener **un solo
 * manifiesto** para jugador y constructor en vez de dos packs sueltos: las
 * herramientas de construcción se marcan `profiles: ["constructor"]` y el
 * jugador normal ni las ve ni las descarga.
 *
 * Y como `stale` borra lo instalado que ya no toca, **cambiar de perfil
 * desinstala solo** los mods del otro. Sin eso, quien probara el perfil de
 * constructor se quedaría a Axiom cargado para siempre.
 */
const enPerfil = (file, perfil) =>
  !Array.isArray(file.profiles) || file.profiles.includes(perfil);

/**
 * Deja la instancia igual que el manifiesto.
 *
 * Solo se descarga lo que cambia de verdad, así que la primera instalación baja
 * todo y las siguientes actualizaciones apenas mueven los mods tocados. Los
 * ficheros marcados `once` (servers.dat, config/, shaderpacks/) se escriben si
 * faltan pero nunca se pisan: son ajustes del jugador.
 *
 * En un arranque normal la comparación es **el SHA1 del manifiesto contra el de
 * `installed.json`**, más un `stat` para confirmar que el fichero sigue ahí y
 * con su tamaño. No se lee el contenido: resumir los 185 MB del pack en cada
 * arranque —129 de ellos solo Cobblemon— no aporta nada a cambio de segundos.
 *
 * `forzar` salta el atajo de `installed.json` y comprueba el SHA1 de **todo**
 * lo que hay en disco. Es lo que hace el botón de reparar: el atajo da por
 * bueno lo que el launcher cree que instaló, y por eso un jar que se corrompió
 * después —descarga cortada, antivirus, disco lleno— sobrevive a cualquier
 * número de actualizaciones. Ese fallo ya costó una sesión entera en este
 * proyecto, con un `ZipFile invalid LOC header` que no se parecía en nada a su
 * causa.
 */
export async function syncPack(manifest, {
  includeOptional = true, perfil = 'jugador', forzar = false,
} = {}) {
  const installed = await loadInstalled();
  const wanted = manifest.files
    .filter((f) => includeOptional || !f.optional)
    .filter((f) => enPerfil(f, perfil));

  const toDownload = [];
  const nextState = {};

  for (const file of wanted) {
    const dest = safeJoin(file.path);
    nextState[file.path] = file.sha1;

    if (file.once) {
      // Solo si no existe. Nada de comparar tamaño ni hash: en cuanto el jugador
      // toca un ajuste el fichero deja de coincidir con el del manifiesto, y darlo
      // por corrupto significaba restaurarlo y borrarle la configuración en cada
      // arranque. `isIntact` sin criterio se limita a comprobar que hay algo.
      if (!(await isIntact(dest))) toDownload.push({ file, dest });
      continue;
    }
    if (file.archive) {
      // De una carpeta no se puede comprobar el SHA1: basta con que el manifiesto
      // siga anunciando el mismo zip que se extrajo la última vez.
      if (forzar || installed.files?.[file.path] !== file.sha1) toDownload.push({ file, dest });
      continue;
    }
    if (!forzar) {
      // El atajo. Si `installed.json` dice que este fichero ya está con este
      // mismo SHA1, no se relee del disco: basta comprobar que sigue ahí y con
      // el tamaño correcto, que es un `stat`. Sin esto habría que leer y
      // resumir los 185 MB del pack **en cada arranque**, y Cobblemon solo ya
      // son 129.
      if (installed.files?.[file.path] !== file.sha1) {
        toDownload.push({ file, dest });
        continue;
      }
      if (await isIntact(dest, { size: file.size })) continue;
      toDownload.push({ file, dest });
      continue;
    }
    // Reparar: no se fía ni de `installed.json` ni del tamaño, y comprueba el
    // SHA1 de verdad.
    if (await isIntact(dest, { sha1: file.sha1 })) continue;
    toDownload.push({ file, dest });
  }

  // Lo que estaba instalado y ya no está en el manifiesto (mod retirado o renombrado).
  const stale = Object.keys(installed.files ?? {})
    .filter((rel) => !nextState[rel] && isManaged(rel));

  return {
    manifest,
    toDownload,
    stale,
    // Lo que quedará instalado al terminar: SOLO lo del perfil elegido.
    // `applySync` lo guarda tal cual — ver allí por qué no vale `manifest.files`.
    nextState,
    bytes: toDownload.reduce((n, d) => n + (d.file.size ?? 0), 0),
  };
}

export async function applySync(plan, onProgress) {
  const { toDownload, stale, manifest } = plan;

  // `recursive` porque una entrada retirada puede ser un fichero o una carpeta extraída.
  for (const rel of stale) {
    await rm(safeJoin(rel), { force: true, recursive: true }).catch(() => {});
  }

  const total = plan.bytes || 1;
  let doneBytes = 0;
  let doneFiles = 0;

  await mkdir(paths.instance, { recursive: true });
  await pool(toDownload, CONCURRENCY, async ({ file, dest }) => {
    const track = (n) => {
      doneBytes += n;
      onProgress?.({
        phase: 'pack',
        message: 'Descargando el modpack…',
        progress: Math.min(doneBytes / total, 1),
      });
    };

    if (file.archive) {
      // Carpetas con muchísimos ficheros pequeños (el shaderpack son 1234) viajan
      // como un zip: una petición en vez de mil, y se extrae encima de lo que haya
      // para no borrar la config que generan los mods al ejecutarse.
      const cached = path.join(paths.cache, `${file.sha1}.zip`);
      await download(file.url, cached, { sha1: file.sha1, size: file.size, onChunk: track });
      await mkdir(dest, { recursive: true });
      await extractZip(cached, dest, { strip: file.strip ?? 0 });
      await rm(cached, { force: true });
    } else {
      await download(file.url, dest, {
        sha1: file.once ? undefined : file.sha1,
        size: file.size,
        onChunk: track,
      });
    }
    doneFiles++;
    onProgress?.({
      phase: 'pack',
      message: `Descargando el modpack… (${doneFiles}/${toDownload.length})`,
      progress: Math.min(doneBytes / total, 1),
    });
  });

  // Se guarda `nextState` —lo del perfil elegido— y NO `manifest.files`.
  //
  // Con `manifest.files` el estado mentía: un jugador normal quedaba con Axiom
  // y Litematica anotados como instalados sin haberlos descargado nunca. No
  // llegaba a romper nada porque antes de darlos por buenos se mira el disco,
  // pero un fichero de estado que miente acaba engañando a quien lo lea
  // después — y ahora que el atajo se fía de él, sería justo lo que dejaría al
  // constructor sin sus herramientas.
  await saveInstalled({
    version: manifest.packVersion,
    updatedAt: Date.now(),
    files: plan.nextState,
  });

  return { downloaded: doneFiles, removed: stale.length };
}
