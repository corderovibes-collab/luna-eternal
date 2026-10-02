# Crash al pulsar PokeReport — 2026-10-02

Los informes locales de las 00:12:08 y 00:13:35 identifican como primer fallo `InvalidInjectionException` de `lunaeternal.client.mixins.json:ConnectScreenMixin`. El injector intenta encontrar `renderBackground` (`method_25420`) en ConnectScreen. Esa clase hereda el método de Screen, por lo que no contiene bytecode donde realizar la inyección. Los errores posteriores de Jade aparecen cuando NotEnoughCrashes intenta recuperar el cliente.

Se sustituye el injector por una sobrescritura normal del método heredado mediante el mixin, conservando el fondo personalizado. No se usa `require=0`, porque ocultaría el fallo sin conservar el comportamiento. El bytecode compilado contiene `method_25420(DrawContext,int,int,float)` y no contiene la anotación Inject. La inspección de la clase real de Minecraft 1.21.1 confirma que no declara ese método.

Parche de cliente: solo cambia `net/pokereport/luna/client/mixin/ConnectScreenMixin.class`; otras 1914 entradas idénticas. Base SHA256 `6b93ff8fa3aa8889a70e42e9f7fa0711998829e4646ab2329b8a58d2d15b2205`; resultado `b2e426f88d4cdff2d7c1d32ba3ba0995f9a74eb1c2a8120bc5e7f353a249cb8e`. Copia anterior en `build/lunaeternal-local-before-connect-fix.jar`; auditoría en `build/lunaeternal-connect-fix-client.jar.audit.json`.

Aplicado a la instalación local después de comprobar que Minecraft no estaba ejecutándose. Publicado como actualización dirigida del launcher. No requiere cambios ni reinicio del servidor. Compilación y comprobación de bytecode realizadas; pendiente confirmar el clic de conexión desde Minecraft ejecutándose.

Después de corregir la distribución y reabrir el launcher de forma visible, el usuario informó que aparentemente todo funciona. No se detectó un nuevo crash comunicado tras esta corrección.
