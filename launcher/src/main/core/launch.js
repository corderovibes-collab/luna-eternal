import { spawn } from 'node:child_process';
import { createWriteStream } from 'node:fs';
import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { allowed } from './minecraft.js';
import { newSessionId } from './auth.js';
import { paths } from './paths.js';
import { ES_WINDOWS, flagsDeSistema } from './plataforma.js';

/** Sustituye los `${...}` de los argumentos del JSON de versión. */
const substitute = (value, vars) =>
  value.replace(/\$\{(\w+)\}/g, (match, key) => (key in vars ? vars[key] : match));

/** Aplana `arguments.game` / `arguments.jvm`, respetando las reglas condicionales. */
function collectArgs(section, vars, features) {
  const out = [];
  for (const arg of section ?? []) {
    if (typeof arg === 'string') {
      out.push(substitute(arg, vars));
      continue;
    }
    if (!allowed(arg.rules, features)) continue;
    const values = Array.isArray(arg.value) ? arg.value : [arg.value];
    out.push(...values.map((v) => substitute(v, vars)));
  }
  return out;
}

/**
 * Flags de JVM para el cliente.
 *
 * Es el mismo perfil de G1 que usa el servidor: pausas cortas y frecuentes en vez
 * de parones largos. Aquí `-Xms` se deja bajo a propósito (al revés que en el
 * servidor): en un PC de escritorio interesa no reservar RAM que otros programas
 * puedan necesitar.
 */
function jvmFlags(ramGb) {
  const mb = ramGb * 1024;
  return [
    `-Xmx${mb}M`,
    `-Xms${Math.max(512, Math.floor(mb / 4))}M`,
    '-XX:+UseG1GC',
    '-XX:+ParallelRefProcEnabled',
    '-XX:MaxGCPauseMillis=130',
    '-XX:+UnlockExperimentalVMOptions',
    '-XX:+DisableExplicitGC',
    '-XX:G1NewSizePercent=28',
    '-XX:G1HeapRegionSize=16M',
    '-XX:G1ReservePercent=20',
    '-XX:G1MixedGCCountTarget=3',
    '-XX:InitiatingHeapOccupancyPercent=10',
    '-XX:G1RSetUpdatingPauseTimePercent=0',
    '-XX:SurvivorRatio=32',
    '-XX:MaxTenuringThreshold=1',
    '-XX:+PerfDisableSharedMem',
    '-Dfile.encoding=UTF-8',
    '-Dfml.ignoreInvalidMinecraftCertificates=true',
    '-Djava.net.preferIPv4Stack=true',
    // Lo propio de cada sistema. En macOS NO hace falta anadir
    // `-XstartOnFirstThread`: viene en el JSON de version de Mojang bajo una
    // regla `os=osx`, asi que lo pone `collectArgs` solo. Ponerlo aqui lo
    // duplicaria.
    ...flagsDeSistema(),
  ];
}

export async function launchGame({
  java, versionJson, fabric, minecraft, account, ramGb, onExit, onLog,
}) {
  await mkdir(paths.logs, { recursive: true });

  const classpath = [...fabric.classpath, ...minecraft.classpath, minecraft.clientJar];
  const features = { is_demo_user: false, has_custom_resolution: false };

  const vars = {
    auth_player_name: account.name,
    auth_uuid: account.uuid.replace(/-/g, ''),
    auth_access_token: account.accessToken,
    auth_session: `token:${account.accessToken}`,
    auth_xuid: account.xuid ?? '',
    clientid: newSessionId(),
    user_type: account.type === 'microsoft' ? 'msa' : 'legacy',
    user_properties: '{}',
    version_name: fabric.profile.id ?? versionJson.id,
    version_type: versionJson.type ?? 'release',
    game_directory: paths.instance,
    assets_root: paths.assets,
    game_assets: paths.assets,
    assets_index_name: minecraft.assetIndexId,
    natives_directory: minecraft.nativesDir,
    classpath: classpath.join(path.delimiter),
    classpath_separator: path.delimiter,
    library_directory: paths.libraries,
    launcher_name: 'luna-eternal-launcher',
    launcher_version: '1.0.0',
  };

  // El JSON de Fabric hereda `arguments` del de vanilla, así que se concatenan.
  const jvmFromJson = [
    ...collectArgs(versionJson.arguments?.jvm, vars, features),
    ...collectArgs(fabric.profile.arguments?.jvm, vars, features),
  ];
  const gameArgs = [
    ...collectArgs(versionJson.arguments?.game, vars, features),
    ...collectArgs(fabric.profile.arguments?.game, vars, features),
  ];

  // A proposito no se pasa `--quickPlayMultiplayer`: el juego arranca en el menu y
  // se entra con el boton "Conectarse a PokeReport". Entrar solo no dejaba ni tocar
  // las opciones, y en un PC justo parecia que se habia colgado.

  const args = [
    ...jvmFlags(ramGb),
    ...jvmFromJson,
    fabric.mainClass,
    ...gameArgs,
  ];

  const child = spawn(java.javaw, args, {
    cwd: paths.instance,
    detached: false,
    // Solo significa algo en Windows; en Mac y Linux se ignora, pero se deja
    // condicionado para que se lea que es cosa de un sistema concreto.
    windowsHide: ES_WINDOWS,
    stdio: ['ignore', 'pipe', 'pipe'],
  });

  const logFile = createWriteStream(path.join(paths.logs, `game-${Date.now()}.log`));
  logFile.write(`${java.javaw}\n${args.join('\n')}\n\n`);
  for (const stream of [child.stdout, child.stderr]) {
    stream.setEncoding('utf8');
    stream.on('data', (chunk) => {
      logFile.write(chunk);
      onLog?.(chunk);
    });
  }

  child.on('exit', (code) => {
    logFile.end();
    onExit?.(code ?? 0);
  });

  return child;
}
