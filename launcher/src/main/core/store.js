import { readFile, writeFile, rename } from 'node:fs/promises';
import { totalmem } from 'node:os';
import { paths } from './paths.js';

/** RAM por defecto: la mitad de la del equipo, acotada a 4-8 GB.
 *  Pasar de 8 GB no mejora nada en este pack y alarga las pausas del GC. */
function defaultRam() {
  const gb = Math.floor(totalmem() / 1024 ** 3);
  return Math.max(4, Math.min(8, Math.floor(gb / 2)));
}

const DEFAULTS = {
  ramGb: defaultRam(),
  accounts: [],
  activeAccount: null,
  keepLauncherOpen: false,
  extraOptimization: true,
  // `jugador` o `constructor`. El de constructor añade Axiom y WorldEdit CUI,
  // que son 46 MB que un jugador normal no usa jamás (P10). Por defecto,
  // jugador: quien construye sabe que construye, quien juega no tiene por qué
  // enterarse de que existe la otra opción.
  perfil: 'jugador',
};

async function readJson(file, fallback) {
  try {
    return { ...fallback, ...JSON.parse(await readFile(file, 'utf8')) };
  } catch {
    return { ...fallback };
  }
}

/** Escritura atómica: si el proceso muere a medias, el fichero previo sigue intacto. */
async function writeJson(file, data) {
  const tmp = `${file}.tmp`;
  await writeFile(tmp, JSON.stringify(data, null, 2));
  await rename(tmp, file);
}

let config = null;

export async function loadConfig() {
  config ??= await readJson(paths.config, DEFAULTS);
  return config;
}

export async function saveConfig(patch) {
  config = { ...(await loadConfig()), ...patch };
  await writeJson(paths.config, config);
  return config;
}

/** Manifiesto del pack tal y como quedó instalado, para calcular el delta. */
export async function loadInstalled() {
  return readJson(paths.state, { version: null, files: {} });
}

export async function saveInstalled(state) {
  await writeJson(paths.state, state);
}
