package net.pokereport.luna.test;
import net.minecraft.client.gui.screen.Screen;
import java.lang.reflect.Method;
public class TestBlur {
    public static void run() {
        for (Method m : Screen.class.getMethods()) {
            if (m.getName().toLowerCase().contains("back")) {
                System.out.println("Method: " + m.getName());
            }
            if (m.getName().toLowerCase().contains("blur")) {
                System.out.println("Method: " + m.getName());
            }
        }
    }
}