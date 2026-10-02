import fs from 'node:fs';
import path from 'node:path';

const root = path.resolve(import.meta.dirname, '..');
const source = process.argv[2] ?? 'C:/Users/JUAN/Documents';
const assets = path.join(root, 'mod/src/main/resources/assets/lunaeternal');
const geometry = JSON.parse(fs.readFileSync(path.join(source, 'eevee_female.geo.json')));
const skeleton = geometry['minecraft:geometry'][0].bones;
const byName = new Map(skeleton.map(b => [b.name, b]));
function restPoint(name, point) {
  const bone = byName.get(name);
  const [rx, ry, rz] = bone.rotation ?? [0, 0, 0];
  if (ry || rz) throw new Error('Head attachment requires reviewing the changed rest rotations.');
  const pivot = bone.pivot ?? [0, 0, 0];
  const angle = -rx * Math.PI / 180;
  const y = point[1] - pivot[1], z = point[2] - pivot[2];
  const result = [point[0], pivot[1] + y * Math.cos(angle) - z * Math.sin(angle),
    pivot[2] + y * Math.sin(angle) + z * Math.cos(angle)];
  return bone.parent ? restPoint(bone.parent, result) : result;
}
const offset = restPoint('head_angle', [0, 0, 0]);
for (let axis = 0; axis < 3; axis++) {
  const unit = [0, 0, 0]; unit[axis] = 1;
  restPoint('head_angle', unit).forEach((v, i) => {
    if (Math.abs(v - offset[i] - unit[i]) > 1e-8) throw new Error('Head rest orientation changed.');
  });
}
const rootCubes = byName.get('Lunita').cubes;
if (rootCubes?.length !== 2) throw new Error('Review nose/chin fur assignment for the changed model.');
const attached = rootCubes.map(c => {
  const cube = structuredClone(c);
  for (const key of ['origin', 'pivot']) if (cube[key]) cube[key] = cube[key].map((v, i) => v - offset[i]);
  return cube;
});
delete byName.get('Lunita').cubes;
skeleton.push({ name: 'lunita_nose', parent: 'head_angle', pivot: [0, 0, 0], cubes: [attached[0]] });
skeleton.push({ name: 'lunita_chin_fur', parent: 'head_angle', pivot: [0, 0, 0], cubes: [attached[1]] });
const bones = new Set(geometry['minecraft:geometry'][0].bones.map(b => b.name));
const target = path.join(assets, 'animations/lunita.animation.json');
const existing = JSON.parse(fs.readFileSync(target)).animations;
const native = JSON.parse(fs.readFileSync(path.join(source, 'eevee_female.animation.json'))).animations;

// Cobblemon wraps static channels in {vector}; GeckoLib expects the vector itself.
function channel(value) {
  if (value.vector) return value.vector;
  if (Array.isArray(value)) return value;
  return Object.fromEntries(Object.entries(value).map(([time, point]) =>
    [time, point.vector ?? point]));
}
const animations = {};
for (const [name, animation] of Object.entries(native)) {
  const result = { ...animation, bones: {} };
  // The Eevee sound is a Cobblemon event, not a GeckoLib sound keyframe handler.
  delete result.sound_effects;
  for (const [bone, channels] of Object.entries(animation.bones ?? {})) {
    if (!bones.has(bone)) throw new Error(`${name}: missing bone ${bone}`);
    result.bones[bone] = Object.fromEntries(Object.entries(channels).map(([key, value]) => [key, channel(value)]));
  }
  animations[name] = result;
}
for (const name of ['idle', 'walk']) {
  const animation = structuredClone(existing[`animation.lunita.${name}`]);
  animation.bones = Object.fromEntries(Object.entries(animation.bones).filter(([bone]) => bones.has(bone)));
  animations[`animation.lunita.${name}`] = animation;
}
animations['animation.lunita.greet'] = {
  animation_length: 1.25,
  bones: {
    ...structuredClone(animations['animation.eevee.ground_idle'].bones),
    head: { rotation: { '0': [0, 0, 0], '0.25': [-12, 0, 0], '0.65': [6, 0, 0], '1.25': [0, 0, 0] } },
    tail1: { rotation: { '0': [20, 0, 0], '0.25': [20, -20, 0], '0.65': [20, 20, 0], '1.25': [20, 0, 0] } }
  }
};
animations['animation.lunita.return_home'] = structuredClone(animations['animation.lunita.walk']);
animations['animation.lunita.blink'] = {
  loop: true, animation_length: 5,
  bones: Object.fromEntries(['eyelid_left', 'eyelid_right'].map(bone => [bone, {
    position: { '0': [0, 0, 0], '4.8': [0, 0, 0], '4.84': [0, 0, -0.1], '4.96': [0, 0, -0.1], '5': [0, 0, 0] }
  }]))
};
fs.writeFileSync(path.join(assets, 'geo/lunita.geo.json'), JSON.stringify(geometry, null, 2) + '\n');
fs.copyFileSync(path.join(source, 'lunita textura.png'), path.join(assets, 'textures/entity/lunita.png'));
fs.writeFileSync(target, JSON.stringify({ format_version: '1.8.0', animations }, null, 2) + '\n');
console.log(`Lunita v2: ${bones.size} bones; ${Object.keys(animations).length} animations.`);
