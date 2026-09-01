package net.pokereport.luna.gym;

import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.world.LunaDimensions;

/**
 * LAS SALAS DE LA DIMENSIÓN DE GIMNASIOS: plataforma, clonado y viaje.
 *
 * <h2>⚠⚠ LA PLATAFORMA ES PEQUEÑA A PROPÓSITO</h2>
 *
 * 9×9, lo justo para no caerse al vacío mientras se pega el esquema. Grande
 * estorbaría: habría que borrarla a mano antes de construir, y borrar a mano es
 * lo que hace que un día quede un trozo debajo del suelo del gimnasio y nadie
 * sepa por qué hay piedra flotando.
 *
 * <h2>⚠ Y NO SE PONE SOLA AL ARRANCAR</h2>
 *
 * {@link #preparar} se ejecuta cuando alguien lo pide. Puesta en cada arranque
 * <b>pisaría el gimnasio ya construido</b> cada vez que se reinicia el servidor.
 */
public final class Arenas {

    /**
     * EL LADO DE LA PLATAFORMA DE ANCLAJE.
     *
     * <p>⚠⚠ TRES Y NO NUEVE (2026-08-31, decisión del usuario). Nueve era un
     * suelo en el que ponerse a mirar; tres es <b>una marca</b>. Con veintitrés
     * gimnasios por levantar, lo que hace falta es que el origen se vea y que la
     * plataforma <b>estorbe lo menos posible</b> al pegar el esquema encima:
     * cuanto más grande, más piedra que sobra por debajo de la sala.
     *
     * <p>⚠⚠⚠ Y ESTE NÚMERO NO VIAJA SOLO. Al bajarlo de 9 a 3, el
     * teletransporte —que llevaba al jugador al centro con un {@code +4.5}
     * escrito a mano— lo habría dejado <b>fuera de la plataforma</b>, cayendo al
     * vacío de la dimensión. Hoy el centro se calcula de aquí, así que los dos
     * números no se pueden separar. Es la misma trampa que el marcador de
     * combate y su {@code spawnHeightOffset}.
     */
    public static final int LADO = 3;

    /** El centro de la plataforma, para dejar ahí al que va a construir. */
    public static double centro() {
        return LADO / 2.0;
    }

    /**
     * LO QUE MIDE CADA GIMNASIO, RECORDADO.
     *
     * <h2>⚠⚠ NO ES UNA OPTIMIZACION BONITA: SE NOTA AL ENTRAR</h2>
     *
     * {@link #medir} barre medio hueco de gimnasio en X y en Z, que son
     * <b>millones de lecturas de bloque</b>. Y {@link #clonar} lo llamaba cada
     * vez, o sea <b>una vez por ranura</b>. Medido en vivo comprobando las
     * siete: un tirón de <b>2,3 s</b> en la primera.
     *
     * <p>Eso mismo le pasa a un jugador que entra a una ranura que aún no
     * estaba hecha — que es justo el peor momento, porque está esperando a
     * aparecer dentro.
     *
     * <p>⚠ Se olvida al tocar el maestro ({@link #limpiarRanuras} y el comando
     * {@code reclonar}): si no, un cambio en la sala no llegaría a las copias y
     * eso <b>no daría ningún error</b> — daría copias viejas.
     *
     * <p>⚠ Y el comando {@code medir} sigue midiendo de verdad, sin caché: es
     * el diagnóstico, y un diagnóstico que contesta de memoria no sirve.
     */
    private static final java.util.Map<String, int[]> MEDIDAS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Se olvida lo medido: el maestro ha cambiado. */
    public static void olvidarMedidas() {
        MEDIDAS.clear();
    }

    /** Lo que mide, de memoria si ya se sabía. */
    public static int[] medida(MinecraftServer servidor, Gimnasio.Gimnasio_ g) {
        int[] m = MEDIDAS.get(g.id());
        if (m != null) {
            return m;
        }
        m = medir(servidor, g);
        if (m != null) {
            MEDIDAS.put(g.id(), m);
        }
        return m;
    }

    private Arenas() {}

    /**
     * Mide la sala construida: la caja de lo que NO es aire.
     *
     * <h2>⚠⚠⚠ ESTO EXISTE PARA DEJAR DE SUPONER</h2>
     *
     * El area a clonar estaba escrita a ojo (96x48x56). Clonar de menos deja el
     * gimnasio <b>cortado</b>, y eso no se ve hasta que alguien camina hasta el
     * borde de su copia y se encuentra media pared. Clonar de mas puede llegar a
     * <b>copiar la sala de al lado</b>.
     *
     * <p>Midiendo, el numero deja de ser una opinion.
     *
     * @return {@code {minX, minY, minZ, ancho, alto, fondo}} relativo al origen,
     *         o {@code null} si no hay nada construido
     */
    /**
     * Cuántas capas de Z vacías seguidas se aceptan dentro de una sala.
     *
     * <p>Una sala tiene suelo, así que <b>ninguna capa de Z interior está
     * completamente vacía</b>: si aparecen varias seguidas, se ha salido del
     * edificio. Ocho da margen para un cartel suelto o una farola apartada, y es
     * mucho menos que el hueco que queda entre una copia y la siguiente.
     */
    private static final int AIRE_QUE_CORTA = 8;

    public static int[] medir(MinecraftServer servidor, Gimnasio.Gimnasio_ g) {
        ServerWorld mundo = mundo(servidor);
        if (mundo == null) {
            return null;
        }
        BlockPos o = Gimnasio.maestro(g);
        int minX = 9999, minY = 9999;
        int maxX = -9999, maxY = -9999;
        var pos = new BlockPos.Mutable();
        // ⚠ Se barre un area GENEROSA (medio hueco de gimnasio) porque medir es
        //   barato y quedarse corto al medir es el mismo fallo que se venia a
        //   arreglar.
        // ⚠⚠⚠ EL BARRIDO NO SE LIMITA CON `PASO_RANURA`, y la primera version
        //    lo hacia: medi el gimnasio de Brock y salio «64 de fondo», que era
        //    EXACTAMENTE el limite del barrido. Medir con la regla que estas
        //    intentando validar solo te devuelve la regla.
        int alcance = Gimnasio.SEPARACION / 2;
        boolean[] ocupada = new boolean[alcance];
        for (int dy = -16; dy < 120; dy++) {
            for (int dz = 0; dz < alcance; dz++) {
                for (int dx = 0; dx < alcance; dx++) {
                    pos.set(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
                    if (mundo.getBlockState(pos).isAir()) {
                        continue;
                    }
                    ocupada[dz] = true;
                    if (dx < minX) minX = dx;
                    if (dy < minY) minY = dy;
                    if (dx > maxX) maxX = dx;
                    if (dy > maxY) maxY = dy;
                }
            }
        }
        if (maxX < minX) {
            return null;
        }

        // ⚠⚠⚠ Y AHORA LA PARTE QUE FALTABA, Y QUE COSTO UN «PELIGRO» EN VIVO.
        //
        //    Barrer ancho arreglo el fallo circular y creo otro: a partir de la
        //    primera vez que se clona una ranura, EL BARRIDO SE COME LA COPIA.
        //    El gimnasio de Brock mide 86 y la copia de la ranura 1 empieza en
        //    128, asi que la medicion daba 214 -- «el maestro mas su copia».
        //
        //    Y eso no es un numero feo y ya: `clonar` MIDE ANTES DE COPIAR, o
        //    sea que la siguiente copia habria sido de 214 de fondo y habria
        //    escrito encima de las ranuras 1 y 2.
        //
        //    ⚠⚠ Las dos versiones anteriores estaban mal por el mismo motivo de
        //       fondo: usaban un LIMITE en vez de mirar LO QUE HAY. Una sala
        //       tiene suelo, asi que sus capas de Z estan todas ocupadas; entre
        //       una copia y la siguiente hay AIRE. El aire es el dato, y no
        //       depende de ningun numero que estemos intentando validar.
        int primera = 0;
        while (primera < alcance && !ocupada[primera]) {
            primera++;
        }
        int fin = primera;
        int vacias = 0;
        for (int dz = primera; dz < alcance; dz++) {
            if (ocupada[dz]) {
                fin = dz + 1;
                vacias = 0;
            } else if (++vacias >= AIRE_QUE_CORTA) {
                break;
            }
        }
        // ¿Queda algo mas alla? Se dice, en vez de ignorarlo en silencio: casi
        // siempre seran las copias de las ranuras, y saberlo evita la duda.
        int masAlla = 0;
        for (int dz = fin; dz < alcance; dz++) {
            if (ocupada[dz]) {
                masAlla++;
            }
        }
        if (masAlla > 0) {
            LunaEternal.LOG.info("Gimnasio {}: medido hasta z={} ({} de fondo). "
                    + "Hay {} capas mas alla, separadas por aire: son las copias "
                    + "de las ranuras y NO se cuentan.",
                    g.id(), fin, fin - primera, masAlla);
        }
        return new int[] {minX, minY, primera,
                          maxX - minX + 1, maxY - minY + 1, fin - primera};
    }

    public static ServerWorld mundo(MinecraftServer servidor) {
        return servidor.getWorld(LunaDimensions.GIMNASIOS);
    }

    /**
     * Pone la plataforma de anclaje del maestro.
     *
     * <p>⚠ Con la esquina noroeste EXACTAMENTE en el origen: ese punto es el
     * que se le da a Litematica o a WorldEdit al pegar, así que tiene que ser el
     * mismo número que devuelve {@link Gimnasio#maestro}.
     */
    public static BlockPos preparar(MinecraftServer servidor, Gimnasio.Gimnasio_ g) {
        ServerWorld mundo = mundo(servidor);
        if (mundo == null) {
            return null;
        }
        BlockPos o = Gimnasio.maestro(g);
        cargar(mundo, o);
        for (int dx = 0; dx < LADO; dx++) {
            for (int dz = 0; dz < LADO; dz++) {
                // ⚠ Piedra pulida y no hierba: al pegar el esquema encima, lo
                //   que sobresalga tiene que verse que NO es del gimnasio.
                mundo.setBlockState(o.add(dx, 0, dz),
                        Blocks.POLISHED_ANDESITE.getDefaultState());
            }
        }
        // la esquina exacta marcada, para no dudar nunca de cuál es el origen
        mundo.setBlockState(o, Blocks.GOLD_BLOCK.getDefaultState());
        LunaEternal.LOG.info("Gimnasio {}: maestro en {} {} {}",
                g.id(), o.getX(), o.getY(), o.getZ());
        return o;
    }

    /**
     * ¿HAY YA UNA SALA CONSTRUIDA EN ESTE MAESTRO?
     *
     * <h2>⚠⚠⚠ Existe para que «ponlas todas» no pise lo ya construido</h2>
     *
     * {@link #preparar} escribe piedra en el origen. Lanzarlo sobre un gimnasio
     * que ya tiene sala <b>le mete un cuadrado de andesita en mitad del
     * suelo</b>, y eso no da ningún error: se descubre cuando alguien pasa por
     * encima, o peor, cuando se clona a las siete ranuras.
     *
     * <p>⚠ Mira una caja pequeña sobre el origen y no toda la sala: es un
     * vistazo, no una medición. {@link #medir} barre millones de bloques y aquí
     * hay que preguntarlo veintitrés veces seguidas.
     *
     * <p>⚠ Y la propia plataforma NO cuenta como construido: si contara, poner
     * una plataforma una vez impediría volver a ponerla.
     */
    public static boolean hayObra(ServerWorld mundo, Gimnasio.Gimnasio_ g) {
        BlockPos o = Gimnasio.maestro(g);
        cargar(mundo, o);
        var pos = new BlockPos.Mutable();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int dy = 0; dy < 8; dy++) {
                    pos.set(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
                    var b = mundo.getBlockState(pos).getBlock();
                    if (b == Blocks.AIR || b == Blocks.POLISHED_ANDESITE
                            || b == Blocks.GOLD_BLOCK) {
                        continue;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Carga el chunk del origen.
     *
     * <p>⚠⚠ HACE FALTA, Y ES LA LECCIÓN QUE YA PAGAMOS DOS VECES: en una
     * dimensión sin nadie dentro <b>no hay ni un chunk cargado</b>, así que leer
     * un bloque contesta lo que haya en memoria —nada— y escribirlo se pierde.
     * El censo que decía «no hay ningún Geodude» fue esto mismo.
     */
    private static void cargar(ServerWorld mundo, BlockPos o) {
        mundo.getChunk(o);
    }

    /**
     * Clona el maestro a una ranura, si no estaba ya.
     *
     * <p>⚠⚠ SOLO UNA VEZ POR RANURA. Copiar ocho mil bloques es rápido, pero
     * hacerlo en cada combate serían ocho mil escrituras por reto — y con gente
     * retando en cadena eso sí se nota en el hilo del servidor.
     *
     * <p>⚠ Y se copia de abajo arriba y de norte a sur, en el mismo orden en
     * ambos lados: copiar en distinto orden con las áreas solapadas se pisa a sí
     * mismo. Aquí no solapan —van a 64 de distancia— pero el día que alguien
     * baje {@code PASO_RANURA} el orden es lo único que lo salva.
     */
    public static void clonar(MinecraftServer servidor, Gimnasio.Gimnasio_ g,
                              int ranura) {
        if (Ranuras.marcarConstruida(g, ranura)) {
            return;   // ya estaba
        }
        ServerWorld mundo = mundo(servidor);
        if (mundo == null) {
            return;
        }
        BlockPos src = Gimnasio.maestro(g);
        BlockPos dst = Gimnasio.origen(g, ranura);
        // ⚠⚠ SE MIDE LO CONSTRUIDO EN VEZ DE SUPONERLO. Antes iba a ojo
        //    (96x48x56): de menos deja el gimnasio CORTADO --y eso no se ve
        //    hasta que alguien camina hasta el borde de su copia y se encuentra
        //    media pared-- y de mas puede llegar a copiar la sala de al lado.
        int[] m = medida(servidor, g);
        if (m == null) {
            LunaEternal.LOG.warn("Gimnasio {}: el maestro esta vacio, "
                    + "no hay nada que clonar", g.id());
            return;
        }
        int x0 = m[0], y0 = m[1], z0 = m[2], ancho = m[3], alto = m[4], fondo = m[5];
        // ⚠⚠⚠ SI LA SALA NO CABE EN SU RANURA, NO SE CLONA. Con un gimnasio mas
        //    profundo que `PASO_RANURA`, la copia se escribiria ENCIMA de la
        //    ranura siguiente -- y eso no da ningun error: da dos gimnasios
        //    fundidos, y el segundo jugador aparece dentro de la pared del
        //    primero. Mejor negarse y decirlo.
        if (z0 + fondo > Gimnasio.PASO_RANURA) {
            LunaEternal.LOG.error("Gimnasio {}: la sala mide {} de fondo y las "
                    + "ranuras van cada {}. NO SE CLONA: la copia pisaria la "
                    + "ranura siguiente. Sube PASO_RANURA.",
                    g.id(), z0 + fondo, Gimnasio.PASO_RANURA);
            return;
        }
        int puestos = 0;
        var pos = new BlockPos.Mutable();
        var destino = new BlockPos.Mutable();
        for (int dy = y0; dy < y0 + alto; dy++) {
            for (int dz = z0; dz < z0 + fondo; dz++) {
                for (int dx = x0; dx < x0 + ancho; dx++) {
                    pos.set(src.getX() + dx, src.getY() + dy, src.getZ() + dz);
                    var estado = mundo.getBlockState(pos);
                    if (estado.isAir()) {
                        continue;   // el vacío no se copia: la sala flota
                    }
                    destino.set(dst.getX() + dx, dst.getY() + dy, dst.getZ() + dz);
                    mundo.setBlockState(destino, estado, 2);
                    puestos++;
                }
            }
        }
        LunaEternal.LOG.info("Gimnasio {}: ranura {} clonada, {} bloques de una sala de {}x{}x{}", g.id(), ranura, puestos, ancho, alto, fondo);
    }

    /**
     * BORRA LAS COPIAS DE LAS RANURAS.
     *
     * <h2>⚠⚠ HACE FALTA MIENTRAS SE CONSTRUYE, Y NO ES OBVIO POR QUE</h2>
     *
     * {@link #clonar} <b>no copia el aire</b>: recorre el maestro y escribe lo
     * que no es aire. Así que si se <i>quita</i> un bloque del maestro —una
     * pared mal puesta, una prueba— la copia se lo queda para siempre, y volver
     * a clonar no lo arregla.
     *
     * <p>⚠ Es lento a propósito y no se llama solo: borra cientos de miles de
     * bloques. Se ejecuta a mano, cuando toca.
     *
     * <p>⚠ Y se salta las ranuras vacías mirando una sola capa: casi siempre
     * solo hay una o dos copias hechas, y barrer las siete enteras costaría
     * segundos de hilo de servidor para no borrar nada.
     *
     * @return cuántos bloques se quitaron
     */
    public static int limpiarRanuras(MinecraftServer servidor,
                                     Gimnasio.Gimnasio_ g) {
        ServerWorld mundo = mundo(servidor);
        if (mundo == null) {
            return 0;
        }
        // ⚠ El maestro puede haber cambiado desde la última vez: aquí se mide
        //   de verdad, y de paso se tira lo recordado.
        olvidarMedidas();
        int[] m = medida(servidor, g);
        if (m == null) {
            return 0;
        }
        int x0 = m[0], y0 = m[1], z0 = m[2], ancho = m[3], alto = m[4], fondo = m[5];
        var aire = Blocks.AIR.getDefaultState();
        var pos = new BlockPos.Mutable();
        int quitados = 0;
        int lideres = 0;
        for (int ranura = 1; ranura < Gimnasio.RANURAS; ranura++) {
            BlockPos dst = Gimnasio.origen(g, ranura);
            // ⚠⚠⚠ Y SE LLEVA TAMBIEN A LOS LIDERES, porque NO HAY OTRA FORMA DE
            //    QUITARLOS. `/kill` NO funciona con ellos: llevan la etiqueta de
            //    los decorativos y nuestra propia proteccion
            //    (ServerLivingEntityEvents.ALLOW_DAMAGE) corta el daño ANTES de
            //    que Minecraft decida nada -- incluido el daño de `/kill` y el
            //    del vacio. La consola dice «Killed 3 entities» y no muere
            //    ninguno.
            //    ⚠ Esto contradice lo que decia CLAUDE.md («lo que sigue
            //      funcionando es /kill»). Era falso, y se descubrio intentando
            //      limpiar tres Brocks apilados.
            lideres += Lideres.quitarDeRanura(mundo, g, ranura);
            for (int dy = y0; dy < y0 + alto; dy++) {
                for (int dz = z0; dz < z0 + fondo; dz++) {
                    for (int dx = x0; dx < x0 + ancho; dx++) {
                        pos.set(dst.getX() + dx, dst.getY() + dy, dst.getZ() + dz);
                        if (mundo.getBlockState(pos).isAir()) {
                            continue;
                        }
                        mundo.setBlockState(pos, aire, 2);
                        quitados++;
                    }
                }
            }
        }
        Ranuras.olvidarConstruidas();
        LunaEternal.LOG.info("Gimnasio {}: {} bloques y {} lideres quitados de "
                + "las ranuras", g.id(), quitados, lideres);
        return quitados;
    }

    /**
     * COMPRUEBA QUE LAS SIETE COPIAS ESTAN BIEN.
     *
     * <h2>Por que hace falta un comando y no basta con mirar</h2>
     *
     * Una ranura mal clonada <b>no da ningun error</b>: el jugador que le toque
     * entra en una sala con media pared, o sin los bloques de posicion, y desde
     * dentro parece que el gimnasio esta roto. Y solo le pasa a el, porque las
     * otras seis estan bien.
     *
     * <p>Peor todavia: las copias se hacen <b>la primera vez que hacen falta</b>,
     * asi que una ranura alta puede llevar semanas sin clonarse y estrenarse el
     * dia que hay ocho personas retando a la vez.
     *
     * <h2>⚠⚠ QUE SE COMPRUEBA, Y POR QUE ESO</h2>
     *
     * <ol>
     *   <li><b>Bloque a bloque contra el maestro</b>, solo lo que no es aire —
     *       que es exactamente lo que {@code clonar} escribe. Un bloque distinto
     *       es una copia que se hizo antes de un cambio.</li>
     *   <li><b>Los cuatro bloques de posicion</b>. Sin los dos obligatorios el
     *       combate se juega igual, con los Pokemon donde caigan, y se calla.</li>
     * </ol>
     *
     * <h2>⚠⚠⚠ VA UNA RANURA POR VEZ, REPARTIDA EN VARIOS TICKS</h2>
     *
     * Siete ranuras son ~325.000 lecturas y hasta 250 chunks que cargar. Hacerlo
     * de golpe congela el servidor varios segundos — {@code limpiarranuras} ya
     * dejo el hilo 2,6 s atras con la tercera parte del trabajo. Repartido, cada
     * ranura cuesta lo suyo y entre medias el servidor respira.
     */
    public static void comprobarRanuras(MinecraftServer servidor,
                                        Gimnasio.Gimnasio_ g,
                                        java.util.function.Consumer<String> decir) {
        // ⚠ Se mide UNA VEZ y se reparte a las siete: medir por ranura era lo
        //   que costaba los tirones.
        olvidarMedidas();
        int[] m = medida(servidor, g);
        if (m == null) {
            decir.accept("§cNo hay nada construido en el maestro de §f"
                    + g.id());
            return;
        }
        decir.accept("§6Comprobando las " + (Gimnasio.RANURAS - 1)
                + " ranuras de §f" + g.id()
                + "§7 (sala de " + m[3] + "x" + m[4] + "x" + m[5]
                + "). Va una por vez.");
        siguiente(servidor, g, 1, m, decir);
    }

    /** Una ranura, y encola la siguiente. */
    private static void siguiente(MinecraftServer servidor, Gimnasio.Gimnasio_ g,
                                  int ranura, int[] m,
                                  java.util.function.Consumer<String> decir) {
        if (ranura >= Gimnasio.RANURAS) {
            decir.accept("§7Listo. Una ranura con bloques que §eSOBRAN§7 se "
                    + "arregla con §f/luna gimnasio <cual> limpiarranuras§7: "
                    + "clonar no borra, solo escribe.");
            return;
        }
        ServerWorld mundo = mundo(servidor);
        if (mundo == null) {
            return;
        }
        // ⚠ Se clona si no estaba: comprobar una ranura vacia y decir «faltan
        //   46.000 bloques» seria verdad y no serviria de nada. Lo util es
        //   dejarla hecha Y comprobada.
        clonar(servidor, g, ranura);

        BlockPos src = Gimnasio.maestro(g);
        BlockPos dst = Gimnasio.origen(g, ranura);
        int x0 = m[0], y0 = m[1], z0 = m[2], ancho = m[3], alto = m[4], fondo = m[5];
        int distintos = 0, total = 0, sobran = 0;
        var a = new BlockPos.Mutable();
        var b = new BlockPos.Mutable();
        for (int dy = y0; dy < y0 + alto; dy++) {
            for (int dz = z0; dz < z0 + fondo; dz++) {
                for (int dx = x0; dx < x0 + ancho; dx++) {
                    a.set(src.getX() + dx, src.getY() + dy, src.getZ() + dz);
                    b.set(dst.getX() + dx, dst.getY() + dy, dst.getZ() + dz);
                    var esperado = mundo.getBlockState(a);
                    if (esperado.isAir()) {
                        // ⚠⚠⚠ EL PUNTO CIEGO QUE ESTO TENIA, Y ES EL QUE MAS
                        //    IMPORTA: `clonar` NO COPIA EL AIRE, asi que un
                        //    bloque QUITADO del maestro se queda en la copia
                        //    PARA SIEMPRE. Comparar solo lo que el maestro tiene
                        //    no lo ve nunca -- la copia pasaba como «igual».
                        //
                        //    Paso de verdad con los bloques de posicion: se
                        //    movieron de sitio en el maestro y las copias
                        //    acabaron con DOS, el viejo y el nuevo. Y dos
                        //    marcadores del mismo tipo es que el mod elige el
                        //    mas cercano, que puede no ser el que quieres.
                        if (!mundo.getBlockState(b).isAir()) {
                            sobran++;
                        }
                        continue;
                    }
                    total++;
                    if (!mundo.getBlockState(b).equals(esperado)) {
                        distintos++;
                    }
                }
            }
        }
        // Los cuatro bloques de posicion, en la COPIA.
        int puestos = 0, obligatorios = 0;
        String[] ids = {"player_pokemon_position", "trainer_pokemon_position",
                        "player_stand_position", "trainer_stand_position"};
        for (int i = 0; i < ids.length; i++) {
            var bloque = net.minecraft.registry.Registries.BLOCK.get(
                    net.minecraft.util.Identifier.of(
                            "cobblemonbattlepositions", ids[i]));
            if (bloque == net.minecraft.block.Blocks.AIR) {
                continue;
            }
            if (buscarEn(mundo, dst, m, bloque)) {
                puestos++;
                if (i < 2) {
                    obligatorios++;
                }
            }
        }
        final int d = distintos, tt = total, pp = puestos, oo = obligatorios;
        final int so = sobran;
        final int r = ranura;
        boolean bien = d == 0 && so == 0 && oo == 2;
        decir.accept(String.format(
            "  %sranura %d§7  %s%s  §8%d/%d bloques de posicion%s",
            bien ? "§a" : "§c", r,
            d == 0 ? (so == 0 ? "§aigual que el maestro" : "§aigual")
                   : "§c" + d + " de " + tt + " DISTINTOS",
            so == 0 ? "" : "  §e+" + so + " que SOBRAN (de un clonado viejo)",
            pp, ids.length,
            oo < 2 ? "  §cFALTA UN OBLIGATORIO" : ""));

        Programador.en(2, () -> siguiente(servidor, g, r + 1, m, decir));
    }

    /** ¿Está ese bloque dentro de la caja de una copia? */
    private static boolean buscarEn(ServerWorld mundo, BlockPos origen, int[] m,
                                    net.minecraft.block.Block bloque) {
        var pos = new BlockPos.Mutable();
        for (int dy = m[1]; dy < m[1] + m[4]; dy++) {
            for (int dz = m[2]; dz < m[2] + m[5]; dz++) {
                for (int dx = m[0]; dx < m[0] + m[3]; dx++) {
                    pos.set(origen.getX() + dx, origen.getY() + dy,
                            origen.getZ() + dz);
                    if (mundo.getBlockState(pos).isOf(bloque)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Lleva a un jugador a su ranura.
     *
     * <p>⚠ Se apunta dónde estaba ANTES de moverlo. Después ya está en la
     * arena, y volver le devolvería a la arena — la misma trampa que ya estaba
     * resuelta en el viaje al Mundo Hogar.
     */
    public static boolean llevar(ServerPlayerEntity jugador,
                                 Gimnasio.Gimnasio_ g, int ranura) {
        MinecraftServer servidor = jugador.getServer();
        if (servidor == null) {
            return false;
        }
        ServerWorld mundo = mundo(servidor);
        if (mundo == null) {
            LunaEternal.LOG.error("No existe la dimension de gimnasios");
            return false;
        }
        // ⚠ Un jugador que ya se ha ido no se mueve: entre que acepta el reto y
        //   que llega aqui puede haberse desconectado, y teletransportar a una
        //   entidad retirada revienta el tick del servidor.
        if (jugador.isRemoved() || jugador.isDisconnected()) {
            return false;
        }
        var p = Gimnasio.entrada(g, ranura);
        // ⚠ Con giro fijo: sin fijarlo entra mirando a donde estuviera mirando
        //   en la ciudadela, que puede ser a una pared.
        // ⚠⚠ Y por `Traslado`, que carga el chunk de destino: en una dimension
        //    sin jugadores no hay ni uno cargado.
        return net.pokereport.luna.world.Traslado.ir(
                jugador, mundo, p, Gimnasio.giroEntrada(g), 0f);
    }
}
