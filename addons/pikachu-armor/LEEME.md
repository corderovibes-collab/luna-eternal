# Pikachu Animated Armor

Mod Fabric 1.21.1 con cuatro objetos equipables y GeckoLib 4.7.6.

La animación `animation.pikachu_armor.idle` dura 3,2 segundos y se repite:

- casco: boca con doble succión, cola de tres segmentos, aletas y bigotes;
- pechera: aleta/cola dorsal con balanceo;
- botas: aletas laterales alternadas;
- pantalones: geometría estable y flipbook, para no deformar las piernas.

Las cuatro texturas tienen un flipbook sutil de 8 frames, 3 ticks por frame y
sin interpolación. El primer frame conserva exactamente la paleta original.

Objetos:

```
/give @s pikachuarmor:pikachu_helmet
/give @s pikachuarmor:pikachu_chestplate
/give @s pikachuarmor:pikachu_leggings
/give @s pikachuarmor:pikachu_boots
```

Instala el JAR en cliente y servidor junto con Fabric API y GeckoLib 4.7.6.

