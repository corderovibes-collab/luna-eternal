package net.pokereport.luna;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class FontLoadingTest {

    @Test
    void testIconosJsonOnClasspath() throws Exception {
        InputStream is = getClass().getResourceAsStream("/assets/lunaeternal/font/iconos.json");
        assertNotNull(is, "iconos.json NOT found on classpath!");

        Gson gson = new Gson();
        JsonObject obj = gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
        assertNotNull(obj, "Parsed json is null");
        assertTrue(obj.has("providers"), "Missing providers key in iconos.json");

        var providers = obj.getAsJsonArray("providers");
        System.out.println("Providers count: " + providers.size());

        for (int i = 0; i < providers.size(); i++) {
            JsonObject p = providers.get(i).getAsJsonObject();
            String file = p.get("file").getAsString();
            System.out.println("Provider " + i + ": type=" + p.get("type").getAsString() + ", file=" + file);

            String namespace = file.substring(0, file.indexOf(':'));
            String path = file.substring(file.indexOf(':') + 1);
            String resourcePath = "/assets/" + namespace + "/textures/" + path;

            InputStream imgStream = getClass().getResourceAsStream(resourcePath);
            assertNotNull(imgStream, "Texture NOT found on classpath: " + resourcePath);

            BufferedImage img = ImageIO.read(imgStream);
            assertNotNull(img, "Could not decode PNG: " + resourcePath);
            System.out.println("  -> Image size: " + img.getWidth() + "x" + img.getHeight());
        }
    }
}