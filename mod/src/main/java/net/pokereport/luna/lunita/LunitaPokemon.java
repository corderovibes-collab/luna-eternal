package net.pokereport.luna.lunita;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokedex.Dexes;
import com.cobblemon.mod.common.api.pokemon.egg.EggGroup;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.LunaEternal;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** The memorial Pokémon is independent of the unique, reward-bearing guardian. */
public final class LunitaPokemon {
    public static final Identifier SPECIES = Identifier.of("lunaeternal", "lunita");
    private static int blockedNaturalSpawns;
    private LunitaPokemon() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
            dispatcher.register(literal("lunita").requires(source -> source.hasPermissionLevel(4))
                .then(literal("dar").then(argument("jugador", EntityArgumentType.player())
                    .executes(ctx -> give(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "jugador"), 50))
                    .then(argument("nivel", IntegerArgumentType.integer(1, 100)).executes(ctx ->
                        give(ctx.getSource(), EntityArgumentType.getPlayer(ctx, "jugador"), IntegerArgumentType.getInteger(ctx, "nivel"))))))
                .then(literal("verificar").executes(ctx -> verifyCommand(ctx.getSource())))));
        // Defense in depth: a later datapack cannot accidentally make her wild.
        // This event belongs to Cobblemon spawners, not owned Pokémon sent out.
        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe(event -> {
            if (SPECIES.equals(event.getEntity().getPokemon().getSpecies().getResourceIdentifier())) {
                event.cancel();
                blockedNaturalSpawns++;
                LunaEternal.LOG.warn("[LUNITA-POKEMON] blocked natural spawn");
            }
        });
        CobblemonEvents.POKEMON_SENT_POST.subscribe(event -> {
            var entity = event.getPokemonEntity();
            if (entity != null && SPECIES.equals(entity.getPokemon().getSpecies().getResourceIdentifier())
                    && entity.getWorld() instanceof ServerWorld world) {
                world.spawnParticles(ParticleTypes.HEART, entity.getX(), entity.getY() + .45,
                        entity.getZ(), 3, .18, .1, .18, 0);
                world.spawnParticles(ParticleTypes.END_ROD, entity.getX(), entity.getY() + .7,
                        entity.getZ(), 4, .15, .1, .15, .01);
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                verifyLoadedSpecies();
                LunaEternal.LOG.info("[LUNITA-POKEMON] verified species, typing, abilities, moves, shoulders and breeding exclusion");
            } catch (Exception e) {
                LunaEternal.LOG.error("[LUNITA-POKEMON] validation failed", e);
            }
        });
    }

    public static void verifyLoadedSpecies() {
        var species = PokemonSpecies.getByIdentifier(SPECIES);
        if (species == null) throw new IllegalStateException("Lunita species was not loaded");
        var dex = Dexes.INSTANCE.getDexEntryMap().get(SPECIES);
        if (dex == null || dex.getEntries().stream().noneMatch(entry -> SPECIES.equals(entry.getSpeciesId()))) {
            throw new IllegalStateException("Memorial Pokédex entry was not loaded");
        }
        if (!species.getPrimaryType().getShowdownId().equals("fairy") || species.getSecondaryType() == null
                || !species.getSecondaryType().getShowdownId().equals("psychic")) throw new IllegalStateException("Wrong typing");
        if (!species.getShoulderMountable() || !species.getEggGroups().contains(EggGroup.UNDISCOVERED)
                || !species.getStandardForm().getEvolutions().isEmpty()) throw new IllegalStateException("Acquisition/shoulder configuration invalid");
        for (String ability : new String[]{"runaway", "adaptability", "anticipation"}) {
            var pokemon = PokemonProperties.Companion.parse("species=lunaeternal:lunita level=50 ability=" + ability).create();
            if (!SPECIES.equals(pokemon.getSpecies().getResourceIdentifier()) || !pokemon.getAbility().getName().equals(ability)) {
                throw new IllegalStateException("Failed Pokémon creation or ability: " + ability);
            }
        }
        for (String move : new String[]{"moonblast", "psychic", "drainingkiss", "wish", "calmmind", "lifedew", "healingwish"}) {
            if (Moves.getByName(move) == null) throw new IllegalStateException("Unknown move " + move);
        }
    }

    private static int give(ServerCommandSource source, ServerPlayerEntity player, int level) {
        try {
            verifyLoadedSpecies();
            var pokemon = PokemonProperties.Companion.parse("species=lunaeternal:lunita level=" + level
                    + " gender=female friendship=255").create();
            var storage = Cobblemon.INSTANCE.getStorage();
            boolean party = storage.getParty(player).add(pokemon);
            if (!party && !storage.getPC(player).add(pokemon)) throw new IllegalStateException("Team and PC are full");
            source.sendFeedback(() -> Text.literal("§dLunita entregada a §f" + player.getName().getString()
                    + " §7(nivel " + level + ", " + (party ? "equipo" : "PC") + ")."), true);
            player.sendMessage(Text.literal("§d✦ Lunita te acompañará en cada aventura. El cariño que dejó brilla para siempre."), false);
            LunaEternal.LOG.info("[LUNITA-POKEMON] admin={} recipient={} pokemon={} level={}",
                    source.getName(), player.getUuid(), pokemon.getUuid(), level);
            return 1;
        } catch (Exception e) {
            LunaEternal.LOG.error("[LUNITA-POKEMON] grant failed", e);
            source.sendError(Text.literal("No se pudo entregar Lunita: " + e.getMessage()));
            return 0;
        }
    }

    private static int verifyCommand(ServerCommandSource source) {
        try {
            verifyLoadedSpecies();
            source.sendFeedback(() -> Text.literal("§aLunita: Hada/Psíquico; habilidades de Eevee; hombros habilitados;"
                    + " cría/evolución deshabilitadas; apariciones naturales bloqueadas=" + blockedNaturalSpawns), false);
            return 1;
        } catch (Exception e) {
            source.sendError(Text.literal("Lunita: " + e.getMessage()));
            return 0;
        }
    }
}
