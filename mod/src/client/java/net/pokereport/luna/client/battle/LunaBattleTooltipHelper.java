package net.pokereport.luna.client.battle;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.moves.categories.DamageCategory;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.battles.Targetable;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import name.modid.client.BattleExtrasConfig;
import name.modid.client.BattleMessageSubscriber;
import name.modid.client.CustomBattleController;
import name.modid.client.MoveDamagePreviewCalculator;
import name.modid.client.MoveTooltipOverlayState;
import name.modid.client.TypeChart;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.pokereport.luna.client.mixin.MoveTooltipOverlayStateAccessor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Auditor y reparador en tiempo real de tooltips, efectividad y rango de daño
 * en la interfaz de combate de Cobblemon.
 * Resuelve de raíz la pérdida intermitente de tooltips por caducidad de 50ms
 * y la omisión de efectividad/daño tras debilitaciones (K.O.) o cambios.
 */
public final class LunaBattleTooltipHelper {

    private LunaBattleTooltipHelper() {}

    public static void auditAndFixTooltip(BattleMoveSelection.MoveTile moveTile, int mouseX, int mouseY) {
        if (moveTile == null) return;
        if (!moveTile.isHovered(mouseX, mouseY)) return;

        MoveTemplate moveTemplate = moveTile.getMoveTemplate();
        if (moveTemplate == null) return;

        boolean isDamaging = moveTemplate.getDamageCategory() != DamageCategories.INSTANCE.getSTATUS()
                && moveTemplate.getPower() > 0;

        boolean queued = MoveTooltipOverlayStateAccessor.isQueued();
        List<Text> lines = MoveTooltipOverlayStateAccessor.getLines();

        if (!queued || lines == null || lines.isEmpty()) {
            // El mod original no llegó a encolar nada (excepción o fallo silencioso).
            // Reconstruimos el tooltip completo garantizado.
            rebuildAndQueueFullTooltip(moveTile, mouseX, mouseY);
            return;
        }

        if (isDamaging) {
            boolean hasEffectiveness = false;
            boolean hasDamageRange = false;

            for (Text line : lines) {
                String str = line.getString();
                if (containsEffectiveness(str)) {
                    hasEffectiveness = true;
                    if (str.contains("\u2694") || str.contains("%")) {
                        hasDamageRange = true;
                    }
                }
            }

            if (!hasEffectiveness) {
                // El bug clásico: la ronda comenzó con target nulo temporal y se cacheó sin efectividad.
                List<ClientBattlePokemon> targets = resolveTargets(moveTile);
                if (!targets.isEmpty()) {
                    List<Text> updatedLines = new ArrayList<>(lines);
                    updatedLines.add(Text.empty()); // Separador

                    boolean showPrefix = targets.size() > 1;
                    for (ClientBattlePokemon target : targets) {
                        float eff = calculateEffectiveness(moveTemplate, target);
                        Text effectLine = buildEffectivenessLine(moveTile, target, moveTemplate, eff, showPrefix);
                        updatedLines.add(effectLine);
                    }

                    MoveTooltipOverlayStateAccessor.setLines(updatedLines);
                    invalidateOriginalCache();
                }
            } else if (!hasDamageRange && BattleExtrasConfig.isMoveDamageRangeEnabled()) {
                // Hay efectividad pero faltaba el rango de daño ("cuánto le quito").
                enhanceExistingEffectivenessWithDamage(moveTile, lines);
            }
        }
    }

    private static boolean containsEffectiveness(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("\u00d70") || lower.contains("x0")
                || lower.contains("\u00d70.25") || lower.contains("x0.25")
                || lower.contains("\u00d70.5") || lower.contains("x0.5")
                || lower.contains("\u00d71") || lower.contains("x1")
                || lower.contains("\u00d72") || lower.contains("x2")
                || lower.contains("\u00d74") || lower.contains("x4")
                || lower.contains("immune") || lower.contains("inmune")
                || lower.contains("effective") || lower.contains("eficaz");
    }

    private static void rebuildAndQueueFullTooltip(BattleMoveSelection.MoveTile moveTile, int mouseX, int mouseY) {
        MoveTemplate template = moveTile.getMoveTemplate();
        if (template == null) return;

        List<Text> tooltip = new ArrayList<>();
        int typeColor = template.getElementalType().getHue();

        // 1. Nombre del movimiento
        String moveName = template.getDisplayName().getString();
        tooltip.add(Text.literal(moveName).styled(s -> s.withColor(typeColor).withBold(true)));

        // 2. Tipo | Categoría
        String typeName = template.getElementalType().getDisplayName().getString();
        String catName = template.getDamageCategory().getDisplayName().getString();
        tooltip.add(Text.literal(typeName + " | " + catName).formatted(Formatting.GRAY));

        // 3. Descripción
        try {
            String descKey = "cobblemon.move." + template.getName().toLowerCase(Locale.ROOT) + ".desc";
            MutableText descComp = Text.translatable(descKey);
            String desc = descComp.getString();
            if (desc != null && !desc.isBlank() && !desc.equals(descKey)) {
                tooltip.add(Text.empty());
                for (String part : wrapText(desc, 35)) {
                    tooltip.add(Text.literal(part).formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
                }
            }
        } catch (Throwable ignored) {}

        // 4. Estadísticas base
        tooltip.add(Text.empty());
        boolean isStatus = template.getDamageCategory() == DamageCategories.INSTANCE.getSTATUS();
        double power = template.getPower();
        double accuracy = template.getAccuracy();

        Pokemon playerPokemon = moveTile.getPokemon();
        float stab = 1.0f;
        if (playerPokemon != null) {
            if (playerPokemon.getPrimaryType() == template.getElementalType()
                    || playerPokemon.getSecondaryType() == template.getElementalType()) {
                stab = 1.5f;
            }
        }

        if (power > 0.0) {
            int basePwr = (int) power;
            if (stab > 1.0f) {
                int adj = (int) (basePwr * stab);
                tooltip.add(Text.translatable("move.battleinfo.power.stab", basePwr, adj).formatted(Formatting.RED));
            } else {
                tooltip.add(Text.translatable("move.battleinfo.power", basePwr).formatted(Formatting.RED));
            }
        } else if (!isStatus) {
            tooltip.add(Text.translatable("move.battleinfo.power.none").formatted(Formatting.DARK_GRAY));
        }

        if (accuracy > 0.0) {
            tooltip.add(Text.translatable("move.battleinfo.accuracy", (int) accuracy).formatted(Formatting.AQUA));
        } else {
            tooltip.add(Text.translatable("move.battleinfo.accuracy.none").formatted(Formatting.DARK_GRAY));
        }

        int prio = template.getPriority();
        tooltip.add(Text.translatable("move.battleinfo.priority", prio > 0 ? "+" + prio : String.valueOf(prio))
                .formatted(prio > 0 ? Formatting.GREEN : (prio < 0 ? Formatting.RED : Formatting.GRAY)));

        InBattleMove inBattle = moveTile.getMove();
        if (inBattle != null) {
            int pp = inBattle.getPp();
            int maxPp = inBattle.getMaxpp();
            Formatting ppColor = pp == 0 ? Formatting.RED : (pp <= maxPp / 4 ? Formatting.GOLD : Formatting.YELLOW);
            tooltip.add(Text.translatable("move.battleinfo.pp", pp, maxPp).formatted(ppColor));
        }

        // 5. Efectividad y rango de daño
        if (!isStatus && power > 0.0) {
            List<ClientBattlePokemon> targets = resolveTargets(moveTile);
            if (!targets.isEmpty()) {
                tooltip.add(Text.empty());
                boolean showPrefix = targets.size() > 1;
                for (ClientBattlePokemon target : targets) {
                    float eff = calculateEffectiveness(template, target);
                    tooltip.add(buildEffectivenessLine(moveTile, target, template, eff, showPrefix));
                }
            }
        }

        int[] tileBounds = BattleExtrasConfig.isVanillaStyleMoveTooltipsEnabled()
                ? CustomBattleController.getTilePosition(moveTile) : null;

        MoveTooltipOverlayState.queue(
                tooltip,
                typeColor,
                template.getElementalType(),
                template.getDamageCategory(),
                mouseX,
                mouseY,
                -1,
                tileBounds
        );
    }

    private static List<ClientBattlePokemon> resolveTargets(BattleMoveSelection.MoveTile moveTile) {
        List<ClientBattlePokemon> targets = new ArrayList<>();
        try {
            List<Targetable> list = moveTile.getTargetList();
            if (list != null) {
                for (Targetable t : list) {
                    if (t instanceof ActiveClientBattlePokemon active) {
                        ClientBattlePokemon bp = active.getBattlePokemon();
                        if (bp != null) targets.add(bp);
                    }
                }
                if (!targets.isEmpty()) return targets;
            }
        } catch (Throwable ignored) {}

        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            if (battle != null) {
                ClientBattleSide playerSide = null;
                try {
                    ActiveClientBattlePokemon active = moveTile.getMoveSelection().getRequest().getActivePokemon();
                    if (active != null && active.getActor() != null) {
                        playerSide = active.getActor().getSide();
                    }
                } catch (Throwable ignored) {}

                ClientBattleSide opponentSide = null;
                if (playerSide != null) {
                    opponentSide = (playerSide == battle.getSide1()) ? battle.getSide2() : battle.getSide1();
                } else {
                    opponentSide = (battle.getSide2() != null) ? battle.getSide2() : battle.getSide1();
                }

                if (opponentSide != null && opponentSide.getActors() != null) {
                    for (ClientBattleActor actor : opponentSide.getActors()) {
                        if (actor == null) continue;
                        for (ActiveClientBattlePokemon active : actor.getActivePokemon()) {
                            if (active == null) continue;
                            ClientBattlePokemon bp = active.getBattlePokemon();
                            if (bp != null) targets.add(bp);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (targets.size() > 1) {
            Collections.reverse(targets);
        }
        return targets;
    }

    private static float calculateEffectiveness(MoveTemplate moveTemplate, ClientBattlePokemon opponentPokemon) {
        if (moveTemplate == null || opponentPokemon == null) return 1.0f;
        try {
            String moveType = moveTemplate.getElementalType().getName().toLowerCase(Locale.ROOT);
            String opponentName = opponentPokemon.getSpecies().getName();

            String teraType = BattleMessageSubscriber.getTeraType("opponent", opponentName);
            if (teraType != null && !teraType.isBlank()) {
                return TypeChart.getEffectivenessAgainstTypes(moveType, teraType.toLowerCase(Locale.ROOT), null);
            }

            String transformedType = BattleMessageSubscriber.getTransformedType("opponent", opponentName);
            if (transformedType != null && !transformedType.isBlank()) {
                return TypeChart.getEffectivenessAgainstTypes(moveType, transformedType.toLowerCase(Locale.ROOT), null);
            }

            List<String> megaTypes = BattleMessageSubscriber.getMegaTypes("opponent", opponentName);
            if (megaTypes != null && !megaTypes.isEmpty()) {
                String m1 = megaTypes.get(0).toLowerCase(Locale.ROOT);
                String m2 = megaTypes.size() > 1 ? megaTypes.get(1).toLowerCase(Locale.ROOT) : null;
                return TypeChart.getEffectivenessAgainstTypes(moveType, m1, m2);
            }

            ElementalType type1 = opponentPokemon.getSpecies().getPrimaryType();
            ElementalType type2 = opponentPokemon.getSpecies().getSecondaryType();
            return TypeChart.getEffectiveness(moveTemplate, type1, type2);
        } catch (Throwable t) {
            return 1.0f;
        }
    }

    private static Text buildEffectivenessLine(BattleMoveSelection.MoveTile moveTile, ClientBattlePokemon target, MoveTemplate move, float effectiveness, boolean showPrefix) {
        MutableText effectComp = createEffectivenessComponent(effectiveness);

        if (effectiveness > 0.0f && BattleExtrasConfig.isMoveDamageRangeEnabled()) {
            Text dmgComp = computeDamageRange(moveTile, target, effectiveness);
            if (dmgComp != null) {
                effectComp.append(dmgComp);
            }
        }

        if (showPrefix) {
            String name = target.getSpecies().getTranslatedName().getString();
            int hue = target.getSpecies().getPrimaryType() != null
                    ? target.getSpecies().getPrimaryType().getHue() : 0xAAAAAA;
            MutableText prefix = Text.literal(name + ": ").styled(s -> s.withColor(hue));
            return Text.empty().append(prefix).append(effectComp);
        }
        return Text.empty().append(effectComp);
    }

    private static MutableText createEffectivenessComponent(float effectiveness) {
        if (effectiveness == 0.0f) {
            return Text.translatable("move.battleinfo.immune", "\u00d70").formatted(Formatting.DARK_GRAY);
        } else if (effectiveness <= 0.25f) {
            return Text.translatable("move.battleinfo.mostly_ineffective", "\u00d70.25").formatted(Formatting.DARK_GRAY);
        } else if (effectiveness < 1.0f) {
            return Text.translatable("move.battleinfo.not_effective", "\u00d70.5").formatted(Formatting.GRAY);
        } else if (effectiveness == 1.0f) {
            return Text.translatable("move.battleinfo.normal_effective", "\u00d71").formatted(Formatting.GRAY);
        } else if (effectiveness >= 4.0f) {
            return Text.translatable("move.battleinfo.extremely_effective", "\u00d74").formatted(Formatting.GREEN);
        } else {
            return Text.translatable("move.battleinfo.super_effective", "\u00d72").formatted(Formatting.DARK_GREEN);
        }
    }

    private static Text computeDamageRange(BattleMoveSelection.MoveTile moveTile, ClientBattlePokemon opponentPokemon, float effectiveness) {
        try {
            MoveTemplate move = moveTile.getMoveTemplate();
            if (move == null || move.getPower() <= 0.0) return null;

            Pokemon playerPokemon = moveTile.getPokemon();
            if (playerPokemon == null) return null;

            int attackerLevel = playerPokemon.getLevel() > 0 ? playerPokemon.getLevel() : 50;
            int opponentLevel = opponentPokemon.getLevel() > 0 ? opponentPokemon.getLevel() : attackerLevel;

            Species oppSpecies = opponentPokemon.getSpecies();
            FormData oppForm = oppSpecies.getStandardForm();
            int baseDef = getBaseStat(oppForm, oppSpecies, Stats.DEFENCE);
            int baseSpDef = getBaseStat(oppForm, oppSpecies, Stats.SPECIAL_DEFENCE);
            int baseHp = getBaseStat(oppForm, oppSpecies, Stats.HP);
            if (baseDef <= 0 || baseSpDef <= 0 || baseHp <= 0) return null;

            int atkStat = playerPokemon.getStat(Stats.ATTACK);
            int spaStat = playerPokemon.getStat(Stats.SPECIAL_ATTACK);
            int defStat = playerPokemon.getStat(Stats.DEFENCE);

            String playerName = playerPokemon.getSpecies().getName();
            String oppName = oppSpecies.getName();

            int atkStage = BattleMessageSubscriber.getStatStage("player", playerName, "attack");
            int spaStage = BattleMessageSubscriber.getStatStage("player", playerName, "special-attack");
            int defStage = BattleMessageSubscriber.getStatStage("opponent", oppName, "defense");
            int spDefStage = BattleMessageSubscriber.getStatStage("opponent", oppName, "special-defense");

            float stab = 1.0f;
            if (playerPokemon.getPrimaryType() == move.getElementalType()
                    || playerPokemon.getSecondaryType() == move.getElementalType()) {
                stab = 1.5f;
            }

            String heldItem = "";
            try {
                ItemStack stack = playerPokemon.heldItem();
                if (stack != null && !stack.isEmpty()) {
                    Identifier id = Registries.ITEM.getId(stack.getItem());
                    heldItem = id != null ? id.getPath() : "";
                }
            } catch (Throwable ignored) {}

            String ability = "";
            try {
                if (playerPokemon.getAbility() != null) {
                    ability = playerPokemon.getAbility().getName();
                }
            } catch (Throwable ignored) {}

            float hpRatio = playerPokemon.getMaxHealth() > 0
                    ? (float) playerPokemon.getCurrentHealth() / (float) playerPokemon.getMaxHealth() : 1.0f;
            float oppHpRatio = opponentPokemon.getHpValue();

            String statusName = "";
            try {
                if (playerPokemon.getStatus() != null && playerPokemon.getStatus().getStatus() != null) {
                    statusName = playerPokemon.getStatus().getStatus().getName().getPath();
                }
            } catch (Throwable ignored) {}

            var attackContext = new MoveDamagePreviewCalculator.AttackContext(
                    attackerLevel, atkStat, spaStat, atkStage, spaStage,
                    statusName,
                    true, heldItem, ability, hpRatio, defStat, 0
            );

            var defenceContext = new MoveDamagePreviewCalculator.DefenceContext(
                    opponentLevel, baseHp, baseDef, baseSpDef,
                    opponentPokemon.getMaxHp(), opponentPokemon.isHpFlat(),
                    defStage, spDefStage, oppHpRatio, true, false
            );

            var preview = MoveDamagePreviewCalculator.calculatePreview(
                    attackContext, defenceContext, move, effectiveness, stab
            );

            return MoveDamagePreviewCalculator.buildInlineComponent(preview);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void enhanceExistingEffectivenessWithDamage(BattleMoveSelection.MoveTile moveTile, List<Text> lines) {
        List<ClientBattlePokemon> targets = resolveTargets(moveTile);
        if (targets.isEmpty()) return;

        List<Text> newLines = new ArrayList<>();
        boolean modified = false;

        int targetIdx = 0;
        for (Text line : lines) {
            String str = line.getString();
            if (containsEffectiveness(str) && !str.contains("\u2694") && targetIdx < targets.size()) {
                ClientBattlePokemon target = targets.get(targetIdx++);
                float eff = calculateEffectiveness(moveTile.getMoveTemplate(), target);
                Text dmgComp = computeDamageRange(moveTile, target, eff);
                if (dmgComp != null) {
                    MutableText enriched = line.copy().append(dmgComp);
                    newLines.add(enriched);
                    modified = true;
                    continue;
                }
            }
            newLines.add(line);
        }

        if (modified) {
            MoveTooltipOverlayStateAccessor.setLines(newLines);
            invalidateOriginalCache();
        }
    }

    private static int getBaseStat(FormData form, Species species, Stat stat) {
        if (form != null) {
            try {
                var map = form.getBaseStats();
                if (map != null && map.containsKey(stat)) {
                    Integer v = map.get(stat);
                    if (v != null && v > 0) return v;
                }
            } catch (Throwable ignored) {}
        }
        if (species != null) {
            try {
                var map = species.getBaseStats();
                if (map != null && map.containsKey(stat)) {
                    Integer v = map.get(stat);
                    if (v != null && v > 0) return v;
                }
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    private static void invalidateOriginalCache() {
        try {
            Class<?> clazz = Class.forName("name.modid.mixin.client.MoveTileMixin");
            Field field = clazz.getDeclaredField("cachedMoveTooltipLines");
            field.setAccessible(true);
            field.set(null, null);
        } catch (Throwable ignored) {}
    }

    private static List<String> wrapText(String text, int maxLineLength) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String w : words) {
            if (current.length() + w.length() + 1 > maxLineLength) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            }
            if (current.length() > 0) current.append(" ");
            current.append(w);
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }
}
