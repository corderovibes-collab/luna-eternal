# GUÍA MCREATOR EN WINDOWS — ARMADURAS Y HERRAMIENTAS LUNA ETERNAL

## Lo que ya está instalado

No hace falta volver a descargar MCreator en este PC.

- Programa: `C:\Program Files\Pylo\MCreator\mcreator.exe`
- Versión: `2026.2.33518`
- Workspaces: `C:\Users\JUAN\MCreatorWorkspaces`
- Workspace preparado: `C:\Users\JUAN\MCreatorWorkspaces\armaduraspokereport`
- Generador actual del workspace: `fabric-1.21.1`
- Plugins: `C:\Users\JUAN\.mcreator\plugins`

Para abrirlo en Windows:

1. Pulsa Inicio y busca `MCreator`.
2. También puedes abrir directamente
   `C:\Program Files\Pylo\MCreator\mcreator.exe`.
3. En la pantalla inicial selecciona `Open workspace`.
4. Abre
   `C:\Users\JUAN\MCreatorWorkspaces\armaduraspokereport\armaduraspokereport.mcreator`.

## Si se instala en otro PC Windows

1. Descarga el instalador x86-64 desde https://mcreator.net/download.
2. Ejecuta el `.exe` y conserva la ruta predeterminada
   `C:\Program Files\Pylo\MCreator`.
3. Abre MCreator y entra en `Preferences > Manage plugins`.
4. Usa `Load plugin` para seleccionar el ZIP del generador apropiado.
5. Reinicia MCreator después de instalar o cambiar un plugin.

No se debe extraer manualmente el ZIP del plugin. MCreator lo carga como ZIP.

## Situación real del generador Fabric 1.21.1 instalado

El archivo `C:\Users\JUAN\.mcreator\plugins\Plugin.Fabric.1.21.1.zip` permite
crear desde la interfaz:

- bloques;
- ítems;
- pestañas creativas.

No contiene definiciones de armadura, GUI ni elementos GeckoLib. Por eso puede
administrar las herramientas como ítems, pero no puede convertir por sí solo
las armaduras avanzadas actuales en elementos editables completos de la UI.

Los generadores Fabric 1.21.8 y 26.1.2 instalados tienen más funciones, pero no
se pueden usar para compilar el servidor actual, que es Minecraft 1.21.1.

## Método recomendado para este servidor Fabric 1.21.1

La interfaz de MCreator puede manejar el catálogo y la configuración visible,
mientras el código avanzado GeckoLib permanece en los addons Fabric que ya
funcionan.

### Herramientas y armas

1. En el workspace `armaduraspokereport`, pulsa `+ New mod element`.
2. Selecciona `Item`.
3. Usa exactamente el mismo identificador que el addon final.
4. Importa el icono PNG en `Resources > Import item texture`.
5. Configura desde la interfaz: nombre, stack máximo, durabilidad, rareza,
   brillo y pestaña creativa.
6. Para espada, pico, hacha, pala, azada y arco avanzados, conserva las clases
   Fabric existentes para comportamiento, atributos y render 3D. El elemento
   visual de MCreator actúa como ficha de edición, no como sustituto automático
   de la clase avanzada.
7. Copia únicamente los recursos generados que se hayan revisado al addon
   correspondiente. No reemplaces `fabric.mod.json`, `build.gradle` ni las
   clases de registro del addon con los archivos del workspace.

### Armaduras GeckoLib

1. Abre cada modelo en Blockbench con formato GeckoLib Animated Model.
2. Usa la estructura de huesos de armadura:
   `armorHead`, `armorBody`, `armorRightArm`, `armorLeftArm`,
   `armorRightLeg`, `armorLeftLeg`, `armorRightBoot`, `armorLeftBoot`.
3. Exporta `Geo Model`, `Animation` y la textura PNG.
4. Conserva las cuatro piezas como ítems separados: casco, pechera, pantalones
   y botas.
5. Coloca los recursos en el addon Fabric:
   `assets/<namespace>/geo/armor`, `animations/armor`, `textures/armor` y
   `models/item`.
6. Para flipbook, usa una tira PNG vertical y un archivo `.png.mcmeta` junto al
   PNG. Cada cuadro debe ser realmente distinto.
7. Usa las clases `GeoArmorRenderer` y `GeoItemRenderer` ya creadas en los
   addons. Estas son las responsables de seguir el cuerpo y de dibujar el ítem
   3D en inventario.

## Ruta totalmente gráfica para aprender o prototipar

MCreator 2024.4 para Windows soporta oficialmente NeoForge 1.21.1 y la última
versión de Nerdy's GeckoLib Plugin para 2024.4 ofrece elementos de armadura e
ítems animados.

Se puede instalar la edición ZIP 2024.4 al lado de MCreator 2026.2, en una
carpeta distinta, y crear allí un workspace de prueba. Es útil para diseñar
visualmente los elementos GeckoLib. Su JAR final es NeoForge y no debe ponerse
en el servidor Luna Eternal Fabric.

## Para tener armadura avanzada 100 % editable en MCreator y exportar Fabric

Hace falta ampliar `Plugin.Fabric.1.21.1.zip` con:

- definición de elemento de armadura;
- paneles de propiedades para las ocho partes GeckoLib;
- plantillas Java Fabric para registro y render;
- soporte de modelo, textura, animación y renderer de ítem;
- dependencias GeckoLib compatibles con 1.21.1;
- generador de JSON y recursos por cada pieza.

Eso produciría un plugin propio de MCreator para Luna Eternal. Es viable, pero
debe desarrollarse y probarse como proyecto separado; copiar las plantillas de
NeoForge sin portarlas a Fabric generará errores o un JAR incompatible.

## Reglas para no romper el servidor

- No mezclar JAR NeoForge con el servidor Fabric.
- No cambiar la versión de Minecraft del workspace existente.
- No instalar simultáneamente dos generadores que declaren el mismo id.
- Hacer copia del workspace antes de actualizar MCreator o plugins.
- Mantener iguales los JAR del cliente, servidor y manifiesto.
- No sobrescribir los `.bbmodel` originales.
- Probar primero una sola pieza, empezando por el casco Pikachu.
