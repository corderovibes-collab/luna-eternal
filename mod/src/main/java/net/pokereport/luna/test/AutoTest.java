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

    private int passed = 0;
    private int failed = 0;

    public AutoTest(Database db, PlayerService players, EconomyService economy,
                    Consumer<String> out) {
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
            testPokedex(a);
            testKits(a);
            testQuests(a);
            testTelemetria(a, b);
            testCazas(a);
            testVozPokedex();

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
        // Victreebel (dex 71) es la unica especie de Gen 1 sin grabar: se
        // salto en la sesion de grabacion y queda como el caso real de
        // "sin voz", en vez de uno inventado.
        check("una especie sin grabar no da clave",
            net.pokereport.luna.pokedex.VozService.clave("Victreebel", "").isEmpty());
        check("hay las 255 voces del catalogo (150 de Kanto sin Victreebel, "
                + "100 de Johto, mas 5 formas de Alola)",
            net.pokereport.luna.pokedex.VozService.cuantas() == 255);
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
            !net.pokereport.luna.pokedex.VozService.tieneVoz("victreebel"));
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
