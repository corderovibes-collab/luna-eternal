# El launcher

## Purpose

Cómo entra un jugador **sin instalar nada a mano** y cómo recibe todas las
actualizaciones —del pack y del propio launcher— sin que nadie le mande nada.

## Dependencies

- [`client-pack.md`](client-pack.md) · [`../world/construccion.md`](../world/construccion.md) §3-ter

## Current Status

Código completo en [`launcher/`](../../launcher). **25/25 pruebas del núcleo en
verde**, incluidas descargas y metadatos reales de Mojang y Fabric.

| | |
|---|---|
| Base | Electron 43 · sin dependencias de runtime salvo `electron-updater` |
| Origen | Adaptado del launcher de `D:\PokeReport 2` (v1.6.2), que ya funcionaba |
| Datos | `%APPDATA%\.lunaeternal` — desinstalar es borrar esa carpeta |
| Manifiesto | `luna-eternal-pack` (repo **público**) |
| Releases | `luna-eternal-pack` (repo **público**) |

---

## 1. Por qué un launcher y no seguir con el `.mrpack`

El `.mrpack` sirvió para probar, y se conserva. Pero para repartirlo a gente
que no es tú, pierde en todo lo que importa:

| | `.mrpack` | Launcher |
|---|---|---|
| Qué instala el jugador | PrismLauncher **+** importar el pack | un `.exe` |
| Java | el suyo, y que sea el correcto | **lo trae** el launcher |
| Actualizar el pack | reimportar a mano | **solo** |
| Actualizar el launcher | — | **solo** |
| Cuando algo se rompe | «mándame una captura» | **dice la causa y ofrece el arreglo** |

La diferencia decisiva es la tercera y la cuarta. Con 12 personas se puede pedir
que reimporten; con un servidor abierto, no.

---

## 2. Las dos actualizaciones, que son distintas

Se confunden con facilidad y funcionan por caminos separados:

```
ACTUALIZAR EL PACK            manifest.json en el repo público
  mod nuevo, quitar un mod      → el launcher compara SHA1 y baja el delta
  ~segundos                     → NO hace falta repartir nada

ACTUALIZAR EL LAUNCHER        release en el repo público
  cambia el launcher mismo      → electron-updater compara versión
  ~unos MB (blockmap)           → NO hace falta repartir nada
```

**El instalador se reparte UNA vez.** A partir de ahí, todo llega solo.

### Cómo se actualiza el pack

```bash
python tools/gen_manifest.py              # generar y ver qué sale
python tools/gen_manifest.py --publicar   # subirlo al repo público
```

Las versiones de los mods **se consultan a Modrinth**, no se escriben a mano.
Un número escrito a mano caduca en silencio — ya pasó con la versión del
cargador de Fabric, y el pack generado no arrancaba.

> ⚠️ **La rama de `luna-eternal-pack` es `master`, no `main`.** Escribir `main`
> por costumbre publica el manifiesto correctamente y deja el enlace en 404, o
> sea: todo el mundo sin pack, y el launcher sin forma de saber por qué. El
> script lo consulta con `gh` en vez de suponerlo; el valor por defecto del
> launcher (`main.js`) sí está escrito, y es el único sitio donde hay que
> tocarlo si algún día cambia.

**Publicado y verificado** el 2026-08-12: `HTTP 200`, 10 ficheros, 182 MB, con
Axiom y WorldEdit CUI marcados solo para el perfil de constructor.

> ⚠️ **El servidor de desarrollo es `.lat`, no `.com`.** Estaba escrito
> `s12.mia.us.tarohosting.com` en la documentación, en `gen_modpack.py` y por
> tanto en el `servers.dat` que reparte el pack. Ese dominio **no existe en
> DNS**, así que el launcher marcaba el servidor como *"No responde"* — y el
> síntoma no apuntaba a un dominio mal escrito, apuntaba a que el servidor
> estaba caído. Comprobado contra la asignación real del panel:
> `103.195.100.223` con alias `s12.mia.us.tarohosting.lat`, puerto `33043`.
> Producción siempre había estado bien (`s17.…lat`); solo el de desarrollo
> tenía la errata.

### Cómo se actualiza el launcher

```bash
# 1. subir la versión en launcher/package.json
# 2.
git tag launcher-v1.0.1 && git push --tags
```

GitHub Actions compila Windows y macOS y publica la release. Los jugadores la
reciben al abrir el launcher: se descarga sola y se instala al cerrar.

> ⚠️ **Un paso, una sola vez:** el código vive en el repositorio privado y los
> binarios se publican en el público, así que el token automático de Actions no
> sirve. Hay que crear un token personal con permiso de escritura sobre
> `luna-eternal-pack` y guardarlo como secreto `PACK_TOKEN`. Está explicado
> paso a paso en [`.github/workflows/launcher.yml`](../../.github/workflows/launcher.yml).
> Sin él la compilación **no falla**: deja el `.exe` como artefacto descargable.

---

## 3. Los dos perfiles

Un solo instalador y un solo manifiesto sirven para las dos formas de entrar:

| Perfil | Qué añade | Para quién |
|---|---|---|
| **Jugador** | nada | todo el mundo |
| **Constructor** | Axiom · WorldEdit CUI · Litematica + MaLiLib (~50 MB) | quien construye la ciudadela |

> **Litematica es para medir y para superponer un plano**, no para construir:
> para eso está Axiom. Su ajuste `easyPlace` coloca los bloques del esquema
> solo, con una precisión y un ritmo que ningún humano tiene — viene apagado y
> así se queda. En un servidor ajeno es justo lo que hace que te baneen.

Se elige en la pantalla de Jugar. Cambiar de perfil **instala o desinstala**
esas herramientas la próxima vez que se juegue, sin tocar mundos ni ajustes:
el manifiesto marca esos ficheros con `profiles: ["constructor"]` y la limpieza
de ficheros retirados hace el resto.

> El perfil solo decide **qué se instala en tu PC**. Para poder *usar* Axiom
> hace falta además permiso en el servidor — nivel de operador 2, ver
> [construccion.md §3-ter](../world/construccion.md).

---

## 4. Lo que lo hace rápido, y por qué

Heredado del launcher anterior, donde ya estaba medido:

- **Solo baja lo que cambia.** Cada fichero lleva su SHA1; se compara con el
  disco. La primera instalación son ~900 MB, las siguientes unos pocos.
- **El hash se calcula mientras se descarga**, no releyendo después. Con ~4000
  assets de Minecraft, eso es segundos en vez de minutos.
- **16 descargas a la vez**, con reintentos y retroceso exponencial.
- **Escritura atómica:** se baja a `.part` y se renombra al terminar. Una
  descarga cortada nunca deja un fichero corrupto que *parezca* bueno.
- **Cero dependencias de runtime** salvo `electron-updater`. Incluido un lector
  de ZIP propio.

---

## 5. Cuando algo se rompe

Esto es lo nuevo respecto al launcher anterior, y sale directamente de los
fallos que ya costaron tiempo en este proyecto.

### Reparar

Comprueba **fichero a fichero** la huella de todo el pack y vuelve a bajar lo
que no cuadre. Está en Ajustes, y también aparece como botón cuando el
diagnóstico lo recomienda.

> **Por qué hace falta, si ya se comprueba el SHA1 al instalar:** el camino
> normal se fía de `installed.json` para no rehashear miles de ficheros en cada
> arranque. Eso es correcto casi siempre, y es exactamente lo que deja pasar un
> fichero que se corrompió **después** de instalarse.

### Diagnóstico

Cuando el juego se cierra solo, el launcher traduce la causa. Cada entrada de la
tabla es un fallo que **ya ha ocurrido de verdad**:

| Síntoma en el log | Qué se le dice | Botón |
|---|---|---|
| `invalid LOC header`, `Unexpected end of ZLIB` | hay un fichero corrupto | Reparar |
| `OutOfMemoryError` | se quedó sin memoria | Ir a Ajustes |
| `Incompatible mods found` | quedó a medio actualizar | Reparar |
| `Mixin apply failed` | dos mods se pisan | Reparar |
| `Failed to create window`, `OpenGL 3.2` | controlador de la gráfica | — |
| código `-1073741819` | violación de acceso, casi siempre la gráfica | — |

> **El registro manda sobre el código de salida**, y hay una prueba que lo fija:
> un código conocido no puede tapar una causa que el log dice a las claras.

---

## 6. Cuentas

Solo **cuentas offline**: se pide un nombre y se entra. Es lo que corresponde a
`online-mode=false` (ver CLAUDE.md §0).

> ⚠️ El UUID se calcula a partir del nombre (`OfflinePlayer:<nombre>`, igual que
> lo hace el servidor). **El progreso va atado al nombre**: quien se lo cambie
> empieza de cero. Hay una prueba que fija esa fórmula contra la del servidor,
> porque si se desviara nadie se daría cuenta hasta perder partidas.

El motor de inicio de sesión con Microsoft sigue en el código (`core/auth.js`),
apagado en la interfaz: necesita un Client ID de Azure aprobado por Mojang, y en
un servidor offline no aporta nada. Si algún día se pasa a `online-mode=true`,
es volver a enseñar su panel.

---

## 7. Cómo está montado

```
launcher/
  src/main/core/    net · zip · java · minecraft · fabric · pack
                    auth · launch · ping · preflight · plataforma
                    actualizar   ← autoactualización del launcher
                    diagnostico  ← por qué se cerró el juego
  src/main/main.js  ventana, IPC y ciclo de vida
  src/preload/      puente cerrado entre la interfaz y el proceso principal
  src/renderer/     interfaz (HTML/CSS/JS a pelo, sin framework)
  tools/            pruebas del núcleo y capturas
```

Decisiones que conviene conocer antes de tocarlo:

- **La interfaz no puede tocar el disco ni la red.** `contextIsolation`, sin
  Node, y solo ve lo que expone el preload. Todo el trabajo ocurre en el
  proceso principal (es el mismo principio que P6 en el servidor).
- **`versions/`, `libraries/` y `assets/` siguen el layout oficial de Mojang**,
  así que valen para cualquier otro launcher si algún día se migra.
- **El manifiesto manda sobre la versión de Minecraft y de Fabric.** Si el pack
  salta a 1.22, el launcher se adapta solo.
- **La carpeta de datos es propia** (`.lunaeternal`). No se comparte con el
  launcher del proyecto anterior: son servidores distintos con mods distintos, y
  compartirla significaría que instalar uno rompe el otro.

---

## 8. Firma de código

No va firmado, y es una decisión con consecuencias que conviene tener claras:

| | Sin firma |
|---|---|
| **Windows** | SmartScreen avisa la primera vez: *Más información* → *Ejecutar de todas formas*. **La autoactualización funciona igual**: electron-updater verifica el SHA512 del `latest.yml` |
| **macOS** | Gatekeeper lo bloquea: clic derecho → *Abrir*. Y la autoactualización **no puede aplicarse**, así que en Mac el launcher solo avisa y abre la página de descargas |

Un certificado OV cuesta ~200 €/año y **sigue mostrando el aviso** hasta
acumular reputación; solo un EV lo evita desde el primer día, y es bastante más
caro. Para un servidor privado no compensa todavía.

---

## 9. Compilarlo

```bash
cd launcher
npm install
npm start        # abrirlo en modo desarrollo
npm test         # 25 pruebas del núcleo
npm run dist     # instalador en launcher/dist/
```

> En desarrollo **no se comprueban actualizaciones**: sin empaquetar no existe
> `app-update.yml` y electron-updater lanzaría un error feo. Es deliberado.

## Next Actions

1. Crear el secreto `PACK_TOKEN` (§2) — es del usuario
2. `python tools/gen_manifest.py --publicar` para dejar el manifiesto en línea
3. Etiquetar `launcher-v1.0.0` y repartir el `.exe` de la release
4. Arte propio: icono del launcher y logo del raíl (hoy son los del proyecto
   anterior, como relleno)

## Related Systems

- [El pack de cliente](client-pack.md) · [Construcción](../world/construccion.md)
