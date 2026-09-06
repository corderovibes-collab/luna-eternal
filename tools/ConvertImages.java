import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class ConvertImages {
    public static void main(String[] args) throws Exception {
        String[] files = {
            "1vs1", "C:/Users/JUAN/.gemini/antigravity/brain/bd705599-0e86-4654-9e4f-20af6bf25d04/.user_uploaded/media_1788710347739.jpg",
            "2vs2", "C:/Users/JUAN/.gemini/antigravity/brain/bd705599-0e86-4654-9e4f-20af6bf25d04/.user_uploaded/media_1788710347721.jpg",
            "aleatorio", "C:/Users/JUAN/.gemini/antigravity/brain/bd705599-0e86-4654-9e4f-20af6bf25d04/.user_uploaded/media_1788710347732.jpg"
        };
        
        String outDir = "mod/src/client/resources/assets/lunaeternal/textures/gui/pokepad/";
        new File(outDir).mkdirs();
        
        for (int i = 0; i < files.length; i += 2) {
            String name = files[i];
            String path = files[i+1];
            
            BufferedImage img = ImageIO.read(new File(path));
            BufferedImage bg = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = bg.createGraphics();
            g.drawImage(img, 0, 32, 256, 192, null);
            g.dispose();
            
            File out = new File(outDir + "torre_modo_" + name + ".png");
            ImageIO.write(bg, "PNG", out);
            System.out.println("Saved " + out.getAbsolutePath());
        }
    }
}