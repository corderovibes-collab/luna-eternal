# El pack de cliente

## Purpose

Cómo entra un jugador. Qué necesita instalar y por qué.

## Dependencies

- [`../world/worlds.md`](../world/worlds.md) §7

## Current Status

**Generado y listo.** `build/PokeReport-LunaEternal-0.1.0.mrpack` — 137 MB.

---

## 1. Corrección de P10

El principio decía *"ningún mod de cliente es obligatorio"*. **Era cierto
cuando se escribió y dejó de serlo al instalar Cobblemon**, que es cliente y
servidor por naturaleza: sin él en el cliente, no se puede conectar.

El principio corregido no es "cero mods" sino **el mínimo posible**:

| | Diosesmon | Nosotros |
|---|---|---|
| Descarga | Modpack completo | **137 MB** |
| RAM recomendada | **8 200 MB** | 4 GB sobran |
| Mods | Decenas | **7** |
| Obligatorio antes de jugar | Sí | Sí, pero es un import de dos minutos |

La ventaja se mantiene, solo que la cifra honesta es 137 MB, no cero.

---

## 2. Qué lleva, y por qué cada uno

| Mod | Motivo | Licencia |
|---|---|---|
| **Cobblemon 1.7.3** | Es el juego | MPL-2.0 |
| **Fabric API** | Lo exige Cobblemon | Apache-2.0 |
| **Sodium** | Rendimiento gráfico. Duplica los FPS en equipos modestos | Polyform Shield |
| **Lithium** | Rendimiento general | LGPL-3.0 |
| **FerriteCore** | Menos memoria | MIT |
| **EntityCulling** | No dibuja lo que no se ve | tr7zw Protective |
| **Mod Menu** | Gestionar mods desde el juego | MIT |
| **WorldEdit CUI** | Dibuja la selección de WorldEdit | EPL-2.0 |

> **WorldEdit CUI** solo hace falta a quien construya, pero pesa 200 KB y sin
> él se construye a ciegas: marcas dos esquinas y no ves qué has marcado.

**Nada más.** Cada mod añadido es un punto de fallo, RAM y un bloqueo
potencial cuando salga Minecraft 1.22 (P5).

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

El pack se genera con un script contra la API de Modrinth, así que actualizar
versiones es volver a ejecutarlo — no editar JSON a mano.

```
python tools/gen_modpack.py
```

## Next Actions

1. Probar el pack e informar de fallos
2. `LNC-001` — adaptar el launcher de Electron cuando el servidor esté listo

## Related Systems

- [Los mundos](../world/worlds.md) · [Construcción](../world/construccion.md)
