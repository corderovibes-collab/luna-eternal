package net.pokereport.luna.client.pokepad;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.pokereport.luna.client.EstadoCliente;
import net.pokereport.luna.net.Red;

/** Catálogo social de lugares públicos. Toda autorización se repite en servidor. */
public final class PwarpsScreen extends Screen {
    private static final String[] TABS = {"PÚBLICOS", "FAVORITOS", "POPULARES", "RECIENTES", "MIS WARPS"};
    private static final String[] CATS = {"OTROS", "TIENDA", "GRANJA", "CONSTRUCCION", "EVENTO", "SERVICIO"};
    private final Screen anterior;
    private int tab, scroll;
    private long seleccionado = -1;
    private String categoria = "OTROS";
    private TextFieldWidget descripcion;
    private Red.EstadoPwarps estado;

    public PwarpsScreen(Screen anterior) {
        super(Text.literal("pWarps"));
        this.anterior = anterior;
    }

    @Override protected void init() {
        descripcion = new TextFieldWidget(textRenderer, width / 2 - 150, height - 42, 220, 20, Text.literal("Descripción"));
        descripcion.setMaxLength(120);
        descripcion.setPlaceholder(Text.literal("Descripción del lugar"));
        addDrawableChild(descripcion);
        ClientPlayNetworking.send(new Red.PedirPwarps());
    }

    @Override public boolean shouldPause() { return false; }

    private List<Red.PwarpDato> visibles() {
        if (estado == null) return List.of();
        List<Red.PwarpDato> out = new ArrayList<>();
        for (var p : estado.lista()) {
            if (tab == 1 && !p.favorito()) continue;
            if (tab == 3 && p.reciente() <= 0) continue;
            if (tab == 4 && !p.propio()) continue;
            out.add(p);
        }
        if (tab == 2) out.sort(Comparator.comparingLong(Red.PwarpDato::visitas).reversed());
        if (tab == 3) out.sort(Comparator.comparingLong(Red.PwarpDato::reciente).reversed());
        return out;
    }

    @Override public void render(DrawContext c, int mx, int my, float delta) {
        renderBackground(c, mx, my, delta);
        var nuevo = EstadoCliente.pwarps();
        if (nuevo != null && nuevo != estado) estado = nuevo;
        int x = Math.max(20, width / 2 - 360), y = Math.max(18, height / 2 - 225), w = Math.min(720, width - 40), h = Math.min(450, height - 36);
        c.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF25D9FF);
        c.fill(x, y, x + w, y + h, 0xF20A1425);
        c.fill(x, y, x + w, y + 48, 0xFF142B50);
        c.drawCenteredTextWithShadow(textRenderer, "RED DE pWARPS", x + w / 2, y + 12, 0xFF7EEBFF);
        c.drawCenteredTextWithShadow(textRenderer, "Descubre, guarda y visita construcciones de la comunidad", x + w / 2, y + 28, 0xFFB9C9E8);

        int tw = Math.max(88, (w - 20) / TABS.length);
        for (int i = 0; i < TABS.length; i++) {
            int tx = x + 10 + i * tw;
            c.fill(tx, y + 54, tx + tw - 4, y + 76, i == tab ? 0xFF23BFEA : 0xFF22304A);
            c.drawCenteredTextWithShadow(textRenderer, TABS[i], tx + (tw - 4) / 2, y + 61, i == tab ? 0xFF07131E : 0xFFD7E4FF);
        }
        c.fill(x + 10, y + 82, x + w - 10, y + h - 50, 0xFF0E1A2D);
        List<Red.PwarpDato> lista = visibles();
        int max = Math.max(0, lista.size() - 7); scroll = Math.min(scroll, max);
        if (estado == null) c.drawCenteredTextWithShadow(textRenderer, "Cargando catálogo seguro…", x + w / 2, y + 180, 0xFFFFFFFF);
        else if (lista.isEmpty()) c.drawCenteredTextWithShadow(textRenderer, "No hay lugares en esta sección", x + w / 2, y + 180, 0xFF8394B5);
        for (int i = 0; i < 7 && i + scroll < lista.size(); i++) {
            var p = lista.get(i + scroll); int ry = y + 88 + i * 42;
            boolean sel = p.id() == seleccionado;
            c.fill(x + 16, ry, x + w - 16, ry + 36, sel ? 0xFF294A67 : (i % 2 == 0 ? 0xFF17263D : 0xFF132137));
            c.drawTextWithShadow(textRenderer, p.nombre() + "  §7por §b" + p.creador(), x + 25, ry + 6, 0xFFFFFFFF);
            String desc = p.descripcion().isBlank() ? "Sin descripción" : p.descripcion();
            if (desc.length() > 55) desc = desc.substring(0, 52) + "…";
            c.drawTextWithShadow(textRenderer, "§8[§e" + p.categoria() + "§8] §7" + desc, x + 25, ry + 20, 0xFF9FB0CC);
            c.drawTextWithShadow(textRenderer, "§6" + p.visitas() + " visitas", x + w - 200, ry + 7, 0xFFFFFFFF);
            c.drawTextWithShadow(textRenderer, p.favorito() ? "§e★" : "§7☆", x + w - 86, ry + 13, 0xFFFFFFFF);
            c.fill(x + w - 65, ry + 7, x + w - 24, ry + 29, 0xFF19A96E);
            c.drawCenteredTextWithShadow(textRenderer, "IR", x + w - 45, ry + 14, 0xFFFFFFFF);
        }
        c.drawTextWithShadow(textRenderer, "§7Rueda: desplazar  •  ★: favorito  •  IR: viaje seguro", x + 18, y + h - 36, 0xFFFFFFFF);
        c.fill(x + w - 155, y + h - 42, x + w - 85, y + h - 19, 0xFF304769);
        c.drawCenteredTextWithShadow(textRenderer, "PARADAS", x + w - 120, y + h - 35, 0xFFFFFFFF);
        c.fill(x + w - 78, y + h - 42, x + w - 15, y + h - 19, 0xFFB93232);
        c.drawCenteredTextWithShadow(textRenderer, "SALIR", x + w - 47, y + h - 35, 0xFFFFFFFF);

        var elegido = seleccionado();
        boolean editar = elegido != null && elegido.propio();
        descripcion.visible = editar;
        if (editar) {
            c.fill(width / 2 + 75, height - 42, width / 2 + 150, height - 22, 0xFF304769);
            c.drawCenteredTextWithShadow(textRenderer, categoria, width / 2 + 112, height - 35, 0xFFFFFFFF);
            c.fill(width / 2 + 155, height - 42, width / 2 + 215, height - 22, 0xFF19A96E);
            c.drawCenteredTextWithShadow(textRenderer, "GUARDAR", width / 2 + 185, height - 35, 0xFFFFFFFF);
        }
        // No llamar super.render(): en 1.21.1 vuelve a aplicar el blur sobre la interfaz ya dibujada.
        if (descripcion.visible) descripcion.render(c, mx, my, delta);
    }

    private Red.PwarpDato seleccionado() {
        if (estado != null) for (var p : estado.lista()) if (p.id() == seleccionado) return p;
        return null;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        int x = Math.max(20, width / 2 - 360), y = Math.max(18, height / 2 - 225), w = Math.min(720, width - 40), h = Math.min(450, height - 36);
        int tw = Math.max(88, (w - 20) / TABS.length);
        if (my >= y + 54 && my <= y + 76) {
            int n = (int)((mx - x - 10) / tw); if (n >= 0 && n < TABS.length) { tab = n; scroll = 0; return true; }
        }
        List<Red.PwarpDato> lista = visibles();
        for (int i = 0; i < 7 && i + scroll < lista.size(); i++) {
            var p = lista.get(i + scroll); int ry = y + 88 + i * 42;
            if (my >= ry && my <= ry + 36) {
                if (mx >= x + w - 70) ClientPlayNetworking.send(new Red.AccionPwarp("visitar", p.id(), "", ""));
                else if (mx >= x + w - 105) ClientPlayNetworking.send(new Red.AccionPwarp("favorito", p.id(), "", ""));
                else { seleccionado = p.id(); descripcion.setText(p.descripcion()); categoria = p.categoria(); }
                return true;
            }
        }
        if (my >= y + h - 45 && my <= y + h - 16 && mx >= x + w - 160) {
            if (mx < x + w - 82) client.setScreen(new ViajesScreen(anterior)); else close(); return true;
        }
        var p = seleccionado();
        if (p != null && p.propio() && my >= height - 45) {
            if (mx >= width / 2 + 70 && mx <= width / 2 + 152) {
                int n = (java.util.Arrays.asList(CATS).indexOf(categoria) + 1) % CATS.length; categoria = CATS[n]; return true;
            }
            if (mx >= width / 2 + 152) {
                ClientPlayNetworking.send(new Red.AccionPwarp("editar", p.id(), descripcion.getText(), categoria)); return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mx, double my, double ha, double vertical) {
        int max = Math.max(0, visibles().size() - 7);
        scroll = Math.max(0, Math.min(max, scroll + (vertical < 0 ? 1 : -1)));
        return true;
    }

    @Override public void close() { client.setScreen(anterior); }
}
