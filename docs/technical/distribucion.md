# Distribución del pack

## Purpose

Cómo llega el pack al disco del jugador, y **qué pasa cuando algo de esa cadena
falla**. Es la parte del proyecto que no tiene segunda oportunidad: si esto se
rompe, no se rompe para uno, se rompe para todos a la vez.

## Dependencies

- [`launcher.md`](launcher.md) · [`client-pack.md`](client-pack.md)

## Current Status

Reestructurada el **2026-08-17**. Antes de ese día, **cero de los cinco riesgos
de crecimiento estaban cubiertos**. Hoy quedan cubiertos dos, y los otros tres
están identificados con su coste. Ver §5.

---

## 1. El problema que había

```
launcher ──► raw.githubusercontent/…/manifest.json     ← PRIMERA peticion del arranque
                                                          limita por peticiones (429)
                                                          cachea 3 min POR RUTA
                                                          se SOBRESCRIBE en cada publicacion
             cada fichero: UNA sola url                   sin espejo, sin failover
```

Tres cosas, y las tres solo se ven cuando ya hay gente dentro:

| | Qué pasaba |
|---|---|
| **429 en la primera petición** | `raw` no es un CDN de distribución. Ya costó media mañana: quien instalaba de cero se llevaba `HTTP 429 en manifest.json` **antes de empezar**, y a quien ya lo tenía bajado no le pasaba nada — de ahí el «a mí me funciona y a ellos no» |
| **Sin vuelta atrás** | `manifest.json` se sobrescribía. Una publicación mala rompe a **todo el mundo a la vez**, y arreglarlo era regenerar y republicar 185 MB con el pack roto mientras tanto |
| **Un solo origen por fichero** | Si GitHub tose, no hay a dónde ir. No hay plan B, solo esperar |

---

## 2. Cómo funciona ahora

```
launcher ──► releases/pack-manifest/latest.json      ~250 B · CDN de descargas · SIN limite
                    │                                 es lo UNICO mutable de la cadena
                    │  {"manifest": "…/manifest-a1b2c3d4e5.json", "sha1": "…"}
                    ▼
             releases/pack-assets/manifest-<huella>.json    INMUTABLE, no se toca jamas
                    │
                    │  cada entrada: "urls": [primario, espejo, …]
                    ▼
             el launcher recorre la lista hasta que uno conteste
```

**La idea entera cabe en una frase:** lo que cambia es de 250 bytes, y todo lo
que pesa es inmutable.

### Volver atrás

Los manifiestos viejos no se borran nunca — son unos kilobytes cada uno.
Volver a cualquiera de ellos es reescribir el puntero:

```bash
gh release view pack-assets --repo corderovibes-collab/luna-eternal-pack   # ver las huellas
python tools/gen_manifest.py --volver-a a1b2c3d4e5
```

Eso es toda la operación. **250 bytes en vez de 185 MB**, y el pack vuelve a
estar bueno en el tiempo que tarda una subida de un fichero de texto.

### El orden de publicación no es negociable

```
1. subir los activos          (185 MB)
2. subir manifest-<huella>    inmutable
3. mover el puntero           ← LO ULTIMO
```

Al revés hay una ventana —la que tarden los 185 MB— en la que el puntero
anuncia ficheros que todavía no existen, y cualquiera que le dé a Jugar en ese
rato se lleva un 404 a mitad de descarga. Al derecho no hay ventana: un activo
subido que aún nadie referencia no le hace daño a nadie.

### Se comprueba la huella del manifiesto

El puntero trae el `sha1` del manifiesto, y el launcher lo verifica **antes de
fiarse de su contenido**.

> No es paranoia de manual: el manifiesto elige de qué URL salen los 185 MB que
> se instalan, y con ellas **lo que acaba ejecutándose en la máquina del
> jugador**. Sin esa comprobación, cualquiera que pueda colocarle un JSON —un
> DNS envenenado, el proxy de un wifi público— le elige los mods. Es el único
> punto de la cadena donde una firma sale gratis, porque el puntero ya la trae.

### Espejos

Cada entrada lleva `urls` en orden de preferencia en vez de una `url` suelta.

- Un **4xx es definitivo para ese origen, no para el fichero**: si hay espejo,
  se prueba. Antes un 404 en el primario abortaba la descarga entera sin llegar
  a preguntarle al segundo.
- Con varios orígenes, cada uno tiene **un intento** antes de pasar al
  siguiente. Con los 8 reintentos de siempre y retroceso hasta 30 s, un origen
  caído se comía **más de dos minutos** antes de que al espejo le llegara el
  turno — y para entonces el jugador ya ha cerrado el launcher. Un espejo al
  que se llega tarde no sirve de nada.
- **Solo se espejan los ficheros nuestros.** Los de Modrinth y Mojang salen de
  sus propios CDN, y copiarlos ni es nuestro trabajo ni lo permiten todas sus
  licencias (D-030).

> ⚠️ **`url` (singular) se mantiene además de `urls`.** Los launchers ya
> instalados leen ese campo. Quitarlo dejaría sin pack a todo el que no se haya
> actualizado todavía — que es justo quien no puede actualizarse, porque el pack
> no le baja. Se retira cuando no quede nadie en 1.0.x.

---

## 3. Compatibilidad hacia atrás

Se publica **todo por los dos caminos** mientras queden launchers antiguos:

| Camino | Quién lo usa |
|---|---|
| `pack-manifest/latest.json` → manifiesto con huella | launcher ≥ 1.1.0 |
| `raw…/manifest.json` | launcher 1.0.x |

`fetchManifest` distingue puntero de manifiesto **por el contenido, no por la
URL** (el puntero trae `manifest`; el manifiesto trae `files`). Es lo único que
no depende de que nadie haya migrado nada: un launcher con la URL vieja guardada
en su configuración sigue funcionando sin tocarle nada.

---

## 4. Qué se midió

| | Antes | Ahora |
|---|---|---|
| Ficheros servidos desde `raw` | 5 (incluido el manifiesto) | **0** |
| Orígenes por fichero | 1 | 1-2, ampliable sin tocar el launcher |
| Coste de volver atrás | regenerar + republicar 185 MB | **250 bytes** |
| Manifiesto verificado | no | sí, sha1 desde el puntero |
| Pruebas del launcher | 32 | **37** |

---

## 5. Lo que sigue sin estar cubierto

Honestidad por delante: de los cinco riesgos de crecimiento, **esto cubre dos**.

| # | Riesgo | Estado | Qué costaría |
|---|---|---|---|
| 1 | Distribución sin CDN ni espejo | ✅ **cubierto** | — |
| 5 | Sin versionado ni vuelta atrás | ✅ **cubierto** | — |
| 3 | **Sin firma de código** | ⏸ **diferido a proposito** | Un certificado, ~120 $/año. **No compensa al tamaño actual** y no rompe nada. Ver §6 |
| 2 | La identidad es el nombre | ⬜ | Que el UUID lo dé el servidor. Toca mod + base de datos |
| 4 | Cero observabilidad | ⬜ | Telemetría anónima de instalación: fase + error, sin datos personales |

### El espejo de verdad, cuando haga falta

Hoy `ESPEJOS_ACTIVOS` está vacío: no hay segundo CDN gratuito que sirva ficheros
de 129 MB. El mecanismo ya está montado y probado, así que el día que se abra un
bucket es **añadir una cadena y republicar**:

```python
# tools/gen_manifest.py
ESPEJOS_ACTIVOS = ["https://pack.pokereport.net/"]
```

Cloudflare R2 da 10 GB de almacenamiento y **egreso a coste cero**; Bunny cobra
~0,01 $/GB, o sea ~2 $/mes para 185 GB. Con 1 000 jugadores instalando de cero
al mes, eso es el orden de magnitud del que hablamos.

---

## 6. La firma de código

> ⏸️ **DIFERIDO a propósito** (decisión del usuario, 2026-08-17). Lo que sigue
> queda escrito para cuando toque, no como tarea pendiente.

### Qué NO es

**No tiene nada que ver con las cuentas no premium.** Son dos cosas separadas y
es fácil unirlas por error:

| | Qué resuelve | Coste |
|---|---|---|
| Cuentas no premium | Que alguien sin cuenta de Mojang entre al servidor | **0 €**, ya hecho: `online-mode=false` + cuentas offline |
| Firma de código | Que Windows no acuse al **instalador** de ser un virus | ~120 $/año |

La firma no mira las cuentas de Minecraft: mira **quién fabricó el `.exe`**.

### Qué es, entonces

Sin firma, quien abre `LunaEternal-setup.exe` se encuentra una pantalla azul a
todo tamaño —*«Windows protegió su PC»*— y tiene que buscar el enlace pequeño de
**Más información → Ejecutar de todas formas**. Una parte de la gente no lo
hace: asume que es un virus y cierra.

**No tenerlo no rompe nada.** El launcher se reparte sin firmar desde siempre.
Lo único que se pierde de verdad es la **autoactualización en macOS**, que sin
firma no puede aplicarse — en Windows sí funciona, porque electron-updater
verifica el SHA512 del `latest.yml`.

### Cuándo compensa

Con 20 personas, el aviso es un mensaje de Discord. Con 2 000 es el ticket de
soporte número uno. **El umbral es cuando explicar dónde hay que pulsar cueste
más que el certificado**, no un número de jugadores concreto.

Las releases de FreesmLauncher también van sin firmar, así que el fork **no lo
arregla** (D-035).

Además, en macOS la autoactualización **no puede aplicarse sin firma**: hoy el
launcher solo avisa y abre la página de descargas.

### Qué comprar

| Opción | Precio | Pega |
|---|---|---|
| **Azure Trusted Signing** ← recomendado | ~10 $/mes | Exige validación de identidad (individual u organización). Sin token físico |
| Certificado OV clásico | ~300 $/año | **Obliga a token físico o HSM** desde 2023 (CA/B Forum). Firmar desde CI se complica |
| Certificado EV | ~500 $/año | Reputación de SmartScreen inmediata. Mismo problema del token |

Para este caso —un servidor de Minecraft, persona o empresa pequeña, que firma
desde GitHub Actions— **Azure Trusted Signing es la respuesta**: es la única de
las tres que firma desde CI sin hardware de por medio.

> ⚠️ **La validación de identidad tarda días, no minutos.** Es lo primero que
> hay que arrancar y lo último de lo que uno se acuerda. Mientras no exista el
> certificado, no hay nada que programar aquí.

### Qué pasa cuando llegue

No hace falta tocar `electron-builder.yml` para un certificado clásico:
**electron-builder recoge `CSC_LINK` y `CSC_KEY_PASSWORD` del entorno solo**.
Para Trusted Signing sí hace falta un bloque `win.azureSignOptions` y las
variables `AZURE_TENANT_ID` / `AZURE_CLIENT_ID` / `AZURE_CLIENT_SECRET`.

Para el fork de Qt es `signtool` sobre el `.exe` y sobre el instalador NSIS,
como un paso más del flujo de compilación.

> ⚠️ **Firmar el instalador no basta: hay que firmar también el ejecutable de
> dentro.** Un instalador firmado que suelta un `.exe` sin firmar vuelve a
> disparar SmartScreen la primera vez que se ejecuta el juego — que es
> justamente cuando el jugador ya creía haber terminado.

---

## Last Decision

**2026-08-17** — el manifiesto deja de servirse desde `raw` y pasa a resolverse
por un puntero inmutable con espejos. Ver CLAUDE.md D-036.

## Next Actions

- `LNC-003` **Comprar el certificado de firma.** Bloquea el riesgo #3
- `LNC-004` Telemetría anónima de instalación (riesgo #4)
- `SEC-007` Identidad servida por el servidor (riesgo #2, toca el mod)
