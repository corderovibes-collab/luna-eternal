package net.pokereport.luna.pokestop;

import java.util.*;

/** Lista cerrada: no puede sortear objetos raros de otros mods. */
public final class StopRewards {
    public static final long COOLDOWN_MS = 86_400_000L;
    public record Gift(String item, int count) {}
    private static final List<String> BASIC = List.of(
        "cobblemon:poke_ball", "cobblemon:oran_berry", "cobblemon:pecha_berry",
        "cobblemon:chesto_berry", "cobblemon:red_apricorn", "cobblemon:blue_apricorn",
        "cobblemon:yellow_apricorn", "cobblemon:black_apricorn", "cobblemon:white_apricorn",
        "minecraft:wheat_seeds", "minecraft:pumpkin_seeds", "minecraft:melon_seeds",
        "minecraft:beetroot_seeds", "minecraft:oak_log", "minecraft:birch_log",
        "minecraft:spruce_log", "minecraft:stick", "minecraft:cobblestone",
        "minecraft:carrot", "minecraft:potato", "minecraft:bread", "minecraft:torch");
    public static List<String> allowed() { return BASIC; }
    public static List<Gift> roll(Random random) {
        var pool = new ArrayList<>(BASIC);
        Collections.shuffle(pool, random);
        int size = 4 + random.nextInt(2);
        var result = new ArrayList<Gift>();
        for (int i = 0; i < size; i++) {
            String item = pool.get(i);
            int max = item.equals("cobblemon:poke_ball") || item.equals("minecraft:bread") ? 2 : 4;
            result.add(new Gift(item, 1 + random.nextInt(max)));
        }
        return List.copyOf(result);
    }
    public static long remaining(long now, long last) { return Math.max(0, last + COOLDOWN_MS - now); }
    public static boolean milestone(long count) { return count > 0 && count % 50 == 0; }
}
