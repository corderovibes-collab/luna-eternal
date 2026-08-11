# El pack de cliente

## Purpose

Cómo entra un jugador. Qué necesita instalar y por qué.

## Dependencies

- [`../world/worlds.md`](../world/worlds.md) §7

## Current Status

**Generados y listos.** Son **dos** packs, no uno:

| Pack | Fichero | Mods | Descarga |
|---|---|---|---|
| **Jugador** | `PokeReport-LunaEternal-0.1.0.mrpack` | 7 | **136 MB** |
| **Constructor** | `PokeReport-LunaEternal-Constructor-0.1.0.mrpack` | 9 | 182 MB |

Ambos en `build/`. **Tú quieres el de constructor** — ver §2-bis.

---

## 1. Corrección de P10

El principio decía *"ningún mod de cliente es obligatorio"*. **Era cierto
cuando se escribió y dejó de serlo al instalar Cobblemon**, que es cliente y
servidor por naturaleza: sin él en el cliente, no se puede conectar.

El principio corregido no es "cero mods" sino **el mínimo posible**:

| | Diosesmon | Nosotros |
|---|---|---|
| Descarga | Modpack completo | **136 MB** |
| RAM recomendada | **8 200 MB** | 4 GB sobran |
| Mods | Decenas | **7** |
| Obligatorio antes de jugar | Sí | Sí, pero es un import de dos minutos |

La ventaja se mantiene, solo que la cifra honesta es 136 MB, no cero.

---

## 2. Qué lleva el pack de jugador, y por qué cada uno

| Mod | Motivo | Licencia |
|---|---|---|
| **Cobblemon 1.7.3** | Es el juego | MPL-2.0 |
| **Fabric API** | Lo exige Cobblemon | Apache-2.0 |
| **Sodium** | Rendimiento gráfico. Duplica los FPS en equipos modestos | Polyform Shield |
| **Lithium** | Rendimiento general | LGPL-3.0 |
| **FerriteCore** | Menos memoria | MIT |
| **EntityCulling** | No dibuja lo que no se ve | tr7zw Protective |
| **Mod Menu** | Gestionar mods desde el juego | MIT |

**Nada más.** Cada mod añadido es un punto de fallo, RAM y un bloqueo
potencial cuando salga Minecraft 1.22 (P5).

---

## 2-bis. El pack de constructor, y por qué está separado

Añade dos mods que **solo sirven para construir**:

| Mod | Motivo | Licencia |
|---|---|---|
| **WorldEdit CUI** | Dibuja la selección. Sin él marcas dos esquinas y no ves qué marcaste | EPL-2.0 |
| **Axiom 5.4.2** | Editor de construcción con interfaz de verdad | propietaria — ver abajo |

**Por qué no van en el pack de todos:** son 46 MB de herramienta de desarrollo
que un jugador normal no usará jamás. Meterlos en el pack general contradice el
propio P10 que acabamos de corregir — el mínimo posible es el mínimo *para
jugar*, no el mínimo para trabajar.

> **Axiom y su licencia (D-008):** su uso no comercial en un servidor privado
> propio es gratuito, pero **exige pedir una whitelist en su Discord**. Está
> instalado en el servidor; el trámite sigue pendiente y es del usuario.
> Detalle en [construccion.md §3-bis](../world/construccion.md).

> **Sobre licencias (D-008):** el `.mrpack` **no redistribuye** ningún mod:
> guarda URLs y hashes, y el launcher los baja de Modrinth. Por eso las
> licencias restrictivas de Sodium y EntityCulling no son un problema — se
> descargan de su canal oficial.

---

## 3. Cómo se instala

### PrismLauncher *(el que ya tienes)*

```
Add Instance → Import → seleccionar el .mrpack → Launch
```

### Modrinth App

```
Arrastrar el .mrpack a la ventana
```

El servidor **ya viene en la lista de multijugador**: el pack incluye un
`servers.dat` con la dirección, así que no hay que escribir ninguna IP.

---

## 4. Por qué un `.mrpack` y no el launcher de Electron

El proyecto anterior tiene un launcher propio en Electron, con instaladores
para Windows y macOS. **Es mejor producto y llegará**, pero:

| | `.mrpack` | Launcher Electron |
|---|---|---|
| Tiempo hasta jugar | **Hoy** | Días de trabajo |
| Actualizaciones | Reimportar | Automáticas |
| Marca propia | Ninguna | Completa |
| Java incluido | No | Sí |

Para **probar el servidor ahora**, el `.mrpack` sobra. El launcher es lo que
se le da a los jugadores el día del lanzamiento, y se adapta del que ya existe
en `D:\PokeReport 2\launcher` (Electron 43, electron-builder, CI en GitHub
Actions ya montada).

---

## 5. Regenerarlo

Los packs se generan con un script contra la API de Modrinth, así que
actualizar versiones es volver a ejecutarlo — no editar JSON a mano. Una sola
ejecución produce los dos.

```
python tools/gen_modpack.py
```

> ⚠️ **La versión del cargador también se consulta, no se escribe.** Estaba
> fijada a mano en `0.16.14` y el pack **no arrancaba**: Cobblemon 1.7.3 exige
> `0.17.2` o superior y Fabric aborta con *"Incompatible mods found!"*. Ahora
> se pide a `meta.fabricmc.net` la última estable y se comprueba contra
> `LOADER_MINIMO`; si algún día fuera menor, el script falla en vez de generar
> un pack roto.
>
> **La lección es la de siempre en este proyecto:** todo número escrito a mano
> caduca en silencio. Las versiones de los 9 mods ya se consultaban; la del
> cargador se me quedó fuera.

## Next Actions

1. Importar el pack de **constructor** y entrar al servidor
2. Pedir la whitelist de Axiom en su Discord (§2-bis)
3. `LNC-001` — adaptar el launcher de Electron cuando el servidor esté listo

## Related Systems

- [Los mundos](../world/worlds.md) · [Construcción](../world/construccion.md)
