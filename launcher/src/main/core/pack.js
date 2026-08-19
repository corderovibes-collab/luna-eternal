import { createHash } from 'node:crypto';
import { rm, mkdir, readdir } from 'node:fs/promises';
import path from 'node:path';
import { download, getJson, getText, isIntact, pool } from './net.js';
import { paths } from './paths.js';
import { loadInstalled, saveInstalled } from './store.js';
import { extractZip } from './zip.js';

const CONCURRENCY = 10;

/** Carpetas que administra el launcher: solo dentro de ellas puede borrar. */
const MANAGED = ['mods', 'config', 'resourcepacks', 'shaderpacks', 'datapacks'];

const isManaged = (rel) => MANAGED.some((dir) => rel === dir || rel.startsWith(`${dir}/`));

/**
 * De donde se puede bajar este fichero, en orden de preferencia.
 *
 * `urls` llega desde el pack 0.3.0; `url` es lo que traen los manifiestos
 * anteriores. Se admiten los dos porque el launcher se actualiza solo pero no
 * a la vez que el pack, y durante un rato conviven.
 */
const origenes = (file) => (Array.isArray(file.urls) && file.urls.length ? file.urls : [file.url]);

/** Bloquea rutas que se escapen de la instancia (`../`, absolutas, unidades). */
function safeJoin(rel) {
  const target = path.join(paths.instance, rel);
  const check = path.relative(paths.instance, target);
  if (check.startsWith('..') || path.isAbsolute(check)) {
    throw new Error(`Ruta no permitida en el manifiesto: ${rel}`);
  }
  return target;
}

/**
 * El manifiesto vigente, resuelto a traves del puntero.
 *
 * POR QUE HAY UN PUNTERO EN MEDIO
 *
 * Antes esto pedia `manifest.json`, que se sobrescribia en cada publicacion.
 * Eso tenia dos problemas y los dos son de los que solo se ven cuando ya hay
 * gente dentro:
 *
 *   1. VIVIA EN `raw.githubusercontent`, que limita por peticiones y cachea
 *      tres minutos por ruta. Era LA PRIMERA peticion del arranque, o sea el
 *      peor sitio posible para un 429.
 *   2. NO HABIA VUELTA ATRAS. Una publicacion mala rompia a todo el mundo a la
 *      vez, y arreglarlo era regenerar y republicar 185 MB con el pack roto
 *      mientras tanto.
 *
 * Ahora `latest.json` --250 bytes servidos por el CDN de descargas-- dice cual
 * es el manifiesto bueno, y cada manifiesto lleva su huella en el nombre y no
 * se toca jamas. Volver atras es reescribir el puntero.
 *
 * ⚠ SE ADMITEN LOS DOS FORMATOS, y hace falta: el launcher se actualiza solo
 *   pero no a la vez que el pack, y un launcher ya instalado tiene la URL
 *   vieja guardada en su configuracion. Se distinguen POR EL CONTENIDO y no por
 *   la URL, que es lo unico que no depende de que nadie haya migrado nada.
 */
export async function fetchManifest(url) {
  const crudo = await getText(url, { headers: { 'Cache-Control': 'no-cache' } });
  const primero = JSON.parse(crudo);

  if (!Array.isArray(primero.files) && typeof primero.manifest === 'string') {
    return seguirPuntero(primero);
  }
  if (!Array.isArray(primero.files)) throw new Error('El manifiesto no trae lista de ficheros');
  return primero;
}

async function seguirPuntero(puntero) {
  const texto = await getText([puntero.manifest, ...(puntero.espejos ?? [])]);

  // ⚠ SE COMPRUEBA LA HUELLA ANTES DE FIARSE DEL CONTENIDO.
  //   El manifiesto elige de que URL salen los 185 MB que se instalan, y con
  //   ellas lo que acaba ejecutandose en la maquina del jugador. Sin esta
  //   comprobacion, quien pudiera colocarle un JSON --un DNS envenenado, un
  //   proxy de una red publica-- le elegiria los mods. Es la unica parte de la
  //   cadena donde una firma sale gratis: el puntero ya trae el sha1.
  if (puntero.sha1) {
    const suyo = createHash('sha1').update(texto).digest('hex');
    if (suyo !== puntero.sha1.toLowerCase()) {
      throw new Error('El manifiesto no coincide con su huella. '
        + 'Vuelve a darle a Jugar; si sigue pasando, avisa en Discord.');
    }
  }

  const manifest = JSON.parse(texto);
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

  // ⚠⚠ Y ADEMÁS SE BARRE `mods/` DE VERDAD, NO SOLO LO ANOTADO.
  //
  // La limpieza de arriba solo alcanza lo que `installed.json` recuerda. Un jar
  // que llegó por otra vía —una instalación anterior, una versión del launcher
  // que aún no lo apuntaba, o el propio jugador— sobrevive a TODAS las
  // actualizaciones, para siempre.
  //
  // Eso dejó a jugadores fuera del servidor el 2026-08-19. Arrastraban
  // `trinkets` y `accessories-compat-layer` de un pack anterior; el servidor ya
  // no los tenía, y el puente exportaba unas ranuras que el servidor no sabía
  // leer:
  //
  //     Failed to decode packet 'clientbound/custom_payload'
  //     Caused by: StructFieldException: [Field: exported_slots]
  //
  // El mensaje no nombra ni Accessories ni Trinkets. Y quien tenía la
  // instalación bien anotada NO lo sufría, así que parecía cosa de máquinas
  // concretas.
  //
  // Solo se barre `mods/`: es 100 % del pack. `config/`, `resourcepacks/` y
  // `shaderpacks/` llevan cosas del jugador y ahí no se toca nada que no
  // estuviera anotado.
  const modsEsperados = new Set(
    Object.keys(nextState).filter((rel) => rel.startsWith('mods/')),
  );
  try {
    for (const nombre of await readdir(path.join(paths.instance, 'mods'))) {
      if (!nombre.toLowerCase().endsWith('.jar')) continue;
      const rel = `mods/${nombre}`;
      if (!modsEsperados.has(rel) && !stale.includes(rel)) stale.push(rel);
    }
  } catch { /* aún no hay carpeta mods: instalación desde cero */ }

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
      await download(origenes(file), cached, { sha1: file.sha1, size: file.size, onChunk: track });
      await mkdir(dest, { recursive: true });
      await extractZip(cached, dest, {
        strip: file.strip ?? 0,
        keepExisting: file.keepExisting === true,
      });
      await rm(cached, { force: true });
    } else {
      await download(origenes(file), dest, {
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
