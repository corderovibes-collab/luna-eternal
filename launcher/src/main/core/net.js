import { createHash } from 'node:crypto';
import { createReadStream, createWriteStream } from 'node:fs';
import { mkdir, rename, stat, unlink } from 'node:fs/promises';
import path from 'node:path';
import { pipeline } from 'node:stream/promises';
import { Readable } from 'node:stream';

const UA = 'PokeReportLauncher/1.2';
const RETRIES = 8;

/**
 * Cuanto se espera a que el servidor CONTESTE antes de darlo por colgado.
 *
 * ⚠ SIN ESTO EL LAUNCHER SE QUEDA EN "COMPROBANDO ACTUALIZACIONES" PARA
 * SIEMPRE. El `fetch` de Node NO TIENE TIEMPO LIMITE POR DEFECTO: si la
 * conexion se queda a medias --y `raw.githubusercontent` contesta
 * `503 Backend.max_conn reached`, o directamente no contesta, cuando le llegan
 * muchas conexiones-- la promesa no se resuelve ni se rechaza NUNCA. El jugador
 * ve el mensaje ahi clavado: sin error, sin barra y sin forma de salir.
 *
 * Es justo lo que hacia que a unos les funcionara y a otros no. Quien ya tiene
 * el pack bajado no vuelve a pedir casi nada; quien instala de cero se quedaba
 * colgado en la primera peticion, que ademas es la del manifiesto.
 *
 * Cubre solo la ESPERA A LA CABECERA, no la descarga: el reloj se para en
 * cuanto el servidor empieza a contestar, asi que un fichero de 130 MB no se
 * corta por tardar en bajar.
 */
const TIEMPO_LIMITE = 30_000;

/** Tope de la espera entre reintentos. */
const ESPERA_MAX = 30_000;


/** Espera con retroceso exponencial y algo de jitter para no sincronizar reintentos. */
const backoff = (attempt) =>
  new Promise((r) => setTimeout(r, Math.min(500 * 2 ** attempt, ESPERA_MAX) + Math.random() * 250));

/**
 * Ejecuta `worker` sobre cada elemento con como máximo `limit` en vuelo.
 *
 * Es el único punto de concurrencia del launcher: descargas de librerías, assets
 * y mods lo reutilizan en vez de tener cada uno su propio control.
 */
export async function pool(items, limit, worker) {
  const results = new Array(items.length);
  let next = 0;
  const runners = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (true) {
      const i = next++;
      if (i >= items.length) return;
      results[i] = await worker(items[i], i);
    }
  });
  await Promise.all(runners);
  return results;
}

async function request(url, init = {}, attempt = 0) {
  // El reloj se para en cuanto llega la cabecera: `clearTimeout` corre al salir
  // de esta funcion, y el cuerpo se lee DESPUES. Asi un fichero grande puede
  // tardar lo que haga falta en bajar sin que nadie lo aborte.
  const corte = new AbortController();
  const reloj = setTimeout(() => corte.abort(), TIEMPO_LIMITE);
  try {
    const res = await fetch(url, {
      ...init,
      signal: corte.signal,
      headers: { 'User-Agent': UA, ...(init.headers ?? {}) },
    });
    // 4xx (salvo 408/429) son definitivos: reintentar solo gasta tiempo.
    if (!res.ok && res.status < 500 && res.status !== 408 && res.status !== 429) {
      throw Object.assign(new Error(`HTTP ${res.status} en ${url}`), { fatal: true });
    }
    if (!res.ok) throw new Error(`HTTP ${res.status} en ${url}`);
    return res;
  } catch (err) {
    if (err.fatal || attempt >= RETRIES) {
      // Un aborto es un cuelgue, y "This operation was aborted" no le dice nada
      // a nadie. Se cambia por lo que de verdad ha pasado.
      if (err?.name === 'AbortError' || err?.name === 'TimeoutError') {
        throw new Error(`El servidor no contesta (${url}). `
          + 'Vuelve a darle a Jugar en un minuto.');
      }
      throw err;
    }
    await backoff(attempt);
    return request(url, init, attempt + 1);
  } finally {
    clearTimeout(reloj);
  }
}

export async function getJson(url, init) {
  return (await request(url, init)).json();
}

export async function getText(url, init) {
  return (await request(url, init)).text();
}

export async function postJson(url, body, headers = {}) {
  const res = await request(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json', ...headers },
    body: JSON.stringify(body),
  });
  return res.json();
}

export async function postForm(url, fields) {
  const res = await request(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(fields).toString(),
  });
  return res.json();
}

/**
 * Descarga un lote de ficheros midiendo el progreso **por bytes**, no por unidades.
 *
 * Lo comparten la instalación de Minecraft y la de Fabric: 4000 assets diminutos y
 * un jar de 25 MB no pueden pesar lo mismo en la barra, y esa cuenta no merece estar
 * duplicada en cada instalador.
 */
export async function downloadAll(jobs, { concurrency = 12, phase, message, onProgress } = {}) {
  const totalBytes = jobs.reduce((sum, job) => sum + (job.size ?? 0), 0) || 1;
  let bytes = 0;
  let files = 0;

  const report = () => onProgress?.({
    phase,
    message: message(files, jobs.length),
    progress: Math.min(bytes / totalBytes, 1),
  });

  await pool(jobs, concurrency, async (job) => {
    await download(job.url, job.dest, {
      sha1: job.sha1,
      sha256: job.sha256,
      size: job.size,
      onChunk: (n) => { bytes += n; report(); },
    });
    files++;
    report();
  });

  return { files, bytes };
}

/**
 * Elige el algoritmo segun lo que traiga cada origen.
 *
 * Modrinth y Mojang publican SHA-1; Adoptium publica SHA-256. Verificar un
 * SHA-256 como si fuera SHA-1 no falla ruidosamente: simplemente no coincide
 * nunca y la descarga se reintenta hasta rendirse.
 */
function digestSpec({ sha1, sha256 }) {
  if (sha256) return { algorithm: 'sha256', value: sha256.toLowerCase() };
  if (sha1) return { algorithm: 'sha1', value: sha1.toLowerCase() };
  return null;
}

export async function hashFile(file, algorithm = 'sha1') {
  const hash = createHash(algorithm);
  await pipeline(createReadStream(file), hash);
  return hash.digest('hex');
}

export const sha1File = (file) => hashFile(file, 'sha1');

/** ¿El fichero ya está en disco, con el tamaño y el hash esperados? */
export async function isIntact(dest, { sha1, sha256, size } = {}) {
  try {
    const st = await stat(dest);
    if (size != null && st.size !== size) return false;
    const spec = digestSpec({ sha1, sha256 });
    if (!spec) return st.size > 0;
    return (await hashFile(dest, spec.algorithm)) === spec.value;
  } catch {
    return false;
  }
}

/**
 * Descarga `url` en `dest` verificando SHA1 **durante** el volcado a disco.
 *
 * Hacerlo en el mismo paso evita releer cada fichero para comprobarlo, que con
 * ~4000 assets es la diferencia entre segundos y minutos. Escribe en `.part` y
 * renombra al final, así una descarga cortada nunca deja un fichero a medias que
 * parezca válido.
 */
export async function download(url, dest, { sha1, sha256, size, onChunk } = {}) {
  const spec = digestSpec({ sha1, sha256 });
  if (await isIntact(dest, { sha1, sha256, size })) return { skipped: true, bytes: 0 };

  await mkdir(path.dirname(dest), { recursive: true });
  const tmp = `${dest}.part`;

  for (let attempt = 0; ; attempt++) {
    const hash = spec ? createHash(spec.algorithm) : null;
    let bytes = 0;
    try {
      const res = await request(url);
      const out = createWriteStream(tmp);
      const body = Readable.fromWeb(res.body);
      body.on('data', (c) => {
        bytes += c.length;
        hash?.update(c);
        onChunk?.(c.length);
      });
      await pipeline(body, out);

      if (hash && hash.digest('hex') !== spec.value) {
        throw new Error(
          `La descarga de ${path.basename(dest)} llego corrupta `
          + `(${spec.algorithm.toUpperCase()} no coincide).`,
        );
      }
      await rename(tmp, dest);
      return { skipped: false, bytes };
    } catch (err) {
      await unlink(tmp).catch(() => {});
      onChunk?.(-bytes); // devolver lo contado para que el progreso no se dispare
      if (err.fatal || attempt >= RETRIES) throw err;
      await backoff(attempt);
    }
  }
}
