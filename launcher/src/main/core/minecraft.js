import { readFile, writeFile, mkdir, rm } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { download, downloadAll, getJson } from './net.js';
import { mavenToPath, paths } from './paths.js';
import { ARCH_MOJANG, OS_MOJANG, esNativo, nativoDeEstaMaquina } from './plataforma.js';
import { extractZip } from './zip.js';

const VERSION_MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
const RESOURCES = 'https://resources.download.minecraft.net';
const CONCURRENCY = 16;



function ruleApplies(rule, features) {
  if (rule.os) {
    if (rule.os.name && rule.os.name !== OS_MOJANG) return false;
    if (rule.os.arch && rule.os.arch !== ARCH_MOJANG) return false;
    if (rule.os.version && !new RegExp(rule.os.version).test(os.release())) return false;
  }
  if (rule.features) {
    for (const [key, want] of Object.entries(rule.features)) {
      if (Boolean(features[key]) !== Boolean(want)) return false;
    }
  }
  return true;
}

/** Semántica de Mojang: sin reglas se permite; con reglas gana la última que encaje. */
export function allowed(rules, features = {}) {
  if (!rules?.length) return true;
  let action = 'disallow';
  for (const rule of rules) if (ruleApplies(rule, features)) action = rule.action;
  return action === 'allow';
}

/**
 * Aplana `libraries` a descargas concretas, separando classpath de natives.
 *
 * Soporta los dos formatos que conviven en los JSON de Mojang: el moderno
 * (el classifier va en el propio nombre Maven) y el antiguo (`natives` +
 * `downloads.classifiers`), porque Fabric todavía arrastra librerías del viejo.
 */
export function resolveLibraries(libraries) {
  const classpath = [];
  const natives = [];
  const seen = new Set();

  for (const lib of libraries) {
    if (!allowed(lib.rules)) continue;

    const artifact = lib.downloads?.artifact;
    if (artifact?.path) {
      const dest = path.join(paths.libraries, artifact.path);
      if (!seen.has(dest)) {
        seen.add(dest);
        const entry = { url: artifact.url, dest, sha1: artifact.sha1, size: artifact.size };
        // En el formato moderno los natives son un artifact normal con el
        // classifier en el nombre. Las reglas de Mojang NO distinguen
        // arquitectura —`natives-macos` y `natives-macos-arm64` llevan la misma—,
        // asi que hay que mirar el sufijo o se extraen las dos encima.
        if (esNativo(lib.name ?? '')) {
          if (nativoDeEstaMaquina(lib.name)) natives.push(entry);
        } else {
          classpath.push(entry);
        }
      }
    } else if (lib.name && !lib.downloads) {
      // Librería sin bloque `downloads` (típico de Fabric): se arma desde `url` + Maven.
      const rel = mavenToPath(lib.name);
      const dest = path.join(paths.libraries, rel);
      if (!seen.has(dest)) {
        seen.add(dest);
        const base = (lib.url ?? 'https://libraries.minecraft.net/').replace(/\/?$/, '/');
        classpath.push({ url: base + rel.split(path.sep).join('/'), dest, sha1: lib.sha1 });
      }
    }

    const legacy = lib.natives?.windows?.replace('${arch}', ARCH === 'x86' ? '32' : '64');
    const classifier = legacy && lib.downloads?.classifiers?.[legacy];
    if (classifier?.path) {
      const dest = path.join(paths.libraries, classifier.path);
      if (!seen.has(dest)) {
        seen.add(dest);
        natives.push({ url: classifier.url, dest, sha1: classifier.sha1, size: classifier.size, exclude: lib.extract?.exclude });
      }
    }
  }
  return { classpath, natives };
}

/** Descarga (o reutiliza) el JSON de una versión de Minecraft. */
export async function fetchVersionJson(id) {
  const cached = path.join(paths.versions, id, `${id}.json`);
  try {
    return JSON.parse(await readFile(cached, 'utf8'));
  } catch { /* primera vez */ }

  const manifest = await getJson(VERSION_MANIFEST);
  const entry = manifest.versions.find((v) => v.id === id);
  if (!entry) throw new Error(`Minecraft ${id} no existe en el manifiesto de Mojang`);

  const json = await getJson(entry.url);
  await mkdir(path.dirname(cached), { recursive: true });
  await writeFile(cached, JSON.stringify(json));
  return json;
}

/**
 * Deja instalado el cliente de Minecraft: jar, librerías, natives y assets.
 * Devuelve lo que necesita el lanzador para montar el comando.
 */
export async function installMinecraft(versionJson, onProgress) {
  const id = versionJson.id;
  const clientJar = path.join(paths.versions, id, `${id}.jar`);
  const { classpath, natives } = resolveLibraries(versionJson.libraries ?? []);

  const index = versionJson.assetIndex;
  const indexFile = path.join(paths.assets, 'indexes', `${index.id}.json`);
  await download(index.url, indexFile, { sha1: index.sha1, size: index.size });
  const objects = JSON.parse(await readFile(indexFile, 'utf8')).objects;

  const assetJobs = Object.values(objects).map(({ hash, size }) => ({
    url: `${RESOURCES}/${hash.slice(0, 2)}/${hash}`,
    dest: path.join(paths.assets, 'objects', hash.slice(0, 2), hash),
    sha1: hash,
    size,
  }));

  const jobs = [
    { url: versionJson.downloads.client.url, dest: clientJar, sha1: versionJson.downloads.client.sha1, size: versionJson.downloads.client.size },
    ...classpath,
    ...natives,
    ...assetJobs,
  ];

  await downloadAll(jobs, {
    concurrency: CONCURRENCY,
    phase: 'minecraft',
    message: (done, total) => `Descargando Minecraft… (${done}/${total})`,
    onProgress,
  });

  // Los natives se re-extraen en cada instalación: son pocos MB y así una DLL
  // corrupta se arregla sola en vez de quedarse para siempre.
  const nativesDir = path.join(paths.natives, id);
  await rm(nativesDir, { recursive: true, force: true });
  await mkdir(nativesDir, { recursive: true });
  for (const nat of natives) {
    await extractZip(nat.dest, nativesDir, {
      filter: (name) =>
        !name.startsWith('META-INF/') &&
        !name.endsWith('/') &&
        !(nat.exclude ?? []).some((ex) => name.startsWith(ex)),
    });
  }

  return { clientJar, classpath: classpath.map((c) => c.dest), nativesDir, assetIndexId: index.id };
}
