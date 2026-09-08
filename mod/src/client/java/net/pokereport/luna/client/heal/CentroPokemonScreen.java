package net.pokereport.luna.client.heal;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.pokereport.luna.net.Red;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;

public class CentroPokemonScreen extends Screen {

    private final Screen padre;
    private long tickInicio;
    private static final String TEXTO = "¡Hola! Bienvenido al Centro Pokémon. ¿Te gustaría que curase a tus Pokémon? Podría llevarme un momento, pero te los dejaré como nuevos.";
    private int ultimoChar;
    private LivingEntity enfermeraCache = null;

    // Colores del UI (estilo PokePad)
    private static final int FONDO_CAJA = 0xEE1E2430;
    private static final int BORDE_CAJA = 0xFF4A566E;
    private static final int COLOR_TEXTO = 0xFFFFFFFF;
    private static final int COLOR_TITULO = 0xFFFFB900;
    private static final int BOTON_NORMAL = 0xFF2D3545;
    private static final int BOTON_HOVER = 0xFF586B8C;
    private static final int BOTON_BORDE = 0xFF8399C2;

    public CentroPokemonScreen(Screen padre) {
        super(Text.literal("Centro Pokémon"));
        this.padre = padre;
    }

    @Override
    protected void init() {
        this.tickInicio = System.currentTimeMillis();
        this.ultimoChar = 0;
    }

    private LivingEntity buscarEnfermera() {
        if (enfermeraCache != null) return enfermeraCache;
        if (this.client == null || this.client.world == null || this.client.player == null) return null;
        
        for (Entity e : this.client.world.getEntities()) {
            if (e instanceof LivingEntity le) {
                String name = net.minecraft.registry.Registries.ENTITY_TYPE.getId(e.getType()).toString();
                if (name.contains("nurse") || name.contains("npc") || e.getCommandTags().contains("luna_enfermera")) {
                    if (e.squaredDistanceTo(this.client.player) < 100) {
                        enfermeraCache = le;
                        return le;
                    }
                }
            }
        }
        return null;
    }

    private void dibujarBoton(DrawContext ctx, int x, int y, int w, int h, String texto, boolean hover) {
        // Estilo botón clásico Pokémon
        ctx.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF9E1B1B); // Borde exterior oscuro
        ctx.fill(x, y, x + w, y + h, hover ? 0xFFFFE5E5 : 0xFFFFFFFF); // Fondo
        ctx.drawBorder(x + 1, y + 1, w - 2, h - 2, 0xFFE33539); // Borde interior rojo brillante
        
        int tx = x + (w - this.textRenderer.getWidth(texto)) / 2;
        int ty = y + (h - this.textRenderer.fontHeight) / 2;
        ctx.drawText(this.textRenderer, texto, tx, ty, hover ? 0xFFE33539 : 0xFF222222, false);
    }

    @Override
    public void render(DrawContext ctx, int rx, int ry, float delta) {
        super.render(ctx, rx, ry, delta);

        int w = this.width;
        int h = this.height;

        int pW = Math.min(600, (int) (w * 0.7)); // Un poco más estrecho para ser más elegante
        int pH = 120;
        int pX = (w - pW) / 2;
        int pY = h - pH - 30;

        // Fondo semi-transparente suave
        ctx.fillGradient(0, 0, w, h, 0x33000000, 0x88000000);

        // --- RENDERIZADO 3D DE LA ENFERMERA ---
        LivingEntity enfermera = buscarEnfermera();
        if (enfermera != null) {
            int ex = w / 2;
            int ey = pY + 20;
            
            // Resplandor rosado/blanco curativo detrás de ella
            ctx.fillGradient(ex - 70, ey - 220, ex + 70, ey, 0x00FFB900, 0x55FFC0CB);
            
            // El visor mira fijo hacia el frente (al centro de sí misma)
            InventoryScreen.drawEntity(ctx, ex - 60, ey - 200, ex + 60, ey,
                    70, 0.0f, (float)ex, (float)(ey - 50), enfermera);
        }

        // --- CAJA DE DIÁLOGO ESTILO POKÉMON ---
        // Sombra exterior negra
        ctx.fill(pX - 4, pY - 4, pX + pW + 4, pY + pH + 4, 0x66000000);
        // Borde exterior rojo oscuro
        ctx.fill(pX - 2, pY - 2, pX + pW + 2, pY + pH + 2, 0xFF9E1B1B);
        // Fondo rojo principal
        ctx.fill(pX, pY, pX + pW, pY + pH, 0xFFE33539);
        // Fondo blanco donde va el texto
        ctx.fill(pX + 4, pY + 26, pX + pW - 4, pY + pH - 4, 0xFFF8F8F8);

        long ahora = System.currentTimeMillis();
        int chars = (int) ((ahora - tickInicio) / 25);
        
        if (chars > TEXTO.length()) chars = TEXTO.length();

        if (chars > ultimoChar && chars < TEXTO.length()) {
            if (chars % 3 == 0 && this.client != null) {
                this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.8f, 0.2f));
            }
            ultimoChar = chars;
        }

        String t = TEXTO.substring(0, chars);
        
        // Título Enfermera Claudia
        ctx.drawTextWithShadow(this.textRenderer, "Enfermera Claudia", pX + 15, pY + 8, 0xFFFFCC00);
        
        // Separador sutil
        ctx.fill(pX + 4, pY + 26, pX + pW - 4, pY + 28, 0xFF9E1B1B);

        // Texto principal (sin sombra para que resalte en blanco)
        ctx.drawTextWrapped(this.textRenderer, net.minecraft.text.StringVisitable.plain(t), 
                pX + 20, pY + 42, pW - 40, 0xFF222222);

        // --- BOTONES ---
        if (chars >= TEXTO.length()) {
            int btnW = 120;
            int btnH = 26;
            int b1X = pX + pW - (btnW * 2) - 30;
            int b2X = pX + pW - btnW - 15;
            int bY = pY + pH - btnH - 15;

            boolean sobreSi = rx >= b1X && rx <= b1X + btnW && ry >= bY && ry <= bY + btnH;
            boolean sobreNo = rx >= b2X && rx <= b2X + btnW && ry >= bY && ry <= bY + btnH;

            dibujarBoton(ctx, b1X, bY, btnW, btnH, "Sí, por favor", sobreSi);
            dibujarBoton(ctx, b2X, bY, btnW, btnH, "No, gracias", sobreNo);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        long ahora = System.currentTimeMillis();
        int chars = (int) ((ahora - tickInicio) / 25);

        if (chars < TEXTO.length()) {
            tickInicio = ahora - (TEXTO.length() * 25L);
            if (this.client != null) {
                this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 2.0f, 0.3f));
            }
            return true;
        }

        int w = this.width;
        int h = this.height;
        int pW = Math.min(600, (int) (w * 0.7));
        int pH = 120;
        int pX = (w - pW) / 2;
        int pY = h - pH - 30;

        int btnW = 120;
        int btnH = 26;
        int b1X = pX + pW - (btnW * 2) - 30;
        int b2X = pX + pW - btnW - 15;
        int bY = pY + pH - btnH - 15;

        boolean sobreSi = mx >= b1X && mx <= b1X + btnW && my >= bY && my <= bY + btnH;
        boolean sobreNo = mx >= b2X && mx <= b2X + btnW && my >= bY && my <= bY + btnH;

        if (sobreSi) {
            ClientPlayNetworking.send(new Red.ConfirmarCuraCentro());
            if (this.client != null) this.client.setScreen(padre);
            return true;
        }

        if (sobreNo) {
            if (this.client != null) this.client.setScreen(padre);
            return true;
        }

        return super.mouseClicked(mx, my, boton);
    }
}
