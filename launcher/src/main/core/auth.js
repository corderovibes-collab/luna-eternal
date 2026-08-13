import { createHash, randomUUID } from 'node:crypto';
import { getJson, postJson } from './net.js';
import { loadConfig, saveConfig } from './store.js';

const OAUTH = 'https://login.microsoftonline.com/consumers/oauth2/v2.0';
const SCOPE = 'XboxLive.signin offline_access';
const XBL = 'https://user.auth.xboxlive.com/user/authenticate';
const XSTS = 'https://xsts.auth.xboxlive.com/xsts/authorize';
const MC_LOGIN = 'https://api.minecraftservices.com/authentication/login_with_xbox';
const MC_PROFILE = 'https://api.minecraftservices.com/minecraft/profile';

/**
 * UUID offline, igual que lo calcula el servidor de Minecraft.
 *
 * Es un UUID v3 (MD5) sobre "OfflinePlayer:<nombre>". Coincidir con la fórmula del
 * servidor es lo que hace que el jugador conserve su inventario y sus Pokémon entre
 * sesiones. Corolario importante: **el progreso va atado al nombre**, así que
 * cambiar de nombre equivale a empezar de cero.
 */
export function offlineUuid(name) {
  const md5 = createHash('md5').update(`OfflinePlayer:${name}`, 'utf8').digest();
  md5[6] = (md5[6] & 0x0f) | 0x30; // versión 3
  md5[8] = (md5[8] & 0x3f) | 0x80; // variante RFC 4122
  const hex = md5.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

const VALID_NAME = /^[A-Za-z0-9_]{3,16}$/;

export function createOfflineAccount(name) {
  const trimmed = name.trim();
  if (!VALID_NAME.test(trimmed)) {
    throw new Error('El nombre debe tener entre 3 y 16 caracteres: letras, números o guion bajo.');
  }
  return {
    id: `offline:${trimmed.toLowerCase()}`,
    type: 'offline',
    name: trimmed,
    uuid: offlineUuid(trimmed),
    accessToken: '0', // el cliente exige un token; con el servidor en offline no se valida
  };
}

// ---------------------------------------------------------------- Microsoft

/** El polling del device code responde 400 con `error`, así que no se puede usar el fetch con reintentos. */
async function rawForm(url, fields) {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(fields).toString(),
  });
  return res.json();
}

async function xboxAuthenticate(msAccessToken) {
  const xbl = await postJson(XBL, {
    Properties: {
      AuthMethod: 'RPS',
      SiteName: 'user.auth.xboxlive.com',
      RpsTicket: `d=${msAccessToken}`,
    },
    RelyingParty: 'http://auth.xboxlive.com',
    TokenType: 'JWT',
  });

  let xsts;
  try {
    xsts = await postJson(XSTS, {
      Properties: { SandboxId: 'RETAIL', UserTokens: [xbl.Token] },
      RelyingParty: 'rp://api.minecraftservices.com/',
      TokenType: 'JWT',
    });
  } catch {
    throw new Error(
      'Xbox Live rechazó la cuenta. Suele ser que no tiene perfil de Xbox creado, '
      + 'o que es una cuenta infantil que necesita ir en un grupo familiar.',
    );
  }

  const uhs = xsts.DisplayClaims?.xui?.[0]?.uhs;
  if (!uhs) throw new Error('Xbox Live no devolvió el hash de usuario');

  const mc = await postJson(MC_LOGIN, { identityToken: `XBL3.0 x=${uhs};${xsts.Token}` });
  const profile = await getJson(MC_PROFILE, {
    headers: { Authorization: `Bearer ${mc.access_token}` },
  }).catch(() => null);

  if (!profile?.id) {
    throw new Error('Esta cuenta de Microsoft no tiene Minecraft: Java Edition.');
  }

  const uuid = profile.id.replace(
    /^(.{8})(.{4})(.{4})(.{4})(.{12})$/, '$1-$2-$3-$4-$5',
  );
  return { name: profile.name, uuid, accessToken: mc.access_token, skins: profile.skins ?? [] };
}

/**
 * Inicia el flujo device code. Devuelve el código que el usuario teclea en
 * microsoft.com/link y una promesa que se resuelve cuando autoriza.
 */
export async function beginMicrosoftLogin(clientId) {
  if (!clientId) {
    throw new Error(
      'Falta el Client ID de Azure. Créalo gratis (5 min) siguiendo docs/launcher.md '
      + 'y pégalo en Ajustes.',
    );
  }

  const device = await rawForm(`${OAUTH}/devicecode`, { client_id: clientId, scope: SCOPE });
  if (!device.device_code) {
    throw new Error(device.error_description ?? 'Microsoft no devolvió un device code');
  }

  const wait = (async () => {
    const deadline = Date.now() + device.expires_in * 1000;
    let interval = (device.interval ?? 5) * 1000;

    while (Date.now() < deadline) {
      await new Promise((r) => setTimeout(r, interval));
      const token = await rawForm(`${OAUTH}/token`, {
        client_id: clientId,
        grant_type: 'urn:ietf:params:oauth:grant-type:device_code',
        device_code: device.device_code,
      });

      if (token.access_token) {
        const mc = await xboxAuthenticate(token.access_token);
        return {
          id: `microsoft:${mc.uuid}`,
          type: 'microsoft',
          refreshToken: token.refresh_token,
          clientId,
          ...mc,
        };
      }
      if (token.error === 'authorization_pending') continue;
      if (token.error === 'slow_down') { interval += 5000; continue; }
      throw new Error(token.error_description ?? `Error de Microsoft: ${token.error}`);
    }
    throw new Error('Se agotó el tiempo para autorizar en microsoft.com/link');
  })();

  return {
    userCode: device.user_code,
    verificationUri: device.verification_uri ?? 'https://www.microsoft.com/link',
    expiresIn: device.expires_in,
    wait,
  };
}

/** Renueva el token de Minecraft (dura ~24 h) con el refresh token de Microsoft. */
export async function refreshMicrosoftAccount(account) {
  const token = await rawForm(`${OAUTH}/token`, {
    client_id: account.clientId,
    grant_type: 'refresh_token',
    refresh_token: account.refreshToken,
    scope: SCOPE,
  });
  if (!token.access_token) throw new Error('La sesión de Microsoft caducó, vuelve a iniciar sesión.');
  const mc = await xboxAuthenticate(token.access_token);
  return { ...account, refreshToken: token.refresh_token ?? account.refreshToken, ...mc };
}

// ------------------------------------------------------------------ Cuentas

export async function listAccounts() {
  const { accounts, activeAccount } = await loadConfig();
  return { accounts, activeAccount };
}

export async function upsertAccount(account) {
  const cfg = await loadConfig();
  const accounts = cfg.accounts.filter((a) => a.id !== account.id);
  accounts.push(account);
  return saveConfig({ accounts, activeAccount: account.id });
}

export async function removeAccount(id) {
  const cfg = await loadConfig();
  const accounts = cfg.accounts.filter((a) => a.id !== id);
  const activeAccount = cfg.activeAccount === id ? (accounts[0]?.id ?? null) : cfg.activeAccount;
  return saveConfig({ accounts, activeAccount });
}

/** Cuenta activa lista para lanzar, renovando el token de Microsoft si hace falta. */
export async function resolveActiveAccount() {
  const { accounts, activeAccount } = await loadConfig();
  const account = accounts.find((a) => a.id === activeAccount);
  if (!account) throw new Error('No hay ninguna cuenta seleccionada.');
  if (account.type === 'offline') return account;

  const fresh = await refreshMicrosoftAccount(account);
  await upsertAccount(fresh);
  return fresh;
}

export const newSessionId = () => randomUUID().replace(/-/g, '');
