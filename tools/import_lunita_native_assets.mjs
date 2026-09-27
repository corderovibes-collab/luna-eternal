#!/usr/bin/env node
/**
 * Imports the user-exported Blockbench geometry without reconstructing cubes
 * and converts the Eevee keyframes embedded in the original .bbmodel.
 */
import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..');
const nativeGeometry = process.argv[2] ?? 'C:/Users/JUAN/Downloads/lunitagecko.geo.json';
const sourceModel = process.argv[3] ?? 'C:/Users/JUAN/Downloads/lunitapokemon.bbmodel';
const geometryTarget = path.join(root, 'mod/src/main/resources/assets/lunaeternal/geo/lunita.geo.json');
const animationsTarget = path.join(root, 'mod/src/main/resources/assets/lunaeternal/animations/lunita.animation.json');

const geometry = JSON.parse(fs.readFileSync(nativeGeometry, 'utf8'));
const source = JSON.parse(fs.readFileSync(sourceModel, 'utf8'));
const geometryRoot = geometry['minecraft:geometry']?.[0];
if (!geometryRoot?.bones?.length) throw new Error('El .geo.json nativo no contiene huesos.');

const boneNames = new Set(geometryRoot.bones.map((bone) => bone.name));
const groups = new Map((source.groups ?? []).map((group) => [group.uuid, group.name]));
const previous = JSON.parse(fs.readFileSync(animationsTarget, 'utf8'));

function number(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) throw new Error(`Valor de animacion invalido: ${value}`);
  return parsed;
}

function vector(point) {
  return [number(point.x ?? 0), number(point.y ?? 0), number(point.z ?? 0)];
}

function importAnimation(animation) {
  const bones = {};
  for (const [uuid, animator] of Object.entries(animation.animators ?? {})) {
    if (uuid === 'effects' || animator.type !== 'bone' || !(animator.keyframes?.length)) continue;
    const name = groups.get(uuid);
    if (!name || !boneNames.has(name)) throw new Error(`El hueso ${name ?? uuid} de ${animation.name} no existe en el .geo.json nativo.`);
    const channels = {};
    for (const keyframe of animator.keyframes) {
      if (!['rotation', 'position', 'scale'].includes(keyframe.channel)) continue;
      // Blockbench stores the value after a boundary as the last data point.
      // GeckoLib's simple timestamp form is the same post-boundary value.
      const data = keyframe.data_points?.at(-1);
      if (!data) continue;
      (channels[keyframe.channel] ??= {})[String(number(keyframe.time))] = vector(data);
    }
    if (Object.keys(channels).length) bones[name] = channels;
  }
  const result = { animation_length: number(animation.length ?? 0), bones };
  if (animation.loop === 'loop' || animation.loop === true) result.loop = true;
  return result;
}

const imported = Object.fromEntries((source.animations ?? []).map((animation) => [animation.name, importAnimation(animation)]));
const custom = previous.animations ?? {};
// These are authored for Lunita's exported bone hierarchy.  The source Eevee
// "ground_idle" is a zero-length pose, so it cannot serve as live idle motion.
custom['animation.lunita.idle'] = {
  loop: true,
  animation_length: 2.4,
  bones: {
    body: { position: { '0': [0, 0, 0], '0.6': [0, 0.18, 0], '1.2': [0, 0, 0], '1.8': [0, 0.18, 0], '2.4': [0, 0, 0] } },
    head: { rotation: { '0': [0, -3, 0], '0.6': [1.5, 2, 0], '1.2': [0, 4, 0], '1.8': [-1.5, 2, 0], '2.4': [0, -3, 0] } },
    ear_left: { rotation: { '0': [0, 0, -3], '0.6': [2, 0, 4], '1.2': [0, 0, -1], '1.8': [-2, 0, 3], '2.4': [0, 0, -3] } },
    ear_right: { rotation: { '0': [0, 0, 3], '0.6': [2, 0, -4], '1.2': [0, 0, 1], '1.8': [-2, 0, -3], '2.4': [0, 0, 3] } },
    tail1: { rotation: { '0': [0, -10, 0], '0.6': [0, 10, 0], '1.2': [0, 18, 0], '1.8': [0, 5, 0], '2.4': [0, -10, 0] } },
    tail2: { rotation: { '0': [0, -6, 0], '0.6': [0, 8, 0], '1.2': [0, 13, 0], '1.8': [0, 3, 0], '2.4': [0, -6, 0] } },
    wingLeft: { rotation: { '0': [0, 0, -4], '0.6': [0, 0, 7], '1.2': [0, 0, 2], '1.8': [0, 0, 7], '2.4': [0, 0, -4] } },
    wingRight: { rotation: { '0': [0, 0, 4], '0.6': [0, 0, -7], '1.2': [0, 0, -2], '1.8': [0, 0, -7], '2.4': [0, 0, 4] } },
    Aureola: { rotation: { '0': [0, 0, 0], '2.4': [0, 360, 0] } }
  }
};
custom['animation.lunita.walk'] = {
  loop: true,
  animation_length: 0.8,
  bones: {
    body: { position: { '0': [0, 0, 0], '0.2': [0, 0.16, 0], '0.4': [0, 0, 0], '0.6': [0, 0.16, 0], '0.8': [0, 0, 0] } },
    head: { rotation: { '0': [3, 0, 0], '0.4': [-2, 0, 0], '0.8': [3, 0, 0] } },
    front_leg_left: { rotation: { '0': [28, 0, 0], '0.2': [0, 0, 0], '0.4': [-28, 0, 0], '0.6': [0, 0, 0], '0.8': [28, 0, 0] } },
    front_leg_right: { rotation: { '0': [-28, 0, 0], '0.2': [0, 0, 0], '0.4': [28, 0, 0], '0.6': [0, 0, 0], '0.8': [-28, 0, 0] } },
    back_leg_left: { rotation: { '0': [-28, 0, 0], '0.2': [0, 0, 0], '0.4': [28, 0, 0], '0.6': [0, 0, 0], '0.8': [-28, 0, 0] } },
    back_leg_right: { rotation: { '0': [28, 0, 0], '0.2': [0, 0, 0], '0.4': [-28, 0, 0], '0.6': [0, 0, 0], '0.8': [28, 0, 0] } },
    tail1: { rotation: { '0': [0, -13, 0], '0.4': [0, 13, 0], '0.8': [0, -13, 0] } },
    wingLeft: { rotation: { '0': [0, 0, -8], '0.4': [0, 0, 8], '0.8': [0, 0, -8] } },
    wingRight: { rotation: { '0': [0, 0, 8], '0.4': [0, 0, -8], '0.8': [0, 0, 8] } }
  }
};
// The original cry only references Cobblemon's Eevee sound event.  Keep its
// source timeline and provide Lunita's separate visual reaction instead.
custom['animation.lunita.cry'] ??= {
  animation_length: 1.2,
  bones: {
    body: { rotation: { '0': [0, 0, 0], '0.2': [-10, 0, 0], '0.55': [6, 0, 0], '1.2': [0, 0, 0] } },
    wingLeft: { rotation: { '0': [0, 0, -8], '0.3': [0, 0, 22], '0.65': [0, 0, -18], '1.2': [0, 0, -8] } },
    wingRight: { rotation: { '0': [0, 0, 8], '0.3': [0, 0, -22], '0.65': [0, 0, 18], '1.2': [0, 0, 8] } }
  }
};

fs.mkdirSync(path.dirname(geometryTarget), { recursive: true });
// No cube, UV, pivot or bone is regenerated: this is the exact native export.
fs.copyFileSync(nativeGeometry, geometryTarget);
fs.writeFileSync(animationsTarget, `${JSON.stringify({ format_version: '1.8.0', animations: { ...custom, ...imported } }, null, 2)}\n`);
console.log(`Geometria nativa importada: ${geometryRoot.bones.length} huesos; ${geometryRoot.bones.reduce((sum, bone) => sum + (bone.cubes?.length ?? 0), 0)} cubos.`);
console.log(`Animaciones Eevee importadas: ${Object.keys(imported).join(', ')}.`);
