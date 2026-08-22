package net.pokereport.luna.cosmetics;

import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Comprar y equipar cosméticos.
 *
 * <p>Con {@code D-039} —solo se consiguen comprándolos o en eventos— este
 * servicio es el <b>único</b> sitio por donde un cosmético puede entrar en la
 * colección de alguien. No hay botín, ni receta, ni comando de mundo que lo
 * conceda, así que todo el anti-abuso cabe aquí.
 */
public class CosmeticsService {

    private final Database db;

    public CosmeticsService(Database db) {
        this.db = db;
    }

    /** Lo que posee un jugador. */
    public Set<String> poseidos(long playerId) throws SQLException {
        Set<String> out = new HashSet<>();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT cosmetic_id FROM player_cosmetics WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    /** Lo que lleva puesto, por categoría. */
    /**
     * Quien tiene que cosmetico RETIRADO, y cuanto pago. Ver `/luna cosmeticos huerfanos`.
     *
     * <p>Se filtra en Java y no en SQL a proposito: el catalogo vive en el codigo,
     * no en la base, asi que la consulta no puede saber que es un huerfano. Son
     * pocas filas --una por cosmetico comprado en todo el servidor-- y traerlas
     * enteras es mas simple que mantener una lista de identificadores en SQL que
     * habria que regenerar en cada despliegue.
     */
    public java.util.List<String> huerfanos() throws SQLException {
        var salida = new java.util.ArrayList<String>();
        // ⚠ La columna es `username`, no `name`. Lo escribi de memoria y
        //   MariaDB lo caza al ejecutar, no al compilar: el fallo salio en
        //   produccion como un WARN en el log y una respuesta vacia.
        String sql = "SELECT p.username, c.cosmetic_id, c.precio_pagado, c.origen "
                + "FROM player_cosmetics c JOIN player p ON p.player_id = c.player_id "
                + "ORDER BY p.username, c.cosmetic_id";
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString(2);
                if (Catalogo.de(id) != null) {
                    continue;
                }
                salida.add(String.format("%-16s %-34s %d LunaCoins (%s)",
                        rs.getString(1), id, rs.getLong(3), rs.getString(4)));
            }
        }
        return salida;
    }

    public Map<String, String> equipados(long playerId) throws SQLException {
        Map<String, String> out = new HashMap<>();
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT categoria, cosmetic_id FROM player_cosmetic_equipped "
                             + "WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return out;
    }

    public record Resultado(boolean ok, String mensaje) {
        static Resultado si() {
            return new Resultado(true, "");
        }

        static Resultado no(String m) {
            return new Resultado(false, m);
        }
    }

    /**
     * Compra un cosmético: cobra y anota la posesión <b>en la misma
     * transacción</b>.
     *
     * <p>⚠ Las dos cosas van juntas o no va ninguna. Cobrar fuera de la
     * transacción abre la única ventana que de verdad duele: descontar
     * LunaCoins —que se compran con dinero real (D-013)— y que la anotación
     * falle. El jugador se queda sin saldo y sin cosmético, y eso no se arregla
     * solo.
     *
     * <p>⚠⚠ La defensa contra comprar dos veces <b>no es</b> la comprobación de
     * más abajo: es la clave primaria {@code (player_id, cosmetic_id)}. La
     * comprobación evita cobrar en el caso normal, pero dos clics rápidos
     * pueden pasarla los dos. Lo que impide el doble cobro es que la segunda
     * inserción choca contra la clave y deshace su transacción entera.
     *
     * @param clave clave de idempotencia (R4). <b>Un UUID nuevo por peticion</b>,
     *              NO una clave derivada del jugador y el cosmetico: esa se
     *              probo y abria un agujero -- si alguna vez se le retira el
     *              cosmetico a alguien y lo vuelve a comprar, la clave ya esta
     *              usada, la economia contesta {@code ALREADY_APPLIED}, el cobro
     *              se salta y el {@code INSERT} si entra. Cosmetico gratis, sin
     *              error. El doble clic ya lo cubren la transaccion y la clave
     *              primaria
     */
    public Resultado comprar(long playerId, String cosmeticId, String clave)
            throws SQLException {

        Catalogo.Pieza pieza = Catalogo.de(cosmeticId);
        if (pieza == null) {
            // Un identificador que no está en el catálogo. El cliente no debería
            // poder mandarlo, y justamente por eso se comprueba: lo que "no
            // debería poder pasar" es lo que hay que rechazar explícitamente.
            return Resultado.no("Ese cosmético no existe.");
        }
        if (pieza.precio() <= 0) {
            // Precio 0 es "no está a la venta", no "gratis" (D-039). Sin esta
            // línea, todo cosmético de evento sería gratis para cualquiera.
            return Resultado.no("Ese cosmético no está a la venta: solo sale en eventos.");
        }

        try (Connection c = db.connection()) {
            c.setAutoCommit(false);
            try {
                if (tiene(c, playerId, cosmeticId)) {
                    c.rollback();
                    return Resultado.no("Ya tienes ese cosmético.");
                }

                LunaEternal.economy().applyInTransaction(
                        c, playerId, Currency.REPORTCOIN, -pieza.precio(),
                        "cosmetico_compra", "cosmetic", null, clave);

                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO player_cosmetics
                            (player_id, cosmetic_id, origen, precio_pagado)
                        VALUES (?, ?, 'compra', ?)
                        """)) {
                    ps.setLong(1, playerId);
                    ps.setString(2, cosmeticId);
                    ps.setInt(3, pieza.precio());
                    ps.executeUpdate();
                }

                c.commit();
                return Resultado.si();
            } catch (EconomyException e) {
                c.rollback();
                // Se distingue por tipo. Antes todo fallo economico decia "no
                // tienes suficientes", asi que un ALREADY_APPLIED --que es un
                // fallo NUESTRO de claves-- se le echaba al saldo del jugador y
                // nadie lo habria investigado nunca.
                return switch (e.kind) {
                    case INSUFFICIENT_FUNDS -> Resultado.no("No tienes LunaCoins suficientes.");
                    default -> {
                        LunaEternal.LOG.warn("Compra de {} por el jugador {} rechazada: {} ({})",
                                cosmeticId, playerId, e.kind, e.getMessage());
                        yield Resultado.no("No se pudo completar la compra.");
                    }
                };
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * Concede un cosmético sin cobrar: eventos y regalos.
     *
     * <p>Es la <b>otra</b> vía de D-039, y la que evita que la tienda se apague
     * sola: si todo fuera de pago, los únicos que llevarían cosméticos serían
     * los que pagan, y un cosmético que nadie ve no vale nada.
     *
     * <p>No pasa por la economía —no hay nada que cobrar— pero sí por la misma
     * clave primaria, así que conceder dos veces es inofensivo.
     */
    public Resultado conceder(long playerId, String cosmeticId, String origen)
            throws SQLException {
        if (Catalogo.de(cosmeticId) == null) {
            return Resultado.no("Ese cosmético no existe.");
        }
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT IGNORE INTO player_cosmetics
                         (player_id, cosmetic_id, origen, precio_pagado)
                     VALUES (?, ?, ?, 0)
                     """)) {
            ps.setLong(1, playerId);
            ps.setString(2, cosmeticId);
            ps.setString(3, "evento".equals(origen) ? "evento" : "regalo");
            ps.executeUpdate();
        }
        return Resultado.si();
    }

    /**
     * Se pone un cosmético que ya posee, o se lo quita.
     *
     * <p>⚠ <b>Comprobar la posesión aquí no es redundante.</b> Sin esta línea,
     * un cliente modificado podría equiparse cualquier cosa del catálogo sin
     * haberla comprado — y como el equipado es lo que ven los demás, sería
     * indistinguible de haberla comprado. La tienda se quedaría sin sentido sin
     * que nadie tocara la tabla de posesión.
     *
     * @param cosmeticId {@code null} o vacío para quitarse el de esa categoría
     */
    public Resultado equipar(long playerId, String categoria, String cosmeticId)
            throws SQLException {

        if (cosmeticId == null || cosmeticId.isEmpty()) {
            try (Connection c = db.connection();
                 PreparedStatement ps = c.prepareStatement(
                         "DELETE FROM player_cosmetic_equipped "
                                 + "WHERE player_id = ? AND categoria = ?")) {
                ps.setLong(1, playerId);
                ps.setString(2, categoria);
                ps.executeUpdate();
            }
            return Resultado.si();
        }

        Catalogo.Pieza pieza = Catalogo.de(cosmeticId);
        if (pieza == null || !pieza.categoria().equals(categoria)) {
            return Resultado.no("Ese cosmético no existe en esa categoría.");
        }

        try (Connection c = db.connection()) {
            if (!tiene(c, playerId, cosmeticId)) {
                return Resultado.no("No tienes ese cosmético.");
            }
            // REPLACE y no INSERT: la clave (jugador, categoria) garantiza una
            // sola pieza puesta, y reemplazar la cambia en UN paso. Con borrar
            // e insertar habría un instante llevando dos, o ninguna.
            try (PreparedStatement ps = c.prepareStatement("""
                    REPLACE INTO player_cosmetic_equipped
                        (player_id, categoria, cosmetic_id)
                    VALUES (?, ?, ?)
                    """)) {
                ps.setLong(1, playerId);
                ps.setString(2, categoria);
                ps.setString(3, cosmeticId);
                ps.executeUpdate();
            }
        }
        return Resultado.si();
    }


    /**
     * Le pone el disfraz a un Pokémon del equipo.
     *
     * <p>Esto sustituye al «llevar una mascota detrás». Los 66 cosméticos de
     * {@code CobblemonMoreCosmetics} son <b>species features</b> de Cobblemon
     * —una bandera con {@code isAspect: true}— así que activarla hace que
     * <b>Cobblemon lo dibuje él</b>: en combate, cuando lo sacas, en el PC y en
     * el resumen. Por eso se ve bien sin que nosotros dibujemos nada.
     *
     * <p>⚠ <b>Se comprueba que la especie coincide.</b> El aspecto {@code knight}
     * solo está asignado a Charizard en el datapack; ponérselo a un Pidgey
     * activaría una bandera que su modelo no conoce, y Cobblemon dibujaría un
     * Pidgey normal. No falla, no avisa, y el jugador ha pagado por nada.
     *
     * <p>⚠⚠ Y se comprueba <b>la posesión</b>, igual que antes. Sin eso un
     * cliente modificado disfraza cualquier cosa sin comprarla — y como el
     * disfraz lo ven los demás, sería indistinguible de haberlo comprado.
     *
     * @param ranura del equipo, 0..5. Se resuelve contra el equipo de ESTE
     *               jugador, así que un Pokémon ajeno no es un estado que se
     *               pueda pedir
     */
    public Resultado disfrazar(net.minecraft.server.network.ServerPlayerEntity jugador,
                               long playerId, String cosmeticId, int ranura)
            throws SQLException {

        Catalogo.Pieza pieza = Catalogo.de(cosmeticId);
        if (pieza == null) {
            return Resultado.no("Ese cosmético no existe.");
        }
        if (!pieza.esDePokemon()) {
            return Resultado.no("Ese cosmético no se le puede poner a un Pokémon.");
        }
        try (Connection c = db.connection()) {
            if (!tiene(c, playerId, cosmeticId)) {
                return Resultado.no("No tienes ese cosmético.");
            }
        }

        // ⚠ RANURA NEGATIVA = "elige tu". El cliente no tiene por que saber en
        //   que ranura esta: preguntarselo obligaba a que leyera su propio
        //   equipo, y eso es justo lo que fallaba -- comparaba por el nombre
        //   VISIBLE de la especie. El servidor tiene el equipo de verdad.
        if (ranura < 0) {
            ranura = primeraRanura(jugador, pieza);
            if (ranura < 0) {
                return Resultado.no("No tienes ningún "
                        + pieza.especie().substring(pieza.especie().indexOf(':') + 1)
                        + " en el equipo.");
            }
        }
        var equipo = com.cobblemon.mod.common.Cobblemon.INSTANCE
                .getStorage().getParty(jugador);
        var pokemon = equipo.get(ranura);
        if (pokemon == null) {
            return Resultado.no("No hay ningún Pokémon en esa ranura.");
        }

        // ⚠⚠ SE COMPARA POR IDENTIFICADOR, NO POR `getName()`.
        //
        //   `getName()` es el nombre PARA MOSTRAR: se traduce, y en algunos
        //   idiomas no coincide con el identificador. Comparando por ahi, el
        //   disfraz "no encaja" en español y si en ingles -- un fallo que
        //   depende del idioma del servidor y que nadie relacionaria con esto.
        //
        //   `getResourceIdentifier()` es `cobblemon:charizard` siempre.
        if (!coincide(pokemon, pieza)) {
            return Resultado.no("Ese disfraz no es para " + pokemon.getSpecies().getName() + ".");
        }

        // ⚠⚠⚠ SE FUERZA EL ASPECTO. NI BANDERA, NI OBJETO. Y LAS TRES FORMAS
        //     SE PROBARON, EN ESTE ORDEN:
        //
        //  1) FlagSpeciesFeature -- leyendo el repositorio de GitHub, que declara
        //     los cosmeticos como `species_features`. No pasaba nada: la version
        //     PUBLICADA del pack no usa eso.
        //
        //  2) swapCosmeticItem -- el sistema `cosmetic_items` nativo de Cobblemon
        //     1.7, que es el que el pack SI usa. Funcionaba... y ABRIA UN AGUJERO
        //     QUE SE LLEVABA LA TIENDA POR DELANTE:
        //
        //       `charizard_knight` se aplica con un `minecraft:iron_helmet`.
        //       Cualquiera craftea uno, se lo da a su Charizard, y tiene el
        //       disfraz de 2.500 LunaCoins gratis. Y al reves: se lo quita por
        //       el menu de Cobblemon y SE QUEDA CON EL OBJETO.
        //
        //     Eso no es un fallo de esta funcion: es que `cosmetic_items` esta
        //     pensado para conseguirse jugando, y D-039 dice exactamente lo
        //     contrario -- solo con LunaCoins o en eventos. Los dos sistemas no
        //     pueden convivir, porque el suyo es una puerta que no podemos
        //     cerrar desde aqui.
        //
        //  3) forcedAspects -- lo que hay ahora. El aspecto va DIRECTO al
        //     Pokemon, y EL DATAPACK DEL PACK NO SE INSTALA: sin el, `cosmetic_items`
        //     no se registra y el objeto no aplica nada. La puerta no se vigila,
        //     se quita.
        //
        // Funciona porque `updateAspects()` hace  aspectos = proveedores +
        // forcedAspects  (verificado en el bytecode de 1.7.3), `PokemonP3` lo
        // guarda y `ClientPokemonP3` lo sincroniza. O sea: persiste y se ve.
        //
        // ⚠ SE CONSERVA lo que hubiera en forcedAspects que no sea nuestro. Un
        //   `forcedAspects = Set.of(aspecto)` a secas borraria lo que pusiera
        //   cualquier otro mod, y el sintoma seria "al ponerme un disfraz se me
        //   quito otra cosa" en un servidor con 158 mods.
        var aspectos = new java.util.HashSet<>(pokemon.getForcedAspects());
        aspectos.removeAll(aspectosDelCatalogo(pieza.especie()));   // uno cada vez
        aspectos.add(pieza.aspecto());
        pokemon.setForcedAspects(aspectos);

        // ⚠ Y SE LIMPIA EL OBJETO COSMETICO, por si el Pokemon venia de la epoca
        //   de (2). Sin esto quedaria un `cosmeticItem` guardado que no pinta
        //   nada hoy, pero que volveria a pintar si alguien reinstalara el
        //   datapack -- un disfraz que reaparece solo meses despues.
        if (!pokemon.getCosmeticItem().isEmpty()) {
            pokemon.setCosmeticItem(net.minecraft.item.ItemStack.EMPTY);
        }
        pokemon.updateAspects();

        return Resultado.si();
    }

    /**
     * Quitarle el disfraz al Pokemon que lo lleve puesto.
     *
     * <p>⚠ <b>NO hace falta la ranura, y pedirla seria un error.</b> El cliente
     * no sabe en que ranura esta —es justo lo que ya fallo una vez, cuando
     * intentaba leer su propio equipo y comparaba especies por el nombre
     * visible—, y ademas la ranura puede haber cambiado desde que se pinto la
     * pantalla: basta con reordenar el equipo. Se busca aqui, contra el equipo
     * de verdad, en el momento de quitarlo.
     *
     * <p><b>Se quita del {@code forcedAspects}</b>, que es donde lo puso
     * {@code disfrazar}. Ver alli por que no es un objeto: {@code cosmetic_items}
     * se aplica con un objeto crafteable, y eso saltaba la tienda entera.
     *
     * <p>⚠ <b>ESTA ES LA UNICA FORMA DE QUITARSELO, y es deliberado.</b> El menu
     * de Cobblemon no lo ofrece porque no hay objeto cosmetico que devolver: sin
     * el datapack, para Cobblemon ese Pokemon no lleva nada puesto, solo tiene un
     * aspecto forzado. Poner y quitar pasan los dos por el PokePad.
     *
     * <p><b>Quitarlo NO lo devuelve al catalogo:</b> sigue comprado. La posesion
     * esta en {@code player_cosmetics} y esto no la toca. Es lo que permite
     * ponerselo y quitarselo sin volver a pagar, que es lo que un jugador espera
     * de un cosmetico.
     */
    public Resultado desvestir(net.minecraft.server.network.ServerPlayerEntity jugador,
                               long playerId, String cosmeticId)
            throws SQLException {

        Catalogo.Pieza pieza = Catalogo.de(cosmeticId);
        if (pieza == null) {
            return Resultado.no("Ese cosmético no existe.");
        }
        // Se comprueba la posesion igual que al ponerlo. No porque quitar algo
        // que no tienes haga daño --no lo hace--, sino porque si el cliente pide
        // quitar un cosmetico que no ha comprado, algo no cuadra y prefiero que
        // se note aqui.
        try (Connection c = db.connection()) {
            if (!tiene(c, playerId, cosmeticId)) {
                return Resultado.no("No tienes ese cosmético.");
            }
        }

        var equipo = com.cobblemon.mod.common.Cobblemon.INSTANCE
                .getStorage().getParty(jugador);
        for (int i = 0; i < 6; i++) {
            var pokemon = equipo.get(i);
            if (pokemon == null || !coincide(pokemon, pieza)
                    || !pokemon.getAspects().contains(pieza.aspecto())) {
                continue;
            }
            var aspectos = new java.util.HashSet<>(pokemon.getForcedAspects());
            aspectos.remove(pieza.aspecto());
            pokemon.setForcedAspects(aspectos);
            if (!pokemon.getCosmeticItem().isEmpty()) {
                pokemon.setCosmeticItem(net.minecraft.item.ItemStack.EMPTY);
            }
            pokemon.updateAspects();
            // Sin mensaje de exito a proposito: el handler solo enseña los
            // fallos, y el aviso de que ha funcionado es la propia pantalla, que
            // vuelve a decir EQUIPAR en cuanto llega el catalogo nuevo.
            return Resultado.si();
        }
        // Que no lo lleve nadie NO ES UN FALLO. La pantalla pudo pintarse antes
        // de que el jugador cambiara de equipo, o de que el Pokemon se fuera al
        // PC. El estado que queria --que no lo lleve nadie-- ya es el que hay,
        // asi que se contesta que si: un error rojo aqui seria mentir sobre algo
        // que esta bien.
        return Resultado.si();
    }

    /**
     * Todos los aspectos que el catalogo puede poner a esa especie.
     *
     * <p>Sirve para <b>quitar el disfraz anterior al poner uno nuevo</b>: un
     * Pokemon lleva uno cada vez. Sin esto, equipar `knight` sobre `steampunk`
     * dejaria los dos aspectos puestos, y lo que se dibuja con dos resolvers
     * compitiendo no lo decide nadie -- saldria uno u otro segun el orden, que
     * es la clase de fallo que solo aparece a veces.
     */
    private static java.util.Set<String> aspectosDelCatalogo(String especie) {
        java.util.Set<String> s = new java.util.HashSet<>();
        for (Catalogo.Pieza p : Catalogo.todas()) {
            if (p.especie().equals(especie) && !p.aspecto().isEmpty()) {
                s.add(p.aspecto());
            }
        }
        return s;
    }

    /** La especie de ese Pokemon es la que pide el cosmetico. Ver `disfrazar`. */
    public static boolean coincide(com.cobblemon.mod.common.pokemon.Pokemon pokemon,
                                   Catalogo.Pieza pieza) {
        if (pokemon == null || pieza.especie().isEmpty()) {
            return false;
        }
        return pieza.especie().equalsIgnoreCase(
                pokemon.getSpecies().getResourceIdentifier().toString());
    }

    /**
     * ¿Hay algún Pokémon del equipo llevando ESTE disfraz ahora mismo?
     *
     * <p>⚠⚠ LA VERDAD ESTA EN EL POKEMON, NO EN UNA TABLA NUESTRA.
     *
     * Antes «equipado» salia de `player_cosmetic_equipped`, que se escribia al
     * equipar. Con el cambio a disfraces eso dejo de ser cierto por dos motivos:
     *
     *   1. El disfraz lo lleva el POKEMON, y puede perderlo por caminos que no
     *      pasan por nosotros -- un comando, otro mod, una transferencia.
     *   2. Aquella tabla guardaba UNO POR CATEGORIA. Ahora puedes tener a la vez
     *      un Charizard con armadura y un Snorlax de cocinero: los dos son
     *      «mascotas», asi que la tabla solo podia recordar uno y mentia sobre
     *      el otro.
     *
     * Leyendolo del equipo no hay nada que sincronizar y no puede desfasarse.
     */
    public static boolean loLleva(net.minecraft.server.network.ServerPlayerEntity jugador,
                                  Catalogo.Pieza pieza) {
        if (pieza.aspecto().isEmpty()) {
            return false;
        }
        var equipo = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(jugador);
        for (int i = 0; i < 6; i++) {
            var p = equipo.get(i);
            if (p != null && coincide(p, pieza) && p.getAspects().contains(pieza.aspecto())) {
                return true;
            }
        }
        return false;
    }

    /** La primera ranura del equipo que sirve para ese cosmetico, o -1. */
    public static int primeraRanura(net.minecraft.server.network.ServerPlayerEntity jugador,
                                    Catalogo.Pieza pieza) {
        var equipo = com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getParty(jugador);
        for (int i = 0; i < 6; i++) {
            if (coincide(equipo.get(i), pieza)) {
                return i;
            }
        }
        return -1;
    }

    private boolean tiene(Connection c, long playerId, String cosmeticId)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM player_cosmetics WHERE player_id = ? AND cosmetic_id = ?")) {
            ps.setLong(1, playerId);
            ps.setString(2, cosmeticId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
