package net.pokereport.luna.rotom;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RotomAssetsTest {
    private static final Path ASSETS=Path.of("src/main/resources/assets/lunaeternal");
    @Test void animationBonesAndEditableSourceMatchExport() throws Exception {
        var geo=JsonParser.parseString(Files.readString(ASSETS.resolve("geo/rotom_dex.geo.json")))
                .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        Set<String> names=new HashSet<>(); int count=0;
        for (var value:geo.getAsJsonArray("bones")) {
            var bone=value.getAsJsonObject();
            assertTrue(names.add(bone.get("name").getAsString()));
            count+=bone.getAsJsonArray("cubes").size();
        }
        for (var value:geo.getAsJsonArray("bones")) {
            var bone=value.getAsJsonObject();
            if(bone.has("parent")) assertTrue(names.contains(bone.get("parent").getAsString()));
        }
        var animation=JsonParser.parseString(Files.readString(ASSETS.resolve("animations/rotom_dex.animation.json")))
                .getAsJsonObject().getAsJsonObject("animations");
        for(String name:List.of("idle","blink","notice","interact","scan","happy")) {
            var item=animation.getAsJsonObject("animation.rotom."+name);
            assertNotNull(item,name);
            for(String bone:item.getAsJsonObject("bones").keySet()) assertTrue(names.contains(bone),bone);
        }
        var editable=JsonParser.parseString(Files.readString(Path.of("../arte/rotom/rotom_dex.bbmodel"))).getAsJsonObject();
        assertEquals(count,editable.getAsJsonArray("elements").size());
        assertEquals(6,editable.getAsJsonArray("animations").size());
        assertTrue(count>=40);
    }
    @Test void expressionTexturesAndGlowMasksDecodeAtSameSize() throws Exception {
        Set<Integer> faceHashes=new HashSet<>();
        for(int i=0;i<7;i++) {
            var texture=javax.imageio.ImageIO.read(ASSETS.resolve("textures/entity/rotom_dex_"+i+".png").toFile());
            var mask=javax.imageio.ImageIO.read(ASSETS.resolve("textures/entity/rotom_dex_"+i+"_glowmask.png").toFile());
            assertEquals(128,texture.getWidth()); assertEquals(128,texture.getHeight());
            assertEquals(128,mask.getWidth()); assertEquals(128,mask.getHeight());
            faceHashes.add(Arrays.hashCode(texture.getRGB(0,32,32,32,null,0,32)));
        }
        assertTrue(faceHashes.size()>=6,"At least six genuinely distinct expressions");
    }
}
