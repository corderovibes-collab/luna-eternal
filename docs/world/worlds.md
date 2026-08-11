# Los mundos

## Purpose

Resolver cómo conviven un mundo donde se construye y no se borra nunca, y otro
donde aparecen muchos más Pokémon y que se reinicia. Y cómo se entra a todo
ello desde el lobby.

## Dependencies

- [`world-structure.md`](world-structure.md) — lobby, ciudadela, mundo
- [`../game-design/core-loop.md`](../game-design/core-loop.md)

## Related Documents

- [`../economy/economy-overview.md`](../economy/economy-overview.md)
- [`../technical/data-model.md`](../technical/data-model.md)

## Current Status

**PROPUESTA.** Diosesmon tiene los dos mundos pero **su wiki no explica cómo
los gestiona**, así que esto es diseño propio, no copia.

## Last Decision

Pendiente.

---

## 1. El problema real

Son dos deseos que se contradicen:

| Quiero… | Necesita… |
|---|---|
| Construir mi casa y que no desaparezca | Un mundo **permanente** y protegido |
| Que aparezcan muchos Pokémon y legendarios | Un mundo **fresco**, sin agotar |

Un solo mundo no puede ser las dos cosas. Con el tiempo, un mundo permanente
se llena de construcciones, se agotan los recursos, las zonas buenas quedan
reclamadas por los primeros, y el jugador nuevo llega a un vertedero.

Por eso **dos mundos no es un capricho: es la única solución limpia.**

---

## 2. Los cuatro espacios

```
┌────────┐   ┌────────────┐   ┌──────────────┐   ┌───────────────┐
│ LOBBY  │──▶│ CIUDADELA  │──▶│  HOGAR       │   │   SALVAJE     │
│        │   │            │   │  permanente  │   │   se reinicia │
└────────┘   └────────────┘   └──────────────┘   └───────────────┘
 recibir      servicios         construir           cazar
 segundos     minutos           siempre             sesiones
```

Los dos últimos **son hermanos, no niveles**: se va y se vuelve libremente
desde la ciudadela. No hay que "elegir bando".

### 🏠 Mundo Hogar — permanente

| | |
|---|---|
| **Se reinicia** | Nunca |
| **Protecciones** | Sí — reclamas tu terreno |
| **Aparición de Pokémon** | Baja |
| **Legendarios** | No |
| **Para qué** | Tu casa, tu almacén, tu base, tu granja |

Es donde el jugador **deja huella**. Todo lo que construya ahí sigue estando
dentro de un año.

### 🌿 Mundo Salvaje — se reinicia

| | |
|---|---|
| **Se reinicia** | Cada temporada (ver §4) |
| **Protecciones** | No |
| **Aparición de Pokémon** | Alta |
| **Legendarios** | Sí, con condiciones |
| **Para qué** | Explorar, cazar, expediciones |

Es donde **vive el core loop**: el bucle 2 de la expedición
([core-loop.md](../game-design/core-loop.md)) ocurre aquí y termina volviendo
a la ciudadela.

---

## 3. Por qué esto encaja con nuestra visión (y no la rompe)

Hay una objeción evidente: si nuestra visión dice que *"el conocimiento del
mundo es la progresión"*, ¿no la destruye un mundo que se borra?

**No, y merece la pena entender por qué**, porque de aquí sale una regla de
diseño útil:

| Tipo de conocimiento | ¿Sobrevive al reinicio? |
|---|---|
| *"Hay un cofre en 340, 70, -1200"* | ❌ Se pierde |
| *"Los de tipo hada salen en colinas con luna llena"* | ✅ **Sobrevive** |
| *"Con lluvia y de noche aparece X en pantanos"* | ✅ **Sobrevive** |
| *"El señuelo 3 en playa da mejores resultados"* | ✅ **Sobrevive** |

> **Regla:** el conocimiento que premiamos es **sistémico**, no geográfico. Un
> mapa memorizado no es saber jugar; saber leer el mundo, sí.

Y el reinicio **añade** algo que un mundo permanente no puede dar: **el mapa
vuelve a ser desconocido**. La exploración —nuestro pilar— se renueva sola,
sin producir contenido nuevo. Es exactamente lo que buscábamos con el ciclo
lunar, pero a escala de temporada.

---

## 4. El reinicio, sin traumas

Lo que se pierde y lo que no. **Esto debe estar clarísimo desde el primer día,
o genera enfado justificado.**

| Sobrevive ✅ | Se pierde ❌ |
|---|---|
| **Todos tus Pokémon** (PC y equipo) | Bloques que hayas puesto en el Salvaje |
| **Todo tu dinero** (las tres monedas) | Cofres y objetos guardados allí |
| Objetos del inventario | Nada más |
| Progresión, vías, medallas, Pokédex | |
| Tu casa del Mundo Hogar | |
| Cosméticos, rangos, clan | |

**Nada de lo que importa vive en el mundo.** Vive en la base de datos y en el
almacén del jugador, que son independientes del terreno
([data-model.md](../technical/data-model.md)). Esa es la ventaja de haber
metido MariaDB antes que nada.

### Protocolo de reinicio

```
−7 días   aviso en el LunaPad, en el chat al entrar y en la barra lateral
−3 días   recordatorio; el Mundo Hogar se anuncia como refugio
−1 día    aviso cada hora
−1 hora   cuenta atrás
   0      Salvaje cerrado · regenerado · reabierto con semilla nueva
+0        "Nueva temporada": una razón para volver, no un castigo
```

**El reinicio se presenta como estreno, no como pérdida.** Es un evento de
comunidad: mapa nuevo, todos empiezan a explorar a la vez.

### Cada cuánto

| Periodo | Efecto |
|---|---|
| 1 mes | Demasiado. No da tiempo a conocer el mundo |
| **2-3 meses** | **Recomendado.** Se conoce, se agota, se renueva |
| 6 meses | Se agota mucho antes del reinicio |

Se calibra con datos reales: cuando la gente deje de explorar zonas nuevas, el
mundo está agotado.

---

## 5. Los legendarios viven aquí

Esto responde a la pregunta abierta de
[treasures.md](../economy/treasures.md) §3: si los legendarios no salen de
cofres de pago, ¿de dónde salen?

**Del Mundo Salvaje.** Y el reinicio lo hace sostenible:

- Aparición **rarísima**, condicionada por fase lunar, zona y clima
- Cupo por temporada: si ya salieron N, no salen más
- Al reiniciar, el contador vuelve a cero → **razón para la temporada nueva**
- El que lo cace tiene una historia: dónde, cuándo, con qué luna

Un legendario así **no se puede farmear** —el cupo lo impide— ni se devalúa
—el reinicio renueva la caza—. Y no hace falta venderlo para que el sistema
sea rentable: lo rentable son los cosméticos.

---

## 6. Cómo se elige y cómo se viaja

**Sin comandos** (P9). Desde la ciudadela:

```
┌─ PUERTA DEL MUNDO ─────────────────────────┐
│                                            │
│   🏠 MUNDO HOGAR          🌿 MUNDO SALVAJE │
│   Tu casa, permanente     Caza y explora   │
│                                            │
│   · Protecciones          · Sin protección │
│   · Pocos Pokémon         · Muchos Pokémon │
│   · No se borra nunca     · Nueva temporada│
│                             en 47 días     │
│                                            │
│   [ Viajar ]              [ Viajar ]       │
└────────────────────────────────────────────┘
```

Diosesmon presenta esta misma elección con dos tarjetas y una descripción de
tres puntos. **Funciona: es legible de un vistazo.** Lo adoptamos, añadiendo
lo que a ellos les falta: **decir cuándo es el próximo reinicio**.

Y desde el LunaPad, la sección **Explorar** lleva a los dos, más un
teletransporte a zona aleatoria del Salvaje.

---

## 7. El lobby y la descarga del modpack

Diosesmon exige modpack y pone carteles en el lobby para descargarlo. Su wiki
lo confirma: *instalar el modpack, configurar 8200 MB de memoria, y aparece un
botón "DIOSESMON" en el menú principal*.

### Nuestra decisión es distinta, y es deliberada

**El servidor debe poder jugarse con un cliente normal** (P10). Motivos:

1. Pedir un modpack **antes de haber jugado nada** es la mayor barrera de
   entrada que existe. Cada paso pierde gente.
2. Exigir 8 GB de RAM excluye a quien juega en un portátil.
3. Nuestras interfaces son menús de servidor: **no necesitan cliente**.

### Entonces, ¿para qué el launcher?

Para **mejorar**, no para **entrar**:

| Sin modpack | Con nuestro launcher |
|---|---|
| Se juega entero | Mejor rendimiento |
| Menús de servidor | Texturas propias, HUD, marcos |
| — | Voces y cinemáticas |
| — | Actualizaciones automáticas |

En el lobby habrá un cartel de descarga, **pero al lado del NPC de entrada, no
delante**. La diferencia entre *"descarga esto para jugar"* y *"si quieres,
esto se ve mejor"* son muchos jugadores.

> Ellos abren el PokePad con la tecla **V**, lo que confirma que su interfaz
> depende del mod de cliente. La nuestra se abre con un objeto — funciona
> siempre, para todo el mundo.

---

## 8. Implementación

| Pieza | Cómo | Coste |
|---|---|---|
| Las 4 dimensiones | Datapack (`dimension` + `dimension_type`) | Bajo |
| Reglas de aparición por dimensión | Datapack, condición de dimensión | Bajo |
| Protecciones solo en Hogar | Mod de claims por dimensión | Bajo |
| Puerta del mundo | Menú del mod propio | Bajo |
| Aviso de temporada | Barra lateral + LunaPad | Bajo |
| **Reinicio del Salvaje** | Borrar `region/` de la dimensión y cambiar semilla | Medio |
| Cupo de legendarios | Mod propio, contador en base de datos | Medio |

### El reinicio en detalle

```
1. servidor parado (ventana anunciada)
2. BACKUP COMPLETO — mundo y base de datos (INF-002 e INF-007)
3. borrar  world/dimensions/luna/salvaje/region  (y poi, entities)
4. nueva semilla en la configuración de la dimensión
5. arrancar; se regenera al entrar
6. reiniciar contadores de temporada en la base
```

> **El paso 2 no es opcional.** Un reinicio es la operación más destructiva
> que hará este servidor de forma rutinaria; hacerlo sin copia verificada es
> exactamente el escenario en que se pierde el proyecto entero.

---

## 9. Riesgos

| Riesgo | Mitigación |
|---|---|
| **El jugador construye en el Salvaje y lo pierde** | Avisos claros, y **no permitir cofres** allí. Es el enfado más probable |
| El Hogar se convierte en un vertedero de parcelas | Parcelas con caducidad por inactividad, revisable |
| Dos mundos = el doble de disco | El Salvaje se borra periódicamente, así que se autolimita |
| 4 GB de RAM con 4 dimensiones | ⚠️ **Real.** Ver `B-003`. Las dimensiones vacías cuestan poco, pero con jugadores dentro no |
| El reinicio espanta gente | Presentarlo como temporada, con recompensas de cierre |

---

## Next Actions

1. Ratificar los cuatro espacios y el periodo de reinicio
2. `WLD-002` — crear las dimensiones por datapack
3. `WLD-003` — protocolo de reinicio, con backup obligatorio
4. `PKM-003` — cupo y condiciones de legendarios

## Related Systems

- [Estructura del mundo](world-structure.md) · [Core loop](../game-design/core-loop.md)
- [Tesoros](../economy/treasures.md) · [Modelo de datos](../technical/data-model.md)
