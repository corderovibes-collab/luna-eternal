package net.pokereport.luna.lunita;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class LunitaPokemonDataTest {
    private JsonObject read(String path) throws Exception {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void guardianGreetingIncludesTheBlendBeforeTheCompleteAnimation() throws Exception {
        var animations = read("/assets/lunaeternal/animations/lunita.animation.json").getAsJsonObject("animations");
        int duration = (int)Math.ceil(20 * animations.getAsJsonObject("animation.lunita.greet").get("animation_length").getAsDouble());
        assertEquals(duration + LunitaEntity.ANIMATION_TRANSITION_TICKS, LunitaEntity.GREETING_TICKS);
    }
    @Test void memorialIsFairyPsychicAndCannotBeBredOrEvolved() throws Exception {
        var dex = read("/data/lunaeternal/dexes/lunita.json");
        assertEquals("lunaeternal:lunita", dex.getAsJsonArray("entries").get(0).getAsString());
        var data = read("/data/lunaeternal/species/custom/lunita.json");
        assertEquals("fairy", data.get("primaryType").getAsString());
        assertEquals("psychic", data.get("secondaryType").getAsString());
        assertEquals(0, data.get("maleRatio").getAsInt());
        assertTrue(data.get("shoulderMountable").getAsBoolean());
        assertEquals("undiscovered", data.getAsJsonArray("eggGroups").get(0).getAsString());
        assertEquals(0, data.getAsJsonArray("evolutions").size());
        assertEquals(0, data.getAsJsonArray("forms").size());
        assertFalse(data.has("preEvolution"));
        assertEquals(Set.of("runaway", "adaptability", "h:anticipation"),
                new HashSet<>(data.getAsJsonArray("abilities").asList().stream().map(v -> v.getAsString()).toList()));
    }
    @Test void bothShouldersMoveTheWholeModelAndAllAnimationsReferenceExistingBones() throws Exception {
        var poser = read("/assets/lunaeternal/bedrock/pokemon/posers/lunita.json");
        var poses = poser.getAsJsonObject("poses");
        for (String side : new String[]{"left", "right"}) {
            var pose = poses.getAsJsonObject("shoulder_" + side);
            assertEquals("SHOULDER_" + side.toUpperCase(), pose.getAsJsonArray("poseTypes").get(0).getAsString());
            assertEquals("__root", pose.getAsJsonArray("transformedParts").get(0).getAsJsonObject().get("part").getAsString());
        }
        Set<String> bones = new HashSet<>();
        var geo = read("/assets/lunaeternal/bedrock/pokemon/models/lunita/lunita.geo.json");
        for (var bone : geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
            bones.add(bone.getAsJsonObject().get("name").getAsString());
        }
        var animations = read("/assets/lunaeternal/bedrock/pokemon/animations/lunita/lunita.animation.json").getAsJsonObject("animations");
        for (var animation : animations.entrySet()) {
            var channels = animation.getValue().getAsJsonObject().getAsJsonObject("bones");
            if (channels != null) assertTrue(bones.containsAll(channels.keySet()), animation.getKey());
        }
        assertEquals("pokemon.eevee.cry", animations.getAsJsonObject("animation.lunita.cry")
                .getAsJsonObject("sound_effects").getAsJsonObject("0.04").get("effect").getAsString());
    }
}
