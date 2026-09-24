package net.pokereport.luna.puerta;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.pokereport.luna.net.Red;

import java.util.List;

/** El libro que presenta la Ciudadela y abre el video oficial del Profesor Oak. */
public final class BienvenidaOak {
    private static final String MARCA_ENTREGADO = "luna_intro_oak_v1";
    public static final String VIDEO = "https://github.com/corderovibes-collab/"
            + "luna-eternal-pack/releases/download/pack-assets/"
            + "profesor-oak-intro-stream-v2-eca79c6a08a2.mp4";

    private BienvenidaOak() {}

    /** Abre la película dentro de Minecraft; nunca entrega una URL al navegador. */
    public static int reproducir(ServerPlayerEntity jugador) {
        if (!ServerPlayNetworking.canSend(jugador, Red.Cinematica.ID)) {
            jugador.sendMessage(Text.literal(
                    "§cTu cliente no tiene el reproductor de cinemáticas. "
                            + "Cierra Minecraft y actualiza desde el launcher."), false);
            return 0;
        }
        ServerPlayNetworking.send(jugador, new Red.Cinematica(VIDEO, 29, true));
        return 1;
    }

    /**
     * Lo entrega una sola vez por jugador. La marca vive en el NBT vanilla del
     * jugador, no en memoria: reconectar o reiniciar el servidor no duplica el
     * libro. Si no cabe, Minecraft lo deja a sus pies en vez de perderlo.
     */
    public static void darSiFalta(ServerPlayerEntity jugador) {
        if (jugador.getCommandTags().contains(MARCA_ENTREGADO)) {
            return;
        }

        ItemStack libro = new ItemStack(Items.WRITTEN_BOOK);
        libro.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Bienvenida del Profesor Oak")
                        .formatted(Formatting.GOLD, Formatting.BOLD));
        libro.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        libro.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("Tu primer paso en la Ciudadela")
                        .formatted(Formatting.GRAY),
                Text.literal("Abre el libro y reproduce el video")
                        .formatted(Formatting.YELLOW))));

        Text abrir = Text.literal("\n\n       ▶ VER VIDEO\n")
                .styled(estilo -> estilo
                        .withColor(Formatting.GOLD)
                        .withBold(true)
                        .withUnderline(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/luna videooak"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Text.literal("Abrir bienvenida oficial"))));
        Text pagina = Text.literal("§6§l§nPOKÉREPORT NETWORK§r\n\n")
                .append(Text.literal("§0¡Avemaría pues, entrenador!\n\n"
                        + "El Profesor Oak te da la bienvenida a la Ciudadela. "
                        + "Mira este video antes de comenzar tu aventura."))
                .append(abrir)
                .append(Text.literal("\n§8Se reproduce dentro del juego."));
        libro.set(DataComponentTypes.WRITTEN_BOOK_CONTENT,
                new WrittenBookContentComponent(
                        RawFilteredPair.of("Bienvenida a PokéReport"),
                        "Profesor Oak", 0,
                        List.of(RawFilteredPair.of(pagina)), true));

        if (!jugador.getInventory().insertStack(libro)) {
            jugador.dropItem(libro, false);
        }
        jugador.addCommandTag(MARCA_ENTREGADO);
        jugador.sendMessage(Text.literal(
                "§6§lPROF. OAK §8» §fTe dejé mi libro de bienvenida. "
                        + "§eÁbrelo y pulsa VER VIDEO§f."), false);
    }
}
