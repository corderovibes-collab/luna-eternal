package net.pokereport.luna.quienesepokemon;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.economy.Currency;

import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** Evento global, autoritativo en el servidor. El cliente nunca conoce el nombre antes del resultado. */
public final class QuienEsEsePokemonService {
    private static final long INTERVALO_MS = 2 * 60 * 60_000L;
    private static final long DURACION_MS = 45_000L;
    private static final long REVELACION_MS = 8_000L;
    private static final long PREMIO = 500L;
    /** Activar Gen 3+ solo exige sumar el número y las entradas/assets de esa generación. */
    private static final Set<Integer> GENERACIONES_ACTIVAS = Set.of(1, 2);
    private static final Random AZAR = new Random();
    private static final ArrayDeque<Integer> RECIENTES = new ArrayDeque<>();
    private static Ronda activa;
    private static long proximaRonda;
    private static boolean registrado;

    private record Especie(int dex, int generacion, String nombre, String respuesta) {}
    private record Ronda(long id, Especie especie, long terminaEn) {}

    private static final List<Especie> CATALOGO = catalogo();

    private QuienEsEsePokemonService() {}

    public static void registrar() {
        if (registrado) return;
        registrado = true;
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((mensaje, jugador, parametros) -> {
            comprobarRespuesta(jugador, mensaje.getContent().getString());
            return true; // Tablist conserva el formato y retransmite el chat normal.
        });
        ServerTickEvents.END_SERVER_TICK.register(QuienEsEsePokemonService::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sincronizar(handler.getPlayer()));
    }

    /** Herramienta de QA: no altera el calendario si ya hay una ronda en curso. */
    public static int iniciarManual(ServerCommandSource origen) {
        if (activa != null) {
            origen.sendError(Text.literal("§cYa hay una ronda activa; espera su resultado."));
            return 0;
        }
        if (origen.getServer().getPlayerManager().getPlayerList().isEmpty()) {
            origen.sendError(Text.literal("§cNo hay jugadores conectados para recibir la ronda."));
            return 0;
        }
        iniciar(origen.getServer(), System.currentTimeMillis());
        origen.sendFeedback(() -> Text.literal("§aRonda global de ¿Quién es este Pokémon? iniciada."), false);
        return 1;
    }

    public static int estado(ServerCommandSource origen) {
        if (activa == null) {
            origen.sendFeedback(() -> Text.literal("§7QEEP: sin ronda activa."), false);
            return 0;
        }
        long segundos = Math.max(0, (activa.terminaEn - System.currentTimeMillis() + 999) / 1000);
        origen.sendFeedback(() -> Text.literal("§dQEEP: ronda activa, Pokédex #" + activa.especie.dex + " durante " + segundos + " s."), false);
        return 1;
    }

    private static void tick(MinecraftServer server) {
        long ahora = System.currentTimeMillis();
        if (activa != null && ahora >= activa.terminaEn) {
            vencer(server);
        }
        if (activa == null && !server.getPlayerManager().getPlayerList().isEmpty() && ahora >= proximaRonda) {
            iniciar(server, ahora);
        }
    }

    private static void iniciar(MinecraftServer server, long ahora) {
        Especie especie = siguiente();
        activa = new Ronda(ahora ^ ((long) especie.dex << 32) ^ AZAR.nextLong(), especie, ahora + DURACION_MS);
        proximaRonda = Long.MAX_VALUE;
        var paquete = new QuienEsEsePokemonNet.Ronda(activa.id, especie.dex, activa.terminaEn);
        for (var player : server.getPlayerManager().getPlayerList()) ServerPlayNetworking.send(player, paquete);
    }

    private static void comprobarRespuesta(ServerPlayerEntity jugador, String texto) {
        Ronda ronda = activa;
        if (ronda == null || System.currentTimeMillis() >= ronda.terminaEn || !ronda.especie.respuesta.equals(normalizar(texto))) return;
        activa = null; // el hilo del servidor serializa chat: exactamente un ganador.
        long ocultarEn = System.currentTimeMillis() + REVELACION_MS;
        revelar(jugador.getServer(), ronda, jugador.getGameProfile().getName(), PREMIO, ocultarEn);
        concederPremio(jugador, ronda);
        proximaRonda = System.currentTimeMillis() + INTERVALO_MS;
    }

    private static void vencer(MinecraftServer server) {
        Ronda ronda = activa;
        if (ronda == null) return;
        activa = null;
        long ocultarEn = System.currentTimeMillis() + REVELACION_MS;
        revelar(server, ronda, "Nadie acertó", 0L, ocultarEn);
        proximaRonda = System.currentTimeMillis() + INTERVALO_MS;
    }

    private static void revelar(MinecraftServer server, Ronda ronda, String ganador, long premio, long ocultarEn) {
        var paquete = new QuienEsEsePokemonNet.Revelacion(ronda.id, ronda.especie.dex, ganador, premio, ocultarEn);
        for (var player : server.getPlayerManager().getPlayerList()) ServerPlayNetworking.send(player, paquete);
    }

    private static void concederPremio(ServerPlayerEntity jugador, Ronda ronda) {
        Long playerId = LunaEternal.players().cachedId(jugador.getUuid());
        if (playerId == null) {
            LunaEternal.LOG.warn("Evento QEEP: {} respondió antes de cargar su perfil", jugador.getName().getString());
            return;
        }
        String clave = "quien_es_ese_pokemon:" + ronda.id + ":" + jugador.getUuid();
        LunaEternal.submit(() -> {
            try {
                LunaEternal.economy().apply(playerId, Currency.POKEDOLLAR, PREMIO,
                        "quien_es_ese_pokemon", "quien_es_ese_pokemon", null, clave);
            } catch (Exception e) {
                LunaEternal.LOG.error("No se pudo acreditar el premio QEEP a {}", jugador.getName().getString(), e);
                jugador.getServer().execute(() -> jugador.sendMessage(Text.literal("§cLa respuesta fue válida, pero el premio quedó pendiente. Un administrador debe revisar la consola."), false));
            }
        });
    }

    private static void sincronizar(ServerPlayerEntity jugador) {
        Ronda ronda = activa;
        if (ronda != null && System.currentTimeMillis() < ronda.terminaEn) {
            ServerPlayNetworking.send(jugador, new QuienEsEsePokemonNet.Ronda(ronda.id, ronda.especie.dex, ronda.terminaEn));
        }
    }

    private static Especie siguiente() {
        List<Especie> disponibles = new ArrayList<>();
        for (Especie especie : CATALOGO) {
            if (GENERACIONES_ACTIVAS.contains(especie.generacion) && !RECIENTES.contains(especie.dex)) {
                disponibles.add(especie);
            }
        }
        Especie elegida = disponibles.get(AZAR.nextInt(disponibles.size()));
        RECIENTES.addLast(elegida.dex);
        if (RECIENTES.size() > 24) RECIENTES.removeFirst();
        return elegida;
    }

    private static String normalizar(String texto) {
        String base = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static List<Especie> catalogo() {
        String[] nombres = ("Bulbasaur,Ivysaur,Venusaur,Charmander,Charmeleon,Charizard,Squirtle,Wartortle,Blastoise,Caterpie,Metapod,Butterfree,Weedle,Kakuna,Beedrill,Pidgey,Pidgeotto,Pidgeot,Rattata,Raticate,Spearow,Fearow,Ekans,Arbok,Pikachu,Raichu,Sandshrew,Sandslash,Nidoran F,Nidorina,Nidoqueen,Nidoran M,Nidorino,Nidoking,Clefairy,Clefable,Vulpix,Ninetales,Jigglypuff,Wigglytuff,Zubat,Golbat,Oddish,Gloom,Vileplume,Paras,Parasect,Venonat,Venomoth,Diglett,Dugtrio,Meowth,Persian,Psyduck,Golduck,Mankey,Primeape,Growlithe,Arcanine,Poliwag,Poliwhirl,Poliwrath,Abra,Kadabra,Alakazam,Machop,Machoke,Machamp,Bellsprout,Weepinbell,Victreebel,Tentacool,Tentacruel,Geodude,Graveler,Golem,Ponyta,Rapidash,Slowpoke,Slowbro,Magnemite,Magneton,Farfetchd,Doduo,Dodrio,Seel,Dewgong,Grimer,Muk,Shellder,Cloyster,Gastly,Haunter,Gengar,Onix,Drowzee,Hypno,Krabby,Kingler,Voltorb,Electrode,Exeggcute,Exeggutor,Cubone,Marowak,Hitmonlee,Hitmonchan,Lickitung,Koffing,Weezing,Rhyhorn,Rhydon,Chansey,Tangela,Kangaskhan,Horsea,Seadra,Goldeen,Seaking,Staryu,Starmie,Mr Mime,Scyther,Jynx,Electabuzz,Magmar,Pinsir,Tauros,Magikarp,Gyarados,Lapras,Ditto,Eevee,Vaporeon,Jolteon,Flareon,Porygon,Omanyte,Omastar,Kabuto,Kabutops,Aerodactyl,Snorlax,Articuno,Zapdos,Moltres,Dratini,Dragonair,Dragonite,Mewtwo,Mew,Chikorita,Bayleef,Meganium,Cyndaquil,Quilava,Typhlosion,Totodile,Croconaw,Feraligatr,Sentret,Furret,Hoothoot,Noctowl,Ledyba,Ledian,Spinarak,Ariados,Crobat,Chinchou,Lanturn,Pichu,Cleffa,Igglybuff,Togepi,Togetic,Natu,Xatu,Mareep,Flaaffy,Ampharos,Bellossom,Marill,Azumarill,Sudowoodo,Politoed,Hoppip,Skiploom,Jumpluff,Aipom,Sunkern,Sunflora,Yanma,Wooper,Quagsire,Espeon,Umbreon,Murkrow,Slowking,Misdreavus,Unown,Wobbuffet,Girafarig,Pineco,Forretress,Dunsparce,Gligar,Steelix,Snubbull,Granbull,Qwilfish,Scizor,Shuckle,Heracross,Sneasel,Teddiursa,Ursaring,Slugma,Magcargo,Swinub,Piloswine,Corsola,Remoraid,Octillery,Delibird,Mantine,Skarmory,Houndour,Houndoom,Kingdra,Phanpy,Donphan,Porygon2,Stantler,Smeargle,Tyrogue,Hitmontop,Smoochum,Elekid,Magby,Miltank,Blissey,Raikou,Entei,Suicune,Larvitar,Pupitar,Tyranitar,Lugia,Ho Oh,Celebi").split(",");
        List<Especie> resultado = new ArrayList<>(nombres.length);
        for (int i = 0; i < nombres.length; i++) {
            int dex = i + 1;
            resultado.add(new Especie(dex, dex <= 151 ? 1 : 2, nombres[i], normalizar(nombres[i])));
        }
        if (resultado.size() != 251) throw new IllegalStateException("Catálogo QEEP incompleto: " + resultado.size());
        return List.copyOf(resultado);
    }
}
