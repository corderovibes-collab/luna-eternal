import { open, mkdir, writeFile, stat, unlink } from 'node:fs/promises';
import path from 'node:path';
import { inflateRaw } from 'node:zlib';
import { promisify } from 'node:util';

const inflate = promisify(inflateRaw);

const EOCD = 0x06054b50;
const EOCD64_LOCATOR = 0x07064b50;
const EOCD64 = 0x06064b50;

/**
 * Lector de ZIP mínimo sobre el directorio central.
 *
 * Se implementa a mano en vez de tirar de una dependencia porque solo hacen falta
 * dos cosas (JRE de Adoptium y los natives de LWJGL) y así el launcher se queda
 * sin dependencias de runtime: menos peso, menos superficie y nada que actualizar.
 * Lee por trozos con un descriptor abierto, sin cargar el zip entero en memoria.
 */
async function readCentralDirectory(fh, fileSize) {
  const tailLen = Math.min(fileSize, 66 * 1024);
  const tail = Buffer.alloc(tailLen);
  await fh.read(tail, 0, tailLen, fileSize - tailLen);

  let eocd = -1;
  for (let i = tail.length - 22; i >= 0; i--) {
    if (tail.readUInt32LE(i) === EOCD) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('ZIP inválido: no se encuentra el EOCD');

  let entries = tail.readUInt16LE(eocd + 10);
  let cdSize = tail.readUInt32LE(eocd + 12);
  let cdOffset = tail.readUInt32LE(eocd + 16);

  // Zip64: los campos de 32 bits vienen saturados y los reales están en el EOCD64.
  if (cdOffset === 0xffffffff || entries === 0xffff || cdSize === 0xffffffff) {
    let loc = -1;
    for (let i = eocd - 20; i >= 0; i--) {
      if (tail.readUInt32LE(i) === EOCD64_LOCATOR) { loc = i; break; }
    }
    if (loc < 0) throw new Error('ZIP64 sin localizador de EOCD');
    const eocd64Offset = Number(tail.readBigUInt64LE(loc + 8));
    const head = Buffer.alloc(56);
    await fh.read(head, 0, 56, eocd64Offset);
    if (head.readUInt32LE(0) !== EOCD64) throw new Error('ZIP64 EOCD inválido');
    entries = Number(head.readBigUInt64LE(32));
    cdSize = Number(head.readBigUInt64LE(40));
    cdOffset = Number(head.readBigUInt64LE(48));
  }

  const cd = Buffer.alloc(cdSize);
  await fh.read(cd, 0, cdSize, cdOffset);

  const list = [];
  let p = 0;
  for (let i = 0; i < entries && p + 46 <= cd.length; i++) {
    const method = cd.readUInt16LE(p + 10);
    const compSize = cd.readUInt32LE(p + 20);
    const uncompSize = cd.readUInt32LE(p + 24);
    const nameLen = cd.readUInt16LE(p + 28);
    const extraLen = cd.readUInt16LE(p + 30);
    const commentLen = cd.readUInt16LE(p + 32);
    let localOffset = cd.readUInt32LE(p + 42);
    const name = cd.toString('utf8', p + 46, p + 46 + nameLen);

    if (localOffset === 0xffffffff) {
      // Buscar el offset real en el campo extra Zip64 (cabecera 0x0001).
      let e = p + 46 + nameLen;
      const end = e + extraLen;
      while (e + 4 <= end) {
        const id = cd.readUInt16LE(e);
        const sz = cd.readUInt16LE(e + 2);
        if (id === 0x0001) {
          let off = e + 4;
          if (uncompSize === 0xffffffff) off += 8;
          if (compSize === 0xffffffff) off += 8;
          localOffset = Number(cd.readBigUInt64LE(off));
          break;
        }
        e += 4 + sz;
      }
    }

    list.push({ name, method, compSize, localOffset });
    p += 46 + nameLen + extraLen + commentLen;
  }
  return list;
}

async function readEntry(fh, entry) {
  // La cabecera local repite nombre y extra con longitudes propias: hay que leerla.
  const head = Buffer.alloc(30);
  await fh.read(head, 0, 30, entry.localOffset);
  const nameLen = head.readUInt16LE(26);
  const extraLen = head.readUInt16LE(28);
  const start = entry.localOffset + 30 + nameLen + extraLen;

  const raw = Buffer.alloc(entry.compSize);
  await fh.read(raw, 0, entry.compSize, start);
  if (entry.method === 0) return raw;
  if (entry.method === 8) return inflate(raw);
  throw new Error(`Método de compresión ${entry.method} no soportado (${entry.name})`);
}

/**
 * Extrae un zip.
 *
 * @param {object} opts
 * @param {(name:string)=>boolean} [opts.filter] qué entradas extraer
 * @param {number} [opts.strip] niveles de directorio a quitar del principio
 */
/**
 * Escribe un fichero aunque el que ya esté ahí sea OCULTO.
 *
 * ⚠ EN WINDOWS, `writeFile` SOBRE UN FICHERO OCULTO FALLA CON `EPERM`.
 *
 * No es un permiso del usuario ni un antivirus: `CreateFile` con
 * `CREATE_ALWAYS` se niega si el fichero existente tiene el atributo
 * `HIDDEN` y la llamada no lo repite. Node abre con `'w'`, que es
 * exactamente ese caso, así que el error sale con el disco sano, la carpeta
 * escribible y el usuario siendo administrador — y el mensaje no menciona en
 * ningún momento que el problema sea que el fichero está oculto.
 *
 * Ocurrió de verdad, y costó llegar hasta aquí: el pack trae
 * `config/euphoria_patcher/.data.json`, y el mod EuphoriaPatcher lo vuelve a
 * crear OCULTO en el PC del jugador. A partir de ahí, toda actualización que
 * reextrajera `config/` moría al 99 % con
 * `EPERM: operation not permitted, open '…/.data.json'`.
 *
 * Se borra y se vuelve a escribir. `unlink` sí funciona sobre un fichero
 * oculto, y el contenido acaba siendo el mismo que si se hubiera pisado.
 */
async function escribirPisando(target, datos) {
  try {
    await writeFile(target, datos);
  } catch (err) {
    if (err?.code !== 'EPERM') throw err;
    await unlink(target).catch(() => {});
    await writeFile(target, datos);
  }
}

export async function extractZip(zipPath, destDir, { filter, strip = 0, keepExisting = false } = {}) {
  const fh = await open(zipPath, 'r');
  try {
    const { size } = await fh.stat();
    const entries = await readCentralDirectory(fh, size);
    let written = 0;

    for (const entry of entries) {
      if (entry.name.endsWith('/')) continue;
      if (filter && !filter(entry.name)) continue;

      const parts = entry.name.split('/').slice(strip);
      if (!parts.length) continue;

      // Nunca escribir fuera del destino (zip slip).
      const target = path.join(destDir, ...parts);
      const rel = path.relative(destDir, target);
      if (rel.startsWith('..') || path.isAbsolute(rel)) continue;

      // `keepExisting`: extraer SIN pisar. Es como se sirve la configuración
      // del pack — llega en un zip por carpeta, y lo que el jugador ya haya
      // ajustado se respeta. Lo que sí llega es un fichero NUEVO, porque ese
      // todavía no existe.
      if (keepExisting && (await stat(target).catch(() => null))) continue;

      await mkdir(path.dirname(target), { recursive: true });
      await escribirPisando(target, await readEntry(fh, entry));
      written++;
    }
    return written;
  } finally {
    await fh.close();
  }
}
