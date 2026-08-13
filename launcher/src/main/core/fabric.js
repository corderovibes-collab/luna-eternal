import { readFile, writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { downloadAll, getJson } from './net.js';
import { paths } from './paths.js';
import { resolveLibraries } from './minecraft.js';

const META = 'https://meta.fabricmc.net/v2/versions/loader';

/**
 * Perfil de Fabric para una pareja (Minecraft, loader).
 *
 * El endpoint `/profile/json` devuelve un JSON con el mismo formato que los de
 * Mojang, así que se puede reutilizar tal cual el resolvedor de librerías.
 */
export async function fetchFabricProfile(minecraft, loader) {
  const id = `fabric-loader-${loader}-${minecraft}`;
  const cached = path.join(paths.versions, id, `${id}.json`);
  try {
    return JSON.parse(await readFile(cached, 'utf8'));
  } catch { /* primera vez */ }

  const json = await getJson(`${META}/${minecraft}/${loader}/profile/json`);
  await mkdir(path.dirname(cached), { recursive: true });
  await writeFile(cached, JSON.stringify(json));
  return json;
}

/** Descarga las librerías de Fabric y devuelve su classpath y la clase principal. */
export async function installFabric(profile, onProgress) {
  const { classpath } = resolveLibraries(profile.libraries ?? []);

  await downloadAll(classpath, {
    concurrency: 8,
    phase: 'fabric',
    message: (done, total) => `Instalando Fabric… (${done}/${total})`,
    onProgress,
  });

  return { classpath: classpath.map((c) => c.dest), mainClass: profile.mainClass, profile };
}
