import { ensureDirs } from './paths.js';
import { ensureJava } from './java.js';
import { fetchVersionJson, installMinecraft } from './minecraft.js';
import { fetchFabricProfile, installFabric } from './fabric.js';
import { applySync, fetchManifest, syncPack } from './pack.js';
import { loadConfig } from './store.js';

/**
 * Deja todo listo para jugar y devuelve las piezas que necesita `launchGame`.
 *
 * El orden importa: primero el manifiesto (dice qué versión de Minecraft y de
 * Fabric hacen falta), y solo después se instala lo demás. Así, el día que el
 * pack salte a otra versión de Minecraft, el launcher se adapta solo sin tener
 * que publicar un launcher nuevo.
 */
export async function prepare(manifestUrl, onProgress, { forzar = false } = {}) {
  await ensureDirs();
  const cfg = await loadConfig();

  onProgress({ phase: 'manifest', message: 'Comprobando actualizaciones…', progress: 0 });
  const manifest = await fetchManifest(manifestUrl);

  const java = await ensureJava(onProgress);

  onProgress({ phase: 'minecraft', message: 'Preparando Minecraft…', progress: 0 });
  const versionJson = await fetchVersionJson(manifest.minecraft);
  const minecraft = await installMinecraft(versionJson, onProgress);

  const profile = await fetchFabricProfile(manifest.minecraft, manifest.fabricLoader);
  const fabric = await installFabric(profile, onProgress);

  onProgress({
    phase: 'pack',
    message: forzar ? 'Comprobando fichero a fichero…' : 'Comprobando el modpack…',
    progress: 0,
  });
  const plan = await syncPack(manifest, {
    includeOptional: cfg.extraOptimization,
    perfil: cfg.perfil,
    forzar,
  });
  const result = await applySync(plan, onProgress);

  onProgress({ phase: 'done', message: 'Listo para jugar', progress: 1 });
  return { manifest, versionJson, java, minecraft, fabric, sync: result };
}
