package net.pokereport.luna.crianza;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyException;
import net.pokereport.luna.economy.EconomyService;
import net.pokereport.luna.ui.Tablist;

/**
 * Servicio de Crianza del PokéPad (Luna Eternal).
 *
 * <p>Dos ranuras gratuitas, tres expansiones de 150 LC y dos por rango.
 *
 * <p>Persiste en la tabla {@code crianza_ranura} y aplica reglas canónicas de
 * compatibilidad por Grupos Huevo y Ditto con Cobblemon API.
 */
public final class CrianzaService {

    public static final int TOTAL_RANURAS = 7;
    public static final long PRECIO_RANURA_LUNACOINS = 150L;
    public static final long DURACION_BASE_MS = 25 * 60 * 1000L;

    /** La duración se fija al iniciar un ciclo y persiste aunque cambie el rango. */
    public static long duracionPorRango(int escalon) {
        if (escalon >= Tablist.Rank.LEYENDA.escalon) return 5 * 60_000L;
        if (escalon >= Tablist.Rank.MAESTRO.escalon) return 10 * 60_000L;
        if (escalon >= Tablist.Rank.CAMPEON.escalon) return 15 * 60_000L;
        if (escalon >= Tablist.Rank.ELITE.escalon) return 20 * 60_000L;
        return DURACION_BASE_MS;
    }

    // Acotado: no conserva UUID de jugadores desconectados en memoria.
    private final Object[] candados = java.util.stream.IntStream.range(0, 64)
            .mapToObj(i -> new Object()).toArray();

    public Object candado(UUID jugador) {
        return candados[Math.floorMod(jugador.hashCode(), candados.length)];
    }

    private static <T> T enServidor(ServerPlayerEntity player, java.util.function.Supplier<T> tarea) {
        var server = player.getServer();
        if (server == null || server.isOnThread()) return tarea.get();
        try {
            return server.submit(tarea).get(5, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            LunaEternal.LOG.error("Timeout esperando al hilo del servidor para tarea de crianza de {}", player.getName().getString());
            return null;
        } catch (Exception e) {
            LunaEternal.LOG.error("Error al ejecutar tarea de crianza en servidor", e);
            return null;
        }
    }

    private final Database db;
    private final EconomyService economy;

    public record CompatibilidadInfo(boolean compatible, String motivo) {}

    public record EntregaPendiente(
            String deliveryId,
            String playerUuid,
            int slotIdx,
            String pokemonUuid,
            String especie,
            byte[] pokemonNbt,
            String estado,
            long creadoMs,
            Long entregadoMs
    ) {}

    public record FichaRanura(
            int indice,
            boolean desbloqueada,
            boolean compradaMonedas,
            int pideEscalon,
            // Madre
            String madreUuid,
            String madreEspecie,
            String madreMote,
            int madreNivel,
            boolean madreShiny,
            String madreGenero,
            // Padre
            String padreUuid,
            String padreEspecie,
            String padreMote,
            int padreNivel,
            boolean padreShiny,
            String padreGenero,
            // Huevo
            boolean compatible,
            String motivoIncompatible,
            long inicioMs,
            long duracionMs,
            boolean huevoListo
    ) {}

    public CrianzaService(Database db, EconomyService economy) {
        this.db = db;
        this.economy = economy;
    }

    /** Política compartida con el cliente; las ranuras de rango no son un prefijo. */
    public static int escalonQuePide(int slotIdx) {
        return switch (slotIdx) {
            case 0, 1 -> 0;
            case 2, 3, 4 -> -1;
            case 5 -> Tablist.Rank.MAESTRO.escalon;
            case 6 -> Tablist.Rank.LEYENDA.escalon;
            default -> Integer.MAX_VALUE;
        };
    }

    public static boolean esComprable(int slotIdx) {
        return slotIdx >= 2 && slotIdx <= 4;
    }

    public static boolean estaDesbloqueada(int slotIdx, int escalon, boolean comprada) {
        if (slotIdx < 0 || slotIdx >= TOTAL_RANURAS) return false;
        // Conserva compras anteriores de las ranuras 6/7 sin volver a cobrar.
        return comprada || (escalonQuePide(slotIdx) >= 0 && escalon >= escalonQuePide(slotIdx));
    }

    private static int rango(ServerPlayerEntity player) {
        int escalon = Tablist.escalonDe(player);
        return player.hasPermissionLevel(2) || escalon < 0 ? Tablist.Rank.LEYENDA.escalon : escalon;
    }

    /** Obtiene el estado completo de las 7 ranuras de un jugador. */
    public List<FichaRanura> obtenerRanuras(ServerPlayerEntity player) {
        int escalon = enServidor(player, () -> rango(player));
        String uuidStr = player.getUuid().toString();
        long ahora = System.currentTimeMillis();

        var ranurasDb = leerRanurasDb(uuidStr);
        return enServidor(player, () -> {
        var resultado = new ArrayList<FichaRanura>(TOTAL_RANURAS);

        for (int i = 0; i < TOTAL_RANURAS; i++) {
            var raw = ranurasDb.get(i);
            boolean comprada = raw != null && raw.compradaMonedas;
            boolean desbloqueada = estaDesbloqueada(i, escalon, comprada);
            int pide = escalonQuePide(i);

            String mUuid = raw != null ? raw.madreUuid : null;
            String pUuid = raw != null ? raw.padreUuid : null;
            long inicio = raw != null ? raw.inicioMs : 0L;
            long duracion = raw != null && raw.duracionMs > 0 ? raw.duracionMs : duracionPorRango(escalon);
            boolean listo = raw != null && raw.huevoListo;

            Pokemon madre = mUuid != null ? buscarPokemon(player, mUuid) : null;
            Pokemon padre = pUuid != null ? buscarPokemon(player, pUuid) : null;

            var compat = comprobarCompatibilidad(madre, padre);
            if (!compat.compatible() || !desbloqueada) listo = false;
            if (desbloqueada && compat.compatible() && inicio > 0 && !listo) {
                if (ahora >= inicio + duracion) {
                    listo = true;
                }
            }

            String madreUuidFinal = madre != null ? mUuid : (raw != null ? raw.madreUuid : null);
            String madreEspecieFinal = madre != null ? madre.getSpecies().getName() : (raw != null && raw.madreEspecie != null ? raw.madreEspecie : "");
            String madreMoteFinal = madre != null && madre.getNickname() != null ? madre.getNickname().getString() : "";
            int madreNivelFinal = madre != null ? madre.getLevel() : 1;
            boolean madreShinyFinal = madre != null && madre.getShiny();
            String madreGeneroFinal = madre != null ? madre.getGender().name() : "";

            String padreUuidFinal = padre != null ? pUuid : (raw != null ? raw.padreUuid : null);
            String padreEspecieFinal = padre != null ? padre.getSpecies().getName() : (raw != null && raw.padreEspecie != null ? raw.padreEspecie : "");
            String padreMoteFinal = padre != null && padre.getNickname() != null ? padre.getNickname().getString() : "";
            int padreNivelFinal = padre != null ? padre.getLevel() : 1;
            boolean padreShinyFinal = padre != null && padre.getShiny();
            String padreGeneroFinal = padre != null ? padre.getGender().name() : "";

            resultado.add(new FichaRanura(
                    i,
                    desbloqueada,
                    comprada,
                    pide,
                    madreUuidFinal,
                    madreEspecieFinal,
                    madreMoteFinal,
                    madreNivelFinal,
                    madreShinyFinal,
                    madreGeneroFinal,
                    padreUuidFinal,
                    padreEspecieFinal,
                    padreMoteFinal,
                    padreNivelFinal,
                    padreShinyFinal,
                    padreGeneroFinal,
                    compat.compatible(),
                    compat.motivo(),
                    inicio,
                    duracion,
                    listo
            ));
        }
        return resultado;
        });
    }

    /** Cargo y desbloqueo se confirman en la misma transacción. */
    public boolean comprarRanura(ServerPlayerEntity player, int slotIdx) {
        if (!esComprable(slotIdx)) return false;
        String uuid = player.getUuid().toString();
        try {
            long pid = LunaEternal.players().resolve(player.getUuid(), player.getName().getString());
            try (Connection c = db.connection()) {
                c.setAutoCommit(false);
                try {
                    try (var ps = c.prepareStatement("INSERT INTO crianza_ranura (player_uuid, ranura_idx) VALUES (?, ?) ON DUPLICATE KEY UPDATE ranura_idx = VALUES(ranura_idx)")) {
                        ps.setString(1, uuid);
                        ps.setInt(2, slotIdx);
                        ps.executeUpdate();
                    }
                    try (var ps = c.prepareStatement("SELECT desbloqueada_monedas FROM crianza_ranura WHERE player_uuid = ? AND ranura_idx = ? FOR UPDATE")) {
                        ps.setString(1, uuid);
                        ps.setInt(2, slotIdx);
                        try (var rs = ps.executeQuery()) {
                            if (!rs.next() || rs.getBoolean(1)) { c.rollback(); return false; }
                        }
                    }
                    economy.applyInTransaction(c, pid, Currency.REPORTCOIN, -PRECIO_RANURA_LUNACOINS,
                            "Ranura de crianza " + (slotIdx + 1), "crianza", null,
                            "crianza_ranura_" + uuid + "_" + slotIdx);
                    try (var ps = c.prepareStatement("UPDATE crianza_ranura SET desbloqueada_monedas = 1 WHERE player_uuid = ? AND ranura_idx = ?")) {
                        ps.setString(1, uuid);
                        ps.setInt(2, slotIdx);
                        ps.executeUpdate();
                    }
                    c.commit();
                } catch (SQLException | EconomyException e) {
                    c.rollback();
                    throw e;
                }
            }
            player.sendMessage(Text.literal("§aRanura " + (slotIdx + 1) + " desbloqueada por 150 LunaCoins."), true);
            return true;
        } catch (SQLException | EconomyException e) {
            LunaEternal.LOG.warn("Compra de crianza rechazada: {}", e.getMessage());
            player.sendMessage(Text.literal("§cNo se completó la compra. Revisa tu saldo e inténtalo de nuevo."), true);
            return false;
        }
    }

    /** Asigna un progenitor (Hembra o Macho) a la ranura. */
    public boolean asignarProgenitor(ServerPlayerEntity player, int slotIdx, boolean esHembra, String pokemonUuid) {
        if (slotIdx < 0 || slotIdx >= TOTAL_RANURAS) {
            return false;
        }
        int escalon = Tablist.escalonDe(player);
        if (player.hasPermissionLevel(2) || escalon < 0) {
            escalon = Math.max(escalon, Tablist.Rank.LEYENDA.escalon);
        }
        String uuidStr = player.getUuid().toString();

        var ranuras = leerRanurasDb(uuidStr);
        var actual = ranuras.get(slotIdx);
        boolean comprada = actual != null && actual.compradaMonedas;
        if (!estaDesbloqueada(slotIdx, escalon, comprada)) {
            player.sendMessage(Text.literal("§cEsta ranura de crianza está bloqueada para tu rango actual."), true);
            return false; // Ranura bloqueada
        }

        Pokemon nuevo = buscarPokemon(player, pokemonUuid);
        if (nuevo == null) {
            LunaEternal.LOG.warn("Crianza: No se encontro el pokemon {} para {}", pokemonUuid, player.getName().getString());
            player.sendMessage(Text.literal("§cNo se encontró el Pokémon seleccionado en tu equipo ni en tu PC."), true);
            return false;
        }

        String canonicalUuid = nuevo.getUuid().toString();
        for (var ocupada : ranuras) {
            if (ocupada == null) continue;
            if (ocupada.ranuraIdx == slotIdx && canonicalUuid.equals(esHembra ? ocupada.madreUuid : ocupada.padreUuid)) return true;
            if (canonicalUuid.equals(ocupada.madreUuid) || canonicalUuid.equals(ocupada.padreUuid)) {
                player.sendMessage(Text.literal("§cEse Pokémon ya está asignado a una ranura de crianza."), true);
                return false;
            }
        }
        pokemonUuid = canonicalUuid;

        if (nuevo.getForm().getEggGroups().contains(EggGroup.UNDISCOVERED)) {
            player.sendMessage(Text.literal("§c" + nuevo.getSpecies().getName() + " no puede criar (Grupo Desconocido / Cría / Legendario)."), true);
            return false;
        }

        boolean esDitto = nuevo.getSpecies().getName().equalsIgnoreCase("ditto");
        boolean esGenderless = nuevo.getGender() == Gender.GENDERLESS;
        if (!esDitto && !esGenderless) {
            if (esHembra && nuevo.getGender() == Gender.MALE) {
                player.sendMessage(Text.literal("§cLa casilla izquierda requiere Hembra, Ditto o Sin Género."), true);
                return false;
            }
            if (!esHembra && nuevo.getGender() == Gender.FEMALE) {
                player.sendMessage(Text.literal("§cLa casilla derecha requiere Macho, Ditto o Sin Género."), true);
                return false;
            }
        }

        Pokemon otro = null;
        if (actual != null) {
            String otroUuid = esHembra ? actual.padreUuid : actual.madreUuid;
            if (otroUuid != null && !otroUuid.isBlank()) {
                otro = buscarPokemon(player, otroUuid);
            }
        }

        // Si ya hay compañero, comprobar compatibilidad
        var compat = comprobarCompatibilidad(esHembra ? nuevo : otro, esHembra ? otro : nuevo);
        long inicio = compat.compatible() ? System.currentTimeMillis() : 0L;
        long duracion = duracionPorRango(escalon);

        String madreUuid = esHembra ? pokemonUuid : (actual != null ? actual.madreUuid : null);
        String padreUuid = !esHembra ? pokemonUuid : (actual != null ? actual.padreUuid : null);
        String madreEsp = esHembra ? nuevo.getSpecies().getName() : (actual != null ? actual.madreEspecie : null);
        String padreEsp = !esHembra ? nuevo.getSpecies().getName() : (actual != null ? actual.padreEspecie : null);

        guardarProgenitores(uuidStr, slotIdx, madreUuid, padreUuid, madreEsp, padreEsp, inicio, duracion, false);
        LunaEternal.LOG.info("Crianza: Progenitor asignado exitosamente: {} ({}) en ranura {} para {}",
                nuevo.getSpecies().getName(), nuevo.getGender(), slotIdx + 1, player.getName().getString());
        player.sendMessage(Text.literal("§a✔ " + nuevo.getSpecies().getName() + " asignado como "
                + (esHembra ? "Hembra" : "Macho") + " en Ranura " + (slotIdx + 1)), true);
        return true;
    }

    /** Retira un progenitor de la ranura. */
    public boolean retirarProgenitor(ServerPlayerEntity player, int slotIdx, boolean esHembra) {
        if (slotIdx < 0 || slotIdx >= TOTAL_RANURAS) {
            return false;
        }
        String uuidStr = player.getUuid().toString();
        var ranuras = leerRanurasDb(uuidStr);
        var actual = ranuras.get(slotIdx);
        if (actual == null) {
            return false;
        }

        String madreUuid = esHembra ? null : actual.madreUuid;
        String padreUuid = !esHembra ? null : actual.padreUuid;
        String madreEsp = esHembra ? null : actual.madreEspecie;
        String padreEsp = !esHembra ? null : actual.padreEspecie;

        guardarProgenitores(uuidStr, slotIdx, madreUuid, padreUuid, madreEsp, padreEsp, 0L, DURACION_BASE_MS, false);
        player.sendMessage(Text.literal("§eProgenitor retirado de la Ranura " + (slotIdx + 1)), true);
        return true;
    }

    /** Reclama el huevo eclosionado o generado y entrega la cría al jugador. */
    public static byte[] serializarPokemon(Pokemon pokemon, DynamicRegistryManager registros) {
        if (pokemon == null) return null;
        try {
            NbtCompound nbt = pokemon.saveToNBT(registros, new NbtCompound());
            var out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(nbt, out);
            return out.toByteArray();
        } catch (Exception e) {
            LunaEternal.LOG.error("Error al serializar Pokemon para crianza pendiente", e);
            return null;
        }
    }

    public static Pokemon deserializarPokemon(byte[] datos, DynamicRegistryManager registros) {
        if (datos == null || datos.length == 0) return null;
        try {
            NbtCompound nbt = NbtIo.readCompressed(
                    new ByteArrayInputStream(datos), NbtSizeTracker.ofUnlimitedBytes());
            return new Pokemon().loadFromNBT(registros, nbt);
        } catch (Throwable t) {
            LunaEternal.LOG.error("Error al deserializar Pokemon desde crianza pendiente", t);
            return null;
        }
    }

    private static Pokemon crearBebe(String baseEspecie, Pokemon madre, Pokemon padre, Pokemon principal) {
        Pokemon baby = PokemonProperties.Companion.parse(baseEspecie).create();
        baby.setLevel(1);

        // Herencia de Naturaleza
        boolean everMadre = madre != null && tieneObjeto(madre, "everstone");
        boolean everPadre = padre != null && tieneObjeto(padre, "everstone");
        if (everMadre && !everPadre) {
            baby.setNature(madre.getNature());
        } else if (everPadre && !everMadre) {
            baby.setNature(padre.getNature());
        } else if (everMadre && everPadre) {
            baby.setNature(Math.random() < 0.5 ? madre.getNature() : padre.getNature());
        }

        // Herencia de IVs (3 normales, o 5 con Lazo Destino)
        boolean destinyKnot = (madre != null && tieneObjeto(madre, "destiny_knot"))
                || (padre != null && tieneObjeto(padre, "destiny_knot"));
        int numHeredar = destinyKnot ? 5 : 3;

        var statsList = new ArrayList<>(List.of(
                Stats.HP, Stats.ATTACK, Stats.DEFENCE,
                Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
        ));
        Collections.shuffle(statsList);
        for (int i = 0; i < numHeredar; i++) {
            var stat = statsList.get(i);
            Pokemon donante = (padre == null || (madre != null && Math.random() < 0.5)) ? madre : padre;
            if (donante != null) {
                baby.getIvs().set(stat, donante.getIvs().getOrDefault(stat));
            }
        }

        // Herencia de Poké Ball
        Pokemon ballParent = (madre != null && !madre.getSpecies().getName().equalsIgnoreCase("ditto"))
                ? madre : padre;
        if (ballParent != null) {
            String ballPath = ballParent.getCaughtBall().getName().getPath();
            if (!ballPath.equalsIgnoreCase("master_ball") && !ballPath.equalsIgnoreCase("cherish_ball")) {
                baby.setCaughtBall(ballParent.getCaughtBall());
            }
        }
        return baby;
    }

    /** Reclama el huevo eclosionado o generado y entrega la cría de forma transaccional e idempotente. */
    public boolean reclamarHuevo(ServerPlayerEntity player, int slotIdx) {
        if (slotIdx < 0 || slotIdx >= TOTAL_RANURAS) return false;
        String uuidStr = player.getUuid().toString();

        // 1. Si ya existe una entrega pendiente para esta ranura (recuperación de crash o desconexión previa),
        // intentar completarla directamente sin volver a generar el bicho.
        EntregaPendiente pendiente = obtenerEntregaPendiente(uuidStr, slotIdx);
        if (pendiente != null) {
            return ejecutarEntregaPendiente(player, pendiente);
        }

        // 2. Si no hay entrega pendiente, validar la ranura en DB
        var ranuras = leerRanurasDb(uuidStr);
        var actual = ranuras.get(slotIdx);
        if (actual == null || !estaDesbloqueada(slotIdx, rango(player), actual.compradaMonedas)
                || actual.inicioMs <= 0 || System.currentTimeMillis() - actual.inicioMs < actual.duracionMs) {
            return false;
        }

        Pokemon madre = actual.madreUuid != null ? buscarPokemon(player, actual.madreUuid) : null;
        Pokemon padre = actual.padreUuid != null ? buscarPokemon(player, actual.padreUuid) : null;

        if (!comprobarCompatibilidad(madre, padre).compatible()) {
            return false;
        }

        Pokemon principal = (madre != null && !madre.getSpecies().getName().equalsIgnoreCase("ditto"))
                ? madre : padre;
        if (principal == null) {
            principal = madre != null ? madre : padre;
        }

        var pre = principal.getPreEvolution();
        while (pre != null && pre.getSpecies().getPreEvolution() != null) {
            pre = pre.getSpecies().getPreEvolution();
        }
        String baseEspecie = pre != null ? pre.getSpecies().getName() : principal.getSpecies().getName();

        try {
            Pokemon baby = crearBebe(baseEspecie, madre, padre, principal);
            if (baby == null) return false;

            // Serializar cría generada para persistirla antes de la entrega
            byte[] nbtBytes = serializarPokemon(baby, player.getRegistryManager());
            if (nbtBytes == null || nbtBytes.length == 0) {
                LunaEternal.LOG.error("No se pudo serializar la cría de {}", baseEspecie);
                return false;
            }

            // Persistir entrega PENDING antes de tocar el almacenamiento del jugador o los temporizadores
            String deliveryId = UUID.randomUUID().toString();
            String pokeUuidStr = baby.getUuid().toString();
            EntregaPendiente nuevaEntrega = new EntregaPendiente(
                    deliveryId, uuidStr, slotIdx, pokeUuidStr, baseEspecie, nbtBytes, "PENDING",
                    System.currentTimeMillis(), null
            );
            insertarEntregaPendiente(nuevaEntrega);

            // Intentar entrega transaccional e idempotente
            return ejecutarEntrega(player, baby, nuevaEntrega, actual);
        } catch (Throwable t) {
            LunaEternal.LOG.error("Error al crear y registrar entrega de cría de {}", baseEspecie, t);
            return false;
        }
    }

    public boolean ejecutarEntregaPendiente(ServerPlayerEntity player, EntregaPendiente pendiente) {
        Pokemon baby = deserializarPokemon(pendiente.pokemonNbt(), player.getRegistryManager());
        if (baby == null) {
            LunaEternal.LOG.error("No se pudo reconstruir la cría pendiente {} ({})",
                    pendiente.deliveryId(), pendiente.especie());
            return false;
        }
        var ranuras = leerRanurasDb(player.getUuid().toString());
        var actual = ranuras.get(pendiente.slotIdx());
        if (actual == null) {
            actual = new RawRanura();
            actual.ranuraIdx = pendiente.slotIdx();
        }
        return ejecutarEntrega(player, baby, pendiente, actual);
    }

    private boolean ejecutarEntrega(ServerPlayerEntity player, Pokemon baby, EntregaPendiente entrega, RawRanura actual) {
        String uuidStr = player.getUuid().toString();
        Boolean entregada = enServidor(player, () -> {
            if (player.isRemoved()) return null;
            var storage = Cobblemon.INSTANCE.getStorage();
            var party = storage.getParty(player);
            var pc = storage.getPC(player);

            // Idempotencia: comprobar si el Pokémon ya está en el equipo o PC del jugador (tras un crash posterior a .add)
            UUID pokeUuid = baby.getUuid();
            boolean yaEnEquipo = (party != null && party.get(pokeUuid) != null);
            boolean yaEnPc = (pc != null && pc.get(pokeUuid) != null);
            if (yaEnEquipo || yaEnPc) {
                LunaEternal.LOG.info("Idempotencia crianza: {} ya tiene la cría {} ({}) en su {}",
                        player.getName().getString(), pokeUuid, entrega.especie(), yaEnEquipo ? "equipo" : "PC");
                return Boolean.TRUE;
            }

            // Entrega directa: primero equipo, luego PC
            if (party != null && party.add(baby)) {
                player.sendMessage(Text.literal("§8[§6Crianza§8] §a¡Tu cría de §e" + entrega.especie() + " §aha sido añadida a tu equipo!"), false);
                return Boolean.TRUE;
            }
            if (pc != null && pc.add(baby)) {
                player.sendMessage(Text.literal("§8[§6Crianza§8] §aTu equipo estaba lleno: ¡tu cría de §e" + entrega.especie() + " §aha ido al PC!"), false);
                return Boolean.TRUE;
            }

            return Boolean.FALSE; // Almacenamiento completamente lleno
        });

        if (Boolean.TRUE.equals(entregada)) {
            // Confirmar entrega en base de datos
            marcarEntregaCompletada(entrega.deliveryId());

            // Avanzar el ciclo de crianza en la ranura
            long nuevoInicio = System.currentTimeMillis();
            guardarProgenitores(uuidStr, entrega.slotIdx(), actual.madreUuid, actual.padreUuid,
                    actual.madreEspecie, actual.padreEspecie, nuevoInicio, duracionPorRango(rango(player)), false);
            LunaEternal.LOG.info("{} reclamó con éxito su cría de {} en ranura {} (deliveryId={})",
                    player.getName().getString(), entrega.especie(), entrega.slotIdx() + 1, entrega.deliveryId());
            return true;
        } else if (Boolean.FALSE.equals(entregada)) {
            player.sendMessage(Text.literal("§cTu equipo y tu PC están llenos. Haz espacio para recibir tu cría pendiente."), true);
            return false;
        } else {
            // null indica jugador desconectado o timeout: la cría queda PENDING para su próxima entrada
            LunaEternal.LOG.warn("Entrega pendiente retenida para {} en ranura {}: jugador desconectado o indisponible",
                    uuidStr, entrega.slotIdx() + 1);
            return false;
        }
    }

    /** Reintenta entregar cualquier cría que hubiera quedado en PENDING (crash / desconexión previa). */
    public void recuperarEntregasPendientes(ServerPlayerEntity player) {
        if (player == null || player.isRemoved()) return;
        String uuidStr = player.getUuid().toString();
        LunaEternal.submit(() -> {
            try {
                var pendientes = obtenerTodasEntregasPendientes(uuidStr);
                if (pendientes.isEmpty()) return;
                LunaEternal.LOG.info("Recuperando {} cría(s) pendiente(s) para {}", pendientes.size(), player.getName().getString());
                for (var p : pendientes) {
                    ejecutarEntregaPendiente(player, p);
                }
            } catch (Exception e) {
                LunaEternal.LOG.error("Error al recuperar crías pendientes de {}", player.getName().getString(), e);
            }
        });
    }

    /** Comprueba compatibilidad biológica según grupos huevo y Ditto. */
    public static CompatibilidadInfo comprobarCompatibilidad(Pokemon a, Pokemon b) {
        if (a == null || b == null) {
            return new CompatibilidadInfo(false, "Falta un progenitor");
        }

        boolean aUndisc = a.getForm().getEggGroups().contains(EggGroup.UNDISCOVERED);
        boolean bUndisc = b.getForm().getEggGroups().contains(EggGroup.UNDISCOVERED);
        if (aUndisc || bUndisc) {
            return new CompatibilidadInfo(false, "Grupo Desconocido / Legendario / Cría");
        }

        boolean aDitto = a.getSpecies().getName().equalsIgnoreCase("ditto");
        boolean bDitto = b.getSpecies().getName().equalsIgnoreCase("ditto");
        if (aDitto && bDitto) {
            return new CompatibilidadInfo(false, "Dos Ditto no pueden criar entre sí");
        }
        if (aDitto || bDitto) {
            return new CompatibilidadInfo(true, "Compatible con Ditto");
        }

        if (a.getGender() == Gender.GENDERLESS || b.getGender() == Gender.GENDERLESS) {
            return new CompatibilidadInfo(false, "Los Pokémon sin género necesitan Ditto");
        }
        if (a.getUuid().equals(b.getUuid()) || a.getGender() == b.getGender()) {
            return new CompatibilidadInfo(false, "Mismo género (se requiere Macho y Hembra)");
        }

        var aGrupos = a.getForm().getEggGroups();
        var bGrupos = b.getForm().getEggGroups();
        EggGroup compartido = null;
        for (EggGroup g : aGrupos) {
            if (bGrupos.contains(g)) {
                compartido = g;
                break;
            }
        }

        if (compartido == null) {
            return new CompatibilidadInfo(false, "Grupos Huevo incompatibles");
        }
        return new CompatibilidadInfo(true, "Compatibles: Grupo " + compartido.getShowdownID());
    }

    private static boolean tieneObjeto(Pokemon p, String subcadena) {
        if (p == null) return false;
        ItemStack stack = p.heldItem();
        if (stack == null || stack.isEmpty()) return false;
        String id = Registries.ITEM.getId(stack.getItem()).getPath();
        return id.equals(subcadena);
    }

    public static Pokemon buscarPokemon(ServerPlayerEntity player, String uuidStr) {
        if (uuidStr == null || uuidStr.isBlank()) return null;
        if (!player.getServer().isOnThread()) return enServidor(player, () -> buscarPokemon(player, uuidStr));
        try {
            var almacen = Cobblemon.INSTANCE.getStorage();
            java.util.UUID uuid;
            try {
                uuid = java.util.UUID.fromString(uuidStr.trim());
            } catch (Exception e) {
                return null;
            }

            // 1. Buscar en el Equipo (Party)
            try {
                var party = almacen.getParty(player);
                if (party != null) {
                    Pokemon direct = party.get(uuid);
                    if (direct != null) return direct;
                    for (Pokemon p : party) {
                        if (p != null && p.getUuid().equals(uuid)) return p;
                    }
                }
            } catch (Throwable ignored) {}

            // 2. Buscar en el PC
            try {
                var pc = almacen.getPC(player);
                if (pc != null) {
                    Pokemon direct = pc.get(uuid);
                    if (direct != null) return direct;
                    for (Pokemon p : pc) {
                        if (p != null && p.getUuid().equals(uuid)) return p;
                    }
                }
            } catch (Throwable ignored) {}
        } catch (Throwable t) {
            LunaEternal.LOG.warn("Error al buscar Pokemon {}", uuidStr, t);
        }
        return null;
    }

    // ------------------------------------------------------------- PERSISTENCIA

    private static class RawRanura {
        int ranuraIdx;
        boolean compradaMonedas;
        String madreUuid;
        String padreUuid;
        String madreEspecie;
        String padreEspecie;
        long inicioMs;
        long duracionMs;
        boolean huevoListo;
    }

    private List<RawRanura> leerRanurasDb(String playerUuid) {
        var mapa = new ArrayList<RawRanura>(Collections.nCopies(TOTAL_RANURAS, null));
        String sql = "SELECT ranura_idx, desbloqueada_monedas, madre_uuid, padre_uuid, "
                   + "       madre_especie, padre_especie, inicio_ms, duracion_ms, huevo_listo "
                   + "FROM crianza_ranura WHERE player_uuid = ?";
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    var r = new RawRanura();
                    r.ranuraIdx = rs.getInt("ranura_idx");
                    r.compradaMonedas = rs.getBoolean("desbloqueada_monedas");
                    r.madreUuid = rs.getString("madre_uuid");
                    r.padreUuid = rs.getString("padre_uuid");
                    r.madreEspecie = rs.getString("madre_especie");
                    r.padreEspecie = rs.getString("padre_especie");
                    r.inicioMs = rs.getLong("inicio_ms");
                    r.duracionMs = rs.getLong("duracion_ms");
                    r.huevoListo = rs.getBoolean("huevo_listo");
                    if (r.ranuraIdx >= 0 && r.ranuraIdx < TOTAL_RANURAS) {
                        mapa.set(r.ranuraIdx, r);
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron leer las ranuras de crianza", e);
        }
        return mapa;
    }

    private void guardarCompraRanura(String playerUuid, int slotIdx) {
        String sql = "INSERT INTO crianza_ranura (player_uuid, ranura_idx, desbloqueada_monedas) "
                   + "VALUES (?, ?, 1) ON DUPLICATE KEY UPDATE desbloqueada_monedas = 1";
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setInt(2, slotIdx);
            ps.executeUpdate();
        } catch (SQLException e) {
            LunaEternal.LOG.error("Error al guardar compra de ranura {}", slotIdx, e);
        }
    }

    private void guardarProgenitores(String playerUuid, int slotIdx, String madreUuid, String padreUuid,
                                     String madreEsp, String padreEsp, long inicioMs, long duracionMs, boolean listo) {
        String sql = "INSERT INTO crianza_ranura (player_uuid, ranura_idx, madre_uuid, padre_uuid, "
                   + "       madre_especie, padre_especie, inicio_ms, duracion_ms, huevo_listo) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                   + "ON DUPLICATE KEY UPDATE madre_uuid = VALUES(madre_uuid), padre_uuid = VALUES(padre_uuid), "
                   + "                        madre_especie = VALUES(madre_especie), padre_especie = VALUES(padre_especie), "
                   + "                        inicio_ms = VALUES(inicio_ms), duracion_ms = VALUES(duracion_ms), "
                   + "                        huevo_listo = VALUES(huevo_listo)";
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setInt(2, slotIdx);
            ps.setString(3, madreUuid);
            ps.setString(4, padreUuid);
            ps.setString(5, madreEsp);
            ps.setString(6, padreEsp);
            ps.setLong(7, inicioMs);
            ps.setLong(8, duracionMs);
            ps.setBoolean(9, listo);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron guardar los progenitores", e);
        }
    }

    private void marcarHuevoListo(String playerUuid, int slotIdx) {
        String sql = "UPDATE crianza_ranura SET huevo_listo = 1 WHERE player_uuid = ? AND ranura_idx = ?";
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setInt(2, slotIdx);
            ps.executeUpdate();
        } catch (SQLException e) {
            LunaEternal.LOG.error("Error al marcar huevo listo en ranura {}", slotIdx, e);
        }
    }

    public void insertarEntregaPendiente(EntregaPendiente ep) {
        String sql = "INSERT INTO crianza_entrega_pendiente "
                   + "(delivery_id, player_uuid, slot_idx, pokemon_uuid, especie, pokemon_nbt, estado, creado_ms) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                   + "ON DUPLICATE KEY UPDATE estado = VALUES(estado), pokemon_nbt = VALUES(pokemon_nbt)";
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, ep.deliveryId());
            ps.setString(2, ep.playerUuid());
            ps.setInt(3, ep.slotIdx());
            ps.setString(4, ep.pokemonUuid());
            ps.setString(5, ep.especie());
            ps.setBytes(6, ep.pokemonNbt());
            ps.setString(7, ep.estado());
            ps.setLong(8, ep.creadoMs());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo insertar entrega pendiente de crianza", e);
        }
    }

    public EntregaPendiente obtenerEntregaPendiente(String playerUuid, int slotIdx) {
        String sql = "SELECT delivery_id, player_uuid, slot_idx, pokemon_uuid, especie, pokemon_nbt, estado, creado_ms, entregado_ms "
                   + "FROM crianza_entrega_pendiente "
                   + "WHERE player_uuid = ? AND slot_idx = ? AND estado = 'PENDING' LIMIT 1";
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setInt(2, slotIdx);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new EntregaPendiente(
                            rs.getString("delivery_id"),
                            rs.getString("player_uuid"),
                            rs.getInt("slot_idx"),
                            rs.getString("pokemon_uuid"),
                            rs.getString("especie"),
                            rs.getBytes("pokemon_nbt"),
                            rs.getString("estado"),
                            rs.getLong("creado_ms"),
                            rs.getObject("entregado_ms") != null ? rs.getLong("entregado_ms") : null
                    );
                }
            }
        } catch (SQLException e) {
            LunaEternal.LOG.error("Error al consultar entrega pendiente de crianza para {} en ranura {}", playerUuid, slotIdx, e);
        }
        return null;
    }

    public List<EntregaPendiente> obtenerTodasEntregasPendientes(String playerUuid) {
        List<EntregaPendiente> out = new ArrayList<>();
        String sql = "SELECT delivery_id, player_uuid, slot_idx, pokemon_uuid, especie, pokemon_nbt, estado, creado_ms, entregado_ms "
                   + "FROM crianza_entrega_pendiente "
                   + "WHERE player_uuid = ? AND estado = 'PENDING'";
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new EntregaPendiente(
                            rs.getString("delivery_id"),
                            rs.getString("player_uuid"),
                            rs.getInt("slot_idx"),
                            rs.getString("pokemon_uuid"),
                            rs.getString("especie"),
                            rs.getBytes("pokemon_nbt"),
                            rs.getString("estado"),
                            rs.getLong("creado_ms"),
                            rs.getObject("entregado_ms") != null ? rs.getLong("entregado_ms") : null
                    ));
                }
            }
        } catch (SQLException e) {
            LunaEternal.LOG.error("Error al consultar todas las entregas pendientes de crianza para {}", playerUuid, e);
        }
        return out;
    }

    public void marcarEntregaCompletada(String deliveryId) {
        String sql = "UPDATE crianza_entrega_pendiente SET estado = 'DELIVERED', entregado_ms = ? WHERE delivery_id = ?";
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, deliveryId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LunaEternal.LOG.error("Error al marcar entrega completada {}", deliveryId, e);
        }
    }
}
