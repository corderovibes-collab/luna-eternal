import { mkdir } from 'node:fs/promises';
import path from 'node:path';

import { raizDatos } from './plataforma.js';

/**
 * Disposición de directorios del launcher.
 *
 * Todo cuelga de una única raíz para que desinstalar sea borrar una carpeta, y
 * para que `versions/`, `libraries/` y `assets/` conserven el layout oficial de
 * Mojang: así los ficheros se comparten entre versiones y valen para cualquier
 * otro launcher si algún día se migra.
 *
 * La raíz se calcula sin pedirle nada a Electron. Así el núcleo se puede ejecutar
 * y testear con node a secas, y `LUNA_ROOT` permite apuntar a otro sitio
 * en pruebas.
 *
 * Cada sistema tiene su convención (`%APPDATA%`, `~/Library/Application Support`,
 * `~/.local/share`); de eso se encarga `plataforma.js`.
 */
const root = process.env.LUNA_ROOT ?? raizDatos();

export const paths = {
  root,
  instance: path.join(root, 'instance'),      // .minecraft del pack (mods, config, saves)
  versions: path.join(root, 'versions'),
  libraries: path.join(root, 'libraries'),
  assets: path.join(root, 'assets'),
  natives: path.join(root, 'natives'),
  runtime: path.join(root, 'runtime'),        // JRE gestionado por el launcher
  cache: path.join(root, 'cache'),
  logs: path.join(root, 'logs'),
  config: path.join(root, 'launcher.json'),
  state: path.join(root, 'installed.json'),   // manifiesto del pack ya instalado
};

/** Crea el árbol de directorios. Idempotente. */
export async function ensureDirs() {
  const dirs = [
    paths.root, paths.instance, paths.versions, paths.libraries,
    paths.assets, path.join(paths.assets, 'objects'), path.join(paths.assets, 'indexes'),
    paths.natives, paths.runtime, paths.cache, paths.logs,
  ];
  await Promise.all(dirs.map((d) => mkdir(d, { recursive: true })));
}

/** Ruta local de una librería a partir de su coordenada Maven (`group:artifact:version[:classifier]`). */
export function mavenToPath(coord) {
  const [group, artifact, version, classifier] = coord.split(':');
  const name = classifier
    ? `${artifact}-${version}-${classifier}.jar`
    : `${artifact}-${version}.jar`;
  return path.join(...group.split('.'), artifact, version, name);
}
