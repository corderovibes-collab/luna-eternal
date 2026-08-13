# Cómo entrar a construir — Luna Eternal

Guía para el equipo de construcción. **Cinco minutos de principio a fin.**

---

## 1. Descarga el launcher

```
https://github.com/corderovibes-collab/luna-eternal-pack/releases/latest
```

Baja el `.exe` e instálalo. Windows avisará la primera vez porque no va
firmado: **Más información → Ejecutar de todas formas**.

No hace falta tener Minecraft instalado, ni Java, ni nada. El launcher se trae
todo. La primera vez tarda (~900 MB); las siguientes son segundos.

**Y no vuelvas a descargar nada nunca más:** el launcher se actualiza solo, y
el modpack también.

## 2. Elige perfil CONSTRUCTOR

En la pantalla de Jugar hay dos opciones. **Marca Constructor.**

Es lo que instala Axiom (el editor de construcción), WorldEdit CUI y
Litematica. Con el perfil de Jugador entras, pero no puedes construir.

## 3. Pon tu nombre

En **Cuentas**, escribe el nombre con el que quieres jugar y añádelo.

> ⚠️ **Elígelo bien a la primera.** El servidor es offline, así que **el nombre
> es tu identidad**: si lo cambias, para el servidor eres otra persona y pierdes
> permisos e inventario.

## 4. Entra y regístrate

Dale a **JUGAR** y conéctate a *PokeReport : Luna Eternal* (ya está en tu lista
de servidores).

La primera vez, el servidor te pedirá una contraseña. En el chat:

```
/register tucontraseña tucontraseña
```

Las veces siguientes solo:

```
/login tucontraseña
```

> Esto protege tu nombre para que nadie lo use. Tienes **60 segundos** para
> hacerlo o te expulsa; vuelves a entrar y ya está.

## 5. Hazte constructor

```
/luna constructor LA-CLAVE-QUE-TE-PASARON
```

Una sola vez. Te da permiso de construcción: creativo, WorldEdit y Axiom.

> La clave te la da TheJuanCE por privado. **No la pegues en un chat público.**

## 6. A construir

```
/luna ir ciudadela        te lleva a la ciudadela
/gamemode creative        modo creativo
```

Y ya dentro, **Shift derecho** abre el editor de Axiom.

| Tecla | Qué hace |
|---|---|
| **Shift derecho** | Abre y cierra el editor de Axiom |
| **Alt izquierdo** | Menú contextual: vuelo, no-clip, alcance infinito |
| `M` | Menú de Litematica (para medir) |
| `/whynoaxiom` | Si Axiom no se activa, esto dice por qué |

---

## Dónde estamos construyendo

Ahora mismo hay **una sola isla flotando en el vacío**: la plaza central, de
56×56, con el suelo en **y=63**. Todo lo demás está vacío a propósito — se
empieza por una zona.

`/luna ir ciudadela` te deja en el centro de la plaza (**4, 69, 0**).

**Se puede construir hacia abajo:** por debajo de la isla no hay nada hasta
y=-64. Son 127 bloques libres para cimientos, raíces o cascadas al vacío. Y el
canto de la isla se ve desde fuera, así que no lo dejes como una pared plana.

**Lo único que hay que respetar:** dejar **cuatro accesos, uno por lado,
centrados y de al menos 8 bloques de ancho**. Por ahí saldrán las avenidas
hacia las otras ocho zonas de la ciudad.

---

## Reglas de convivencia

| | |
|---|---|
| **Una zona por persona**, acordada antes de empezar | Axiom no avisa si dos editáis lo mismo: gana el último |
| Guarda un `.schem` al terminar cada sesión | `//pos1` · `//pos2` · `//copy` · `//schem save loquesea` |
| No construyas en el Mundo Hogar ni en el Salvaje | El Salvaje se reinicia por temporada: lo que hagas ahí se pierde |
| Operaciones enormes, de una en una | El servidor tiene 4 GB. Dos rellenos gigantes a la vez lo tiran |

---

## Si algo falla

| Síntoma | Qué hacer |
|---|---|
| El juego se cierra solo | El launcher te dice **por qué** y te ofrece el arreglo. Hazle caso |
| Va a tirones | Ajustes → baja la RAM si tienes poca, y en el juego pon la distancia a 6-8 chunks |
| *"Flying is not enabled"* | No debería pasar, está permitido. Avisa |
| Axiom no abre | `/whynoaxiom` y pasa lo que diga |
| Falta un mod o algo raro | Ajustes → **Reparar instalación** |

Cualquier otra cosa, al Discord.
