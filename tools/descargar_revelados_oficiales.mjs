#!/usr/bin/env node
/** Build-time importer for official Pokémon.com Pokédex artwork (Gen I–II). */
import fs from 'node:fs';
import https from 'node:https';
import path from 'node:path';

const output = path.resolve(import.meta.dirname, '../mod/src/client/resources/assets/lunaeternal/textures/quien_es_ese_pokemon/revelados');
fs.mkdirSync(output, { recursive: true });
function fetch(url, destination) {
  const temporary = `${destination}.part`;
  return new Promise((resolve, reject) => https.get(url, { headers: { 'User-Agent': 'LunaEternalAssetBuilder/1.0' } }, (response) => {
    if (response.statusCode !== 200) return reject(new Error(`${response.statusCode} ${url}`));
    const file = fs.createWriteStream(temporary);
    response.pipe(file);
    file.on('finish', () => file.close(() => { fs.renameSync(temporary, destination); resolve(); }));
  }).on('error', reject));
}
async function fetchWithRetry(url, destination) {
  for (let attempt = 1; attempt <= 3; attempt++) {
    try { await fetch(url, destination); return; }
    catch (error) { if (fs.existsSync(`${destination}.part`)) fs.rmSync(`${destination}.part`); if (attempt === 3) throw error; }
  }
}
const pending = [];
for (let dex = 1; dex <= 251; dex++) {
  const id = String(dex).padStart(3, '0');
  const destination = path.join(output, `${id}.png`);
  if (!fs.existsSync(destination) || fs.statSync(destination).size === 0) {
    pending.push([id, destination]);
  }
}
let completed = 251 - pending.length;
for (let index = 0; index < pending.length; index += 10) {
  await Promise.all(pending.slice(index, index + 10).map(async ([id, destination]) => {
    await fetchWithRetry(`https://www.pokemon.com/static-assets/content-assets/cms2/img/pokedex/full/${id}.png`, destination);
    completed++;
  }));
  console.log(`revelados: ${completed}/251`);
}
