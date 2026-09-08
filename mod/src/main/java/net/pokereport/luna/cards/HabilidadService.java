package net.pokereport.luna.cards;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.pokereport.luna.LunaEternal;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail;
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail;
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence;
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawnerFactory;
import com.cobblemon.mod.common.pokemon.Species;

/**
 * La habilidad de aparición de las cartas: activas una, y durante 5 minutos
 * su especie tiene más probabilidad de aparecer salvaje. Una hora de espera
 * entre activaciones.
 *
 * <p>Detalle y motivos en {@code docs/analysis/cobblemon-cards.md} §7. En
 * corto, decisión del usuario (2026-09-03).
 *
 * <h2>⚠⚠⚠ EL MULTIPLICADOR SALE DE LA RAREZA DE LA CARTA, NO DE UNA NOTA
 * INVENTADA — y está medido contra los pesos reales de Cobblemon</h2>
 *
 * Se extrajo {@code spawn_pool_world} del jar de Cobblemon 1.7.3 que corre
 * este servidor y se leyeron los pesos de verdad: común 4,5-9 · poco común
 * 0,3-84 (mucha varianza ya de fábrica) · rara 1,5-7,5. Con esa vara de medir,
 * un techo de ×1,3 a ×2,7 según rareza es un movimiento real —se nota
 * buscando esa especie— sin salirse del rango que el propio juego ya maneja
 * entre sus propias entradas.
 *
 * <p>La NOTA (de la estación de calificación) no decide el techo: lo afina
 * dentro de él. Con la restauradora retirada, es lo único que le da valor de
 * juego a calificar — antes de esto, calificar era poner un número bonito.
 *
 * <h2>⚠⚠⚠ LOS LEGENDARIOS DE VERDAD QUEDAN FUERA, Y NO POR UNA LISTA</h2>
 *
 * Comprobado en el jar de Cobblemon: los once legendarios que ya tiene este
 * proyecto en Tesoros (Mewtwo, Mew, Lugia, Ho-Oh, Celebi y los demás) **no
 * tienen ni una entrada en {@code spawn_pool_world}**. No aparecen salvajes en
 * absoluto en el Cobblemon base — solo existen aquí vía el cofre de D-020, que
 * ya trae probabilidades públicas, piedad e idempotencia.
 *
 * <p>Por eso esta habilidad NO necesita una lista de especies prohibidas: el
 * multiplicador actúa sobre un peso que ya existe, y si el peso es cero, cero
 * por cualquier cosa sigue siendo cero. Una lista habría que mantenerla a
 * mano —y una lista sin mantener es donde se esconden los fallos de
 * verdad—; esto se protege solo, con los datos del propio juego. Si algún día
 * se decide dar spawn salvaje a esos once (una decisión aparte, del calibre
 * de D-020), la habilidad los cubriría sin tocar una línea aquí.
 *
 * <h2>⚠⚠ SIN COMPILAR CONTRA EL JAR DE CARTAS</h2>
 *
 * Qué especie y qué rareza tiene una carta se lee del {@code custom_data} de
 * vainilla que {@code cards/parchear.py} espeja en cada carta al crearla
 * ({@code luna_especie}/{@code luna_rareza}/{@code luna_nota}) — no del
 * {@code DataComponentType} propio del mod, que exigiría compilar contra su
 * jar. Sí compilamos contra Cobblemon, que este mod ya usa en media docena de
 * sitios; resolver la especie y mirar sus {@code labels} es API de Cobblemon,
 * no de las cartas.
 */
public final class HabilidadService {

    private HabilidadService() {}

    private static final Identifier CARTA_ITEM =
            Identifier.of("cobblemon-cards", "card");

    /** Cuánto dura la ventana activa. */
    public static final long DURACION_MS = 5 * 60_000L;

    /** Cuánto hay que esperar desde que se activó, para volver a activar. */
    public static final long ESPERA_MS = 60 * 60_000L;

    /**
     * Techo del multiplicador por rareza de carta (nota 10). El suelo
     * (sin calificar, nota 0) es la mitad de lo que sube por encima de 1.
     *
     * <p>⚠ Provisional, como los precios de la tienda: se ancla a los pesos
     * medidos de Cobblemon, no a un análisis de economía que todavía no
     * existe.
     */
    private static final Map<String, Float> TECHO = Map.of(
            "common", 1.30f,
            "uncommon", 1.50f,
            "rare", 1.80f,
            "epic", 2.10f,
            "legendary", 2.40f,
            "mythic", 2.70f);

    /** El multiplicador para esta rareza y esta nota (0-10). */
    public static float multiplicador(String rareza, int nota) {
        float techo = TECHO.getOrDefault(rareza, 1.0f);
        float suelo = 1.0f + (techo - 1.0f) * 0.4f;
        float n = Math.max(0, Math.min(10, nota)) / 10.0f;
        return suelo + (techo - suelo) * n;
    }

    // ---------------------------------------------------------------- leer

    /** @param segActiva > 0 si la ventana sigue abierta, 0 si no */
    public record Estado(String especie, String rareza, float multiplicador,
                         long segActiva, long segEspera) {}

    public static Estado estado(long playerId) throws SQLException {
        try (Connection c = LunaEternal.database().connection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT species, rarity, grade, started_ms "
                             + "FROM card_ability WHERE player_id = ?")) {
            ps.setLong(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new Estado("", "", 0, 0, 0);
                }
                long ahora = System.currentTimeMillis();
                long inicio = rs.getLong("started_ms");
                long transcurrido = ahora - inicio;
                long segActiva = Math.max(0, (DURACION_MS - transcurrido) / 1000);
                long segEspera = Math.max(0, (DURACION_MS + ESPERA_MS - transcurrido) / 1000);
                float mult = multiplicador(rs.getString("rarity"), rs.getInt("grade"));
                return new Estado(rs.getString("species"), rs.getString("rarity"),
                        mult, segActiva, segActiva > 0 ? 0 : segEspera);
            }
        }
    }

    // -------------------------------------------------------------- activar

    public record Resultado(boolean ok, String mensaje) {}

    /**
     * Activa la habilidad con la carta que el jugador tiene en la mano.
     *
     * <p>⚠ No lee la base en el hilo del servidor (R1): se llama desde
     * {@code LunaEternal.submit}.
     */
    public static Resultado activar(ServerPlayerEntity player, ItemStack carta) {
        if (Registries.ITEM.get(CARTA_ITEM) == Items.AIR
                || Registries.ITEM.getId(carta.getItem()) == null
                || !CARTA_ITEM.equals(Registries.ITEM.getId(carta.getItem()))) {
            return new Resultado(false, "§cEso no es una carta.");
        }
        NbtCompound tag = leerEtiqueta(carta);
        if (tag == null || !tag.contains("luna_especie")) {
            // ⚠ Una carta de antes de este parche no lleva el espejo. No es
            //   un error del jugador: se dice que hace falta una nueva.
            return new Resultado(false,
                    "§cEsta carta es de antes de la habilidad. Abre un sobre nuevo.");
        }
        String especie = tag.getString("luna_especie");
        String rareza = tag.getString("luna_rareza");
        int nota = tag.contains("luna_nota") ? tag.getInt("luna_nota") : 0;

        Species sp = PokemonSpecies.INSTANCE.getByName(especie.toLowerCase(Locale.ROOT));
        if (sp == null) {
            return new Resultado(false, "§cNo se reconoce la especie de esta carta.");
        }
        var labels = sp.getLabels();
        boolean esLegendarioDeVerdad = labels.contains("legendary") || labels.contains("mythical")
                || labels.contains("restricted");

        var perfil = player.getGameProfile();
        try {
            long id = LunaEternal.players().resolve(perfil.getId(), perfil.getName());
            try (Connection c = LunaEternal.database().connection()) {
                long ahora = System.currentTimeMillis();
                long inicioPrevio = 0;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT started_ms FROM card_ability WHERE player_id = ? FOR UPDATE")) {
                    ps.setLong(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            inicioPrevio = rs.getLong(1);
                        }
                    }
                }
                if (ahora < inicioPrevio + DURACION_MS + ESPERA_MS) {
                    long faltan = (inicioPrevio + DURACION_MS + ESPERA_MS - ahora) / 1000;
                    return new Resultado(false,
                            "§cTodavía no. Vuelve en " + falta(faltan) + ".");
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO card_ability (player_id, species, rarity, grade, started_ms) "
                                + "VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE "
                                + "species = VALUES(species), rarity = VALUES(rarity), "
                                + "grade = VALUES(grade), started_ms = VALUES(started_ms)")) {
                    ps.setLong(1, id);
                    ps.setString(2, especie);
                    ps.setString(3, rareza);
                    ps.setInt(4, nota);
                    ps.setLong(5, ahora);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            LunaEternal.LOG.error("No se pudo activar la habilidad de la carta", e);
            return new Resultado(false, "§cNo se pudo activar. Inténtalo de nuevo.");
        }

        float mult = multiplicador(rareza, nota);
        ACTIVOS.put(player.getUuid(), new Activa(especie.toLowerCase(Locale.ROOT),
                mult, System.currentTimeMillis() + DURACION_MS));

        // ⚠ Se dice la verdad aunque sea un Pokemon que no va a aparecer:
        //   un mensaje distinto para "no funciona" invitaria a gastar la
        //   hora de espera comprobandolo, cuando ya se sabe de antemano.
        String extra = esLegendarioDeVerdad
                ? " §7(no aparece salvaje en este servidor: la habilidad no hara nada)"
                : "";
        return new Resultado(true, String.format(Locale.ROOT,
                "§a¡Habilidad activada! §7%s durante 5 min, ×%.2f de aparición.%s",
                especie, mult, extra));
    }

    private static NbtCompound leerEtiqueta(ItemStack stack) {
        var datos = stack.get(net.minecraft.component.DataComponentTypes.CUSTOM_DATA);
        return datos == null ? null : datos.copyNbt();
    }

    private static String falta(long s) {
        if (s >= 3600) {
            return (s / 3600) + " h " + ((s % 3600) / 60) + " min";
        }
        return s >= 60 ? (s / 60) + " min" : s + " s";
    }

    // ------------------------------------------------------- la influencia

    /** Species → multiplicador, mientras la ventana siga abierta. */
    private record Activa(String especie, float multiplicador, long expiraMs) {
        boolean vigente() {
            return System.currentTimeMillis() < expiraMs;
        }
    }

    /**
     * ⚠⚠ SOLO EN MEMORIA, NUNCA SE LEE LA BASE AQUÍ. {@code affectWeight} lo
     * llama Cobblemon por cada candidato de cada intento de aparición: es el
     * camino más caliente que toca este sistema, y una consulta ahí sería
     * consultar la base en el hilo del servidor en el peor sitio posible.
     *
     * <p>Si el servidor reinicia a mitad de la ventana de 5 minutos, se
     * pierde — igual que el cooldown de curar. No es un exploit: lo que
     * protege de verdad (la hora de espera) vive en la base.
     */
    private static final Map<UUID, Activa> ACTIVOS = new ConcurrentHashMap<>();

    private static boolean influenciaRegistrada = false;

    /** Engancha la influencia de aparición. Se llama una sola vez al arrancar. */
    public static void registrarInfluencia() {
        if (influenciaRegistrada) {
            return;
        }
        influenciaRegistrada = true;
        PlayerSpawnerFactory.INSTANCE.getInfluenceBuilders().add(Influencia::new);
    }

    private static final class Influencia implements SpawningInfluence {
        private final ServerPlayerEntity jugador;

        Influencia(ServerPlayerEntity jugador) {
            this.jugador = jugador;
        }

        @Override
        public boolean isExpired() {
            return jugador.isRemoved();
        }

        @Override
        public float affectWeight(SpawnDetail detalle, SpawnablePosition posicion, float peso) {
            Activa activa = ACTIVOS.get(jugador.getUuid());
            if (activa == null) {
                return peso;
            }
            if (!activa.vigente()) {
                ACTIVOS.remove(jugador.getUuid(), activa);
                return peso;
            }
            if (!(detalle instanceof PokemonSpawnDetail detallePokemon)) {
                return peso;
            }
            var propiedades = detallePokemon.getPokemon();
            String nombre = propiedades == null ? null : propiedades.getSpecies();
            if (nombre == null || !nombre.equalsIgnoreCase(activa.especie())) {
                return peso;
            }
            return peso * activa.multiplicador();
        }
    }
}
