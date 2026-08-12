# El Discord de PokeReport

## Purpose

Montar la comunidad **antes** que el servidor, para llegar al lanzamiento con
gente esperando en vez de con un mundo vacío.

## Dependencies

- Ninguna del proyecto. Es independiente del mod y del servidor.

## Current Status

`READY`. `tools/discord_setup.py` crea roles, categorías, canales y permisos.
Falta que el usuario cree el servidor y el bot — eso no se puede automatizar.

---

## 1. La decisión que más importa: **empezar con pocos canales**

Un servidor recién abierto con veinticinco canales **parece muerto**: todos
vacíos, ninguno con conversación. Con seis parece que acaba de empezar, que es
la verdad, y la poca gente que entre coincide en el mismo sitio.

Por eso el guion tiene **dos fases**:

| | Cuándo | Qué abre |
|---|---|---|
| **Fase 1** | Ahora | 9 canales de texto y 2 de voz |
| **Fase 2** | Cuando el servidor sea jugable | Guías, comercio, capturas, bugs |

La fase 2 se lanza con `--fase2` el día que haga falta. **No antes.**

## 2. Qué crea

### Roles

| Rol | Para qué |
|---|---|
| 🌙 **Fundador** | Tú. Administrador |
| 🛡️ **Moderador** | Expulsar, banear, borrar mensajes |
| 🔨 **Constructor** | Quien ayude con la ciudadela |
| ⭐ **Beta Tester** | Acceso anticipado. **Es tu mejor moneda antes del lanzamiento** |
| 💎 **Booster** | Reconocimiento a quien mejore el servidor |
| 🤖 **Bots** | Para tenerlos agrupados |

### Canales de la fase 1

```
📌 INFORMACIÓN     bienvenida · anuncios · actualizaciones    (solo lectura)
💬 COMUNIDAD       general · sugerencias · multimedia
🛠️ SOPORTE         ayuda
🔒 STAFF           staff · registro                            (privado)
🔊 VOZ             Sala general · Staff
```

> **`anuncios` se crea como canal de tipo «anuncio».** No es un detalle: otros
> servidores pueden **seguirlo** y tus noticias aparecen en ellos. Es
> publicidad gratis, y solo funciona si el canal es de ese tipo desde el
> principio.

> **Los canales de solo lectura ocultan ESCRIBIR, no VER.** Un canal que no se
> ve no informa a nadie.

## 3. Lo que tienes que hacer tú

No se puede automatizar: crear un servidor y una aplicación exige tu cuenta.

1. **Crea el servidor** en Discord. Nombre: `PokeReport · Luna Eternal`.
2. Ve a **https://discord.com/developers/applications** → *New Application*.
3. Pestaña **Bot** → *Reset Token* → copia el token.
4. Pestaña **OAuth2 → URL Generator**: marca `bot` y `applications.commands`,
   y en permisos **Administrator**. Abre el enlace e invítalo a tu servidor.
5. Activa el **modo desarrollador** en Discord (Ajustes → Avanzado), haz clic
   derecho en tu servidor → *Copiar ID*.
6. Crea `.env` en la raíz del proyecto (**está git-ignorado**):

```
DISCORD_TOKEN=el_token_del_bot
DISCORD_GUILD=el_id_del_servidor
```

7. Ejecuta:

```
python tools/discord_setup.py             # solo te enseña el plan
python tools/discord_setup.py --aplicar   # lo crea
```

Se puede repetir sin miedo: **lo que ya existe no se toca ni se duplica**.

> ⚠️ **El token del bot es una contraseña.** Con él se controla el bot entero.
> Nunca en el repositorio, nunca en una captura. Si se filtra, *Reset Token* y
> a otra cosa.

## 4. Los bots, y por qué estos

Un bot no se puede invitar por script: lo autoriza el dueño del servidor desde
un enlace. Estos son los que hacen falta, y **solo estos** — cada bot de más es
otro sitio desde el que se puede romper algo.

| Bot | Para qué | Por qué este |
|---|---|---|
| **Carl-bot** | Roles por reacción, automoderación, registro | Hace tres cosas que si no necesitarían tres bots |
| **Ticket Tool** | Soporte privado uno a uno | Evita que los problemas personales acaben en `general` |
| **Statbot** | Contador de miembros en un canal | Enseña que la comunidad crece. Es puro efecto, y funciona |

**Antes de instalar nada, mira lo que Discord ya trae**:

- **AutoMod** — filtra insultos y spam sin ningún bot.
- **Onboarding** y **Reglas** — el que entra acepta antes de poder escribir.
- **Roles por incorporación** — el propio Discord ya los reparte.

Con eso cubierto, Carl-bot y Ticket Tool pueden bastar. Statbot es opcional.

## 5. Qué publicar antes del lanzamiento

Un Discord sin contenido se muere en dos semanas. La ventaja es que **tienes
material de sobra**: cada avance del mod es una publicación.

| Cada | Qué |
|---|---|
| Semana | Una captura del PokePad o de lo construido, en `actualizaciones` |
| Que haya | Una decisión explicada: por qué tres monedas, por qué Kanto y Johto |
| Mes | Una pregunta abierta en `sugerencias`. La gente se queda donde se le escucha |

> **El rol ⭐ Beta Tester es tu mejor herramienta.** Cuesta cero y da acceso
> anticipado; es lo que convierte a un curioso en alguien que vuelve.

## Next Actions

1. Crear servidor y bot (usuario)
2. `python tools/discord_setup.py --aplicar`
3. Invitar Carl-bot y Ticket Tool
4. Primera publicación en `actualizaciones` con el PokePad

## Related Systems

- [Identidad visual](../ui/visual-identity.md) — de ahí sale el arte del servidor
