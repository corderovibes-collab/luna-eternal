# Portal oficial PokeReport

Publicado el 2 de octubre de 2026 en el alojamiento existente. No usa Sites, GitBook ni un servicio nuevo.

## Sitio

- `https://pokereport.online/`: portada con Ciudadela, primeras instrucciones y accesos.
- `/jugar/`: instalador oficial Windows 64 bits, instrucciones y preguntas frecuentes.
- `/wiki/`: índice con búsqueda local, diez artículos y navegación por categorías.
- `/tienda/`: aplicación original de la tienda y checkout Tebex, conservados.
- `/descargas/PokeReport-Launcher-0.2.3.exe`: descarga servida desde el propio dominio, sin redirección pública a GitHub.

Diseño inspirado en la organización de [Diosesmon](https://www.diosesmon.net/) y su [wiki](https://wiki.diosesmon.net/), con identidad, textos y recursos de PokeReport. No se copió su código ni sus ilustraciones. Las imágenes son las del menú de PokeReport, extraídas del cliente vigente y optimizadas a WebP; las fuentes proceden del launcher existente.

## Editar y construir

`content.json` contiene los artículos, categorías, secciones y relaciones. `build.py` genera HTML estático, índice de búsqueda, sitemap y 404. CSS y JavaScript están en `public/portal-assets/`. No necesita dependencias de npm ni un servidor de aplicación.

```powershell
python website/build.py
python -m http.server 8765 --bind 127.0.0.1 --directory website/dist
```

Las páginas y guías se leen sin JavaScript; JS añade búsqueda, copia de IP y menús móviles. Los resultados se construyen con `textContent`, con soporte de teclado y normalización de acentos. Se respetan movimiento reducido, foco visible y accesos de teclado. Las guías documentan lo implementado y remiten a las interfaces para requisitos que puedan cambiar.

## Despliegue

Host SSH configurado `su`. Frontend: `/opt/pokereport/frontend/dist`; Nginx frontend: `/opt/pokereport/frontend/nginx.conf`; edge: `/opt/pokereport/nginx-edge/edge.conf`. Se mantienen contenedores, permisos y redes existentes.

El paquete tar debe contener `portal/` (dist construido), `nginx.conf`, `edge.conf` e instalador `PokeReport-Launcher-0.2.3.exe`. El instalador original se obtiene de la release oficial 0.2.3 de `corderovibes-collab/luna-eternal-launcher`, archivo `luna-launcher-win-x64-Setup.exe`. No ejecutar ese archivo durante las comprobaciones.

Subir el paquete como `/tmp/pokereport-portal.tar.gz` y `deploy.sh`; ejecutar `bash /tmp/deploy.sh SHA256_DEL_PAQUETE`. El script valida hash, guarda respaldo y comprueba ambas configuraciones antes de recargar. Retiene JS y assets originales de la tienda, mueve su HTML a `/tienda/` y añade únicamente un enlace de vuelta al portal.

El root con `?status=success` o `?status=cancelled` redirige a la tienda conservando el query. Es el retorno real del checkout existente. Las redirecciones son relativas (`absolute_redirect off`) para no publicar HTTP ni el puerto interno 8080. Edge transmite el instalador sin buffering temporal: la prueba inicial detectó descargas truncadas; después de activar streaming el archivo completo coincidió con el original.

## Evidencia de producción

- Respaldo original: `/opt/pokereport/backups/portal-20261002T060827Z` (dist, frontend Nginx y edge).
- Descarga íntegra: 28.536.262 bytes, SHA256 `4e9dbf8b532313c401f4ef59686398478033db0f8ac1fc2741be371c65bd0f8f`.
- 14 documentos HTML (13 páginas más 404), 438 referencias internas verificadas y una H1 por página.
- Revisión visual de escritorio (1440×1000) y móvil (390×844). Búsqueda en producción, navegación móvil, índice de wiki y renderizado de tienda comprobados con navegador; ningún error de consola en la wiki.
- URLs principales, respuesta 404 y redirecciones de retorno Tebex comprobadas. JS original de tienda idéntico byte por byte. No se hizo una compra de prueba ni se afirmó verificar la acreditación de un pago nuevo.
- Capturas locales en `website/screenshots`; evidencia HTTP en `build/portal-live-validation.json`.

## Integración con Minecraft

Enlaces oficiales centralizados en `client/Enlaces.java`. Wiki usa `https://pokereport.online/wiki/` y tienda `/tienda/`. `Apps` abre Wiki con confirmación de Minecraft y vuelve al PokéPad. `CatalogoPad` habilita su ficha. El menú principal también apunta a la wiki y Discord oficiales.

Se publicó un parche sobre el cliente final corregido de Lunita: cinco clases y tres archivos de traducción; 1907 entradas ajenas conservadas. SHA256 `53fc9f4cb9436d2e7138222c23d7c36d3c04b1403bd33e1b8dcb9a15a41618c2`, SHA1 `fdd01975c98efeb312565425761ad37f7abdf9ed`, manifiesto `4466184e0f`. Las otras 159 entradas y el respaldo del launcher se verificaron. Minecraft estaba abierto: su JAR local no se reemplazó; se recibe al cerrar juego y launcher y abrir de nuevo. No cambian protocolo, base de datos ni servidor de juego. Pendiente confirmación visual del clic Wiki en Minecraft con esa actualización.

## Restaurar

Copiar los archivos `nginx.conf`, `edge.conf` e `index.html` originales del respaldo a sus destinos correspondientes y probar Nginx antes de recargar ambos contenedores. Los assets originales nunca se sobrescribieron. Los directorios nuevos pueden conservarse para recuperación. Revertir también el cliente con el JAR `build/lunaeternal-lunita-pose-fix-client.jar` si se restaura la tienda en la raíz; usar el publicador dirigido para mantener puntero y respaldo sincronizados.
