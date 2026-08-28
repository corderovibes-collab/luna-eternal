# Arte de las tarjetas de Explorar

## Purpose
Los dos prompts para generar las imágenes de **Mundo Hogar** y **Mundo Salvaje**,
y dónde van los ficheros.

## Dependencies
`docs/ui/prompts-arte-pokepad.md` · `ExplorarScreen.java`

---

## 1. Las medidas, y por qué son esas

```
la tarjeta se dibuja a   384 × 256   (unidades de arte, 3:2 exacto)
la imagen se pide a      768 × 512   (el DOBLE justo, mismo 3:2)
formato                  PNG, sin transparencia
```

> ⚠⚠ **768×512 no es un capricho: es exactamente el doble.** El chasis se dibuja
> a 1×, 1,5× o 2× según el monitor (ver `Escalado.java`). Pidiendo el arte al
> doble, en un monitor grande —donde más se nota— **cada píxel de la imagen cae
> en un píxel de pantalla** y no hay que interpolar nada. A 1× se reduce a la
> mitad exacta, que también es limpio.
>
> Pedirla más grande no aporta: por encima de 2× nadie la va a ver.

> ⚠ **A sangre, sin dejar hueco para el texto.** El título y los datos se dibujan
> encima, y el código pone **una banda oscura degradada** arriba y abajo para que
> se lean. Pedirle a la IA que deje espacio vacío sale peor: nunca lo deja donde
> hace falta y se pierde la mitad de la ilustración.

**Dónde van los ficheros:**

```
arte/pokepad/mundo_hogar.png
arte/pokepad/mundo_salvaje.png
```

---

## 2. Prompt — MUNDO HOGAR

> Lo que tiene que transmitir: **seguridad, construido, tuyo**. Es donde la
> gente tiene la casa y nadie se la toca.

```
Epic wide landscape illustration in a stylized Minecraft voxel art style,
3:2 aspect ratio, painted with rich detail and soft volumetric lighting —
like official Pokémon game key art rendered with blocky cube geometry.

SCENE: a peaceful settled valley at golden hour. Cozy blocky houses with
warm glowing windows and lantern-lit paths, tidy farm plots with neat rows
of crops, wooden fences marking claimed land, a small stone-brick bridge
over a calm river. Gentle rolling hills of soft green grass blocks behind.
A few tall oak trees with chunky cubic foliage. Distant snow-capped
mountains on the horizon under a warm sky.

MOOD: safe, welcoming, lived-in, home. Calm and inviting, nothing
threatening.

COLOR: warm golden sunlight, honey-orange highlights, soft teal-blue sky
fading to peach near the horizon, deep cool blue shadows. Rich saturation.

CAMERA: wide three-quarter view from slightly above, horizon in the upper
third, the village occupying the lower two thirds.

STYLE NOTES: everything built from cubes and blocks, but rendered
beautifully — soft shadows, warm bloom around light sources, light haze in
the distance. Clean and readable, not cluttered.

DO NOT INCLUDE: any text, letters, numbers, logos, watermarks, signatures,
UI elements, frames or borders. No people, no faces, no creatures. No
copyrighted characters.
```

---

## 3. Prompt — MUNDO SALVAJE

> Lo que tiene que transmitir: **indómito, peligroso, ahí pasan cosas**. Es
> donde están los Pokémon y los legendarios, y se reinicia cada semana.

```
Epic wide landscape illustration in a stylized Minecraft voxel art style,
3:2 aspect ratio, painted with rich detail and dramatic volumetric lighting —
like official Pokémon game key art rendered with blocky cube geometry.

SCENE: untamed wilderness with no sign of civilization. A dense ancient
forest of massive blocky trees with deep shadowed undergrowth, jagged stone
cliffs and a tall waterfall crashing into a misty pool, rugged mountains
rising into storm clouds. Glowing particles and fireflies drifting through
shafts of light. A faint mysterious glow radiating from deep within the
forest, suggesting something powerful hidden there — light only, no
creature visible.

MOOD: wild, mysterious, adventurous, slightly dangerous. Somewhere you go
looking for something rare.

COLOR: deep emerald and pine greens, cool blue-teal shadows, dramatic
storm-grey sky broken by golden god-rays, a subtle violet-cyan magical glow
in the forest depths. High contrast, moody but vivid.

CAMERA: wide three-quarter view from slightly above, horizon in the upper
third, the forest and cliffs filling the lower two thirds.

STYLE NOTES: everything built from cubes and blocks, but rendered
beautifully — heavy atmospheric depth, mist between the tree layers, strong
rim lighting on the rocks. Dramatic without being dark or grim.

DO NOT INCLUDE: any text, letters, numbers, logos, watermarks, signatures,
UI elements, frames or borders. No people, no faces, no creatures. No
copyrighted characters.
```

---

## 4. Por qué los dos prompts se parecen tanto

> ⚠⚠ **Las dos primeras líneas y las dos últimas son idénticas a propósito.**
> Van una al lado de la otra en la misma pantalla: si una sale en estilo cómic y
> la otra en pintura realista, se ve como un error aunque las dos sean bonitas
> por separado. Lo que cambia entre ellas es **la escena, el ánimo y el color**,
> que es exactamente lo que tiene que distinguirlas.

> ⚠ **Ni personas ni criaturas, y no es prudencia: es que la IA las hace mal.**
> Una cara mal generada a 768 px se ve, y un Pokémon inventado al lado de los
> reales de Cobblemon se nota más todavía. El paisaje aguanta el estilo mucho
> mejor.

> ⚠ **La composición pide el horizonte en el tercio superior** porque ahí es
> donde cae la banda oscura del título. Si el motivo importante queda arriba, el
> velo se lo come.

---

## 5. Cuando estén las imágenes

Se dejan en `arte/pokepad/` con esos nombres exactos y se instalan con el
generador del PokePad. **Hasta entonces la pantalla dibuja el color plano**, no
la textura rosa de «falta esto» — ver `hayArte()` en `ExplorarScreen`.
