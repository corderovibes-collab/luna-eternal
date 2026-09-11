# Eeveelution Armor — Fabric 1.21.1

Este proyecto convierte el set en cuatro objetos de armadura equipables:

- `eeveelution:eeveelution_helmet`
- `eeveelution:eeveelution_chestplate`
- `eeveelution:eeveelution_leggings`
- `eeveelution:eeveelution_boots`

Los `.bbmodel` son los maestros editables. Minecraft carga los `.geo.json`, las
texturas `.png` verticales y sus `.png.mcmeta`. Cada textura tiene 8 fotogramas,
3 ticks por fotograma, bucle explícito y sin interpolación para mantener el pixel art.

Requisitos de ejecución: Minecraft 1.21.1, Fabric Loader, Fabric API y GeckoLib
4.7.6 o compatible de la rama 4.x para 1.21.1.

Compilar en Windows: `gradlew.bat build`. El JAR queda en `build/libs/`.

Comandos de prueba en creativo:

```
/give @s eeveelution:eeveelution_helmet
/give @s eeveelution:eeveelution_chestplate
/give @s eeveelution:eeveelution_leggings
/give @s eeveelution:eeveelution_boots
```

El archivo original `eevelution.bbmodel` no se modifica ni forma parte del JAR.
