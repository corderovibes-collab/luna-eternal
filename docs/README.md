# Documentación — PokeReport: Luna Eternal

Índice de navegación. **Empieza siempre por [`../CLAUDE.md`](../CLAUDE.md).**

Este índice existe para **no cargar contexto innecesario**: localiza el
documento que necesitas, léelo, y sigue solo sus `Dependencies` declaradas.

---

## Estado de la documentación

| Documento | Estado | Qué contiene |
|---|---|---|
| [`../CLAUDE.md`](../CLAUDE.md) | ✅ | Visión, principios, decisiones, fases |
| [`technical/infrastructure.md`](technical/infrastructure.md) | ✅ | Servidores, RAM, riesgos críticos, seguridad |
| [`analysis/current-server-audit.md`](analysis/current-server-audit.md) | ✅ | Qué existe hoy · qué se cosecha · qué se descarta |
| [`roadmap/backlog.md`](roadmap/backlog.md) | ✅ | Tareas con ID, estado y criterios |
| [`analysis/cobblemon-audit.md`](analysis/cobblemon-audit.md) | ✅ | La frontera: nativo · datapack · mod · propio |
| [`analysis/diosesmon-analysis.md`](analysis/diosesmon-analysis.md) | ✅ | 19 sistemas: adaptar / mejorar / evitar |
| [`architecture/modpack-decision.md`](architecture/modpack-decision.md) | ✅ | **CobbleVerse descartado por licencia.** Matriz y plan por hitos |
| [`economy/monetization.md`](economy/monetization.md) | ✅ | F2P + pago: 4 niveles y el test que decide qué se vende |
| [`economy/economy-overview.md`](economy/economy-overview.md) | ✅ | 2 monedas, sources, sinks, velocity, wealth tiers, anti-inflación |
| [`technical/data-model.md`](technical/data-model.md) | ✅ | MariaDB, 5 reglas de esquema, custodia del GTS, rendimiento |
| [`game-design/vision.md`](game-design/vision.md) | 🟡 | La promesa al jugador — **propuesta** |
| [`game-design/core-loop.md`](game-design/core-loop.md) | 🟡 | Tres bucles anidados — **propuesta** |
| [`trading/gts.md`](trading/gts.md) | ✅ | Mercado: acceso por progresión, comisiones, anti-abuso |
| [`progression/progression-model.md`](progression/progression-model.md) | ✅ | 5 vías, desbloqueos de dos factores, sin nivel de jugador |
| [`ui/interfaz-cliente.md`](ui/interfaz-cliente.md) | ✅ | **Cómo se hace la interfaz**, y por qué nunca como menú de cofre |
| [`ui/dibujado.md`](ui/dibujado.md) | ✅ | ⚠️ **Las 5 reglas de dibujado que cumple toda pantalla.** Leer ANTES de escribir una nueva |
| [`world/world-structure.md`](world/world-structure.md) | ✅ | Lobby · ciudadela · mundo, y el recorrido de entrada |
| [`world/worlds.md`](world/worlds.md) | ✅ | Hogar permanente vs Salvaje que reinicia; lobby y modpack |
| [`pokemon/generations.md`](pokemon/generations.md) | ✅ | Kanto + Johto primero, despliegue progresivo. ⚠️ **§3-ter: los mods también meten spawns** |
| [`pokemon/voces-pokedex.md`](pokemon/voces-pokedex.md) | ✅ | **Las 256 voces de la Pokédex**: el pipeline y sus cinco trampas |
| [`economy/treasures.md`](economy/treasures.md) | ⚠️ | Cofres y llaves — **con una objeción abierta sobre legendarios** |
| [`ui/interfaces-catalog.md`](ui/interfaces-catalog.md) | ✅ | Las 22 pantallas, prioridad y patrones comunes |
| [`technical/launcher.md`](technical/launcher.md) | ✅ | **El launcher**: se autoactualiza él y el pack, perfiles, reparar, diagnóstico |
| [`world/construccion.md`](world/construccion.md) | ✅ | Construir la ciudadela con Axiom, **entre varios a la vez** |
| [`world/neon.md`](world/neon.md) | ✅ | **Los bloques de neón**: 96 piezas, brillo sin luz, y el orden del despliegue |
| [`world/bloques.md`](world/bloques.md) | ✅ | **Los bloques de obra**: 506 piezas de hormigón, metal, rejilla, vidrio y pavimento |
| [`ui/interfaz-luna.md`](ui/interfaz-luna.md) | ✅ | **La interfaz de azul luna**: 323 texturas, y qué lo pinta el código |
| [`ui/prompts-arte-pokepad.md`](ui/prompts-arte-pokepad.md) | ✅ | **Los prompts para el arte del PokePad**, y las 3 condiciones que debe cumplir |

✅ escrito · 🟡 en curso · ⬜ pendiente

> **Los documentos ⬜ no existen todavía como archivo.** Se crean cuando tienen
> contenido real. Un stub vacío cuesta contexto y no informa de nada.

---

## Rutas de lectura por tarea

No leas el árbol entero. Carga solo la ruta que corresponda:

```
Trabajar infraestructura o despliegue
  CLAUDE.md → technical/infrastructure.md

Decidir arquitectura del modpack
  CLAUDE.md → analysis/current-server-audit.md
            → analysis/cobblemon-audit.md

Diseñar economía
  CLAUDE.md → economy/economy-overview.md → economy/sinks.md
            → security/economy-abuse.md

Decidir si algo se puede vender en la tienda
  CLAUDE.md → economy/monetization.md          (el test de §6 decide solo)

Diseñar GTS
  CLAUDE.md → economy/economy-overview.md → trading/gts.md
            → security/economy-abuse.md

Diseñar progresión
  CLAUDE.md → game-design/core-loop.md → progression/progression-model.md
```

---

## Convenciones

Cabecera obligatoria de todo documento:

```markdown
# Nombre
## Purpose
## Dependencies        ← qué hay que haber leído antes
## Related Documents
## Current Status
## Last Decision
## Next Actions
```

Y al cierre:

```markdown
## Related Systems
```

`Dependencies` es lo que hace funcionar la lectura progresiva. Si un documento
no las declara, obliga a leerlo todo — y eso rompe el control de contexto.

---

## Reglas

1. **Verificado ≠ supuesto.** Todo dato medido lleva fecha y origen. Lo que sea
   estimación se marca como tal.
2. **Ninguna credencial** en documentación, nunca.
3. Toda decisión arquitectónica se registra en `CLAUDE.md` §5, no aquí.
4. Toda tarea vive en `roadmap/backlog.md` con ID estable.
