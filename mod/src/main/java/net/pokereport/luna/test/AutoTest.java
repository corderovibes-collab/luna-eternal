package net.pokereport.luna.test;

import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyException;
import net.pokereport.luna.economy.EconomyService;
import net.pokereport.luna.player.PlayerService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Batería de invariantes económicos, ejecutable en caliente con
 * {@code /luna autotest}.
 *
 * <p>Existe porque las propiedades que de verdad importan —idempotencia,
 * atomicidad, que un rechazo no deje rastro— no se pueden comprobar mirando el
 * código, y esperar a tener jugadores conectados para probarlas es una mala
 * idea. Usa jugadores sintéticos y **borra todo lo que crea**.
 *
 * <p>Si alguna vez falla algo aquí, hay un agujero por el que se duplica o se
 * pierde dinero. No se despliega nada con esto en rojo.
 */
public final class AutoTest {

    /** UUIDs reservados. Fijos para que la limpieza sea siempre fiable. */
    private static final UUID T1 = UUID.fromString("00000000-0000-3000-8000-000000000001");
    private static final UUID T2 = UUID.fromString("00000000-0000-3000-8000-000000000002");
    private static final String N1 = "__autotest_1";
    private static final String N2 = "__autotest_2";

    private final Database db;
    private final PlayerService players;
    private final EconomyService economy;
    private final Consumer<String> out;

    /**
     * Los registros del servidor. Hacen falta para <b>codificar un paquete de
     * verdad</b>, que es lo único que prueba el protocolo sin un cliente.
     */
    private final net.minecraft.registry.DynamicRegistryManager registros;

    private int passed = 0;
    private int failed = 0;

    public AutoTest(Database db, PlayerService players, EconomyService economy,
                    net.minecraft.registry.DynamicRegistryManager registros,
                    Consumer<String> out) {
        this.registros = registros;
        this.db = db;
        this.players = players;
        this.economy = economy;
        this.out = out;
    }

    public boolean run() {
        out.accept("§7— autotest de invariantes economicos —");
        try {
            cleanup();  // por si una ejecución anterior murió a medias
            long a = players.resolve(T1, N1);
            long b = players.resolve(T2, N2);

            testCreditAndDebit(a);
            testIdempotency(a);
            testInsufficientFundsLeavesNoTrace(a);
            testMarksAreNotTradeable(a, b);
            testTransferIsAtomic(a, b);
            testLedgerMatchesBalance(a, b);
            testProgression(a);
            testShopCatalog();
            testGtsTax();
            testGtsFlow(a, b);
            testEscaparate(a, b);
            testProtocoloNulos();
            testPokedex(a);
            testKits(a);
            testQuests(a);
            testTelemetria(a, b);
            testCazas(a);
            testCazasPremios(a);
            testRangos(a);
            testMochila(a);
            testVozPokedex();
            testCosmeticos();
            testOficios();
            testArbolMisiones();
            testClanes(a, b);
            testMercado(a, b);
            testTasador();
            testCura();
            testViajes();
            testTrajes();
            testGimnasios();
            testTesoros(a);
            testEspera();

        } catch (Exception e) {
            fail("excepcion inesperada", e.toString());
        } finally {
            try {
                cleanup();
                players.forget(T1);
                players.forget(T2);
                out.accept("§7datos de prueba eliminados");
            } catch (Exception e) {
                fail("limpieza", e.toString());
            }
        }

        boolean ok = failed == 0;
        out.accept(ok
            ? "§a" + passed + " comprobaciones correctas. Invariantes intactos."
            : "§c" + failed + " FALLOS de " + (passed + failed) + ". NO desplegar.");
        return ok;
    }

    // ------------------------------------------------------------ pruebas

    /**
     * El ARBOL de misiones: que se pueda dibujar y recorrer.
     *
     * <p>⚠ El catalogo es un JSON que se edita a mano, y sus tres fallos posibles
     * son <b>los tres invisibles</b>:
     *
     * <ul>
     *   <li>Un {@code requires} que apunta a una mision que no existe: la pantalla
     *       dibuja una linea hacia la nada y la mision no se desbloquea jamas.</li>
     *   <li>Un {@code requires} a <b>otra cadena</b>: la pestaña dibujaria una
     *       arista hacia un nodo que no esta en ella.</li>
     *   <li>Un <b>ciclo</b>: A pide B y B pide A. Ninguna de las dos se desbloquea,
     *       y el calculo de profundidad se cuelga en un bucle infinito.</li>
     * </ul>
     *
     * <p>Ninguno da error al cargar --el catalogo entra en un mapa y ya-- asi que
     * el sintoma es «esta mision no se puede hacer», semanas despues.
     */
    private void testArbolMisiones() {
        var quests = LunaEternal.quests().catalogo();
        check("hay misiones en el catalogo", !quests.isEmpty());

        var porId = new java.util.HashMap<String, net.pokereport.luna.quest.Quest>();
        boolean idsUnicos = true;
        for (var q : quests) {
            if (porId.put(q.id(), q) != null) {
                idsUnicos = false;
            }
        }
        check("ningun identificador de mision se repite", idsUnicos);

        boolean aristasSanas = true;
        boolean mismaCadena = true;
        for (var q : quests) {
            String r = q.requires();
            if (r == null || r.isEmpty()) {
                continue;
            }
            var padre = porId.get(r);
            if (padre == null) {
                aristasSanas = false;
            } else if (!padre.chain().equals(q.chain())) {
                mismaCadena = false;
            }
        }
        check("todo `requires` apunta a una mision que existe", aristasSanas);
        check("ningun `requires` cruza de cadena", mismaCadena);

        // Ciclos: se recorre hacia arriba con un limite. Sin limite esto SE
        // CUELGA, que es peor que fallar.
        boolean sinCiclos = true;
        for (var q : quests) {
            var actual = q;
            for (int saltos = 0; actual != null; saltos++) {
                if (saltos > quests.size()) {
                    sinCiclos = false;
                    break;
                }
                String r = actual.requires();
                actual = (r == null || r.isEmpty()) ? null : porId.get(r);
            }
        }
        check("el arbol de misiones no tiene ciclos", sinCiclos);

        // Y que cada cadena tenga al menos una RAIZ, o entera es inalcanzable.
        var conRaiz = new java.util.HashSet<String>();
        var cadenas = new java.util.HashSet<String>();
        for (var q : quests) {
            cadenas.add(q.chain());
            if (q.requires() == null || q.requires().isEmpty()) {
                conRaiz.add(q.chain());
            }
        }
        check("toda cadena tiene al menos una mision inicial",
              conRaiz.containsAll(cadenas));
    }

    /**
     * Los invariantes de los OFICIOS.
     *
     * <p>⚠ El primero es el que importa: <b>el nombre de cada Via tiene que estar
     * en el ENUM de la base</b>. La columna `player_path.path` es un ENUM de
     * MariaDB, no un VARCHAR, y meter un valor que no esta en la lista NO da un
     * error claro: MariaDB guarda la cadena VACIA y suelta un aviso que nadie
     * mira. El oficio se "guardaria" y al leerlo no existiria.
     *
     * <p>Añadir una Via al enum de Java sin su migracion es exactamente eso, y es
     * un cambio de una linea que parece inofensivo.
     */
    private void testOficios() throws Exception {
        // Se prueba CONTRA LA BASE, no contra una lista escrita aqui: lo que hay
        // que comprobar es que la columna acepta el valor, y eso solo lo sabe la
        // base. Una lista nuestra se desincronizaria igual que el enum.
        long p = players.resolve(T1, N1);
        var prog = LunaEternal.progression();
        boolean todasCaben = true;
        for (var via : net.pokereport.luna.progression.Path.values()) {
            try {
                prog.grant(p, via, 1);
            } catch (Exception e) {
                todasCaben = false;
            }
        }
        check("la base acepta todas las Vias declaradas", todasCaben);

        // Solo los oficios pagan. Si una Via normal pagara, subir de nivel seria
        // una fuente de ingresos y P3 dice sumideros antes que fuentes.
        boolean soloOficiosPagan = true;
        boolean oficiosPaganTodo = true;
        for (var via : net.pokereport.luna.progression.Path.values()) {
            for (int n = 1; n <= net.pokereport.luna.progression.Path.MAX_LEVEL; n++) {
                long paga = via.plataPorNivel(n);
                if (!via.esOficio() && paga != 0) {
                    soloOficiosPagan = false;
                }
                if (via.esOficio() && paga <= 0) {
                    oficiosPaganTodo = false;
                }
            }
        }
        check("solo los oficios pagan Plata", soloOficiosPagan);
        check("todo nivel de oficio paga algo", oficiosPaganTodo);
        check("hay al menos un oficio",
              !net.pokereport.luna.progression.Path.oficios().isEmpty());

        // La paga CRECE con el nivel: el salto de IV a V cuesta 75 veces mas XP
        // que el de 0 a I, y pagar lo mismo haria que nadie pasara del segundo.
        boolean creciente = true;
        for (var via : net.pokereport.luna.progression.Path.oficios()) {
            for (int n = 2; n <= net.pokereport.luna.progression.Path.MAX_LEVEL; n++) {
                if (via.plataPorNivel(n) <= via.plataPorNivel(n - 1)) {
                    creciente = false;
                }
            }
        }
        check("la paga de un oficio crece con el nivel", creciente);
    }

    /**
     * Los invariantes del catalogo de cosmeticos.
     *
     * <p>⚠ <b>Existe porque el catalogo esta partido en dos mitades que nadie
     * mira juntas:</b> una la genera un script del zip de CobblemonMoreCosmetics
     * y la otra se escribe a mano. Cada una es correcta por su lado; lo que no
     * comprueba nadie es lo que pasa al unirlas.
     *
     * <p>Y hay precedente: el catalogo ya vendio disfraces que no existian
     * <b>tres veces seguidas</b>, y las tres el sintoma fue el mismo --se cobraba
     * y no se veia nada-- por causas distintas. Ninguna dio error.
     */
    private void testCosmeticos() {
        var todas = net.pokereport.luna.cosmetics.Catalogo.todas();
        check("el catalogo de cosmeticos no esta vacio", !todas.isEmpty());

        // Que no haya identificadores repetidos lo comprueba `Catalogo` al
        // cargarse, y revienta si los hay. Aqui se comprueba lo que aquello no
        // puede: que cada pieza sea COHERENTE consigo misma.
        boolean categoriasValidas = true;
        boolean mascotasConAspecto = true;
        boolean jugadorSinEspecie = true;
        boolean preciosSanos = true;
        var validas = net.pokereport.luna.cosmetics.Catalogo.categorias();
        for (var pieza : todas) {
            if (!validas.contains(pieza.categoria())) {
                categoriasValidas = false;
            }
            // Una mascota sin aspecto no se puede aplicar: `disfrazar` fuerza
            // `pieza.aspecto()` y forzar la cadena vacia no hace nada. Se
            // cobraria y no pasaria nada, que es el fallo de siempre.
            if (pieza.esDePokemon() && pieza.aspecto().isEmpty()) {
                mascotasConAspecto = false;
            }
            // Y al reves: una pieza del jugador CON especie iria por `disfrazar`,
            // que le buscaria un Pokemon de esa especie en el equipo.
            if (!pieza.esDePokemon() && !pieza.especie().isEmpty()) {
                jugadorSinEspecie = false;
            }
            // Un precio negativo cobraria al reves: `comprar` haria un debit de
            // -1500, que es un ingreso. Nunca ha pasado, y por eso mismo nadie
            // lo miraria.
            if (pieza.precio() < 0) {
                preciosSanos = false;
            }
        }
        check("toda pieza tiene una categoria de las declaradas", categoriasValidas);
        check("toda mascota lleva aspecto", mascotasConAspecto);
        check("ninguna pieza de jugador lleva especie", jugadorSinEspecie);
        check("ningun precio es negativo", preciosSanos);

        // Las auras: cada una tiene que estar en el catalogo general Y tener su
        // receta. Estan en dos listas distintas del mismo fichero, y es
        // exactamente el tipo de cosa que se desincroniza al añadir una.
        boolean aurasCompletas = true;
        boolean aurasEnCatalogo = true;
        for (var pa : net.pokereport.luna.cosmetics.CatalogoLuna.AURAS) {
            if (net.pokereport.luna.cosmetics.CatalogoLuna.auraDe(pa.pieza().id()) == null) {
                aurasCompletas = false;
            }
            var enCatalogo = net.pokereport.luna.cosmetics.Catalogo.de(pa.pieza().id());
            if (enCatalogo == null
                    || !enCatalogo.categoria()
                            .equals(net.pokereport.luna.cosmetics.Catalogo.AURAS)) {
                aurasEnCatalogo = false;
            }
            // La cadencia es un modulo: con 0 seria una division por cero en cada
            // fotograma del cliente, y el fallo saldria en el juego y no aqui.
            if (pa.aura().cadencia() <= 0 || pa.aura().cuantas() <= 0) {
                aurasCompletas = false;
            }
        }
        check("cada aura tiene su receta y es dibujable", aurasCompletas);
        check("cada aura esta en el catalogo y en su categoria", aurasEnCatalogo);

        // ⚠ EL IDENTIFICADOR DE UN SOMBRERO ATA EL CATALOGO CON EL DIBUJADO.
        //   `Sombreros.modeloDe` le quita el prefijo `sombrero_` para formar
        //   `lunaeternal:sombreros/<nombre>`, que es donde el generador deja el
        //   modelo. Un identificador sin ese prefijo apuntaria a otro sitio, no
        //   se horneria, y saldria un CUBO MORADO Y NEGRO -- no una excepcion,
        //   sino algo que parece un fallo de textura y se busca donde no es.
        boolean sombrerosBienNombrados = todas.stream()
                .filter(x -> net.pokereport.luna.cosmetics.Catalogo.SOMBREROS
                        .equals(x.categoria()))
                .allMatch(x -> x.id().startsWith("sombrero_") && !x.aspecto().isEmpty());
        check("todo sombrero lleva el prefijo que espera el dibujado",
              sombrerosBienNombrados);

        // Que exista al menos una que NO se vende. D-039 dice que los eventos son
        // «la mitad que hace funcionar la decision»: si todo fuera de pago, los
        // unicos con cosmetico serian los que pagan y el escaparate se apaga solo.
        boolean hayDeEvento = todas.stream().anyMatch(x -> x.precio() == 0);
        check("hay cosmeticos que solo salen en eventos (D-039)", hayDeEvento);
    }


    private void testCreditAndDebit(long p) throws Exception {
        economy.credit(p, Currency.POKEDOLLAR, 1000, "autotest", key());
        check("crédito deja saldo 1000",
              economy.balance(p, Currency.POKEDOLLAR) == 1000);

        economy.debit(p, Currency.POKEDOLLAR, 400, "autotest", key());
        check("débito deja saldo 600",
              economy.balance(p, Currency.POKEDOLLAR) == 600);
    }

    /** R4: repetir la misma clave NO puede aplicar el movimiento dos veces. */
    private void testIdempotency(long p) throws Exception {
        String k = key();
        long before = economy.balance(p, Currency.POKEDOLLAR);

        economy.credit(p, Currency.POKEDOLLAR, 500, "autotest_idem", k);
        long afterFirst = economy.balance(p, Currency.POKEDOLLAR);
        check("primer uso de la clave suma 500", afterFirst == before + 500);

        boolean rejected = false;
        try {
            economy.credit(p, Currency.POKEDOLLAR, 500, "autotest_idem", k);
        } catch (EconomyException e) {
            rejected = e.kind == EconomyException.Kind.ALREADY_APPLIED;
        }
        check("repetir la clave se rechaza", rejected);
        check("repetir la clave NO cambia el saldo",
              economy.balance(p, Currency.POKEDOLLAR) == afterFirst);
    }

    /** Un rechazo debe dejar el sistema exactamente como estaba. */
    private void testInsufficientFundsLeavesNoTrace(long p) throws Exception {
        long before = economy.balance(p, Currency.POKEDOLLAR);
        long entriesBefore = countEntries(p);

        boolean rejected = false;
        try {
            economy.debit(p, Currency.POKEDOLLAR, before + 1, "autotest_over", key());
        } catch (EconomyException e) {
            rejected = e.kind == EconomyException.Kind.INSUFFICIENT_FUNDS;
        }
        check("saldo insuficiente se rechaza", rejected);
        check("saldo intacto tras el rechazo",
              economy.balance(p, Currency.POKEDOLLAR) == before);
        check("el rechazo NO deja asiento en el libro",
              countEntries(p) == entriesBefore);
    }

    /**
     * ECO-001: ninguna moneda vinculada circula entre jugadores.
     *
     * <p>Son los dos invariantes que sostienen el modelo de negocio:
     * <ul>
     *   <li><b>Marcas</b> — si se transfirieran, la progresión se compraría a
     *       otro jugador.</li>
     *   <li><b>ReportCoins</b> — si se transfirieran, se revenderían por
     *       PokéDólares y existiría de facto la conversión prohibida, creando
     *       un mercado gris que nadie decidió abrir.</li>
     * </ul>
     */
    private void testMarksAreNotTradeable(long a, long b) throws Exception {
        for (Currency c : Currency.values()) {
            if (c.tradeable) continue;

            economy.credit(a, c, 50, "autotest", key());
            boolean rejected = false;
            try {
                economy.transfer(a, b, c, 10, "autotest", key());
            } catch (EconomyException e) {
                rejected = e.kind == EconomyException.Kind.NOT_TRADEABLE;
            }
            check("transferir " + c.displayName + " se rechaza", rejected);
            check(c.displayName + ": el origen conserva su saldo",
                  economy.balance(a, c) == 50);
            check(c.displayName + ": el destino no recibe nada",
                  economy.balance(b, c) == 0);
        }
    }

    /** Las dos patas de una transferencia ocurren o no ocurre ninguna. */
    private void testTransferIsAtomic(long a, long b) throws Exception {
        long aBefore = economy.balance(a, Currency.POKEDOLLAR);
        long bBefore = economy.balance(b, Currency.POKEDOLLAR);

        economy.transfer(a, b, Currency.POKEDOLLAR, 300, "autotest_tx", key());
        check("el origen pierde 300",
              economy.balance(a, Currency.POKEDOLLAR) == aBefore - 300);
        check("el destino gana 300",
              economy.balance(b, Currency.POKEDOLLAR) == bBefore + 300);

        // Transferencia imposible: no debe mover nada en ninguno de los dos.
        long aMid = economy.balance(a, Currency.POKEDOLLAR);
        long bMid = economy.balance(b, Currency.POKEDOLLAR);
        try {
            economy.transfer(a, b, Currency.POKEDOLLAR, aMid + 1_000_000,
                             "autotest_tx_fail", key());
        } catch (EconomyException ignored) { }
        check("una transferencia fallida no toca el origen",
              economy.balance(a, Currency.POKEDOLLAR) == aMid);
        check("una transferencia fallida no toca el destino",
              economy.balance(b, Currency.POKEDOLLAR) == bMid);
    }

    /** R3: el saldo es una caché del libro. Si no cuadra, hay un agujero. */
    private void testLedgerMatchesBalance(long a, long b) throws Exception {
        for (long p : new long[]{a, b}) {
            for (Currency c : Currency.values()) {
                check("saldo == suma del libro (jugador " + p + ", " + c + ")",
                      economy.auditDiscrepancy(p, c) == 0);
            }
        }
    }

    /**
     * Progresión: una concesión grande debe cruzar varios niveles de golpe.
     *
     * <p>Si no lo hiciera, el jugador se quedaría con XP de sobra y el nivel
     * sin subir — un fallo silencioso que solo se detecta mirando la tabla.
     */
    private void testProgression(long p) throws Exception {
        var prog = LunaEternal.progression();
        var path = net.pokereport.luna.progression.Path.EXPLORADOR;

        var s1 = prog.grant(p, path, 50);
        check("50 de XP no sube de nivel", s1.level() == 0 && s1.xp() == 50);

        // 100 sube a nivel 1 (necesita 100) y deja 50 hacia el 2.
        var s2 = prog.grant(p, path, 100);
        check("cruzar el umbral sube de nivel", s2.level() == 1);
        check("el sobrante se conserva", s2.xp() == 50);

        // 100 + 400 + 1200 + 3000 + 7500 = 12 200 en total hasta el máximo.
        var s3 = prog.grant(p, path, 100_000);
        check("una concesión enorme llega al nivel máximo",
              s3.level() == net.pokereport.luna.progression.Path.MAX_LEVEL);
        check("al máximo no se acumula XP sobrante", s3.xp() == 0);

        var dominant = prog.dominant(p);
        check("la vía dominante es la de mayor nivel",
              dominant != null && dominant.path() == path);

        var all = prog.all(p);
        check("las cinco vías existen siempre",
              all.size() == net.pokereport.luna.progression.Path.values().length);
    }

    /**
     * El catálogo de la tienda no puede permitir ganar dinero comprando y
     * revendiendo.
     *
     * <p>Es el error clásico que mata una economía en días, y el que audité a
     * mano en la configuración de producción. Aquí queda automatizado para que
     * <b>no pueda reaparecer</b> el día que alguien retoque un precio.
     */
    /**
     * Cazas (HUNT-001). MOD-006: los invariantes ANTES de desplegar.
     *
     * <p>Lo que se persigue aquí es el fallo caro: <b>cobrar dos veces</b>.
     * Un doble clic, o dos paquetes que llegan a la vez, no pueden pagar dos
     * premios. Es exactamente la clase de agujero que no se ve leyendo el
     * código.
     */
    /**
     * La voz de la Pokédex al escanear.
     *
     * <p>No se puede escanear desde aquí —hace falta un jugador con el objeto
     * en la mano— así que lo que se comprueba es <b>el catálogo</b>, que es
     * donde puede colarse el error: mandar al cliente a reproducir un sonido
     * que no existe deja el escaneo mudo y sin decir por qué.
     */
    private void testVozPokedex() {
        check("bulbasaur tiene voz",
            net.pokereport.luna.pokedex.VozService.tieneVoz("bulbasaur"));
        // Cobblemon da el nombre de varias formas segun de donde se lea.
        check("el identificador con espacio de nombres se normaliza",
            net.pokereport.luna.pokedex.VozService.tieneVoz("cobblemon:bulbasaur"));
        check("las mayusculas se normalizan",
            net.pokereport.luna.pokedex.VozService.tieneVoz("Bulbasaur"));
        check("los espacios se normalizan",
            net.pokereport.luna.pokedex.VozService.normalizar("Mr Mime")
                .equals("mr_mime"));
        check("Nidoran-F se normaliza como su fichero",
            net.pokereport.luna.pokedex.VozService.normalizar("Nidoran-F")
                .equals("nidoran_f"));
        // Las formas regionales NO son especies aparte en Cobblemon.
        check("la forma regional tiene su propia voz",
            net.pokereport.luna.pokedex.VozService.clave("Rattata", "Alola")
                .equals("rattata_alola"));
        check("una forma sin grabar cae en la especie base",
            net.pokereport.luna.pokedex.VozService.clave("Pikachu", "Gigantamax")
                .equals("pikachu"));
        check("sin forma se usa la especie",
            net.pokereport.luna.pokedex.VozService.clave("Ekans", "")
                .equals("ekans"));
        // El caso "sin voz" YA NO ES VICTREEBEL: se grabo el 2026-08-22 y con
        // el Kanto quedo completo. Se usa Treecko, que es de Gen 3 y por tanto
        // ni tiene voz ni la va a tener mientras solo esten activas Kanto y
        // Johto (D-017). Sigue siendo un caso REAL y no uno inventado.
        check("una especie sin grabar no da clave",
            net.pokereport.luna.pokedex.VozService.clave("Treecko", "").isEmpty());
        check("hay las 256 voces del catalogo (151 de Kanto COMPLETO, "
                + "100 de Johto, mas 5 formas de Alola)",
            net.pokereport.luna.pokedex.VozService.cuantas() == 256);
        check("Victreebel, el ultimo hueco de Kanto, ya tiene voz",
            net.pokereport.luna.pokedex.VozService.tieneVoz("victreebel"));
        // Johto entero, que es la otra generacion activa (D-017).
        check("Chikorita, el inicial de Johto, tiene voz",
            net.pokereport.luna.pokedex.VozService.tieneVoz("chikorita"));
        // Ho-Oh lleva guion en el nombre y Porygon2 un digito: son los dos
        // nombres de Johto que la normalizacion podia estropear en silencio.
        check("Ho-Oh se normaliza como su fichero",
            net.pokereport.luna.pokedex.VozService.clave("Ho-Oh", "")
                .equals("ho_oh"));
        check("Porygon2 conserva el digito",
            net.pokereport.luna.pokedex.VozService.clave("Porygon2", "")
                .equals("porygon2"));
        // Y lo importante al reves: una especie sin grabar NO manda paquete.
        check("una especie sin grabar no suena",
            !net.pokereport.luna.pokedex.VozService.tieneVoz("treecko"));
        check("un nombre vacio no suena",
            !net.pokereport.luna.pokedex.VozService.tieneVoz(""));
        check("un nombre nulo no revienta",
            !net.pokereport.luna.pokedex.VozService.tieneVoz(null));
        check("hay al menos una voz",
            net.pokereport.luna.pokedex.VozService.cuantas() > 0);

        // El cooldown, que es lo que se traga la rafaga de Cobblemon. Su
        // cliente manda un "he terminado" POR TICK hasta que el servidor le
        // confirma, y sin esto la voz se oye duplicada -- se oyo.
        var cd = new net.pokereport.luna.pokedex.Cooldown(50_000);
        check("la primera vez toca", cd.toca("x"));
        check("la segunda seguida NO toca", !cd.toca("x"));
        check("la tercera seguida tampoco", !cd.toca("x"));
        check("otra clave no se ve afectada", cd.toca("y"));
        cd.olvidar("x");
        check("tras olvidar, vuelve a tocar", cd.toca("x"));

        var rapido = new net.pokereport.luna.pokedex.Cooldown(0);
        check("con espera cero siempre toca", rapido.toca("z") && rapido.toca("z"));
    }

    /**
     * TESOROS.
     *
     * <h2>⚠⚠⚠ LO QUE SE COMPRUEBA ES QUE UN COFRE NO COBRE SIN ENTREGAR</h2>
     *
     * Un identificador de premio mal escrito <b>no da ningun error</b>: gasta la
     * llave, anota la apertura, y la entrega se encuentra con que ese objeto no
     * existe. El jugador paga y no recibe. Es el fallo de los 62 cosmeticos que
     * no existian, aplicado a algo que cuesta dinero de verdad.
     */
    private void testTesoros(long jugador) throws Exception {
        var cofres = net.pokereport.luna.crate.Cofre.TODOS;
        check("hay al menos un cofre", !cofres.isEmpty());

        // ⚠⚠ CADA PREMIO EXISTE DE VERDAD, y se le pregunta al REGISTRO, no a
        //    una lista nuestra: una lista nuestra repetiria el mismo error.
        boolean existen = true, pesos = true, unicos = true;
        var vistos = new java.util.HashSet<String>();
        for (var c : cofres) {
            if (!vistos.add(c.id())) {
                unicos = false;
            }
            if (c.premios().isEmpty() || c.pesoTotal() <= 0) {
                pesos = false;
                LunaEternal.LOG.error("Cofre {}: sin premios o sin peso", c.id());
            }
            for (var pr : c.premios()) {
                if (pr.peso() <= 0) {
                    pesos = false;
                    LunaEternal.LOG.error("Cofre {}: {} pesa {}", c.id(),
                            pr.id(), pr.peso());
                }
                switch (pr.tipo()) {
                    case OBJETO -> {
                        var item = net.minecraft.registry.Registries.ITEM.get(
                                net.minecraft.util.Identifier.of(pr.id()));
                        if (item == net.minecraft.item.Items.AIR) {
                            existen = false;
                            LunaEternal.LOG.error("Cofre {}: el objeto '{}' NO"
                                    + " EXISTE", c.id(), pr.id());
                        }
                    }
                    case POKEMON -> {
                        if (net.pokereport.luna.pokedex.ClaveEspecie
                                .buscar(pr.id()) == null) {
                            existen = false;
                            LunaEternal.LOG.error("Cofre {}: la especie '{}' NO"
                                    + " EXISTE", c.id(), pr.id());
                        }
                    }
                    default -> { }
                }
            }
        }
        check("cada premio de cada cofre existe de verdad", existen);
        check("todos los pesos son positivos", pesos);
        check("cada cofre tiene un identificador unico", unicos);

        // ⚠⚠ LAS PROBABILIDADES SUMAN 1. Es la condicion de D-020 --que sean
        //    publicas-- convertida en algo que se puede comprobar: si no
        //    sumaran, el numero que ve el jugador seria decoracion.
        boolean suman = true;
        for (var c : cofres) {
            double s = 0;
            for (var pr : c.premios()) {
                s += c.probabilidad(pr);
            }
            if (Math.abs(s - 1.0) > 1e-9) {
                suman = false;
                LunaEternal.LOG.error("Cofre {}: las probabilidades suman {}",
                        c.id(), s);
            }
        }
        check("las probabilidades de cada cofre suman el 100%", suman);

        // ⚠⚠ UN COFRE CON PIEDAD TIENE PREMIO MAYOR. Sin el, la piedad no puede
        //    garantizar nada y la pantalla prometeria algo que no llega nunca:
        //    «premio mayor en 3 aperturas» y no hay ninguno que dar.
        boolean coherente = true;
        for (var c : cofres) {
            if (c.piedad() > 0 && !c.tieneMayor()) {
                coherente = false;
                LunaEternal.LOG.error("Cofre {}: promete piedad y no tiene"
                        + " premio mayor", c.id());
            }
        }
        check("todo cofre que promete piedad tiene premio mayor", coherente);

        // ⚠ Solo el diario es gratis. Si otro lo fuera, el precio no se cobraria
        //   y nadie lo notaria hasta mirar la caja.
        boolean precios = true;
        for (var c : cofres) {
            boolean premium = c.llave()
                    == net.pokereport.luna.crate.Cofre.Llave.PREMIUM;
            if (premium && c.precio() <= 0) {
                precios = false;
                LunaEternal.LOG.error("Cofre {}: es premium y cuesta {}",
                        c.id(), c.precio());
            }
            if (!premium && c.precio() != 0) {
                precios = false;
            }
        }
        check("cada cofre premium tiene precio y los de juego no", precios);

        // ⚠⚠⚠ EL SORTEO SIEMPRE DEVUELVE ALGO. Un `null` aqui seria una llave
        //    gastada y ningun premio -- y la transaccion ya habria pasado.
        var rnd = new java.util.Random(1234);
        boolean siempre = true, forzado = true;
        for (var c : cofres) {
            for (int i = 0; i < 500; i++) {
                if (net.pokereport.luna.crate.Cofre.sortear(c, rnd, false) == null) {
                    siempre = false;
                    break;
                }
            }
            // Y con la piedad forzada, si tiene mayores, TIENE que salir uno.
            if (c.tieneMayor()) {
                for (int i = 0; i < 200; i++) {
                    var pr = net.pokereport.luna.crate.Cofre.sortear(c, rnd, true);
                    if (pr == null || !pr.mayor()) {
                        forzado = false;
                        break;
                    }
                }
            }
        }
        check("el sorteo siempre devuelve un premio", siempre);
        check("con la piedad forzada sale un premio mayor", forzado);

        // ⚠⚠⚠ LOS DOS COFRES DE LEGENDARIOS TIENEN QUE SEGUIR PLANOS. Es orden
        //    del usuario --«que todos sean iguales»-- y es exactamente el tipo
        //    de regla que se cae sola: basta con que alguien añada un
        //    legendario con otro peso, o toque uno de los once, para que la
        //    tabla vuelva a estar sesgada. Y NO DARIA NINGUN ERROR: el cofre
        //    seguiria funcionando, repartiendo, cobrando y celebrando. Lo
        //    unico que cambiaria son unos porcentajes que casi nadie compara.
        //
        //    ⚠ Se comprueba sobre LA PROBABILIDAD y no sobre el peso, que es
        //      lo que el jugador ve. Dos pesos distintos que dieran la misma
        //      probabilidad estarian bien; dos pesos iguales en cofres con
        //      distinto total, no.
        boolean planos = true;
        for (String id : new String[] {"legendario", "legendario_shiny"}) {
            var c = net.pokereport.luna.crate.Cofre.de(id);
            if (c == null || c.premios().isEmpty()) { planos = false; continue; }
            double primera = c.probabilidad(c.premios().get(0));
            for (var pr : c.premios()) {
                if (Math.abs(c.probabilidad(pr) - primera) > 1e-9) {
                    planos = false;
                    LunaEternal.LOG.error("Cofre {}: '{}' sale al {}% y '{}' al"
                            + " {}%, y tenian que ser iguales", id, pr.id(),
                            c.probabilidad(pr) * 100,
                            c.premios().get(0).id(), primera * 100);
                }
            }
            // ⚠⚠ Y CON TODOS IGUALES, LA PIEDAD SOBRA. Si alguien la volviera
            //    a poner, la pantalla contaria «te faltan N para el premio
            //    mayor» de algo que sale en CADA tirada.
            if (c.piedad() != 0) {
                planos = false;
                LunaEternal.LOG.error("Cofre {}: piedad {} con todos los premios"
                        + " al mismo porcentaje", id, c.piedad());
            }
        }
        check("los legendarios salen todos con la misma probabilidad", planos);

        // ⚠ Y un cofre desconocido no resuelve: el identificador llega del
        //   cliente y un cliente modificado puede mandar cualquier cosa (P6).
        check("un cofre desconocido no resuelve",
              net.pokereport.luna.crate.Cofre.de("no_existe") == null);
        check("un nulo no resuelve",
              net.pokereport.luna.crate.Cofre.de(null) == null);

        // ⚠ La llave diaria pide una hora. Un cero aqui la regalaria al entrar.
        check("la llave diaria pide tiempo de juego",
              net.pokereport.luna.crate.Actividad.SEGUNDOS_LLAVE > 0);

        // ⚠⚠ LAS LLAVES VIAJAN COMO UNA LISTA Y LA PANTALLA LAS LEE POR
        //    POSICION, asi que su tamaño TIENE que ser el numero de cofres. Si
        //    no coincidiera, el jugador veria las llaves de otro cofre --o
        //    ninguna-- SIN QUE NADA FALLARA. Es la misma familia que las tres
        //    listas de medallas.
        var svcCofres = LunaEternal.crates();
        if (svcCofres != null) {
            check("las llaves llegan una por cofre",
                  svcCofres.todasLasLlaves(jugador).length == cofres.size());
            check("la piedad llega una por cofre",
                  svcCofres.todaLaPiedad(jugador).length == cofres.size());

            // ⚠⚠⚠ AQUI SE COMPRA Y SE ABRE DE VERDAD, CONTRA LA BASE, Y ESA ES
            //    LA UNICA PARTE DE ESTE BLOQUE QUE VIGILA ALGO.
            //
            //    Las once comprobaciones de arriba validan LAS TABLAS --que los
            //    pesos sumen, que los premios existan, que los precios sean
            //    positivos-- y ninguna llegaba a tocar MariaDB. Con todas en
            //    verde, COMPRAR UNA LLAVE PREMIUM FALLABA SIEMPRE:
            //
            //      Data too long for column 'idempotency_key' at row 1
            //
            //    `ledger_entry.idempotency_key` es VARCHAR(64) y yo escribia
            //    «crate_key:<id>:legendario_shiny:<uuid>», o sea 65: UNO DE MAS.
            //    Lo descubrio el usuario --«no me deja comprar llaves»--, no el
            //    autotest, y es la SEGUNDA vez en este proyecto que una columna
            //    corta rompe una funcion entera en silencio: la primera fueron
            //    las transferencias, y tambien la cazo ejercitar el camino.
            //
            //    ⚠⚠⚠ Y SOLO FALLABA UNO DE LOS TRES COFRES DE PAGO. `gachapon`
            //       (57) y `legendario` (59) cabian; el shiny no, porque su
            //       identificador tiene seis letras mas. O sea que lo que
            //       decidia si la compra funcionaba era LA LONGITUD DEL NOMBRE.
            //       Por eso el bucle los recorre TODOS: probar uno solo tenia
            //       dos tercios de probabilidad de pasar con el fallo dentro.
            //
            //    ⚠ Comparar longitudes contra 36 aqui seria comparar constantes
            //      contra constantes --la confianza falsa que ya nos mordio con
            //      la separacion de las ranuras--. Lo que dice la verdad es que
            //      MariaDB acepte la fila.
            for (var cofre : cofres) {
                if (cofre.llave() != net.pokereport.luna.crate.Cofre.Llave.PREMIUM) continue;

                long saldo = LunaEternal.economy().balance(jugador, Currency.REPORTCOIN);
                LunaEternal.economy().apply(jugador, Currency.REPORTCOIN,
                        cofre.precio(), "autotest tesoros", "test", null,
                        java.util.UUID.randomUUID().toString());

                int antes = svcCofres.llaves(jugador, cofre.id());
                int quedan = svcCofres.comprarLlave(jugador, cofre.id(), 1);
                check("se puede comprar la llave de " + cofre.id(),
                      quedan == antes + 1);
                check("comprar la llave de " + cofre.id() + " cobra su precio",
                      LunaEternal.economy().balance(jugador, Currency.REPORTCOIN) == saldo);

                // Y abrirlo, que es donde el premio en Plata vuelve a pasar por
                // el libro de asientos con esa misma clave.
                var r = svcCofres.abrir(jugador, cofre.id(),
                                        java.util.UUID.randomUUID().toString());
                check("se puede abrir " + cofre.id(), r != null && r.premio() != null);
                check("abrir " + cofre.id() + " gasta la llave",
                      r != null && r.llavesRestantes() == antes);
            }

            // ⚠ El diario NO se compra --su llave la da el tiempo de juego--
            //   asi que se le dan por la via normal y se abre igual: es el
            //   unico cofre cuyo premio puede ser Plata, o sea el unico que
            //   ejercita el asiento del PREMIO.
            var diario = net.pokereport.luna.crate.Cofre.de("gacha_diario");
            if (diario != null) {
                svcCofres.darLlaves(jugador, diario.id(), 1);
                var r = svcCofres.abrir(jugador, diario.id(),
                                        java.util.UUID.randomUUID().toString());
                check("se puede abrir el gacha diario",
                      r != null && r.premio() != null);
            }

            // ⚠⚠ SIN LLAVE NO SE ABRE, Y DEVUELVE NULO EN VEZ DE REVENTAR. Si
            //    abriera igual, el cofre seria gratis y no lo diria nadie.
            check("sin llave no se abre",
                  svcCofres.abrir(jugador, "legendario_shiny",
                                  java.util.UUID.randomUUID().toString()) == null);
        }
    }

    private void testCazas(long jugador) throws Exception {
        var hunts = LunaEternal.hunts();
        check("el servicio de cazas está cargado", hunts != null);
        if (hunts == null) return;

        var ciclo = hunts.cicloActual(jugador);
        check("hay un ciclo de cazas vigente", ciclo != null && ciclo.id() > 0);
        check("el ciclo tiene objetivos", !ciclo.objetivos().isEmpty());
        check("el ciclo termina en el futuro",
              ciclo.terminaEn() > System.currentTimeMillis() / 1000L);

        // Dos llamadas seguidas deben dar el MISMO ciclo: si cada consulta
        // creara uno nuevo, las cazas cambiarian al abrir la pantalla.
        var otra = hunts.cicloActual(jugador);
        check("consultar dos veces no crea un ciclo nuevo",
              otra.id() == ciclo.id());

        var obj = ciclo.objetivos().get(0);
        check("un objetivo empieza sin progreso", obj.hechos() == 0);
        check("un objetivo empieza sin cobrar", !obj.cobrado());

        // Sin completar, no se paga.
        long antes = LunaEternal.economy().balance(jugador, Currency.POKEDOLLAR);
        var r0 = hunts.cobrar(jugador, obj.id(), java.util.UUID.randomUUID());
        check("cobrar sin completar se rechaza",
              r0 == net.pokereport.luna.hunt.HuntService.Resultado.NO_COMPLETO);
        check("un cobro rechazado no paga",
              LunaEternal.economy().balance(jugador, Currency.POKEDOLLAR) == antes);

        // Completar y cobrar.
        for (int i = 0; i < obj.necesarios(); i++) {
            hunts.avanzar(jugador, obj.especie(), obj.tipo());
        }
        var conProgreso = hunts.cicloActual(jugador).objetivos().stream()
            .filter(o -> o.id() == obj.id()).findFirst().orElseThrow();
        check("avanzar suma progreso", conProgreso.hechos() >= obj.necesarios());
        check("el objetivo aparece completo", conProgreso.completo());

        var r1 = hunts.cobrar(jugador, obj.id(), java.util.UUID.randomUUID());
        check("cobrar completo paga",
              r1 == net.pokereport.luna.hunt.HuntService.Resultado.PAGADO);
        long despues = LunaEternal.economy().balance(jugador, Currency.POKEDOLLAR);
        check("el pago llega al saldo", despues == antes + obj.premioDolar());

        // EL INVARIANTE QUE IMPORTA.
        var r2 = hunts.cobrar(jugador, obj.id(), java.util.UUID.randomUUID());
        check("cobrar dos veces se rechaza",
              r2 == net.pokereport.luna.hunt.HuntService.Resultado.YA_COBRADO);
        check("el segundo cobro NO paga",
              LunaEternal.economy().balance(jugador, Currency.POKEDOLLAR) == despues);

        // Avanzar una especie que no esta de caza no puede romper nada.
        hunts.avanzar(jugador, "no-existe-este-pokemon",
                      net.pokereport.luna.hunt.HuntService.Tipo.CAPTURA);
        check("avanzar una especie sin caza no falla", true);
    }

    private void testShopCatalog() {
        var catalog = LunaEternal.shop();
        check("el catálogo de la tienda está cargado", catalog != null);
        if (catalog == null) return;

        int arbitrage = 0, nonPositive = 0, total = 0;
        for (var c : catalog.categories()) {
            for (var e : c.entries()) {
                total++;
                if (e.sell() >= e.buy()) arbitrage++;
                if (e.buy() <= 0) nonPositive++;
            }
        }
        check("hay objetos en la tienda", total > 0);
        check("ningún objeto se vende por más de lo que cuesta", arbitrage == 0);
        check("todos los precios de compra son positivos", nonPositive == 0);

        // La moneda premium no puede recomprarse: sería un mercado gris.
        int premiumBuyback = 0;
        for (var c : catalog.categories()) {
            for (var e : c.entries()) {
                if (!e.currency().tradeable && e.sell() > 0) premiumBuyback++;
            }
        }
        check("la moneda premium no se puede recomprar", premiumBuyback == 0);

        // ------------------------------------------------ lo que pide la PANTALLA
        //
        // Lo de arriba comprueba la ECONOMIA del catalogo. Esto comprueba que se
        // pueda DIBUJAR, que es un problema distinto y que ya mordio una vez: la
        // cadena `oficios` del arbol de misiones pedia 754 px en un hueco de 698
        // y la version anterior no lo detectaba -- dibujaba fuera del marco, y
        // desde dentro eso se ve como "faltan misiones".

        java.util.Set<String> ids = new java.util.HashSet<>();
        boolean idsUnicos = true;
        boolean todasConArticulos = true;
        boolean iconosReales = true;
        boolean nombresConColor = true;
        for (var c : catalog.categories()) {
            if (!ids.add(c.id())) idsUnicos = false;
            if (c.entries().isEmpty()) todasConArticulos = false;
            // Un icono que no exista sale como AIRE, y `drawItem` con aire no
            // dibuja NADA: ni hueco, ni cubo morado. La categoria pareceria un
            // boton en blanco y nada avisaria.
            if (c.icon() == net.minecraft.item.Items.AIR) iconosReales = false;
            // La pantalla QUITA los codigos de color del nombre --sobre el panel
            // claro un §f seria invisible--, asi que un nombre que sea SOLO color
            // se quedaria vacio.
            if (c.name() == null || c.name().replaceAll("\u00a7.", "").isBlank()) {
                nombresConColor = false;
            }
        }
        // ⚠⚠ SOLO ARTICULOS DE COBBLEMON. Decision del usuario (2026-08-23):
        //    lo de Minecraft se consigue explorando. Y encaja con lo que ya
        //    habia: las bayas y las bellotas son justo lo que da XP al oficio
        //    AGRICULTOR, y la madera y la piedra lo del MINERO -- venderlas
        //    competiria con los oficios que acabamos de construir.
        //
        //    Se comprueba aqui porque es la clase de regla que se cae sola: el
        //    catalogo se genera, pero alguien puede editarlo a mano un martes.
        boolean soloCobblemon = true;
        boolean iconosCobblemon = true;
        for (var c : catalog.categories()) {
            if (!net.minecraft.registry.Registries.ITEM.getId(c.icon())
                    .getNamespace().equals("cobblemon")) {
                iconosCobblemon = false;
            }
            for (var e : c.entries()) {
                if (!net.minecraft.registry.Registries.ITEM.getId(e.item())
                        .getNamespace().equals("cobblemon")) {
                    soloCobblemon = false;
                }
            }
        }
        check("todo articulo de la tienda es de Cobblemon", soloCobblemon);
        check("todo icono de categoria es de Cobblemon", iconosCobblemon);

        // ⚠ NINGUN ARTICULO EN DOS CATEGORIAS. El servidor busca por
        //   (categoria, objeto), asi que dos precios para el mismo objeto se
        //   veria como "el precio cambia segun por donde entres".
        java.util.Set<String> articulos = new java.util.HashSet<>();
        boolean sinRepetidos = true;
        for (var c : catalog.categories()) {
            for (var e : c.entries()) {
                if (!articulos.add(
                        net.minecraft.registry.Registries.ITEM.getId(e.item()).toString())) {
                    sinRepetidos = false;
                }
            }
        }
        check("ningun articulo esta en dos categorias", sinRepetidos);

        check("ningun identificador de categoria se repite", idsUnicos);
        check("toda categoria tiene al menos un articulo", todasConArticulos);
        check("todo icono de categoria es un objeto real", iconosReales);
        check("todo nombre de categoria queda legible sin sus codigos de color",
                nombresConColor);

        // ⚠ LAS CATEGORIAS TIENEN QUE CABER EN EL PANEL. Van en una lista
        //   vertical de tarjetas de 86+8 px que empieza en 156, y debajo van el
        //   separador y el saldo (~110). El panel acaba en 762.
        //
        //   Hoy son 5 y sobran 26 px. La SEXTA no cabria, y el sintoma no seria
        //   un error: seria una categoria dibujada fuera del marco -- invisible,
        //   e imposible de pulsar. Que se entere aqui y no un jugador.
        int alto = 156 + catalog.categories().size() * (86 + 8) + 110;
        check("las categorias caben en el panel (" + alto + " de 762)", alto <= 762);

        // ⚠ Y LOS ARTICULOS TIENEN QUE CABER EN SU PAGINA. La pantalla pagina
        //   sola, asi que esto no puede desbordar -- pero si una categoria
        //   creciera hasta necesitar mas de 9 paginas, las flechas seguirian
        //   funcionando y nadie llegaria nunca al final por pereza.
        int mayor = 0;
        for (var c : catalog.categories()) {
            mayor = Math.max(mayor, c.entries().size());
        }
        int porPagina = (494 - 2 * 14 - 58) / (62 + 6);
        check("ninguna categoria pasa de 3 paginas (" + mayor + " articulos, "
                + porPagina + " por pagina)", mayor <= porPagina * 3);

        // ⚠ EL DESBORDE DEL PRECIO. La pantalla multiplica precio x cantidad y
        //   la cantidad la elige el CLIENTE. El servidor la acota a 64 antes de
        //   multiplicar; esto comprueba que con ese tope el producto sigue siendo
        //   un numero, y no un long que da la vuelta y se convierte en un INGRESO.
        boolean sinDesborde = true;
        for (var c : catalog.categories()) {
            for (var e : c.entries()) {
                if (e.buy() > Long.MAX_VALUE / 64) sinDesborde = false;
            }
        }
        check("precio x 64 no desborda el long", sinDesborde);
    }

    /**
     * El impuesto del GTS debe ser progresivo <b>por tramos</b>.
     *
     * <p>El fallo clásico es aplicar el porcentaje del tramo alto a todo el
     * importe: entonces vender por 10 001 deja menos neto que vender por
     * 10 000, y los jugadores encuentran ese escalón en horas. Aquí se
     * comprueba que el neto <b>nunca decrece</b>.
     */
    private void testGtsTax() {
        var gts = net.pokereport.luna.gts.GtsService.class;

        long[] prices = {1, 100, 9_999, 10_000, 10_001, 99_999, 100_000, 100_001,
                         999_999, 1_000_000, 1_000_001, 50_000_000};
        long previousNet = -1;
        boolean monotonic = true;
        boolean taxBelowPrice = true;

        for (long price : prices) {
            long tax = net.pokereport.luna.gts.GtsService.taxFor(price);
            long net = price - tax;
            if (net < previousNet) monotonic = false;
            if (tax >= price) taxBelowPrice = false;
            previousNet = net;
        }

        check("el neto del GTS nunca decrece al subir el precio", monotonic);
        check("el impuesto nunca se come el precio entero", taxBelowPrice);
        check("un precio pequeño paga el tramo bajo",
              net.pokereport.luna.gts.GtsService.taxFor(1_000) == 50);
        check("la tasa de publicación es el 1 %",
              net.pokereport.luna.gts.GtsService.listingFee(100_000) == 1_000);
        check("la tasa mínima es 1, nunca 0",
              net.pokereport.luna.gts.GtsService.listingFee(1) >= 1);
    }

    /**
     * Ciclo completo: publicar, comprar y comprobar que <b>no se puede comprar
     * dos veces</b>. Es el invariante que impide duplicar objetos.
     */
    private void testGtsFlow(long seller, long buyer) throws Exception {
        var gts = LunaEternal.gts();
        var economy = LunaEternal.economy();

        economy.credit(seller, Currency.POKEDOLLAR, 100_000, "autotest", key());
        economy.credit(buyer,  Currency.POKEDOLLAR, 100_000, "autotest", key());

        long sellerBefore = economy.balance(seller, Currency.POKEDOLLAR);
        long buyerBefore  = economy.balance(buyer,  Currency.POKEDOLLAR);

        byte[] payload = "objeto-de-prueba".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        long price = 10_000;

        var published = gts.publish(seller, payload, "Prueba",
                                    "minecraft:stone", 1, price);
        check("se puede publicar", published.ok());

        long fee = net.pokereport.luna.gts.GtsService.listingFee(price);
        check("publicar cobra la tasa por adelantado",
              economy.balance(seller, Currency.POKEDOLLAR) == sellerBefore - fee);

        var listings = gts.mine(seller);
        check("el listado aparece como propio", !listings.isEmpty());
        if (listings.isEmpty()) return;
        long listingId = listings.get(0).id();

        check("no se puede comprar el listado propio",
              !gts.buy(seller, listingId).ok());

        var bought = gts.buy(buyer, listingId);
        check("otro jugador sí puede comprarlo", bought.ok());
        check("el comprador recibe el objeto en custodia",
              bought.payload() != null && bought.payload().length == payload.length);

        long tax = net.pokereport.luna.gts.GtsService.taxFor(price);
        check("el comprador paga el precio completo",
              economy.balance(buyer, Currency.POKEDOLLAR) == buyerBefore - price);
        check("el vendedor recibe el precio menos el impuesto",
              economy.balance(seller, Currency.POKEDOLLAR)
                  == sellerBefore - fee + (price - tax));

        // EL invariante que impide duplicar.
        check("NO se puede comprar dos veces el mismo listado",
              !gts.buy(buyer, listingId).ok());

        // ---- entrega diferida: lo comprado queda reclamable -----------------
        var pendientesComprador = gts.pendingClaims(buyer);
        check("lo comprado queda pendiente de entrega",
              pendientesComprador.stream().anyMatch(c -> c.listingId() == listingId));
        check("el vendedor NO puede reclamar lo que vendió",
              gts.pendingClaims(seller).stream().noneMatch(c -> c.listingId() == listingId));

        gts.markDelivered(listingId);
        check("tras entregar deja de estar pendiente",
              gts.pendingClaims(buyer).stream().noneMatch(c -> c.listingId() == listingId));
        // Marcar dos veces no debe resucitar ni duplicar nada.
        gts.markDelivered(listingId);
        check("marcar entregado dos veces es inofensivo",
              gts.pendingClaims(buyer).stream().noneMatch(c -> c.listingId() == listingId));

        // ---- cancelar devuelve al vendedor ---------------------------------
        var otra = gts.publish(seller, payload, "Prueba2", "minecraft:stone", 1, 5_000);
        check("se puede publicar una segunda vez", otra.ok());
        var mios = gts.mine(seller);
        if (!mios.isEmpty()) {
            long id2 = mios.get(0).id();
            check("cancelar funciona", gts.cancel(seller, id2).ok());
            check("lo cancelado queda pendiente para el VENDEDOR",
                  gts.pendingClaims(seller).stream().anyMatch(c -> c.listingId() == id2));
            gts.markDelivered(id2);
        }
    }

    /**
     * EL ESCAPARATE DE OBJETOS, de punta a punta.
     *
     * <h2>⚠⚠ Lo que de verdad se comprueba aquí es EL PAYLOAD</h2>
     *
     * {@code publicarObjeto} escribe {@code identificador + separador +
     * cantidad} en un {@code byte[]}, y quien entrega lo vuelve a leer. Son dos
     * sitios distintos con su propia idea del formato, y si dejaran de estar de
     * acuerdo <b>la compra no daría ningún error</b>: el dinero cambiaría de
     * manos y los objetos no aparecerían. Es el único fallo posible aquí que se
     * come mercancía en silencio.
     *
     * <p>El resto —tasa, impuesto, no comprarte a ti mismo, no comprar dos
     * veces— ya lo cubre {@code testGtsFlow}, porque es el mismo código.
     */
    private void testEscaparate(long vendedor, long comprador) throws Exception {
        var gts = LunaEternal.gts();
        var economy = LunaEternal.economy();

        economy.credit(vendedor, Currency.POKEDOLLAR, 100_000, "autotest", key());
        economy.credit(comprador, Currency.POKEDOLLAR, 100_000, "autotest", key());
        long antes = economy.balance(vendedor, Currency.POKEDOLLAR);

        final String item = "minecraft:cobblestone";
        final int cantidad = 37;
        final long precio = 8_000;

        var pub = gts.publicarObjeto(vendedor, item, "Roca", cantidad, precio, 48);
        check("se puede publicar un objeto", pub.ok());

        long tasa = net.pokereport.luna.gts.GtsService.listingFee(precio);
        check("publicar un objeto cobra la tasa",
              economy.balance(vendedor, Currency.POKEDOLLAR) == antes - tasa);

        var mias = gts.misObjetos(vendedor);
        check("la oferta sale entre las mías", !mias.isEmpty());
        if (mias.isEmpty()) {
            return;
        }
        var oferta = mias.get(0);
        check("la cantidad publicada es la que se pidió", oferta.cantidad() == cantidad);
        check("el precio publicado es el que se pidió", oferta.precio() == precio);
        check("el objeto publicado es el que se pidió", item.equals(oferta.itemId()));

        check("duenoDe dice quién publicó",
              Long.valueOf(vendedor).equals(gts.duenoDe(oferta.id())));
        check("duenoDe de una oferta que no existe es nulo",
              gts.duenoDe(-1) == null);

        // ---- el buscador ---------------------------------------------------
        check("el buscador la encuentra por nombre",
              gts.buscarObjetos("Roca", "NUEVO", 50).stream()
                 .anyMatch(o -> o.id() == oferta.id()));
        check("el buscador la encuentra por identificador",
              gts.buscarObjetos("cobblestone", "NUEVO", 50).stream()
                 .anyMatch(o -> o.id() == oferta.id()));
        check("el buscador NO devuelve lo que no casa",
              gts.buscarObjetos("zzzzzznoexiste", "NUEVO", 50).isEmpty());

        // ⚠ El ORDER BY sale de una enum nuestra: un orden inventado no puede
        //   colarse en el SQL. Si esto reventara, sería inyección.
        check("un orden inventado no rompe la consulta",
              gts.buscarObjetos("", "'; DROP TABLE player; --", 5) != null);

        // ---- lo que más importa: el payload vuelve entero ------------------
        var comprada = gts.buy(comprador, oferta.id());
        check("otro jugador puede comprar la oferta", comprada.ok());
        check("la compra devuelve el payload", comprada.payload() != null);
        if (comprada.payload() != null) {
            String s = new String(comprada.payload(),
                    java.nio.charset.StandardCharsets.UTF_8);
            int corte = s.indexOf((char) 0);
            check("el payload lleva el separador que espera la entrega", corte > 0);
            if (corte > 0) {
                check("el payload devuelve EL MISMO objeto",
                      item.equals(s.substring(0, corte)));
                check("el payload devuelve LA MISMA cantidad",
                      Integer.parseInt(s.substring(corte + 1).trim()) == cantidad);
            }
        }
        check("una oferta comprada ya no sale en el escaparate",
              gts.buscarObjetos("Roca", "NUEVO", 50).stream()
                 .noneMatch(o -> o.id() == oferta.id()));
        // ⚠⚠ HAY QUE MARCARLA ENTREGADA, Y OLVIDARLO NO ES INOCENTE. Comprar
        //   deja una reclamación pendiente, y una reclamación de prueba lleva
        //   un payload que NO es un objeto: al conectarse cualquiera,
        //   `GtsDelivery` intenta leerla, no puede, y lo escribe en el log.
        //   Como no se marca entregada, VUELVE A INTENTARLO EN CADA LOGIN —
        //   para siempre. Dos filas de basura por ejecución del autotest.
        gts.markDelivered(oferta.id());

        // ---- retirar devuelve la mercancía ---------------------------------
        var otra = gts.publicarObjeto(vendedor, item, "Roca2", 5, 1_000, 24);
        check("se puede publicar una segunda vez", otra.ok());
        var mias2 = gts.misObjetos(vendedor);
        if (!mias2.isEmpty()) {
            long id2 = mias2.get(0).id();
            var cancelada = gts.cancel(vendedor, id2);
            check("retirar una oferta propia funciona", cancelada.ok());
            check("retirar devuelve la mercancía", cancelada.payload() != null);
            check("nadie puede retirar la oferta de otro",
                  !gts.cancel(comprador, id2).ok());
            check("una oferta retirada ya no sale entre las mías",
                  gts.misObjetos(vendedor).stream().noneMatch(o -> o.id() == id2));
            gts.markDelivered(id2);
        }

        // ---- lo que NO se puede hacer --------------------------------------
        check("no se puede publicar a precio cero",
              !gts.publicarObjeto(vendedor, item, "Roca", 1, 0, 24).ok());
        check("no se puede publicar a precio negativo",
              !gts.publicarObjeto(vendedor, item, "Roca", 1, -500, 24).ok());
        check("no se puede publicar cantidad cero",
              !gts.publicarObjeto(vendedor, item, "Roca", 0, 100, 24).ok());
    }

    /**
     * EL PROTOCOLO CONTRA LOS NULOS.
     *
     * <h2>⚠⚠⚠ ESTA ES LA COMPROBACIÓN QUE HABRÍA EVITADO UNA DESCONEXIÓN</h2>
     *
     * El 2026-08-25 un Pokémon publicado <b>sin mote</b> dejaba {@code mote} a
     * nulo, y {@code writeString(null)} lanzaba <b>al codificar el paquete</b>:
     * <i>«Failed to encode packet 'clientbound/custom_payload'»</i> y al jugador
     * se le cortaba la conexión al abrir el GTS.
     *
     * <p>Lo que hace que este fallo merezca su propia prueba es <b>dónde</b>
     * revienta. No es un hueco en una pantalla que se vea y se arregle: pasa
     * fuera del hilo del servidor, echa a quien lo provoque, y el mensaje
     * <b>no dice qué campo</b>. Y podía provocarlo cualquiera de los campos de
     * texto del protocolo.
     *
     * <p>Así que aquí <b>se codifica de verdad</b>, con todos los campos a nulo.
     * Si alguien añade un campo y se salta {@code Red.cad()}, esto revienta
     * <b>en consola y no en la cara de un jugador</b>.
     *
     * <p>⚠ Y se comprueba que al decodificar salen <b>cadenas vacías</b>: no
     * basta con no lanzar, porque escribir basura también «no lanza».
     */
    private void testProtocoloNulos() {
        if (registros == null) {
            check("los registros están disponibles para probar el protocolo", false);
            return;
        }
        var ceros = java.util.List.of(0, 0, 0, 0, 0, 0);

        // Un ejemplar con TODAS las cadenas a nulo. Es el peor caso posible y
        // es exactamente lo que llegó a producción con `mote`.
        var ejemplar = new net.pokereport.luna.net.Red.EjemplarGts(
                1L, null, null, null, 5, false, null, null, null, null, null,
                ceros, ceros, 100L, 100L, System.currentTimeMillis());
        var mio = new net.pokereport.luna.net.Red.MioGts(
                null, null, null, 5, false, null, ceros, ceros, null, null, 0L);
        var oferta = new net.pokereport.luna.net.Red.OfertaObj(
                1L, null, null, null, 1, 100L, System.currentTimeMillis());
        var mioObj = new net.pokereport.luna.net.Red.MioObj(null, null, 1);

        var gts = new net.pokereport.luna.net.Red.EstadoGts(
                java.util.List.of(ejemplar), java.util.List.of(ejemplar),
                java.util.List.of(mio), 0L);
        var mercado = new net.pokereport.luna.net.Red.EstadoMercado(
                java.util.List.of(oferta), java.util.List.of(oferta),
                java.util.List.of(mioObj), 0L);

        net.pokereport.luna.net.Red.EstadoGts vueltaGts = null;
        try {
            var buf = new net.minecraft.network.RegistryByteBuf(
                    io.netty.buffer.Unpooled.buffer(), registros);
            net.pokereport.luna.net.Red.EstadoGts.CODEC.encode(buf, gts);
            check("un EstadoGts con TODO a nulo se codifica sin lanzar", true);
            vueltaGts = net.pokereport.luna.net.Red.EstadoGts.CODEC.decode(buf);
        } catch (Exception e) {
            fail("un EstadoGts con TODO a nulo se codifica sin lanzar",
                 e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        if (vueltaGts != null && !vueltaGts.ofertas().isEmpty()) {
            var e = vueltaGts.ofertas().get(0);
            check("un nulo llega al cliente como cadena vacía, no como basura",
                  "".equals(e.mote()) && "".equals(e.vendedor())
                  && "".equals(e.especie()) && "".equals(e.rareza()));
            check("los campos que NO son texto sobreviven al viaje",
                  e.id() == 1L && e.nivel() == 5 && e.precio() == 100L);
        }

        try {
            var buf = new net.minecraft.network.RegistryByteBuf(
                    io.netty.buffer.Unpooled.buffer(), registros);
            net.pokereport.luna.net.Red.EstadoMercado.CODEC.encode(buf, mercado);
            var vuelta = net.pokereport.luna.net.Red.EstadoMercado.CODEC.decode(buf);
            check("un EstadoMercado con TODO a nulo se codifica sin lanzar", true);
            check("la oferta vuelve con cadenas vacías",
                  !vuelta.ofertas().isEmpty()
                  && "".equals(vuelta.ofertas().get(0).nombre()));
        } catch (Exception e) {
            fail("un EstadoMercado con TODO a nulo se codifica sin lanzar",
                 e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * CAZAS: las estrellas y los premios.
     *
     * <p>⚠ NO repite lo que ya comprueba {@link #testCazas}: allí están el
     * ciclo, el progreso y el no-cobrar-dos-veces. Aquí está lo que entró con
     * la pantalla — las estrellas y los premios en objetos.
     *
     * <h2>⚠⚠⚠ LO QUE MÁS IMPORTA: QUE LOS PREMIOS EXISTAN</h2>
     *
     * Un identificador de objeto mal escrito <b>no da ningún error</b>. Se
     * guarda en la fila, la pantalla lo enseña, el jugador se pasa un día
     * capturando, pulsa COBRAR... y no recibe nada. Es el fallo de los 62
     * cosméticos que no existían, con la diferencia de que aquí el jugador
     * <b>ya ha hecho el trabajo</b>.
     *
     * <p>Se comprueban contra el <b>registro del juego</b>. Contra una lista
     * nuestra prometerían lo mismo que el código y no comprobarían nada.
     */
    private void testCazasPremios(long jugador) throws Exception {
        var svc = LunaEternal.hunts();
        if (svc == null) {
            return;
        }
        var ciclo = svc.cicloActual(jugador);
        if (ciclo == null) {
            return;
        }

        // ⚠ 24 h, no 12. Si alguien lo baja sin querer, la caza cambia dos
        //   veces al día y media gente no ve la mitad de los ciclos.
        long faltan = ciclo.terminaEn() * 1000L - System.currentTimeMillis();
        check("el ciclo dura como mucho 24 h", faltan <= 24 * 3600_000L + 60_000L);

        var capturas = new ArrayList<net.pokereport.luna.hunt.HuntService.Objetivo>();
        var crianzas = new ArrayList<net.pokereport.luna.hunt.HuntService.Objetivo>();
        for (var o : ciclo.objetivos()) {
            (o.tipo() == net.pokereport.luna.hunt.HuntService.Tipo.CAPTURA
                    ? capturas : crianzas).add(o);
        }
        check("hay 3 objetivos de captura", capturas.size() == 3);
        check("hay 3 objetivos de crianza", crianzas.size() == 3);

        // ⚠ UNA DE CADA ESTRELLA, y no al azar. Un ciclo de tres difíciles no
        //   lo completaría nadie, y uno de tres fáciles no valdría nada.
        for (var grupo : java.util.List.of(capturas, crianzas)) {
            var vistas = new java.util.HashSet<Integer>();
            for (var o : grupo) {
                vistas.add(o.rareza());
            }
            check("las estrellas son 1, 2 y 3 (una de cada)",
                  vistas.equals(java.util.Set.of(1, 2, 3)));
        }

        boolean todosExisten = true, todosPagan = true;
        for (var o : ciclo.objetivos()) {
            // ⚠ LOS DOS. Comprobar solo el primero dejaria el segundo sin
            //   red -- y el segundo es el que se añadio despues, o sea el que
            //   mas probabilidades tiene de estar mal escrito.
            for (var par : java.util.List.of(
                    java.util.Map.entry(o.premioObjeto(), o.premioCantidad()),
                    java.util.Map.entry(o.premioObjeto2(), o.premioCantidad2()))) {
                if (par.getKey().isBlank()) {
                    continue;
                }
                var id = net.minecraft.util.Identifier.tryParse(par.getKey());
                var item = id == null ? null
                        : net.minecraft.registry.Registries.ITEM.get(id);
                if (item == null || item == net.minecraft.item.Items.AIR
                        || par.getValue() <= 0) {
                    todosExisten = false;
                    LunaEternal.LOG.error("Premio de caza inexistente: {} x{}",
                            par.getKey(), par.getValue());
                }
            }
            // ⚠ Un premio con SOLO el segundo objeto es un hueco en la tabla:
            //   el primero es el que se dibuja arriba, asi que se veria un
            //   premio empezando por el segundo renglon.
            if (o.premioObjeto().isBlank() && !o.premioObjeto2().isBlank()) {
                todosExisten = false;
                LunaEternal.LOG.error("Premio con el primer objeto vacio: {}",
                        o.especie());
            }
            if (o.premioDolar() <= 0 && o.premioMarca() <= 0) {
                todosPagan = false;
            }
        }
        check("todos los premios en objeto EXISTEN en el juego", todosExisten);
        check("todo objetivo paga algo", todosPagan);

        // ⚠ Un objetivo MÁS RARO no puede pagar MENOS. Es la clase de cosa que
        //   se rompe editando la tabla y que nadie mira hasta que un jugador se
        //   queja de que la ★ paga más que la ★★★.
        for (var grupo : java.util.List.of(capturas, crianzas)) {
            grupo.sort(java.util.Comparator.comparingInt(
                    net.pokereport.luna.hunt.HuntService.Objetivo::rareza));
            for (int i = 1; i < grupo.size(); i++) {
                check("una estrella más paga más Plata",
                      grupo.get(i).premioDolar() > grupo.get(i - 1).premioDolar());
                check("una estrella más paga más Marcas",
                      grupo.get(i).premioMarca() > grupo.get(i - 1).premioMarca());
            }
        }

        // ⚠⚠ LA ESTRELLA TIENE QUE COINCIDIR CON EL POKEMON. Es lo que el
        //    usuario pidio --«que tenga sentido»-- y es lo unico que impide
        //    volver al estado anterior, donde la estrella era la POSICION y un
        //    Caterpie podia salir de ★★★ pagando 2.500.
        //    ⚠ Solo se comprueba en los ciclos NUEVOS (rareza guardada): los
        //      anteriores a V019 derivan de la posicion a proposito.
        boolean coherentes = true;
        for (var o : ciclo.objetivos()) {
            int suya = net.pokereport.luna.hunt.Especies.rareza(o.especie());
            if (suya > 0 && suya != o.rareza()) {
                coherentes = false;
                LunaEternal.LOG.error(
                    "Caza incoherente: {} es rareza {} y sale con {} estrellas",
                    o.especie(), suya, o.rareza());
            }
        }
        check("las estrellas coinciden con la rareza del Pokemon", coherentes);

        // ⚠⚠ UNO DE CADA, SIEMPRE (decision del usuario). Es lo que hace que LA
        //    ESTRELLA SEA LO UNICO QUE VARIA: la dificultad la pone entero el
        //    Pokemon. Con cantidades distintas, un ★ de tres capturas podia
        //    costar mas que un ★★★ de una y la estrella dejaba de predecir nada.
        boolean unoDeCada = true;
        for (var o : ciclo.objetivos()) {
            if (o.necesarios() != 1) {
                unoDeCada = false;
            }
        }
        check("cada objetivo se completa con UNO", unoDeCada);

        // ⚠ Solo Kanto y Johto: una especie de fuera sería una caza imposible.
        //   Se pregunta al registro, que es de donde salen.
        boolean enRango = true;
        for (var o : ciclo.objetivos()) {
            // ⚠⚠⚠ `getByName` LANZA, no devuelve null, si la cadena no vale
            //    como ruta de Identifier --construye uno por dentro--. Una caza
            //    de «mr. mime» reventaba AQUI y se llevaba por delante las 300
            //    comprobaciones siguientes: «1 FALLOS de 171» con el fallo real
            //    a trescientas de distancia.
            //    ⚠ Un autotest que aborta no informa de una cosa: ESCONDE todo
            //      lo que venia detras.
            var esp = net.pokereport.luna.pokedex.ClaveEspecie.buscar(o.especie());
            if (esp == null || esp.getNationalPokedexNumber() > 251) {
                enRango = false;
                LunaEternal.LOG.error("Caza fuera de Kanto/Johto: {}", o.especie());
            }
        }
        check("todas las especies son de Kanto o Johto", enRango);

        // ⚠⚠⚠ EL CATALOGO ENTERO, NO EL SORTEO DE HOY. Las tres comprobaciones
        //    de arriba miran los objetivos del ciclo vigente, o sea SEIS
        //    especies de las 251 sorteadas al azar. Con eso, una especie cuyo
        //    identificador no resuelve tiene DOS TERCIOS DE PROBABILIDAD DE NO
        //    SALIR en una ejecucion cualquiera -- y cuando por fin sale, no da
        //    un fallo: LANZA, y se lleva por delante las 300 comprobaciones que
        //    venian detras.
        //
        //    Paso de verdad el 2026-09-01: «1 FALLOS de 171» con el fallo real
        //    a trescientas de distancia, porque al ciclo le toco «mr. mime».
        //
        //    ⚠⚠ Y lo que estaba mal no era el sorteo: era que las cazas
        //       guardaban `getName().toLowerCase()` --el NOMBRE VISIBLE-- como
        //       si fuera un identificador. Acierta con 247 de 251, que es
        //       justo bastante para no enterarse nunca.
        //
        //    Esto recorre las 251 y no depende de la suerte.
        int rotas = 0;
        String primera = null;
        for (var e : net.pokereport.luna.hunt.Especies.disponibles()) {
            if (net.pokereport.luna.pokedex.ClaveEspecie.buscar(e.nombre()) == null) {
                rotas++;
                if (primera == null) primera = e.nombre();
                LunaEternal.LOG.error("Caza imposible: la especie '{}' no"
                        + " resuelve en el registro de Cobblemon", e.nombre());
            }
        }
        check("las " + net.pokereport.luna.hunt.Especies.disponibles().size()
              + " especies cazables resuelven"
              + (primera == null ? "" : " (falla '" + primera + "' y " + (rotas - 1) + " mas)"),
              rotas == 0);

        // ⚠ La entrega se recoge UNA vez. Si se pudiera leer dos, un cobro
        //   entregaría el objeto dos veces.
        // ⚠⚠ `== null` COMPILABA Y COMPROBABA LO CONTRARIO. Al pasar la entrega
        //    de un valor suelto a una lista, esto siguió compilando --una lista
        //    comparada con null es Java valido-- pero paso de «no hay nada» a
        //    «siempre falso». Un cambio de tipo que ROMPE una prueba sin que el
        //    compilador diga nada.
        check("no hay entrega pendiente si no se ha cobrado nada",
              svc.entregaPendiente().isEmpty());

        check("no se cobra un objetivo que no existe",
              svc.cobrar(jugador, -1, java.util.UUID.randomUUID())
                 == net.pokereport.luna.hunt.HuntService.Resultado.CADUCADO);
    }

    /**
     * LOS RANGOS.
     *
     * <p>⚠⚠ Lo que de verdad se comprueba es que <b>un nombre desconocido no
     * degrade a nadie en silencio</b>. {@code Rank.de} devuelve el más bajo
     * ante lo que no reconoce —que es lo correcto para leer una fila vieja—,
     * pero por eso mismo un error de tecleo en el comando bajaría a un LEYENDA
     * a ENTRENADOR sin decir nada. El comando lo comprueba aparte, y esto vigila
     * que la pieza de abajo siga comportándose como se espera.
     */
    private void testRangos(long jugador) throws Exception {
        var svc = LunaEternal.ranks();
        if (svc == null) {
            check("el servicio de rangos está vivo", false);
            return;
        }
        var R = net.pokereport.luna.ui.Tablist.Rank.class;

        check("hay 5 rangos de jugador",
              net.pokereport.luna.ui.Tablist.Rank.deJugador().size() == 5);
        check("el rango por defecto es el más bajo",
              net.pokereport.luna.ui.Tablist.Rank.porDefecto()
                  == net.pokereport.luna.ui.Tablist.Rank.ENTRENADOR);

        // ⚠ Los escalones son 1..5 SIN HUECOS Y SIN REPETIRSE. Un hueco o un
        //   empate haría que dos rangos desbloquearan lo mismo, y entonces
        //   subir de rango no daría nada -- que es peor que no subir.
        var vistos = new java.util.HashSet<Integer>();
        boolean bien = true;
        for (var r : net.pokereport.luna.ui.Tablist.Rank.deJugador()) {
            if (r.escalon < 1 || r.escalon > 5 || !vistos.add(r.escalon)) {
                bien = false;
            }
        }
        check("los escalones de jugador son 1..5, sin huecos ni repetidos",
              bien && vistos.size() == 5);

        // ⚠ Los de equipo van a -1: NO son «más altos» que LEYENDA, son de otra
        //   clase. Con un escalón positivo, dar OP a alguien para mirar una cosa
        //   le regalaría todo lo que desbloquea el rango más alto.
        boolean equipoAparte = true;
        for (var r : R.getEnumConstants()) {
            if (r.equipo && r.escalon != -1) {
                equipoAparte = false;
            }
        }
        check("los rangos de equipo no tienen escalón de progresión", equipoAparte);

        check("un rango desconocido cae al más bajo",
              net.pokereport.luna.ui.Tablist.Rank.de("NO_EXISTE")
                  == net.pokereport.luna.ui.Tablist.Rank.porDefecto());
        check("el nombre se reconoce sin importar mayúsculas",
              net.pokereport.luna.ui.Tablist.Rank.de("leyenda")
                  == net.pokereport.luna.ui.Tablist.Rank.LEYENDA);

        // ⚠⚠ NO SE PUEDE CONCEDER UN RANGO DE EQUIPO. Si se pudiera, cualquiera
        //    con el comando se fabricaría un administrador.
        check("no se puede conceder ADMIN",
              svc.cambiar(jugador, null, net.pokereport.luna.ui.Tablist.Rank.ADMIN) == null);
        check("no se puede conceder MODERADOR",
              svc.cambiar(jugador, null,
                  net.pokereport.luna.ui.Tablist.Rank.MODERADOR) == null);

        // ---- cambiar y volver ----------------------------------------------
        var puesto = svc.cambiar(jugador, null,
                net.pokereport.luna.ui.Tablist.Rank.CAMPEON);
        check("se puede conceder un rango de jugador",
              puesto == net.pokereport.luna.ui.Tablist.Rank.CAMPEON);
        var reparto = svc.reparto();
        check("el reparto cuenta a alguien en CAMPEON",
              reparto.getOrDefault(net.pokereport.luna.ui.Tablist.Rank.CAMPEON, 0) >= 1);
        svc.cambiar(jugador, null, net.pokereport.luna.ui.Tablist.Rank.ENTRENADOR);
    }

    /**
     * LA MOCHILA.
     *
     * <h2>⚠⚠⚠ AQUI LO QUE SE PIERDE SON OBJETOS DEL JUGADOR</h2>
     *
     * En la economia un fallo se ve en un numero y se puede reponer. Aqui un
     * fallo <b>borra la mochila de alguien</b>, y no hay libro de asientos que
     * la reconstruya. Por eso lo que se prueba es el viaje completo: guardar,
     * leer, y que salga lo mismo.
     */
    private void testMochila(long jugador) throws Exception {
        var svc = LunaEternal.backpacks();
        if (svc == null) {
            check("el servicio de mochila está vivo", false);
            return;
        }
        var M = net.pokereport.luna.backpack.Mochila.class;

        // ---- las filas por rango --------------------------------------------
        check("ENTRENADOR abre 1 fila",
              net.pokereport.luna.backpack.Mochila.filasDe(
                  net.pokereport.luna.ui.Tablist.Rank.ENTRENADOR) == 1);
        check("LEYENDA abre todas",
              net.pokereport.luna.backpack.Mochila.filasDe(
                  net.pokereport.luna.ui.Tablist.Rank.LEYENDA)
                  == net.pokereport.luna.backpack.Mochila.FILAS_MAX);

        // ⚠ CADA RANGO ABRE MAS QUE EL ANTERIOR. Si dos abrieran lo mismo,
        //   subir de rango no daria nada -- que es peor que no subir.
        var rangos = net.pokereport.luna.ui.Tablist.Rank.deJugador().reversed();
        int previo = 0;
        boolean crece = true;
        for (var r : rangos) {
            int f = net.pokereport.luna.backpack.Mochila.filasDe(r);
            if (f <= previo) {
                crece = false;
            }
            previo = f;
        }
        check("cada rango abre MAS filas que el anterior", crece);

        // ⚠⚠ EL CARTEL NO PUEDE MENTIR: el rango que dice que hace falta para
        //    una fila tiene que abrirla de verdad. Se rompe en cuanto alguien
        //    toque la tabla de filas y no la del cartel -- por eso el cartel se
        //    calcula de la misma tabla, y esto lo vigila.
        boolean carteles = true;
        for (int fila = 0; fila < net.pokereport.luna.backpack.Mochila.FILAS_MAX; fila++) {
            var pide = net.pokereport.luna.backpack.Mochila.rangoParaFila(fila);
            if (net.pokereport.luna.backpack.Mochila.filasDe(pide) <= fila) {
                carteles = false;
                LunaEternal.LOG.error("La fila {} dice pedir {} y ese rango no la abre",
                        fila, pide);
            }
        }
        check("el rango que pide cada fila la abre de verdad", carteles);

        // ⚠ Un hueco fuera de las filas abiertas esta CERRADO, mire quien mire.
        check("con 1 fila, el hueco 0 esta abierto",
              net.pokereport.luna.backpack.Mochila.abierto(0, 1));
        check("con 1 fila, el hueco 9 esta cerrado",
              !net.pokereport.luna.backpack.Mochila.abierto(9, 1));
        check("un hueco negativo esta cerrado",
              !net.pokereport.luna.backpack.Mochila.abierto(-1, 5));
        check("un hueco fuera del maximo esta cerrado",
              !net.pokereport.luna.backpack.Mochila.abierto(
                  net.pokereport.luna.backpack.Mochila.HUECOS, 9));

        // ---- guardar y leer, de punta a punta -------------------------------
        var registros = net.minecraft.registry.RegistryWrapper.WrapperLookup.of(
                java.util.stream.Stream.of());
        try {
            var inv = new net.minecraft.inventory.SimpleInventory(
                    net.pokereport.luna.backpack.Mochila.HUECOS);
            var pila = new net.minecraft.item.ItemStack(
                    net.minecraft.item.Items.DIAMOND, 17);
            inv.setStack(0, pila);
            inv.setStack(40, new net.minecraft.item.ItemStack(
                    net.minecraft.item.Items.STONE, 3));

            svc.guardar(jugador, inv, registros);
            var vuelta = svc.cargar(jugador, registros);

            check("lo guardado vuelve en su MISMO hueco",
                  vuelta.getStack(0).getItem() == net.minecraft.item.Items.DIAMOND
                  && vuelta.getStack(40).getItem() == net.minecraft.item.Items.STONE);
            check("y con la misma cantidad",
                  vuelta.getStack(0).getCount() == 17
                  && vuelta.getStack(40).getCount() == 3);
            check("los huecos vacíos siguen vacíos", vuelta.getStack(1).isEmpty());

            // ⚠⚠ CONTAR LO QUE QUEDARIA ATRAPADO al bajar de rango. Es lo que
            //    permite avisar en vez de confiscar en silencio.
            check("cuenta lo que quedaria por encima de 1 fila",
                  svc.atrapadosPorEncima(jugador, 1) == 1);
            check("con todas las filas no queda nada atrapado",
                  svc.atrapadosPorEncima(jugador,
                      net.pokereport.luna.backpack.Mochila.FILAS_MAX) == 0);

            // ⚠⚠⚠ GUARDAR UNA MOCHILA VACIA LA VACIA DE VERDAD. Si el borrado y
            //     la escritura no fueran una transaccion, esto es lo que se
            //     quedaria a medias -- y a medias significa PERDER objetos.
            svc.guardar(jugador, new net.minecraft.inventory.SimpleInventory(
                    net.pokereport.luna.backpack.Mochila.HUECOS), registros);
            check("guardar vacía deja la mochila vacía",
                  svc.atrapadosPorEncima(jugador, 0) == 0);
        } catch (Exception e) {
            fail("la mochila guarda y lee sin lanzar",
                 e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Pokedex: la primera captura de una especie debe distinguirse de las
     * siguientes, porque es la que da recompensa. Y la fecha y la luna de esa
     * primera NO pueden sobrescribirse: son la historia del registro.
     */
    private void testPokedex(long p) throws Exception {
        var dex = LunaEternal.pokedex();

        boolean primera = dex.recordCapture(p, "__test_mon", 9001, false, 5, 0);
        check("la primera captura se detecta como nueva", primera);

        boolean segunda = dex.recordCapture(p, "__test_mon", 9001, false, 30, 4);
        check("la segunda NO cuenta como nueva", !segunda);

        var resumen = dex.summary(p);
        check("el resumen cuenta una especie capturada", resumen.caught() == 1);
        check("todavia no hay variocolor", resumen.shiny() == 0);

        dex.recordCapture(p, "__test_mon", 9001, true, 10, 2);
        check("capturar un variocolor lo registra", dex.summary(p).shiny() == 1);

        var filas = dex.range(p, 9000, 9002);
        check("la entrada aparece en el rango", filas.size() == 1);
        if (!filas.isEmpty()) {
            var e = filas.get(0);
            check("cuenta las tres capturas", e.caughtCount() == 3);
            check("guarda el mejor nivel", e.bestLevel() != null && e.bestLevel() == 30);
            check("la luna de la PRIMERA captura no se sobrescribe",
                  e.firstMoonPhase() != null && e.firstMoonPhase() == 0);
        }
    }

    /**
     * Kits: el cooldown tiene que impedir de verdad la segunda reclamacion, y
     * el tope diario de valor tiene que estar por debajo del limite.
     */
    private void testKits(long p) throws Exception {
        var catalogo = LunaEternal.kits();
        var servicio = LunaEternal.kitService();

        check("el catalogo de kits esta cargado", catalogo != null);
        if (catalogo == null) return;
        check("hay kits definidos", !catalogo.kits().isEmpty());

        long inyeccionDiaria = catalogo.kits().stream()
            .mapToLong(net.pokereport.luna.kit.KitCatalog.Kit::dailyValue).sum();
        check("la inyeccion diaria de los kits esta bajo el tope",
              inyeccionDiaria <= catalogo.maxDailyValue());

        var unaVez = catalogo.kits().stream().filter(k -> k.once()).findFirst();
        if (unaVez.isPresent()) {
            var kit = unaVez.get();
            check("un kit de una sola vez se puede reclamar",
                  servicio.claim(p, kit));
            check("NO se puede reclamar dos veces",
                  !servicio.claim(p, kit));
            check("su estado dice que ya se reclamo",
                  !servicio.status(p, kit).claimable());
        }

        var periodico = catalogo.kits().stream()
            .filter(k -> !k.once() && k.requiredRank() == null).findFirst();
        if (periodico.isPresent()) {
            var kit = periodico.get();
            check("un kit periodico se reclama", servicio.claim(p, kit));
            check("el cooldown impide la segunda", !servicio.claim(p, kit));
            var st = servicio.status(p, kit);
            check("el estado dice cuanto queda",
                  !st.claimable() && st.nextAvailable() != null);
        }
    }

    /**
     * Misiones. Estos invariantes deberian haberse escrito ANTES de desplegar
     * el sistema: la regla MOD-006 lo dice y no la cumpli. Se anaden ahora.
     */
    private void testQuests(long p) throws Exception {
        var quests = LunaEternal.quests();
        check("el catalogo de misiones esta cargado", !quests.catalogo().isEmpty());

        var inicial = quests.byId("t1_inicial");
        var segunda = quests.byId("t2_captura");
        if (inicial == null || segunda == null) {
            fail("misiones del tutorial", "no estan en el catalogo");
            return;
        }

        // Una mision encadenada NO avanza mientras su requisito siga sin cobrar.
        quests.advance(p, net.pokereport.luna.quest.Quest.Objective.Type.CATCH, 5);
        check("una mision bloqueada por requisito NO avanza",
              quests.state(p, segunda).progress() == 0);

        // Completar y cobrar la primera desbloquea la segunda.
        quests.advance(p, net.pokereport.luna.quest.Quest.Objective.Type.STARTER, 1);
        var s1 = quests.state(p, inicial);
        check("la primera se completa al cumplir el objetivo", s1.completed());
        check("completada pero sin cobrar es cobrable", s1.claimable());

        check("se cobra una vez", quests.claim(p, inicial));
        check("NO se cobra dos veces", !quests.claim(p, inicial));
        check("tras cobrar deja de ser cobrable",
              !quests.state(p, inicial).claimable());

        // Ahora si avanza la encadenada.
        quests.advance(p, net.pokereport.luna.quest.Quest.Objective.Type.CATCH, 1);
        check("desbloqueada la anterior, la siguiente ya avanza",
              quests.state(p, segunda).progress() >= 1);

        // Los objetivos de FOTO no se acumulan: fijar 3 dos veces deja 3.
        var pokedex = quests.byId("t3_pokedex");
        if (pokedex != null) {
            quests.claim(p, segunda);
            quests.setProgress(p,
                net.pokereport.luna.quest.Quest.Objective.Type.POKEDEX, 3);
            quests.setProgress(p,
                net.pokereport.luna.quest.Quest.Objective.Type.POKEDEX, 3);
            check("un objetivo de foto NO se acumula al repetirse",
                  quests.state(p, pokedex).progress() == 3);
            // Y nunca retrocede aunque llegue un valor menor.
            quests.setProgress(p,
                net.pokereport.luna.quest.Quest.Objective.Type.POKEDEX, 1);
            check("un objetivo de foto nunca retrocede",
                  quests.state(p, pokedex).progress() == 3);
        }

        // Cobrar algo sin completar no debe pasar.
        var diaria = quests.byId("d_captura");
        if (diaria != null) {
            check("no se puede cobrar una mision sin completar",
                  !quests.claim(p, diaria));
        }
    }

    /**
     * Telemetria. El invariante que importa es el CUADRE GLOBAL: la suma de
     * todos los saldos tiene que ser igual a la suma de todos los asientos.
     *
     * <p>Si no lo es, hay dinero creado o perdido fuera del sistema — y eso es
     * lo unico que no se puede arreglar despues, porque no se sabe de donde
     * salio. Se comprueba con datos reales dentro, no con la base vacia.
     */
    private void testTelemetria(long a, long b) throws Exception {
        var stats = LunaEternal.stats();

        for (Currency c : Currency.values()) {
            check("la masa de " + c.displayName + " cuadra con el libro",
                  stats.supplyDiscrepancy(c) == 0);
        }

        var supply = stats.moneySupply();
        check("la masa monetaria se lee", supply.size() == Currency.values().length);

        // Con los jugadores de prueba dentro, tiene que haber flujos.
        var flujos = stats.flows(Currency.POKEDOLLAR, 24);
        check("hay flujos registrados en las ultimas 24 h", !flujos.isEmpty());

        boolean coherentes = flujos.stream()
            .allMatch(f -> f.created() >= 0 && f.destroyed() >= 0 && f.operations() > 0);
        check("todo flujo tiene importes no negativos y operaciones", coherentes);

        var reparto = stats.wealth(Currency.POKEDOLLAR);
        check("el reparto cuenta jugadores", reparto.players() >= 2);
        check("los percentiles estan ordenados",
              reparto.p50() <= reparto.p90() && reparto.p90() <= reparto.p99()
              && reparto.p99() <= reparto.max());

        check("hay jugadores activos", stats.activePlayers(24) >= 2);

        // El informe completo debe generarse sin lanzar.
        var lineas = new ArrayList<String>();
        net.pokereport.luna.command.EconomyReport.render(stats, 24, lineas::add);
        check("el informe se genera sin errores", lineas.size() > 10);
    }

    /**
     * La curación: el reloj y lo que la pantalla necesita para dibujarse.
     *
     * <p>⚠ NO se cura a nadie aquí. Curar necesita un jugador conectado y un
     * equipo de Cobblemon, y el autotest corre desde consola. Lo que sí se puede
     * comprobar —y es donde están los fallos que no se ven— es el <b>contrato</b>:
     * que el cooldown sea un número de segundos coherente, que el enum de estado
     * que viaja al cliente exista de verdad, y que la pantalla quepa.
     */
    /**
     * VIAJES: el moto taxi de la ciudadela.
     *
     * <p>Lo que se comprueba aquí no es que teletransporte —eso necesita un
     * jugador— sino <b>los números que dejan de cuadrar en silencio</b>, que es
     * la familia de fallos que ya nos costó la rejilla del PokePad y la quinta
     * fila del mercado.
     */
    /**
     * LOS TRAJES DE RANGO.
     *
     * <p>⚠ Lo que se vigila aqui es <b>la escalera</b>: que subir de rango
     * siempre abra mas y nunca menos, y que un traje sin arte no se pueda poner
     * pase lo que pase. Los dos se rompen editando una linea del enum, y ninguno
     * da error.
     */
    /**
     * LOS GIMNASIOS Y SUS RANURAS.
     *
     * <p>⚠ Lo que se vigila es lo que <b>no da error</b>: dos salas que se
     * pisan, una ranura que se entrega dos veces, y el maestro entregado como
     * si fuera una copia.
     */
    private void testGimnasios() {
        var todos_ = net.pokereport.luna.gym.Gimnasio.TODOS;
        check("hay gimnasios declarados", !todos_.isEmpty());

        // ⚠⚠⚠ ESTA COMPROBACION ESTABA MAL Y NO VIGILABA NADA. Comparaba el
        //    fondo de las ranuras (eje Z) contra la separacion de los gimnasios
        //    (eje X): EJES DISTINTOS, asi que pasaba siempre. Y mientras pasaba,
        //    PASO_RANURA valia 64 y el gimnasio de Brock mide 86 de fondo -- las
        //    copias se habrian pisado 22 bloques.
        //    UNA COMPROBACION QUE COMPARA COSAS QUE NO SE TOCAN DA CONFIANZA
        //    FALSA, que es peor que no tenerla.
        //
        //    Lo que de verdad protege es `Arenas.clonar`, que MIDE la sala antes
        //    de copiar y se niega si no cabe. Aqui solo se puede vigilar que los
        //    numeros sean sensatos para las salas que ya conocemos.
        check("PASO_RANURA da para una sala de tamaño normal (Brock: 86)",
              net.pokereport.luna.gym.Gimnasio.PASO_RANURA >= 96);
        check("SEPARACION da para una sala de tamaño normal (Brock: 96 de ancho)",
              net.pokereport.luna.gym.Gimnasio.SEPARACION >= 256);

        var vistos = new java.util.HashSet<String>();
        var salas = new java.util.HashSet<Integer>();
        boolean ok = true;
        for (var g : todos_) {
            if (!g.id().matches("[a-z0-9_]+") || !vistos.add(g.id())
                    || !salas.add(g.sala())) {
                ok = false;
                LunaEternal.LOG.error("Gimnasio invalido o repetido: {} (sala {})",
                        g.id(), g.sala());
            }
        }
        check("cada gimnasio tiene id unico, valido y su propia sala", ok);

        // ⚠⚠⚠ QUE EL ENTRENADOR EXISTA DE VERDAD EN EL DATAPACK. Un id mal
        //    escrito NO da error al arrancar: da un gimnasio en el que no
        //    aparece nadie, y eso se descubre con el jugador dentro.
        //    Ya paso: `kanto_lt_surge` no existe --es `kanto_ltsurge`-- y
        //    llevaba dias escrito. Esta comprobacion lo habria cazado el primer
        //    dia, y por eso pregunta a rctmod en vez de comparar contra una
        //    lista nuestra: una lista nuestra repetiria el mismo error.
        if (net.pokereport.luna.LunaEternal.hayEntrenadores()) {
            boolean todos = true;
            for (var g : todos_) {
                // ⚠⚠ LOS DE LA LIGA NARANJA SE SALTAN, Y SE DICE POR QUE. Sus
                //    cinco entrenadores son NUESTROS y todavia no los sirve
                //    nadie: preguntarle a rctmod por ellos daria rojo siempre.
                //    Darlos por buenos en silencio seria peor -- el dia que se
                //    construya la sala apareceria vacia y nadie sabria por que.
                if (!net.pokereport.luna.gym.Gimnasio.entrenadorDeRct(g)) {
                    continue;
                }
                if (!net.pokereport.luna.gym.Lideres.idValido(g)) {
                    todos = false;
                    LunaEternal.LOG.error("Gimnasio {}: el entrenador '{}' NO "
                            + "existe en el datapack", g.id(), g.entrenador());
                }
            }
            check("el entrenador de cada gimnasio existe en el datapack", todos);
        }

        // ⚠ Las insignias son 16 y ninguna se repite: el indice ES el bit de la
        //   mascara, asi que una repetida encenderia dos medallas a la vez.
        var ins = net.pokereport.luna.gym.Gimnasio.insignias();
        check("hay una insignia por gimnasio", ins.size() == todos_.size());
        check("y ninguna se repite",
              new java.util.HashSet<>(ins).size() == ins.size());

        // ⚠ El identificador llega del cliente: uno desconocido no resuelve.
        check("un gimnasio desconocido no resuelve",
              net.pokereport.luna.gym.Gimnasio.de("no_existe") == null);
        check("un nulo no resuelve",
              net.pokereport.luna.gym.Gimnasio.de(null) == null);

        // ---- LOS NIVELES Y LA PERICIA ----------------------------------

        // ⚠⚠ LOS NIVELES SUBEN, SIEMPRE. Si dos empataran o uno bajara, el orden
        //    de las medallas dejaria de significar nada: alguien se encontraria
        //    el gimnasio 9 mas facil que el 8, y la progresion --que es lo unico
        //    que ordena 23 gimnasios-- seria mentira. NO da ningun error.
        boolean suben = true;
        int anterior = 0;
        for (var g : todos_) {
            if (g.nivel() <= anterior) {
                suben = false;
                LunaEternal.LOG.error("Gimnasio {}: nivel {} y el anterior {}",
                        g.id(), g.nivel(), anterior);
            }
            anterior = g.nivel();
        }
        check("los niveles de gimnasio suben, uno a uno", suben);

        boolean rango = true, periciaOk = true;
        for (var g : todos_) {
            if (g.nivel() < 1 || g.nivel() > 100) {
                rango = false;
            }
            // ⚠ 0..5 porque `StrongBattleAI.checkSkillLevel` hace
            //   `skill == 5 ? true : rnd(100) < skill*20`. Un 7 no da error:
            //   se comporta igual que 5 y engaña al que lo lea.
            if (g.pericia() < 0 || g.pericia() > 5) {
                periciaOk = false;
            }
        }
        check("todos los niveles estan entre 1 y 100", rango);
        check("la pericia de la IA esta entre 0 y 5", periciaOk);

        // ---- LAS REGIONES ----------------------------------------------
        //
        // ⚠⚠ NINGUN GIMNASIO SE QUEDA FUERA DE SU PESTAÑA. La Liga solo dibuja
        //    los de la region que miras, asi que uno que no cuadre NO DA ERROR:
        //    DESAPARECE de la pantalla, y desde dentro se lee como «me falta un
        //    gimnasio». Mismo sintoma que la cadena `oficios` del arbol.
        int suma = 0;
        boolean vacias = false;
        for (var r : net.pokereport.luna.gym.Gimnasio.Region.values()) {
            int n = net.pokereport.luna.gym.Gimnasio.deRegion(r).size();
            suma += n;
            if (n == 0) {
                vacias = true;
                LunaEternal.LOG.error("La region {} no tiene ni un gimnasio", r);
            }
        }
        check("las regiones suman todos los gimnasios", suma == todos_.size());
        check("ninguna region esta vacia", !vacias);

        // ⚠⚠ Y CABEN EN LA REJILLA DE SU PESTAÑA, que es de 2x5 = DIEZ huecos.
        //    Una region con once se saldria del marco sin dar ningun error, que
        //    es la CUARTA vez que este proyecto tropieza con lo mismo.
        boolean cabenRegiones = true;
        for (var r : net.pokereport.luna.gym.Gimnasio.Region.values()) {
            int n = net.pokereport.luna.gym.Gimnasio.deRegion(r).size();
            if (n > 10) {
                cabenRegiones = false;
                LunaEternal.LOG.error("La region {} tiene {} y solo caben 10", r, n);
            }
        }
        check("ninguna region pasa de los 10 huecos de su pestaña", cabenRegiones);

        // ---- EL REPERTORIO Y LA ADAPTACION -----------------------------

        // ⚠⚠⚠ CADA ESPECIE Y CADA MOVIMIENTO EXISTEN DE VERDAD. Esto es lo mas
        //    importante de este bloque, porque el fallo es MUDO: un movimiento
        //    que esa especie no puede aprender NO da ningun error -- sale un
        //    Pokemon con tres ataques en vez de cuatro, y el jugador solo nota
        //    que el lider juega raro. Es el fallo de los 62 cosmeticos que no
        //    existian, otra vez.
        boolean especies = true, ataques = true, habilidades = true;
        boolean suTipo = true, bastantes = true;
        for (String gid : net.pokereport.luna.gym.Repertorio.conRepertorio()) {
            var g = net.pokereport.luna.gym.Gimnasio.de(gid);
            var pool = net.pokereport.luna.gym.Repertorio.de(gid);

            // ⚠ Seis o mas, porque un jugador puede traer SEIS. Con cinco, la
            //   paridad se rompe justo en el combate mas dificil.
            if (pool.size() < 6) {
                bastantes = false;
                LunaEternal.LOG.error("Gimnasio {}: solo {} en el repertorio,"
                        + " y un jugador puede traer 6", gid, pool.size());
            }
            for (var f : pool) {
                var sp = net.pokereport.luna.gym.Adaptador.especie(f.especie());
                if (sp == null) {
                    especies = false;
                    LunaEternal.LOG.error("Repertorio de {}: la especie '{}' NO"
                            + " existe", gid, f.especie());
                    continue;
                }
                // ⚠⚠ QUE SEA DE SU TIPO. Es el limite que separa un gimnasio de
                //    un espejo: si el rival se genera SOLO para contrarrestar,
                //    Brock deja de ser Brock. Lo unico que hace memorable a un
                //    gimnasio es que sabes a que vas.
                var tp = net.pokereport.luna.gym.Tipos.deEspecie(sp);
                // ⚠⚠ UN LIDER SIN TIPO DECLARADO ES MIXTO A PROPOSITO --hoy solo
                //    Drake, que es campeon-- y se salta esta comprobacion.
                //    Se pregunta con `esDeUnTipo` y no con `tipoDe(gid) != null`
                //    para que la excepcion sea EXPLICITA: un tipo que falte por
                //    descuido se leeria igual que uno que falta queriendo, y
                //    entonces la comprobacion que protege a los gimnasios
                //    dejaria de proteger a nadie sin avisar.
                boolean esSuyo = !net.pokereport.luna.gym.Repertorio.esDeUnTipo(gid);
                for (String x : tp) {
                    if (x != null && !esSuyo && x.equalsIgnoreCase(
                            net.pokereport.luna.gym.Repertorio.tipoDe(gid))) {
                        esSuyo = true;
                    }
                }
                if (!esSuyo) {
                    suTipo = false;
                    LunaEternal.LOG.error("Repertorio de {}: {} no es de tipo {}",
                            gid, f.especie(), net.pokereport.luna.gym.Repertorio.tipoDe(gid));
                }
                // la habilidad tiene que ser de esa especie
                boolean tieneHab = false;
                for (var ab : sp.getAbilities()) {
                    if (ab.getTemplate().getName().equalsIgnoreCase(f.habilidad())) {
                        tieneHab = true;
                    }
                }
                if (!tieneHab) {
                    habilidades = false;
                    LunaEternal.LOG.error("Repertorio de {}: {} no tiene la"
                            + " habilidad '{}'", gid, f.especie(), f.habilidad());
                }
                // ⚠ De cuatro a seis: con menos de cuatro el Pokemon sale con
                //   huecos, y eso tampoco da error.
                if (f.ataques().size() < 4) {
                    ataques = false;
                    LunaEternal.LOG.error("Repertorio de {}: {} solo tiene {}"
                            + " ataques", gid, f.especie(), f.ataques().size());
                }
            }
        }
        check("cada especie del repertorio existe", especies);
        check("cada habilidad es de esa especie", habilidades);
        check("cada ficha declara al menos 4 ataques", ataques);
        check("todo el repertorio es del tipo de su lider", suTipo);
        check("cada repertorio llega a 6, que es lo que puede traer un jugador",
              bastantes);

        // ⚠⚠ LA TABLA DE TIPOS, contra pares que se saben de memoria. Se genero
        //    de los datos de Showdown, y una tabla generada puede salir
        //    TRASPUESTA sin que nada se queje: agua->roca y roca->agua darian
        //    los dos un numero, solo que el equivocado.
        check("agua contra roca es x2",
              net.pokereport.luna.gym.Tipos.contra("water", "rock", null) == 2.0);
        check("fuego contra roca es x0,5",
              net.pokereport.luna.gym.Tipos.contra("fire", "rock", null) == 0.5);
        check("electrico contra tierra es x0",
              net.pokereport.luna.gym.Tipos.contra("electric", "ground", null) == 0.0);
        check("roca contra volador es x2",
              net.pokereport.luna.gym.Tipos.contra("rock", "flying", null) == 2.0);
        // ⚠ Y uno de DOS tipos, que es donde se ve si multiplica de verdad:
        //   agua contra roca/tierra es 2 x 2 = 4.
        check("agua contra roca/tierra es x4",
              net.pokereport.luna.gym.Tipos.contra("water", "rock", "ground") == 4.0);
        check("un tipo desconocido no revienta y vale x1",
              net.pokereport.luna.gym.Tipos.contra("chicle", "rock", null) == 1.0);

        // ---- las ranuras -----------------------------------------------
        var g0 = todos_.get(0);
        net.pokereport.luna.gym.Ranuras.olvidarTodo();

        // ⚠⚠ LA RANURA 0 NUNCA SE ENTREGA: es el maestro, donde se pega el
        //    esquema. Entregarla haria que alguien combatiera sobre el original,
        //    y un bloque roto ahi estropea la plantilla de TODAS las copias.
        var dados = new java.util.HashSet<Integer>();
        var uuids = new java.util.ArrayList<java.util.UUID>();
        boolean cero = false, repetida = false;
        for (int i = 0; i < net.pokereport.luna.gym.Gimnasio.RANURAS - 1; i++) {
            var u = java.util.UUID.randomUUID();
            uuids.add(u);
            int r = net.pokereport.luna.gym.Ranuras.reservar(g0, u);
            if (r == 0) {
                cero = true;
            }
            if (!dados.add(r)) {
                repetida = true;
            }
        }
        check("la ranura 0 (el maestro) no se entrega nunca", !cero);
        check("no se entrega la misma ranura dos veces", !repetida);

        // ⚠ Y con todas ocupadas se dice que NO, en vez de meter a dos en la
        //   misma sala. El diseño entero se cae si esto devuelve una repetida.
        check("con todas ocupadas, reservar falla",
              net.pokereport.luna.gym.Ranuras.reservar(
                  g0, java.util.UUID.randomUUID()) < 0);
        check("y no quedan libres", net.pokereport.luna.gym.Ranuras.libres(g0) == 0);

        // ⚠⚠ SOLTAR ES LO QUE SE OLVIDA AL DESCONECTAR, y sin ello la ranura
        //    queda pillada para siempre -- sin dar ningun error.
        net.pokereport.luna.gym.Ranuras.soltar(uuids.get(0));
        check("soltar libera la ranura",
              net.pokereport.luna.gym.Ranuras.libres(g0) == 1);
        check("y se puede volver a reservar",
              net.pokereport.luna.gym.Ranuras.reservar(
                  g0, java.util.UUID.randomUUID()) > 0);

        // ⚠ Cada gimnasio tiene sus propias ranuras: llenar Brock no puede
        //   dejar sin sitio a Misty.
        if (todos_.size() > 1) {
            check("las ranuras de un gimnasio no afectan a otro",
                  net.pokereport.luna.gym.Ranuras.libres(todos_.get(1))
                      == net.pokereport.luna.gym.Gimnasio.RANURAS - 1);
        }
        net.pokereport.luna.gym.Ranuras.olvidarTodo();

        // ---- las posiciones dentro de la sala ---------------------------
        //
        // ⚠⚠⚠ UN DESFASE MAYOR QUE `PASO_RANURA` SACA AL JUGADOR DE SU COPIA.
        //    Las posiciones son desfases desde el origen de la ranura, y las
        //    ranuras van una detras de otra en Z. Un desfase en Z de 130 con un
        //    paso de 128 deja al jugador de la ranura 1 apareciendo DENTRO DE LA
        //    RANURA 2 -- en la sala de otro, o en su pared. No da ningun error.
        boolean dentroDeSuSala = true;
        for (var g : todos_) {
            var e = net.pokereport.luna.gym.Gimnasio.entrada(g, 0);
            var l = net.pokereport.luna.gym.Gimnasio.lider(g, 0);
            int paso = net.pokereport.luna.gym.Gimnasio.PASO_RANURA;
            if (e.z < 0 || e.z >= paso || l.z < 0 || l.z >= paso) {
                dentroDeSuSala = false;
                LunaEternal.LOG.error("Gimnasio {}: entrada z={} lider z={} "
                        + "fuera de [0, {})", g.id(), e.z, l.z, paso);
            }
        }
        check("la entrada y el lider caen dentro de su propia ranura",
              dentroDeSuSala);

        // ⚠ Y el lider no puede aparecer ENCIMA del jugador: si coincidieran, el
        //   jugador entraria empotrado contra el, y hablar con el seria imposible.
        boolean separados = true;
        for (var g : todos_) {
            if (!net.pokereport.luna.gym.Gimnasio.tieneTarima(g)) {
                continue;   // sin medir: usa el respaldo, que ya va a 20 de lejos
            }
            var e = net.pokereport.luna.gym.Gimnasio.entrada(g, 1);
            var l = net.pokereport.luna.gym.Gimnasio.lider(g, 1);
            if (e.squaredDistanceTo(l) < 4) {
                separados = false;
                LunaEternal.LOG.error("Gimnasio {}: el lider aparece encima de "
                        + "la entrada", g.id());
            }
        }
        check("el lider no aparece encima de la entrada", separados);

        // ⚠⚠ Y LAS DOS TIENEN QUE MOVERSE CON LA RANURA. Si alguien escribiera
        //    las posiciones como coordenadas absolutas --que parece mas simple--
        //    todos los retadores acabarian en la ranura 0, la del maestro, y
        //    combatirian unos encima de otros sobre el original.
        var e0 = net.pokereport.luna.gym.Gimnasio.entrada(todos_.get(0), 0);
        var e3 = net.pokereport.luna.gym.Gimnasio.entrada(todos_.get(0), 3);
        check("la entrada se desplaza con la ranura",
              Math.abs((e3.z - e0.z)
                       - 3.0 * net.pokereport.luna.gym.Gimnasio.PASO_RANURA) < 0.001);

        // ---- las medallas ----------------------------------------------
        //
        // ⚠⚠⚠ EL BIT DE LA MEDALLA ES SU COLUMNA `bit`, NO SU POSICION. Hasta
        //    el 30-ago era `sala()`, y entonces entro la Liga Naranja EN MITAD
        //    de la lista: con el bit posicional, quien tuviera la medalla del
        //    Campeon de Kanto se habria despertado con la de Cissy. SIN UN SOLO
        //    ERROR, porque una mascara es un numero y un numero siempre se lee.
        //
        //    ⚠⚠ Y por eso lo que se comprueba aqui es que sea UNICO Y QUEPA, no
        //       que coincida con la posicion: comparar contra la posicion seria
        //       volver a atar las dos cosas que acabamos de separar.
        boolean bits = true;
        var vistosBit = new java.util.HashSet<Integer>();
        for (var g : todos_) {
            if (net.pokereport.luna.gym.Gimnasio.bitMedalla(g) != (1 << g.bit())) {
                bits = false;
            }
            // ⚠ Hasta 30: `1 << 31` es NEGATIVO y `1 << 32` vale 1 -- o sea que
            //   el gimnasio 32 encenderia la medalla de Brock. Ninguno da error.
            if (g.bit() < 0 || g.bit() > 30 || !vistosBit.add(g.bit())) {
                bits = false;
                LunaEternal.LOG.error("Gimnasio {}: bit {} repetido o fuera de"
                        + " rango", g.id(), g.bit());
            }
        }
        check("cada medalla tiene un bit unico que cabe en la mascara", bits);

        // ⚠⚠ LAS DIECISEIS INSIGNIAS SON UNA SOLA LISTA. Antes habia tres --esta,
        //    la del PokePad y la del dialogo del gimnasio-- y nada las obligaba a
        //    coincidir. Hoy las pantallas leen de aqui; esto vigila que la lista
        //    siga teniendo dieciseis nombres distintos y en orden.
        var insignias = net.pokereport.luna.gym.Gimnasio.insignias();
        // ⚠ Contra `todos_.size()` y NO contra un 16 escrito a mano: el numero
        //   ya cambio dos veces (8 -> 16 -> 23) y un literal solo avisa la vez
        //   que alguien se acuerda de tocarlo.
        check("hay una insignia por gimnasio", insignias.size() == todos_.size());
        check("ninguna insignia se repite",
              new java.util.HashSet<>(insignias).size() == insignias.size());
        boolean enOrden = true;
        for (var g : todos_) {
            if (!insignias.get(g.bit()).equals(g.insignia())) {
                enOrden = false;
                LunaEternal.LOG.error("Gimnasio {}: su insignia {} no esta en el "
                        + "hueco {} de la lista", g.id(), g.insignia(), g.bit());
            }
        }
        check("cada insignia esta en el hueco de su BIT", enOrden);

        // ⚠ La mascara: leer un bit no puede depender del orden del bucle.
        var quien = java.util.UUID.randomUUID();
        net.pokereport.luna.gym.MedallaService.olvidarTodo();
        check("sin cache, cero medallas",
              net.pokereport.luna.gym.MedallaService.cuantas(quien) == 0);
        check("y no tiene la de Brock",
              !net.pokereport.luna.gym.MedallaService.tiene(quien, todos_.get(0)));
        net.pokereport.luna.gym.MedallaService.ponerEnCache(quien,
              net.pokereport.luna.gym.Gimnasio.bitMedalla(todos_.get(0))
            | net.pokereport.luna.gym.Gimnasio.bitMedalla(todos_.get(2)));
        check("dos medallas se cuentan como dos",
              net.pokereport.luna.gym.MedallaService.cuantas(quien) == 2);
        check("tiene la de Brock",
              net.pokereport.luna.gym.MedallaService.tiene(quien, todos_.get(0)));
        check("y NO la de Misty, que esta en medio de las dos",
              !net.pokereport.luna.gym.MedallaService.tiene(quien, todos_.get(1)));
        net.pokereport.luna.gym.MedallaService.olvidar(quien);
        check("olvidar deja la cache a cero",
              net.pokereport.luna.gym.MedallaService.cuantas(quien) == 0);

        // ⚠⚠ CADA GIMNASIO PIDE MENOS MEDALLAS DE LAS QUE HAY. Un gimnasio que
        //    pidiera ocho seria INALCANZABLE --nunca tendrias ocho sin ganarle a
        //    el-- y no daria ningun error: se quedaria gris para siempre. Es la
        //    misma familia que la novena parada de Viajes.
        boolean alcanzables = true;
        for (var g : todos_) {
            if (g.medallas() < 0 || g.medallas() >= todos_.size()) {
                alcanzables = false;
                LunaEternal.LOG.error("Gimnasio {}: pide {} medallas y solo hay {}",
                        g.id(), g.medallas(), todos_.size());
            }
        }
        check("ningun gimnasio pide mas medallas de las que se pueden tener",
              alcanzables);

        // ⚠ Y el orden tiene que ser un camino: el gimnasio de la sala N no puede
        //   pedir mas medallas que N, o no habria forma de llegar hasta el.
        boolean camino = true;
        for (var g : todos_) {
            if (g.medallas() > g.sala()) {
                camino = false;
                LunaEternal.LOG.error("Gimnasio {} (sala {}) pide {} medallas: "
                        + "no se puede llegar", g.id(), g.sala(), g.medallas());
            }
        }
        check("se puede llegar a todos los gimnasios en orden", camino);

        // ---- las recepciones de la ciudadela ----------------------------
        //
        // ⚠⚠ EL POKEMON DEL LIDER NO PUEDE ESTAR ENCIMA DE EL. Con los dos en el
        //    mismo punto, el clic derecho acertaria a veces a uno y a veces a
        //    otro -- y tocar al Pokemon no hace nada, asi que el dialogo
        //    "a veces no abre".
        boolean recepcionesOk = true;
        for (var g : net.pokereport.luna.gym.Gimnasio.conRecepcion()) {
            var r = net.pokereport.luna.gym.Gimnasio.recepcion(g);
            if (r.lider().squaredDistanceTo(r.pokemon()) < 1.0
                    || r.especie() == null || r.especie().isBlank()) {
                recepcionesOk = false;
                LunaEternal.LOG.error("Gimnasio {}: su recepcion esta mal puesta",
                        g.id());
            }
        }
        check("cada recepcion separa al lider de su Pokemon", recepcionesOk);

        // ⚠⚠⚠ Y BROCK TIENE QUE TENER RECEPCION. Sin ella no hay forma de entrar
        //    al gimnasio: el dialogo SOLO se abre tocando al lider de la
        //    ciudadela. Un gimnasio construido al que no se puede llegar no da
        //    ningun error -- simplemente no existe para el jugador.
        check("Brock tiene sitio en la ciudadela",
              net.pokereport.luna.gym.Gimnasio.recepcion(todos_.get(0)) != null);

        // ---- el programador de la vuelta --------------------------------
        //
        // ⚠ Es lo que devuelve al jugador tras el combate. Si no disparara, se
        //   quedaria encerrado en la arena: de esa dimension no se sale andando.
        net.pokereport.luna.gym.Programador.olvidarTodo();
        final boolean[] corrio = {false};
        net.pokereport.luna.gym.Programador.en(1, () -> corrio[0] = true);
        check("una tarea programada queda pendiente",
              net.pokereport.luna.gym.Programador.pendientes() == 1);
        net.pokereport.luna.gym.Programador.tick(null);
        check("y corre en su tick", corrio[0]);
        check("y no se queda encolada",
              net.pokereport.luna.gym.Programador.pendientes() == 0);

        // ⚠⚠ UNA TAREA QUE FALLA NO SE LLEVA A LAS DEMAS POR DELANTE. Si una
        //    excepcion vaciara la lista, un jugador se quedaria dentro de la
        //    arena porque LA VUELTA DE OTRO revento.
        net.pokereport.luna.gym.Programador.en(1, () -> {
            throw new RuntimeException("a proposito");
        });
        final boolean[] segunda = {false};
        net.pokereport.luna.gym.Programador.en(1, () -> segunda[0] = true);
        net.pokereport.luna.gym.Programador.tick(null);
        check("una tarea que falla no cancela a la siguiente", segunda[0]);
        net.pokereport.luna.gym.Programador.olvidarTodo();
    }

    /**
     * LA CUENTA ATRAS DE LOS VIAJES.
     *
     * <p>⚠⚠ Lo que se vigila no es que cuente bien --eso se ve-- sino que
     * <b>cancelarla suelte lo que habia reservado</b>. Retar a un gimnasio
     * aparta una ranura ANTES de empezar a contar; si el jugador se mueve y
     * nadie la suelta, esa copia queda apartada para alguien que no va a ir. Al
     * octavo, nadie puede retar al lider — y no da ningun error.
     */
    private void testEspera() {
        net.pokereport.luna.world.Espera.olvidarTodo();
        net.pokereport.luna.gym.Ranuras.olvidarTodo();
        var g = net.pokereport.luna.gym.Gimnasio.TODOS.get(0);
        int libresAntes = net.pokereport.luna.gym.Ranuras.libres(g);

        // ---- moverse (o irse) suelta la ranura
        var uno = java.util.UUID.randomUUID();
        int r = net.pokereport.luna.gym.Ranuras.reservar(g, uno);
        check("la cuenta empieza con la ranura ya apartada", r > 0);
        net.pokereport.luna.world.Espera.pedirDePrueba(uno, () -> { },
                () -> net.pokereport.luna.gym.Ranuras.soltar(uno));
        check("mientras cuenta, hay una espera pendiente",
              net.pokereport.luna.world.Espera.pendientes() == 1);
        net.pokereport.luna.world.Espera.olvidar(uno);
        check("cancelar SUELTA la ranura",
              net.pokereport.luna.gym.Ranuras.libres(g) == libresAntes);
        check("y no queda ninguna espera",
              net.pokereport.luna.world.Espera.pendientes() == 0);

        // ---- pedir dos veces cancela la primera
        // ⚠ Sin esto, pulsar «ir» dos veces apartaria DOS ranuras y solo
        //   soltaria una: la fuga mas facil de provocar, y sin querer.
        var dos = java.util.UUID.randomUUID();
        int r1 = net.pokereport.luna.gym.Ranuras.reservar(g, dos);
        final boolean[] cancelada = {false};
        net.pokereport.luna.world.Espera.pedirDePrueba(dos, () -> { },
                () -> cancelada[0] = true);
        net.pokereport.luna.world.Espera.pedirDePrueba(dos, () -> { }, () -> { });
        check("pedir dos veces cancela la anterior", cancelada[0]);
        check("y sigue habiendo una sola espera",
              net.pokereport.luna.world.Espera.pendientes() == 1);
        net.pokereport.luna.world.Espera.olvidarTodo();
        net.pokereport.luna.gym.Ranuras.soltar(dos);
        check("la ranura de prueba queda libre",
              net.pokereport.luna.gym.Ranuras.libres(g) == libresAntes && r1 > 0);

        // ⚠ Cinco segundos es la espera; cero la anularia y treinta seria un
        //   castigo. Se comprueba que sea un numero razonable porque cambiarlo
        //   es una linea y nadie mira lo que pasa con el resto.
        check("la espera dura entre 1 y 10 segundos",
              net.pokereport.luna.world.Espera.SEGUNDOS >= 1
              && net.pokereport.luna.world.Espera.SEGUNDOS <= 10);
        net.pokereport.luna.gym.Ranuras.olvidarTodo();
    }

    private void testTrajes() {
        var todos = net.pokereport.luna.traje.Traje.todos();
        check("hay trajes declarados", !todos.isEmpty());

        // ⚠⚠ UN TRAJE SIN ARTE NO SE PUEDE PONER, NI SIENDO LEYENDA. Es lo que
        //    impide vender humo: sin esto, se equipa, se sincroniza, no da
        //    ningun error y el jugador NO VE NADA. Es el fallo de los 62
        //    cosmeticos que no existian, otra vez.
        // Un jugador de mentira que lo tiene TODO comprado: si aun asi no puede
        // ponerse un traje sin arte, la guarda esta donde tiene que estar.
        var quienSea = java.util.UUID.randomUUID();
        boolean humo = false;
        for (var t : todos) {
            if (!t.listo() && net.pokereport.luna.traje.TrajeService.tiene(quienSea, t)) {
                humo = true;
                LunaEternal.LOG.error("El traje {} no tiene arte y se puede poner",
                        t.id());
            }
        }
        check("un traje sin arte no se puede poner aunque lo tengas", !humo);

        // ⚠⚠⚠ EL ENTRENADOR ES EL UNICO GRATIS, y esto no es una perogrullada:
        //    `gratis()` es lo que salta la tabla de propiedad. Si otro traje
        //    devolviera true, se REGALARIA a todo el mundo -- sin error, sin
        //    traza, y sin que nadie lo comprara. Es lo contrario de vender humo:
        //    es regalar lo que se vende.
        int gratis = 0;
        for (var t : todos) {
            if (t.gratis()) {
                gratis++;
            }
        }
        check("solo hay UN traje gratis", gratis == 1);
        check("y es el ENTRENADOR",
                net.pokereport.luna.traje.Traje.ENTRENADOR.gratis());

        // ⚠⚠ NADIE TIENE NADA POR DEFECTO. Un jugador que no ha comprado nada
        //    solo puede ponerse el gratis. Se rompe el dia que alguien haga que
        //    `tiene` caiga a true ante un uuid desconocido -- y entonces todo el
        //    mundo llevaria el traje de LEYENDA.
        boolean regalado = false;
        for (var t : todos) {
            if (!t.gratis() && net.pokereport.luna.traje.TrajeService.tiene(quienSea, t)) {
                regalado = true;
                LunaEternal.LOG.error("El traje {} se puede poner sin haberlo "
                        + "adquirido", t.id());
            }
        }
        check("sin comprar nada, solo se puede poner el gratis", !regalado);

        // ⚠⚠ LA ESCALERA SUBE Y NO BAJA. Ya no gobierna el permiso --eso lo
        //    hace la propiedad (V028)-- pero sigue siendo el ORDEN en que se
        //    dibujan y el que empareja cada traje con su rango en la tienda. Se
        //    rompe reordenando el enum, y entonces la pantalla los lista al
        //    reves de como se venden.
        int previo = -1;
        boolean crece = true;
        for (var t : todos) {
            int pide = t.pide().escalon;
            if (pide <= previo) {
                crece = false;
                LunaEternal.LOG.error("El traje {} pide escalon {} y el anterior {}",
                        t.id(), pide, previo);
            }
            previo = pide;
        }
        check("cada traje pide un escalon MAS ALTO que el anterior", crece);

        // ⚠⚠⚠ CADA TRAJE TIENE SU PROPIO RANGO, Y NINGUNO SE REPITE. Es lo que
        //    hace que «comprar CAMPEON» signifique una sola cosa: con dos trajes
        //    apuntando al mismo rango, la tienda no sabria cual esta vendiendo y
        //    el jugador se llevaria el que decidiera el orden del enum.
        var rangos = new java.util.HashSet<String>();
        boolean unicos = true;
        for (var t : todos) {
            if (!rangos.add(t.pide().name())) {
                unicos = false;
                LunaEternal.LOG.error("Dos trajes piden el rango {}", t.pide().name());
            }
        }
        check("no hay dos trajes para el mismo rango", unicos);

        // ⚠ El identificador llega DEL CLIENTE: uno desconocido no puede caer al
        //   primero, o un cliente modificado siempre acertaria con algo (P6).
        check("un traje desconocido no resuelve",
              net.pokereport.luna.traje.Traje.de("no_existe") == null);
        check("una cadena vacia no resuelve",
              net.pokereport.luna.traje.Traje.de("") == null);
        check("un nulo no resuelve",
              net.pokereport.luna.traje.Traje.de(null) == null);

        // ⚠ El nombre que ve el jugador sale de una clave de traduccion, y una
        //   clave que no existe se pinta CRUDA sin dar ningun error.
        boolean forma = true;
        var vistos = new java.util.HashSet<String>();
        for (var t : todos) {
            if (!t.id().matches("[a-z0-9_]+") || !vistos.add(t.id())) {
                forma = false;
                LunaEternal.LOG.error("Identificador de traje invalido o repetido: {}",
                        t.id());
            }
        }
        check("los identificadores valen para una clave y no se repiten", forma);

        // ⚠⚠ Y EL PRIMER TRAJE TIENE QUE SER ALCANZABLE POR TODO EL MUNDO. Si
        //    pidiera mas que el rango de partida, un jugador nuevo abriria la
        //    pantalla y no podria ponerse NADA -- que se lee como «esto no
        //    funciona», no como «te falta rango».
        var base = net.pokereport.luna.ui.Tablist.Rank.ENTRENADOR;
        check("el traje mas bajo lo abre el rango de partida",
              todos.get(0).pide().escalon <= base.escalon);
    }

    private void testViajes() {
        var todas = net.pokereport.luna.world.Paradas.TODAS;

        check("hay paradas declaradas", !todas.isEmpty());

        // ⚠⚠ LA REJILLA DEL CLIENTE SON 4x2 = OCHO HUECOS. Una novena parada no
        //    daría ningún error: se dibujarían ocho y la novena sería
        //    INALCANZABLE. Es exactamente lo que pasó con la decimosexta
        //    aplicación del PokePad y con los 54 cosméticos de la página 2.
        check("las paradas caben en la rejilla de Viajes (4x2)",
              todas.size() <= 8);

        // ⚠ Un id repetido no falla: `de()` devuelve siempre el primero, así que
        //   dos fichas llevarían al mismo sitio y una parada sería inalcanzable.
        var ids = new java.util.HashSet<String>();
        boolean unicos = true;
        for (var p : todas) {
            if (!ids.add(p.id())) {
                unicos = false;
                LunaEternal.LOG.error("Parada repetida: {}", p.id());
            }
        }
        check("los identificadores de parada no se repiten", unicos);

        // ⚠ Dos paradas encima la una de la otra dejarían dos Miraidon
        //   superpuestos, y no se pueden separar: no se les puede pegar ni
        //   capturar. Habría que borrarlos por comando.
        boolean separadas = true;
        for (int i = 0; i < todas.size(); i++) {
            for (int j = i + 1; j < todas.size(); j++) {
                if (todas.get(i).pos().distanceTo(todas.get(j).pos()) < 4) {
                    separadas = false;
                    LunaEternal.LOG.error("Paradas pegadas: {} y {}",
                            todas.get(i).id(), todas.get(j).id());
                }
            }
        }
        check("ninguna parada se solapa con otra", separadas);

        // ⚠ Un id que no existe se RECHAZA, no cae en la primera. El cliente
        //   manda el identificador y P6 dice que no nos fiamos de él.
        check("un identificador desconocido no resuelve",
              net.pokereport.luna.world.Paradas.de("no_existe") == null);
        check("una cadena vacía no resuelve",
              net.pokereport.luna.world.Paradas.de("") == null);
        boolean todasResuelven = true;
        for (var p : todas) {
            if (net.pokereport.luna.world.Paradas.de(p.id()) == null) {
                todasResuelven = false;
            }
        }
        check("todas las paradas se resuelven por su identificador", todasResuelven);

        // ⚠⚠ EL NOMBRE QUE VE EL JUGADOR SALE DE UNA CLAVE DE TRADUCCIÓN, y una
        //    clave que no existe NO DA NINGÚN ERROR: Minecraft pinta la clave
        //    cruda. Aquí solo se puede comprobar la forma del identificador —lo
        //    otro lo caza `tools/comprobar_textos.py`— pero un id con mayúsculas
        //    o espacios ya rompería la clave.
        boolean formaOk = true;
        for (var p : todas) {
            if (!p.id().matches("[a-z0-9_]+")) {
                formaOk = false;
                LunaEternal.LOG.error("Identificador de parada mal formado: {}", p.id());
            }
        }
        check("los identificadores valen para una clave de traducción", formaOk);
    }

    private void testCura() {
        // ⚠ EL COOLDOWN NO PUEDE SER 0 NI NEGATIVO. A 0 la curación deja de ser
        //   un recurso: se cura entre turno y turno y el combate deja de tener
        //   consecuencia, que es justo lo que el cooldown existe para evitar.
        check("la curacion tiene cooldown y es positivo",
            net.pokereport.luna.heal.HealService.COOLDOWN_MIN > 0);

        // ⚠ Y TAMPOCO PUEDE PASARSE. Diez minutos es lo diseñado; una hora
        //   convertiría «gratis» en «gratis pero inservible», que es cobrar por
        //   curar por la puerta de atrás (P4).
        check("el cooldown de curar sigue siendo el diseñado (30 min)",
            net.pokereport.luna.heal.HealService.COOLDOWN_MIN == 30);

        // ⚠⚠ LO QUE VIAJA AL CLIENTE ES EL NOMBRE DE SHOWDOWN DEL ESTADO, y la
        //    pantalla lo traduce a color. Si Cobblemon renombrara uno, aquí no
        //    fallaría nada: la pantalla dibujaría el estado en gris «no sé qué
        //    es esto» y nadie lo notaría hasta que alguien envenenado viera su
        //    Pokémon como sano.
        //
        //    Se comprueban los cinco que la pantalla pinta, contra los que
        //    Cobblemon registra de verdad.
        java.util.Set<String> registrados = new java.util.HashSet<>();
        try {
            for (var s : com.cobblemon.mod.common.api.pokemon.status.Statuses.INSTANCE
                    .getPersistentStatuses()) {
                registrados.add(s.getShowdownName());
            }
        } catch (Throwable t) {
            fail("no se pudieron leer los estados de Cobblemon", t.toString());
        }
        for (String estado : new String[] {"psn", "brn", "par", "slp", "frz"}) {
            check("el estado '" + estado + "' que dibuja la pantalla existe en Cobblemon",
                registrados.contains(estado));
        }
    }

    // ------------------------------------------------------------ auxiliares

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private long countEntries(long playerId) throws Exception {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT COUNT(*) FROM ledger_entry WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /** Borra en orden inverso a las claves ajenas. */
    private void cleanup() throws Exception {
        List<String> uuids = new ArrayList<>(List.of(T1.toString(), T2.toString()));
        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                // Orden inverso a las claves ajenas: primero se sueltan las
                // referencias al comprador, luego se borran los listados.
                for (String sql : List.of(
                    "DELETE mc FROM market_claim mc JOIN player p "
                        + "ON p.player_id = mc.player_id WHERE p.mc_uuid = ?",
                    "DELETE mt FROM market_trade mt JOIN player p "
                        + "ON p.player_id = mt.buyer_id WHERE p.mc_uuid = ?",
                    "DELETE mt FROM market_trade mt JOIN player p "
                        + "ON p.player_id = mt.seller_id WHERE p.mc_uuid = ?",
                    "DELETE mo FROM market_order mo JOIN player p "
                        + "ON p.player_id = mo.player_id WHERE p.mc_uuid = ?",
                    "DELETE cm FROM clan_member cm JOIN player p "
                        + "ON p.player_id = cm.player_id WHERE p.mc_uuid = ?",
                    "DELETE ci FROM clan_invite ci JOIN player p "
                        + "ON p.player_id = ci.player_id WHERE p.mc_uuid = ?",
                    "DELETE cl FROM clan cl JOIN player p "
                        + "ON p.player_id = cl.leader_id WHERE p.mc_uuid = ?",
                    "DELETE ck FROM crate_key ck JOIN player p "
                        + "ON p.player_id = ck.player_id WHERE p.mc_uuid = ?",
                    "DELETE co FROM crate_open co JOIN player p "
                        + "ON p.player_id = co.player_id WHERE p.mc_uuid = ?",
                    "DELETE cp FROM crate_pity cp JOIN player p "
                        + "ON p.player_id = cp.player_id WHERE p.mc_uuid = ?",
                    "DELETE pa FROM player_activity pa JOIN player p "
                        + "ON p.player_id = pa.player_id WHERE p.mc_uuid = ?",
                    "DELETE hp FROM hunt_progress hp JOIN player p "
                        + "ON p.player_id = hp.player_id WHERE p.mc_uuid = ?",
                    "DELETE qp FROM quest_progress qp JOIN player p "
                        + "ON p.player_id = qp.player_id WHERE p.mc_uuid = ?",
                    "DELETE kc FROM kit_claim kc JOIN player p "
                        + "ON p.player_id = kc.player_id WHERE p.mc_uuid = ?",
                    "DELETE pd FROM pokedex_entry pd JOIN player p "
                        + "ON p.player_id = pd.player_id WHERE p.mc_uuid = ?",
                    "UPDATE gts_listing g JOIN player p "
                        + "ON p.player_id = g.buyer_id SET g.buyer_id = NULL "
                        + "WHERE p.mc_uuid = ?",
                    "DELETE g FROM gts_listing g JOIN player p "
                        + "ON p.player_id = g.seller_id WHERE p.mc_uuid = ?",
                    "DELETE pp FROM player_path pp JOIN player p "
                        + "ON p.player_id = pp.player_id WHERE p.mc_uuid = ?",
                    "DELETE le FROM ledger_entry le JOIN player p "
                        + "ON p.player_id = le.player_id WHERE p.mc_uuid = ?",
                    "DELETE pe FROM player_economy pe JOIN player p "
                        + "ON p.player_id = pe.player_id WHERE p.mc_uuid = ?",
                    "DELETE FROM player WHERE mc_uuid = ?")) {
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        for (String u : uuids) {
                            ps.setString(1, u);
                            ps.executeUpdate();
                        }
                    }
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * CLANES: quien manda, quien no, y que el tesoro cuadre.
     *
     * <p>&#9888;&#9888; Un sistema social es <b>reglas de permiso</b>, y una regla
     * de permiso no falla ruidosamente: falla dejando que alguien haga algo que no
     * debia. No hay excepcion, no hay traza en el log, y el sintoma llega en forma
     * de jugador enfadado porque le han vaciado el tesoro. Por eso aqui se
     * comprueba sobre todo <b>lo que NO se puede hacer</b>.
     *
     * <p>&#9888; Y el tesoro es dinero: pasa por {@code applyInTransaction} como
     * todo lo demas (R3, R4), asi que la comprobacion que de verdad importa es la
     * de <b>suma cero</b> -- lo que sale del bolsillo entra en el tesoro, ni un
     * PokeDolar aparece ni desaparece por el camino.
     */
    private void testClanes(long a, long b) throws Exception {
        var svc = new net.pokereport.luna.clan.ClanService(db);
        long coste = net.pokereport.luna.clan.ClanService.COSTE_FUNDAR;
        var OFICIAL = net.pokereport.luna.clan.ClanService.Rol.OFICIAL;
        var MIEMBRO = net.pokereport.luna.clan.ClanService.Rol.MIEMBRO;
        var LIDER = net.pokereport.luna.clan.ClanService.Rol.LIDER;

        // --- el nombre, antes de tocar la base
        check("un nombre corto se rechaza",
                !svc.fundar(a, "ab", "AB", 'b', "", clave()).ok());
        check("una etiqueta larga se rechaza",
                !svc.fundar(a, "Clan de Prueba", "DEMASIADO", 'b', "", clave()).ok());
        // El § es el que importa: con un codigo de color dentro, la
        // etiqueta pintaria el resto de la linea del chat de TODO el mundo.
        check("una etiqueta con codigo de color se rechaza",
                !svc.fundar(a, "Clan de Prueba", "\u00a7cX", 'b', "", clave()).ok());

        // --- sin dinero no se funda, y no queda rastro
        long vaciar = economy.balance(a, Currency.POKEDOLLAR);
        if (vaciar > 0) {
            economy.debit(a, Currency.POKEDOLLAR, vaciar, "autotest_clan_vaciar", clave());
        }
        check("sin Plata suficiente NO se funda",
                !svc.fundar(a, "Clan de Prueba", "PRB", 'b', "", clave()).ok());
        check("el intento fallido no deja clan", svc.clanDe(a) == null);

        // --- con dinero si, y cobra exactamente el coste
        economy.credit(a, Currency.POKEDOLLAR, coste * 6, "autotest_clan", clave());
        long antes = economy.balance(a, Currency.POKEDOLLAR);
        var rFundar = svc.fundar(a, "Clan de Prueba", "PRB", 'b', "", clave());
        check("con Plata se funda", rFundar.ok());
        check("fundar cobra exactamente el coste",
                economy.balance(a, Currency.POKEDOLLAR) == antes - coste);
        check("fundar afecta al fundador", rFundar.afectados().contains(a));

        var clan = svc.clanDe(a);
        check("el clan existe tras fundarlo", clan != null);
        check("el fundador es el LIDER", svc.rolDe(a) == LIDER);
        check("el clan nace con un solo miembro", clan != null && clan.miembros() == 1);
        check("el clan nace con el tesoro vacio", clan != null && clan.tesoro() == 0);
        check("el clan nace con un tope de oficial mayor que cero",
                clan != null && clan.topeOficial() > 0);
        check("fundar queda anotado en el registro",
                svc.registro(clan.id(), 10).stream()
                   .anyMatch(x -> "FUNDAR".equals(x.accion())));

        // --- uno y solo uno
        check("no se puede fundar un segundo clan estando en uno",
                !svc.fundar(a, "Otro Clan", "OTR", 'b', "", clave()).ok());
        // El choque es contra un indice unico sobre la version en MINUSCULAS.
        economy.credit(b, Currency.POKEDOLLAR, coste * 6, "autotest_clan", clave());
        check("el nombre no se puede repetir cambiando mayusculas",
                !svc.fundar(b, "CLAN DE PRUEBA", "XYZ", 'b', "", clave()).ok());
        check("la etiqueta no se puede repetir cambiando mayusculas",
                !svc.fundar(b, "Clan Distinto", "prb", 'b', "", clave()).ok());

        // --- invitar, aceptar
        check("un desconocido no puede invitar por el clan", !svc.invitar(b, a).ok());
        var rInv = svc.invitar(a, b);
        check("el lider invita", rInv.ok());
        // ⚠ EL INVITADO NO ES MIEMBRO Y AUN ASI HAY QUE AVISARLE: es el unico
        //   que tiene algo nuevo que ver. Si la lista de afectados saliera del
        //   clan, la invitacion no aparece hasta que reabra la pantalla.
        check("invitar afecta al INVITADO, que no es miembro",
                rInv.afectados().contains(b));
        check("la invitacion le llega al invitado", svc.invitaciones(b).size() == 1);
        check("no se puede aceptar una invitacion que no existe",
                !svc.aceptar(b, clan.id() + 9999).ok());
        check("el invitado acepta", svc.aceptar(b, clan.id()).ok());
        check("el que acepta entra como MIEMBRO", svc.rolDe(b) == MIEMBRO);
        check("el clan pasa a dos miembros", svc.clanDe(a).miembros() == 2);
        check("aceptar consume la invitacion", svc.invitaciones(b).isEmpty());
        check("entrar queda anotado",
                svc.registro(clan.id(), 10).stream()
                   .anyMatch(x -> "ENTRAR".equals(x.accion())));

        // --- LO QUE NO SE PUEDE HACER. Es el corazon de la prueba.
        check("un miembro raso NO puede invitar", !svc.invitar(b, a).ok());
        check("un miembro raso NO puede echar a nadie", !svc.echar(b, a).ok());
        check("un miembro raso NO puede ascender a nadie",
                !svc.cambiarRol(b, b, OFICIAL).ok());
        check("un miembro raso NO puede disolver el clan", !svc.disolver(b).ok());
        check("un miembro raso NO puede cambiar el tope", !svc.cambiarTope(b, 1).ok());
        check("el lider NO puede echarse a si mismo", !svc.echar(a, a).ok());
        // Si el lider pudiera salir, el clan se quedaria sin nadie que pueda
        // invitar, echar ni disolverlo: vivo y sin gobierno para siempre.
        check("el lider NO puede salir mientras quede alguien", !svc.salir(a).ok());
        check("tras los intentos, el clan sigue con sus dos miembros",
                svc.clanDe(a).miembros() == 2);

        // --- el tesoro y SU HISTORIAL
        long bolsilloAntes = economy.balance(b, Currency.POKEDOLLAR);
        check("un miembro raso SI puede aportar", svc.aportar(b, 1_000, clave()).ok());
        check("aportar sale del bolsillo",
                economy.balance(b, Currency.POKEDOLLAR) == bolsilloAntes - 1_000);
        check("aportar entra en el tesoro", svc.clanDe(b).tesoro() == 1_000);
        check("un miembro raso NO puede sacar del tesoro", !svc.sacar(b, 100, clave()).ok());
        check("el rechazo no toca el tesoro", svc.clanDe(b).tesoro() == 1_000);
        // El CHECK de la columna es la ultima red.
        check("no se puede sacar mas de lo que hay", !svc.sacar(a, 5_000, clave()).ok());
        check("un descubierto no deja el tesoro negativo", svc.clanDe(a).tesoro() == 1_000);
        long liderAntes = economy.balance(a, Currency.POKEDOLLAR);
        check("el lider saca", svc.sacar(a, 600, clave()).ok());
        check("sacar baja el tesoro", svc.clanDe(a).tesoro() == 400);
        check("sacar llega al bolsillo",
                economy.balance(a, Currency.POKEDOLLAR) == liderAntes + 600);
        check("nada se crea ni se pierde: aportado == sacado + tesoro",
                600 + svc.clanDe(a).tesoro() == 1_000);

        // ⚠ EL HISTORIAL ES LO QUE PIDIO EL USUARIO, y lo que lo hace fiable no
        //   es que exista sino que CUADRE: la suma de los deltas tiene que ser
        //   el tesoro. Si no cuadra, hay un movimiento que no se anoto -- y ese
        //   es justo el que estaria buscando quien investiga un robo.
        var movs = svc.historial(clan.id(), 50);
        check("el historial tiene las dos lineas", movs.size() == 2);
        check("el historial guarda el signo: una entrada y una salida",
                movs.stream().anyMatch(m -> m.delta() > 0)
                        && movs.stream().anyMatch(m -> m.delta() < 0));
        check("la suma del historial ES el tesoro",
                movs.stream().mapToLong(m -> m.delta()).sum() == svc.clanDe(a).tesoro());
        check("el historial dice QUIEN movio cada cosa",
                movs.stream().allMatch(m -> m.quien() != null && !m.quien().isBlank()));
        check("el saldo despues de la ultima linea es el tesoro de ahora",
                movs.get(0).saldoDespues() == svc.clanDe(a).tesoro());

        // --- EL TOPE DIARIO: la pieza de seguridad
        check("el lider asciende a oficial", svc.cambiarRol(a, b, OFICIAL).ok());
        check("ascender queda anotado",
                svc.registro(clan.id(), 10).stream()
                   .anyMatch(x -> "ASCENDER".equals(x.accion())));
        check("ascender al mismo rango que ya tiene se rechaza",
                !svc.cambiarRol(a, b, OFICIAL).ok());
        check("el lider baja el tope a 100", svc.cambiarTope(a, 100).ok());
        check("un tope negativo se rechaza", !svc.cambiarTope(a, -1).ok());
        check("un tope absurdo se rechaza", !svc.cambiarTope(a, Long.MAX_VALUE).ok());
        // Hay 400 en el tesoro; el oficial tiene tope 100.
        check("el oficial NO puede pasarse del tope", !svc.sacar(b, 200, clave()).ok());
        check("el rechazo por tope no toca el tesoro", svc.clanDe(a).tesoro() == 400);
        check("el oficial SI puede sacar hasta su tope", svc.sacar(b, 100, clave()).ok());
        check("lo sacado hoy queda contado", svc.sacadoHoy(clan.id(), b) == 100);
        // ⚠ Y AQUI ESTA LA GRACIA: ya gasto su cupo, asi que un segundo intento
        //   falla aunque el tesoro tenga de sobra. Sin ventana deslizante, dos
        //   retiradas a caballo de la medianoche darian el doble.
        check("agotado el cupo, el oficial no saca mas hoy",
                !svc.sacar(b, 1, clave()).ok());
        check("el lider NO tiene tope", svc.sacar(a, 300, clave()).ok());
        check("con tope 0 un oficial no saca nada",
                svc.cambiarTope(a, 0).ok() && !svc.sacar(b, 1, clave()).ok());
        check("el tope queda anotado en el registro",
                svc.registro(clan.id(), 20).stream()
                   .anyMatch(x -> "TOPE".equals(x.accion())));

        // --- TRASPASAR: el fallo mas grave que tuvo este sistema
        // ⚠ La primera version NO comprobaba que el nuevo lider fuera miembro:
        //   subia role='LIDER' a un player_id cualquiera. Con alguien de otro
        //   clan le convertia en lider DEL SUYO y dejaba este con un lider que
        //   no es miembro -- sin nadie que pueda dirigirlo y sin arreglo desde
        //   dentro. La pantalla no lo expone, pero el manejador de red si.
        check("no se puede pasar el mando a alguien que no esta en el clan",
                !svc.traspasar(a, 999_999_999L).ok());
        check("no se puede pasar el mando a uno mismo", !svc.traspasar(a, a).ok());
        check("un oficial NO puede pasar el mando", !svc.traspasar(b, a).ok());
        check("sigue mandando el mismo tras los intentos",
                svc.clanDe(a).liderId() == a);
        check("el lider pasa el mando", svc.traspasar(a, b).ok());
        check("el nuevo es LIDER", svc.rolDe(b) == LIDER);
        // Si el anterior se quedara LIDER habria dos; si se fuera, pasar el
        // mando seria tambien irse del clan, que no es lo que nadie pide.
        check("el anterior baja a OFICIAL y sigue dentro", svc.rolDe(a) == OFICIAL);
        check("el clan tiene un solo lider", svc.clanDe(a).liderId() == b);
        check("y sigue teniendo dos miembros", svc.clanDe(a).miembros() == 2);
        check("el traspaso queda anotado",
                svc.registro(clan.id(), 20).stream()
                   .anyMatch(x -> "TRASPASAR".equals(x.accion())));
        check("el mando vuelve", svc.traspasar(b, a).ok());

        // --- ECHAR, Y A QUIEN HAY QUE AVISAR
        // ⚠⚠ ESTE ES EL BUG QUE REPORTO EL USUARIO. Al echar a alguien, la lista
        //    de miembros de DESPUES ya no le incluye -- asi que quien calculara
        //    los destinatarios mirando el clan resultante dejaba al echado con
        //    la etiqueta puesta y creyendose dentro.
        var rEchar = svc.echar(a, b);
        check("el lider echa al oficial", rEchar.ok());
        check("ECHAR AVISA AL ECHADO, que ya no es miembro",
                rEchar.afectados().contains(b));
        check("echar avisa tambien a los que se quedan",
                rEchar.afectados().contains(a));
        check("el echado se queda sin clan", svc.clanDe(b) == null);
        check("el clan vuelve a un miembro", svc.clanDe(a).miembros() == 1);
        check("echar queda anotado con nombre",
                svc.registro(clan.id(), 20).stream()
                   .anyMatch(x -> "ECHAR".equals(x.accion()) && !x.aQuien().isBlank()));

        // --- SALIR, mismo caso
        check("vuelve a invitar", svc.invitar(a, b).ok());
        check("y vuelve a entrar", svc.aceptar(b, clan.id()).ok());
        var rSalir = svc.salir(b);
        check("el miembro sale", rSalir.ok());
        check("SALIR AVISA AL QUE SE VA", rSalir.afectados().contains(b));
        check("salir avisa tambien al que se queda", rSalir.afectados().contains(a));

        // --- DISOLVER: avisa a TODOS, incluidos los que dejan de serlo
        check("vuelve a invitar para disolver", svc.invitar(a, b).ok());
        check("y entra", svc.aceptar(b, clan.id()).ok());
        var rDisolver = svc.disolver(a);
        check("el lider disuelve", rDisolver.ok());
        check("DISOLVER AVISA A TODOS LOS QUE ERAN MIEMBROS",
                rDisolver.afectados().contains(a) && rDisolver.afectados().contains(b));
        check("disolver borra el clan", svc.clanDe(a) == null);
        check("y deja a todos sin clan", svc.clanDe(b) == null);
        check("y no queda en el listado",
                svc.listar(50).stream().noneMatch(c -> "Clan de Prueba".equals(c.nombre())));
        // El historial y el registro se van con el clan: son SUYOS.
        check("el historial se va con el clan", svc.historial(clan.id(), 10).isEmpty());
        check("el registro se va con el clan", svc.registro(clan.id(), 10).isEmpty());
    }

    /**
     * EL MERCADO: el libro de ordenes.
     *
     * <p>⚠⚠ Lo que se comprueba aqui NO es que el cruce funcione --eso se ve a
     * la primera-- sino que <b>el dinero cuadre en todos los caminos</b>. Un
     * libro de ordenes tiene cuatro sitios por donde se puede crear o destruir
     * Plata sin que nadie lo note:
     *
     * <ul>
     *   <li>reservar y no devolver lo que sobra al ejecutar mas barato;</li>
     *   <li>devolver lo PEDIDO en vez de lo NO EJECUTADO al cancelar;</li>
     *   <li>cobrar al comprador dos veces --al reservar y al cruzar--;</li>
     *   <li>o no cobrarle en ninguno de los dos.</li>
     * </ul>
     *
     * <p>Ninguno da error. Los cuatro se ven aqui.
     */
    private void testMercado(long a, long b) throws Exception {
        var svc = new net.pokereport.luna.market.MarketService(db);
        var COMPRA = net.pokereport.luna.market.MarketService.Lado.COMPRA;
        var VENTA = net.pokereport.luna.market.MarketService.Lado.VENTA;
        String ITEM = "cobblemon:poke_ball";

        // Dinero limpio para las dos partes.
        economy.credit(a, Currency.POKEDOLLAR, 1_000_000, "autotest_mkt", clave());
        economy.credit(b, Currency.POKEDOLLAR, 1_000_000, "autotest_mkt", clave());

        // --- lo que ni siquiera llega a la base
        check("una orden sin objeto se rechaza",
                !svc.poner(a, COMPRA, "", 100, 1, clave()).ok());

        // --- una venta sola se queda en el libro
        var v1 = svc.poner(a, VENTA, ITEM, 500, 10, clave());
        check("una venta sin contraparte se queda en el libro", v1.ok());
        check("y no ejecuta nada", v1.ejecutado() == 0);
        check("aparece en el libro de VENTA",
                svc.libro(ITEM, VENTA).stream().anyMatch(n -> n.precio() == 500));
        check("y NO aparece en el de COMPRA", svc.libro(ITEM, COMPRA).isEmpty());

        // --- ⚠ NADIE SE CRUZA CONSIGO MISMO
        // Cruzarte contigo permite fijar el precio que quieras, y ese precio es
        // el que va a alimentar el indice de inflacion de todo el servidor.
        var propio = svc.poner(a, COMPRA, ITEM, 900, 5, clave());
        check("una compra propia NO se cruza con la venta propia",
                propio.ejecutado() == 0);
        check("las dos ordenes propias conviven en el libro",
                !svc.libro(ITEM, COMPRA).isEmpty() && !svc.libro(ITEM, VENTA).isEmpty());
        check("se puede cancelar la propia", svc.cancelar(a, ordenDe(svc, a, COMPRA)).ok());

        // --- ⚠⚠ EL PRECIO DE EJECUCION ES EL DEL LIBRO
        // b puja 900 contra una venta que estaba a 500: paga 500 y se le
        // devuelve la diferencia. Si se cobrara lo ofrecido, poner una orden
        // generosa seria un castigo y nadie pondria ordenes por encima del
        // minimo -- que es lo que mata la liquidez.
        long antesB = economy.balance(b, Currency.POKEDOLLAR);
        long antesA = economy.balance(a, Currency.POKEDOLLAR);
        var cruce = svc.poner(b, COMPRA, ITEM, 900, 4, clave());
        check("la compra cruza contra la venta que habia", cruce.ok());
        check("ejecuta las 4 unidades", cruce.ejecutado() == 4);
        check("SE EJECUTA AL PRECIO DEL LIBRO (500) y no al ofrecido (900)",
                cruce.gastado() == 4 * 500);
        check("al comprador se le cobra SOLO lo ejecutado",
                economy.balance(b, Currency.POKEDOLLAR) == antesB - 4 * 500);

        // El vendedor cobra el neto: el impuesto progresivo se destruye.
        long bruto = 4 * 500;
        long impuesto = net.pokereport.luna.gts.GtsService.taxFor(bruto);
        check("el vendedor cobra el neto (bruto menos impuesto)",
                economy.balance(a, Currency.POKEDOLLAR) == antesA + bruto - impuesto);
        check("el impuesto es mayor que cero", impuesto > 0);

        // --- ⚠ SUMA CERO. Es el unico invariante que caza dinero creado.
        check("nada se crea: lo que pierde el comprador == lo que gana el "
                + "vendedor + impuesto",
                (antesB - economy.balance(b, Currency.POKEDOLLAR))
                        == (economy.balance(a, Currency.POKEDOLLAR) - antesA) + impuesto);

        // --- el llenado parcial deja el resto vivo
        check("la venta queda a medias, no cerrada",
                svc.mias(a).stream().anyMatch(o -> o.itemId().equals(ITEM)
                        && o.quedan() == 6));
        check("los objetos comprados quedan APUNTADOS como deuda",
                svc.deudas(b).stream().anyMatch(d -> d.itemId().equals(ITEM)
                        && d.qty() == 4));
        // ⚠ Los objetos NO se meten en el inventario al cruzar: el comprador
        //   puede estar desconectado, y un inventario solo existe mientras su
        //   dueño esta dentro. Es la leccion que el GTS aprendio en V006.
        check("una deuda no se entrega dos veces",
                svc.marcarEntregada(svc.deudas(b).get(0).id())
                        && !svc.marcarEntregada(svc.deudas(b).stream()
                                .findFirst().map(d -> d.id()).orElse(-1L)));

        // --- ⚠ CANCELAR DEVUELVE LO NO EJECUTADO, no lo pedido
        long antesCancel = economy.balance(b, Currency.POKEDOLLAR);
        var pendiente = svc.poner(b, COMPRA, ITEM, 300, 10, clave());
        check("una compra por debajo del libro no cruza", pendiente.ejecutado() == 0);
        check("y retiene el dinero AL PONERLA",
                economy.balance(b, Currency.POKEDOLLAR) == antesCancel - 3_000);
        long orden = ordenDe(svc, b, COMPRA);
        check("se cancela", svc.cancelar(b, orden).ok());
        check("y devuelve EXACTAMENTE lo retenido",
                economy.balance(b, Currency.POKEDOLLAR) == antesCancel);
        check("cancelar dos veces la misma orden se rechaza",
                !svc.cancelar(b, orden).ok());
        // ⚠ El jugador B intentando cancelar la orden de A. La primera version
        //   decia A --o sea, su PROPIA orden-- y comprobaba que fallara, que es
        //   justo lo contrario de lo que tiene que pasar. Lo cazo el autotest en
        //   su primera ejecucion: la prueba estaba mal, el codigo bien.
        check("no se puede cancelar la orden de otro",
                !svc.cancelar(b, ordenDe(svc, a, VENTA)).ok());

        // --- los topes, que son lo que impide reventar la base y el long
        check("la cantidad se acota: 2.000 millones no entra tal cual",
                svc.poner(b, COMPRA, ITEM, 1, Integer.MAX_VALUE, clave()).ok());
        check("y queda acotada al maximo",
                svc.mias(b).stream().anyMatch(o -> o.total()
                        == net.pokereport.luna.market.MarketService.MAX_CANTIDAD));
        // ⚠ Precio x cantidad tiene que seguir cabiendo en un long. Con los
        //   topes de hoy el peor producto es 10^12, y un long llega a 9,2x10^18.
        check("precio maximo x cantidad maxima no desborda el long",
                net.pokereport.luna.market.MarketService.MAX_PRECIO
                        < Long.MAX_VALUE
                          / net.pokereport.luna.market.MarketService.MAX_CANTIDAD);

        // --- limpieza de las ordenes que deja la prueba
        for (var o : svc.mias(a)) {
            svc.cancelar(a, o.id());
        }
        for (var o : svc.mias(b)) {
            svc.cancelar(b, o.id());
        }
        check("tras cancelar todo, el libro queda vacio",
                svc.libro(ITEM, COMPRA).isEmpty() && svc.libro(ITEM, VENTA).isEmpty());
    }

    /**
     * EL TASADOR: que el orden de los precios tenga sentido.
     *
     * <p>⚠⚠ Aqui NO se comprueban cifras concretas, y es a proposito: los
     * numeros del tasador son provisionales como toda la economia, asi que una
     * prueba que dijera «un Pikachu vale 1.240» se rompe el dia que se calibre y
     * no habria enseñado nada.
     *
     * <p>Lo que se fija son las <b>relaciones de orden</b>, que son las que
     * tienen que seguir siendo ciertas pase lo que pase: un shiny vale mas que
     * el mismo sin brillo, un 6x31 mas que uno mediocre, un legendario mas que
     * un comun. Si alguna de esas se invierte, el tasador esta roto por mucho
     * que las cifras parezcan razonables.
     */
    private void testTasador() {
        var COMUN = net.pokereport.luna.market.Tasador.Rareza.COMUN;
        var LEGENDARIO = net.pokereport.luna.market.Tasador.Rareza.LEGENDARIO;
        var MITICO = net.pokereport.luna.market.Tasador.Rareza.MITICO;

        // Un ejemplar corriente de referencia: 500 BST, nivel 50, IVs mediocres.
        var normal = ficha("pikachu", 50, false, 500, COMUN, 90, 0, 0, false);
        long base = net.pokereport.luna.market.Tasador.formula(normal);
        check("un Pokemon corriente vale algo", base > 0);
        check("nadie vale menos que el minimo",
                net.pokereport.luna.market.Tasador.formula(
                        ficha("magikarp", 1, false, 200, COMUN, 0, 0, 0, false))
                        >= net.pokereport.luna.market.Tasador.MINIMO);

        // --- SHINY. Va aparte de todo lo demas: un shiny malo sigue siendo un
        //     shiny, asi que su valor no depende de sus numeros.
        var shiny = ficha("pikachu", 50, true, 500, COMUN, 90, 0, 0, false);
        check("un shiny vale mas que el mismo sin brillo",
                net.pokereport.luna.market.Tasador.formula(shiny) > base);
        check("y MUCHO mas, no un poco",
                net.pokereport.luna.market.Tasador.formula(shiny) > base * 5);

        // --- IVs. Se miden dos veces y las dos cuentan.
        var buenos = ficha("pikachu", 50, false, 500, COMUN, 186, 6, 0, false);
        var mediocres = ficha("pikachu", 50, false, 500, COMUN, 90, 0, 0, false);
        check("un 6x31 vale mas que uno mediocre",
                net.pokereport.luna.market.Tasador.formula(buenos)
                        > net.pokereport.luna.market.Tasador.formula(mediocres));
        // ⚠ ESTA ES LA QUE IMPORTA: el mercado competitivo no paga por «180 de
        //   186», paga por CUANTOS 31. Con solo el total, estos dos serian
        //   indistinguibles.
        var seisPerfectos = ficha("pikachu", 50, false, 500, COMUN, 156, 5, 0, false);
        var sinPerfectos = ficha("pikachu", 50, false, 500, COMUN, 156, 0, 0, false);
        check("a igual TOTAL de IVs, gana quien tiene mas PERFECTOS",
                net.pokereport.luna.market.Tasador.formula(seisPerfectos)
                        > net.pokereport.luna.market.Tasador.formula(sinPerfectos));

        // --- EVs y nivel: valen porque son TRABAJO.
        check("los EVs entrenados suben el precio",
                net.pokereport.luna.market.Tasador.formula(
                        ficha("pikachu", 50, false, 500, COMUN, 90, 0, 508, false)) > base);
        check("el nivel sube el precio",
                net.pokereport.luna.market.Tasador.formula(
                        ficha("pikachu", 100, false, 500, COMUN, 90, 0, 0, false)) > base);
        check("la habilidad oculta sube el precio",
                net.pokereport.luna.market.Tasador.formula(
                        ficha("pikachu", 50, false, 500, COMUN, 90, 0, 0, true)) > base);

        // --- RAREZA. El orden tiene que ser el que dice la enum.
        var leg = ficha("mewtwo", 50, false, 500, LEGENDARIO, 90, 0, 0, false);
        var mit = ficha("mew", 50, false, 500, MITICO, 90, 0, 0, false);
        check("un legendario vale mas que un comun",
                net.pokereport.luna.market.Tasador.formula(leg) > base);
        check("un mitico vale mas que un legendario",
                net.pokereport.luna.market.Tasador.formula(mit)
                        > net.pokereport.luna.market.Tasador.formula(leg));

        // --- BST. Mas estadisticas, mas precio.
        check("mas estadisticas base, mas precio",
                net.pokereport.luna.market.Tasador.formula(
                        ficha("dragonite", 50, false, 600, COMUN, 90, 0, 0, false)) > base);

        // --- LAS ETIQUETAS DE COBBLEMON
        // ⚠ De mas rara a menos: una especie puede llevar VARIAS, y tiene que
        //   ganar la mas alta. Al reves, un mitico se tasaria como legendario.
        check("mythical gana a legendary cuando van juntas",
                net.pokereport.luna.market.Tasador.Rareza.de(
                        java.util.List.of("legendary", "mythical")) == MITICO);
        check("legendary se reconoce", net.pokereport.luna.market.Tasador.Rareza.de(
                java.util.List.of("gen1", "legendary")) == LEGENDARIO);
        check("sin etiquetas de rareza es COMUN",
                net.pokereport.luna.market.Tasador.Rareza.de(
                        java.util.List.of("gen1", "kantonian_form")) == COMUN);
        check("una lista nula no revienta",
                net.pokereport.luna.market.Tasador.Rareza.de(null) == COMUN);

        // --- LA MEDIANA, que es lo que protege de la manipulacion
        // ⚠ Con MEDIA, dos cuentas haciendose una venta absurda mueven la
        //   referencia. Con MEDIANA hacen falta mas operaciones falsas que
        //   reales, que es un liston mucho mas alto por el mismo esfuerzo.
        check("la mediana ignora un valor absurdo",
                net.pokereport.luna.market.Tasador.mediana(
                        java.util.List.of(1.0, 1.1, 0.9, 1.0, 900.0)) < 2.0);
        check("la mediana de una lista vacia es neutra",
                net.pokereport.luna.market.Tasador.mediana(java.util.List.of()) == 1.0);
        check("la mediana de un par promedia los dos de en medio",
                net.pokereport.luna.market.Tasador.mediana(
                        java.util.List.of(1.0, 2.0, 3.0, 4.0)) == 2.5);

        // --- SIN VENTAS, EL TASADOR ES PURA FORMULA
        try {
            var t = new net.pokereport.luna.market.Tasador(db)
                    .tasar(ficha("__especie_que_no_existe__", 50, false, 500,
                            COMUN, 90, 0, 0, false));
            check("sin ventas, el estimado ES la formula", t.estimado() == t.formula());
            check("sin ventas, la correccion es neutra", t.correccion() == 1.0);
            check("sin ventas, se dice que es una estimacion",
                    t.ventasVistas() == 0 && !t.explicacion().isBlank());
        } catch (Exception e) {
            fail("tasar sin ventas", e.toString());
        }
    }

    private static net.pokereport.luna.market.Tasador.Ficha ficha(
            String especie, int nivel, boolean shiny, int bst,
            net.pokereport.luna.market.Tasador.Rareza rareza,
            int ivTotal, int perfectos, int evTotal, boolean oculta) {
        return new net.pokereport.luna.market.Tasador.Ficha(especie, nivel, shiny,
                bst, rareza, ivTotal, perfectos, evTotal, oculta);
    }

    /** El identificador de la primera orden viva de alguien por un lado. */
    private long ordenDe(net.pokereport.luna.market.MarketService svc, long playerId,
                         net.pokereport.luna.market.MarketService.Lado lado)
            throws Exception {
        return svc.mias(playerId).stream().filter(o -> o.lado() == lado)
                .findFirst().map(o -> o.id()).orElse(-1L);
    }

    /** Cada operacion economica necesita la suya (R4). */
    private static String clave() {
        return "autotest_clan_" + UUID.randomUUID();
    }

    private void check(String name, boolean ok) {
        if (ok) {
            passed++;
            out.accept("  §a✔ §7" + name);
        } else {
            fail(name, "no se cumple");
        }
    }

    private void fail(String name, String detail) {
        failed++;
        out.accept("  §c✘ " + name + " §8(" + detail + ")");
        LunaEternal.LOG.error("Autotest FALLO: {} — {}", name, detail);
    }
}
