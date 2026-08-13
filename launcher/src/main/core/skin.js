import { copyFile, mkdir, readFile, rm, stat } from 'node:fs/promises';
import path from 'node:path';
import { getJson } from './net.js';
import { paths } from './paths.js';

const MOJANG_NAME = 'https://api.mojang.com/users/profiles/minecraft';
const MOJANG_PROFILE = 'https://sessionserver.mojang.com/session/minecraft/profile';

const skinDir = () => path.join(paths.root, 'skins');
const skinFile = (accountId) => path.join(skinDir(), `${accountId.replace(/[^\w.-]/g, '_')}.png`);

/** Cabecera PNG + dimensiones, leidas del propio fichero (no se fia de la extension). */
function readPngSize(buf) {
  const isPng = buf.length > 24
    && buf.readUInt32BE(0) === 0x89504e47
    && buf.readUInt32BE(4) === 0x0d0a1a0a;
  if (!isPng) return null;
  return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) };
}

/**
 * Valida que el PNG sea realmente una skin de Minecraft.
 *
 * 64x64 es el formato actual; 64x32 es el antiguo (sin segunda capa ni brazos
 * separados) y el juego lo sigue aceptando. Cualquier otra cosa se rechaza aqui
 * en vez de dejar que el modelo salga descuadrado.
 */
export function validateSkin(buf) {
  const size = readPngSize(buf);
  if (!size) throw new Error('Ese fichero no es un PNG.');
  const ok = (size.width === 64 && (size.height === 64 || size.height === 32));
  if (!ok) {
    throw new Error(
      `Una skin tiene que ser de 64x64 (o 64x32 las antiguas) y esta es de `
      + `${size.width}x${size.height}.`,
    );
  }
  return size;
}

const toDataUri = (buf) => `data:image/png;base64,${buf.toString('base64')}`;

/** Skin que el jugador haya elegido a mano para esta cuenta. */
async function customSkin(accountId) {
  try {
    const buf = await readFile(skinFile(accountId));
    return { dataUri: toDataUri(buf), source: 'custom', model: 'classic' };
  } catch {
    return null;
  }
}

/** Descarga la textura de una URL de Mojang y la devuelve como data URI. */
async function fetchTexture(url) {
  const res = await fetch(url, { headers: { 'User-Agent': 'PokeReportLauncher/1.2' } });
  if (!res.ok) throw new Error(`No se pudo descargar la skin (HTTP ${res.status})`);
  return Buffer.from(await res.arrayBuffer());
}

/**
 * Busca la skin de un nombre en Mojang.
 *
 * Es lo que hace que una cuenta offline recupere la skin que ese jugador ya tenia:
 * si el nombre existe en Mojang, se coge su textura publica. Si no existe, no pasa
 * nada y se queda con la de por defecto.
 */
async function mojangSkinByName(name) {
  try {
    const found = await getJson(`${MOJANG_NAME}/${encodeURIComponent(name)}`);
    if (!found?.id) return null;
    return await mojangSkinByUuid(found.id);
  } catch {
    return null;
  }
}

async function mojangSkinByUuid(uuid) {
  try {
    const profile = await getJson(`${MOJANG_PROFILE}/${uuid.replace(/-/g, '')}`);
    const prop = profile?.properties?.find((p) => p.name === 'textures');
    if (!prop) return null;
    const textures = JSON.parse(Buffer.from(prop.value, 'base64').toString('utf8')).textures;
    if (!textures?.SKIN?.url) return null;
    return {
      dataUri: toDataUri(await fetchTexture(textures.SKIN.url)),
      source: 'mojang',
      model: textures.SKIN.metadata?.model === 'slim' ? 'slim' : 'classic',
    };
  } catch {
    return null;
  }
}

/**
 * Resuelve la skin que hay que enseñar para una cuenta, por orden de preferencia:
 * la que haya elegido el jugador, la de su perfil de Mojang, o la que tenga ese
 * nombre en Mojang (para cuentas offline).
 */
export async function resolveSkin(account) {
  if (!account) return null;

  const custom = await customSkin(account.id);
  if (custom) return custom;

  if (account.type === 'microsoft') {
    const url = account.skins?.find((s) => s.state === 'ACTIVE')?.url ?? account.skins?.[0]?.url;
    if (url) {
      try {
        return { dataUri: toDataUri(await fetchTexture(url)), source: 'mojang', model: 'classic' };
      } catch { /* se intenta por uuid abajo */ }
    }
    const byUuid = await mojangSkinByUuid(account.uuid);
    if (byUuid) return byUuid;
  }

  const byName = await mojangSkinByName(account.name);
  if (byName) return { ...byName, source: 'mojang-name' };

  return { dataUri: null, source: 'default', model: 'classic' };
}

/**
 * Copia del PNG dentro de la instancia, con un nombre fijo y facil de encontrar.
 *
 * Quien manda sobre la skin es el servidor (Fabric Tailor): en el juego se abre la
 * ventana de skins y se sube el PNG, y a partir de ahi la ven todos. Esta copia
 * existe solo para tenerlo a mano al hacerlo, sin buscar por medio disco.
 */
const localSkinFile = () => path.join(paths.instance, 'mi-skin.png');

/** Guarda un PNG como skin de esta cuenta. Devuelve la skin ya resuelta. */
export async function setCustomSkin(accountId, filePath) {
  const buf = await readFile(filePath);
  validateSkin(buf);

  await mkdir(skinDir(), { recursive: true });
  await copyFile(filePath, skinFile(accountId));

  // Y una copia a mano dentro de la instancia.
  const enJuego = localSkinFile();
  await mkdir(path.dirname(enJuego), { recursive: true });
  await copyFile(filePath, enJuego);

  return { dataUri: toDataUri(buf), source: 'custom', model: 'classic' };
}

export async function clearCustomSkin(accountId) {
  await rm(skinFile(accountId), { force: true });
  await rm(localSkinFile(), { force: true });
}

export async function hasCustomSkin(accountId) {
  try {
    await stat(skinFile(accountId));
    return true;
  } catch {
    return false;
  }
}
