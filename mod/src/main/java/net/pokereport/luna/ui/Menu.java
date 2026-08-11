package net.pokereport.luna.ui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

/**
 * Base de todas las interfaces del servidor.
 *
 * <p>Un menú es un cofre virtual del que <b>no se puede sacar nada</b>: todos
 * los clics se interceptan y se traducen en acciones. Nada de esto requiere un
 * mod de cliente ({@code docs/ui/navigation.md} §7).
 *
 * <p>Reglas que impone esta clase, y que ninguna subclase debería saltarse:
 * <ul>
 *   <li>Los objetos <b>nunca</b> se mueven: no hay forma de duplicar nada.</li>
 *   <li>Toda acción se ejecuta <b>en el servidor</b> (P6).</li>
 *   <li>Los datos se preparan <b>antes</b> de dibujar: {@link #build} no debe
 *       tocar la base de datos ni bloquear el hilo del servidor.</li>
 *   <li>La pila de navegación permite un botón "atrás" coherente.</li>
 * </ul>
 */
public abstract class Menu implements NamedScreenHandlerFactory {

    /** Ancho fijo de un cofre. */
    public static final int COLS = 9;

    private final String title;
    private final int rows;
    private final SimpleInventory inventory;
    private final Map<Integer, ClickAction> actions = new HashMap<>();

    /** Fondo propio de esta pantalla (D-023). {@code null} = cofre gris. */
    private final String skin;

    /** Menú al que vuelve el botón "atrás". {@code null} si es la raíz. */
    private Menu parent;

    protected Menu(String title, int rows) {
        this(title, rows, null);
    }

    /**
     * @param skin nombre del fondo en {@code gui_chars.json}. Si la pantalla
     *             aún no tiene arte, se pasa {@code null} y sale el cofre
     *             normal — se puede migrar pantalla a pantalla.
     */
    protected Menu(String title, int rows, String skin) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("Un cofre tiene entre 1 y 6 filas");
        }
        this.title = title;
        this.rows = rows;
        this.skin = skin;
        this.inventory = new SimpleInventory(rows * COLS);
    }

    /** Qué ocurre al pulsar un hueco. */
    @FunctionalInterface
    public interface ClickAction {
        void run(ServerPlayerEntity player, int button);
    }

    // ------------------------------------------------------------ contenido

    /**
     * Rellena el menú. Se invoca justo antes de abrir y en cada refresco.
     *
     * <p><b>No consultar la base de datos aquí.</b> Los datos se cargan en el
     * hilo de E/S y se pasan al constructor de la subclase.
     */
    protected abstract void build(ServerPlayerEntity player);

    protected void set(int slot, ItemStack stack) {
        if (slot >= 0 && slot < inventory.size()) inventory.setStack(slot, stack);
    }

    /** Coloca un icono y lo que hace al pulsarlo. */
    protected void set(int slot, ItemStack stack, ClickAction action) {
        set(slot, stack);
        if (action != null) actions.put(slot, action);
    }

    /** Coloca un icono en (fila, columna), contando desde 0. */
    protected void set(int row, int col, ItemStack stack, ClickAction action) {
        set(row * COLS + col, stack, action);
    }

    /** Rellena los huecos vacíos, para que el fondo no sea un cofre a medias. */
    protected void fill(net.minecraft.item.Item pane) {
        ItemStack filler = Icon.filler(pane);
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).isEmpty()) inventory.setStack(i, filler.copy());
        }
    }

    protected void fillRow(int row, net.minecraft.item.Item pane) {
        ItemStack filler = Icon.filler(pane);
        for (int c = 0; c < COLS; c++) set(row * COLS + c, filler.copy());
    }

    protected int size() {
        return inventory.size();
    }

    // ------------------------------------------------------------ apertura

    public void open(ServerPlayerEntity player) {
        actions.clear();
        inventory.clear();
        build(player);
        player.openHandledScreen(this);
    }

    /** Abre otro menú recordando este, para que "atrás" funcione. */
    public void openChild(ServerPlayerEntity player, Menu child) {
        child.parent = this;
        child.open(player);
    }

    public void back(ServerPlayerEntity player) {
        if (parent != null) parent.open(player);
        else player.closeHandledScreen();
    }

    public boolean hasParent() {
        return parent != null;
    }

    /** Redibuja sin cerrar: para saldos y listados que cambian solos. */
    public void refresh(ServerPlayerEntity player) {
        actions.clear();
        inventory.clear();
        build(player);
        if (player.currentScreenHandler instanceof Handler h && h.menu == this) {
            h.sendContentUpdates();
        }
    }

    // ------------------------------------------------------------ interno

    private void click(int slot, int button, ServerPlayerEntity player) {
        ClickAction action = actions.get(slot);
        if (action == null) {
            // Hueco sin acción: sonido sordo. El jugador nota que ha pulsado
            // algo pero que no hace nada, en vez de dudar si va lento.
            play(player, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.5f, 0.8f);
            return;
        }
        play(player, SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.2f);
        action.run(player, button);
    }

    protected static void play(ServerPlayerEntity p,
                               net.minecraft.sound.SoundEvent sound,
                               float volume, float pitch) {
        p.playSoundToPlayer(sound, SoundCategory.MASTER, volume, pitch);
    }

    @Override
    public Text getDisplayName() {
        return Skin.title(skin, title);
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity player) {
        return new Handler(syncId, playerInv, this);
    }

    /**
     * El manejador que convierte un cofre en una interfaz: intercepta todos los
     * clics y no deja mover ni un objeto.
     */
    private static final class Handler extends GenericContainerScreenHandler {

        private final Menu menu;

        private Handler(int syncId, PlayerInventory playerInv, Menu menu) {
            super(typeFor(menu.rows), syncId, playerInv, menu.inventory, menu.rows);
            this.menu = menu;
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType type,
                                PlayerEntity player) {
            // Se traga TODO: ni arrastrar, ni shift-clic, ni teclas numéricas,
            // ni doble clic. Ninguna ruta puede sacar un objeto del menú.
            if (player instanceof ServerPlayerEntity sp
                    && slotIndex >= 0 && slotIndex < menu.inventory.size()) {
                menu.click(slotIndex, button, sp);
            }
        }

        @Override
        public ItemStack quickMove(PlayerEntity player, int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canUse(PlayerEntity player) {
            return true;
        }

        @Override
        public boolean canInsertIntoSlot(net.minecraft.screen.slot.Slot slot) {
            return false;
        }

        private static net.minecraft.screen.ScreenHandlerType<GenericContainerScreenHandler>
        typeFor(int rows) {
            return switch (rows) {
                case 1 -> net.minecraft.screen.ScreenHandlerType.GENERIC_9X1;
                case 2 -> net.minecraft.screen.ScreenHandlerType.GENERIC_9X2;
                case 3 -> net.minecraft.screen.ScreenHandlerType.GENERIC_9X3;
                case 4 -> net.minecraft.screen.ScreenHandlerType.GENERIC_9X4;
                case 5 -> net.minecraft.screen.ScreenHandlerType.GENERIC_9X5;
                default -> net.minecraft.screen.ScreenHandlerType.GENERIC_9X6;
            };
        }
    }
}
