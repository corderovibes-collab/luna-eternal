import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import java.awt.Color;

public class ConvertIcon {
    public static void main(String[] args) throws Exception {
        String in = "C:/Users/JUAN/.gemini/antigravity/brain/bd705599-0e86-4654-9e4f-20af6bf25d04/torre_batalla_icon_1788705667152.jpg";
        String out = "mod/src/main/resources/assets/lunaeternal/textures/gui/pokepad/torre_batalla.png";
        
        BufferedImage img = ImageIO.read(new File(in));
        BufferedImage res = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        
        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                int rgb = img.getRGB(x, y);
                Color c = new Color(rgb);
                // Si es el gris muy oscuro de fondo (aproximado)
                if (c.getRed() < 40 && c.getGreen() < 40 && c.getBlue() < 40) {
                    res.setRGB(x, y, 0x00000000); // Transparente
                } else {
                    res.setRGB(x, y, rgb);
                }
            }
        }
        
        // Ensure dirs
        new File(out).getParentFile().mkdirs();
        ImageIO.write(res, "PNG", new File(out));
    }
}