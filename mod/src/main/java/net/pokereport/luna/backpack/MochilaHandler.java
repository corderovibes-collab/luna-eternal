package net.pokereport.luna.backpack;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/**
 * El contenedor de la mochila.
 *
 * <h2>⚠⚠ ESTO NO ES «UN MENÚ DE COFRE» DE LOS QUE PROHÍBE P9-BIS</h2>
 *
 * D-026 retiró los menús de cofre porque eran <b>pantallas de información</b>
 * disfrazadas de inventario: un cofre enseñando iconos que no se podían coger.
 * Esto es lo contrario — <b>es</b> un inventario, y arrastrar objetos con el
 * ratón <b>exige</b> un {@code ScreenHandler}: no hay forma de hacerlo con una
 * pantalla que solo dibuje.
 *
 * <p>Lo que sí se cumple de P9-bis es lo que importaba: el dibujo es
 * <b>nuestro</b>, con nuestro arte, y no la textura del cofre de Minecraft.
 *
 * <h2>⚠⚠⚠ EL INVARIANTE: UN HUECO BLOQUEADO NO ACEPTA NADA</h2>
 *
 * Y se cumple <b>en el servidor</b>, dentro del propio {@code Slot}. La
 * pantalla dibuja un candado, pero eso es decoración: un cliente modificado no
 * dibuja nada y hace clic donde quiere. Si el candado viviera solo en el
 * dibujado, cualquiera tendría las siete filas (P6).
 */
public class MochilaHandler extends ScreenHandler {

    /** Lo que el servidor le cuenta al cliente al abrir. */
    public record Apertura(int filas) {
        public static final PacketCodec<RegistryByteBuf, Apertura> CODEC =
                PacketCodec.tuple(PacketCodecs.VAR_INT, Apertura::filas, Apertura::new);
    }

    private final Inventory mochila;
    private final int filas;

    /** El de servidor: recibe el inventario ya cargado de la base. */
    public MochilaHandler(int sincId, PlayerInventory jugador, Inventory mochila,
                          int filas) {
        super(Registro.TIPO, sincId);
        this.mochila = mochila;
        this.filas = Math.max(1, Math.min(Mochila.FILAS_MAX, filas));
        mochila.onOpen(jugador.player);
        montar(jugador);
    }

    /**
     * El de cliente: <b>inventario vacío del tamaño correcto</b>.
     *
     * <p>⚠ Del tamaño COMPLETO aunque haya filas bloqueadas. Los índices de los
     * huecos tienen que coincidir con los del servidor: si el cliente creara
     * solo las filas abiertas, el hueco 20 sería uno distinto en cada lado y
     * los objetos aparecerían movidos.
     */
    public MochilaHandler(int sincId, PlayerInventory jugador, Apertura datos) {
        this(sincId, jugador, new SimpleInventory(Mochila.HUECOS), datos.filas());
    }

    private void montar(PlayerInventory jugador) {
        // --- la mochila, arriba
        for (int fila = 0; fila < Mochila.FILAS_MAX; fila++) {
            for (int col = 0; col < Mochila.COLUMNAS; col++) {
                int indice = fila * Mochila.COLUMNAS + col;
                boolean abierto = fila < filas;
                addSlot(new HuecoMochila(mochila, indice,
                        8 + col * 18, 18 + fila * 18, abierto));
            }
        }
        // --- el inventario del jugador, debajo
        int base = 18 + Mochila.FILAS_MAX * 18 + 13;
        for (int fila = 0; fila < 3; fila++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(jugador, col + fila * 9 + 9,
                        8 + col * 18, base + fila * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(jugador, col, 8 + col * 18, base + 58));
        }
    }

    /** Un hueco que puede estar bloqueado. */
    public static class HuecoMochila extends Slot {

        private final boolean abierto;

        public HuecoMochila(Inventory inv, int indice, int x, int y, boolean abierto) {
            super(inv, indice, x, y);
            this.abierto = abierto;
        }

        public boolean abierto() {
            return abierto;
        }

        /** ⚠ No entra nada. Es el candado de verdad. */
        @Override
        public boolean canInsert(ItemStack pila) {
            return abierto;
        }

        /**
         * ⚠⚠ SÍ SE PUEDE SACAR DE UN HUECO BLOQUEADO, y es deliberado.
         *
         * <p>Si alguien baja de rango —o si algún día se reducen las filas— sus
         * objetos quedarían <b>encerrados para siempre</b>. Poder sacarlos y no
         * meterlos es lo que hace que bajar de rango sea reversible en vez de
         * una confiscación.
         */
        @Override
        public boolean canTakeItems(PlayerEntity jugador) {
            return true;
        }

        /** El candado también esconde el hueco: no se dibuja como normal. */
        @Override
        public boolean isEnabled() {
            return true;
        }
    }

    /**
     * Mayúsculas-clic.
     *
     * <h2>⚠⚠ AQUÍ SE ROMPE EL CANDADO SI NO SE MIRA</h2>
     *
     * {@code insertItem} recorre un rango de huecos y mete donde puede — y
     * <b>respeta {@code canInsert}</b>, así que el candado aguanta. Lo que hay
     * que acotar es el RANGO: mandarlo a los 63 huecos y confiar en que los
     * bloqueados lo rechacen funciona, pero deja el objeto en la mano si no
     * cabe. Se le manda solo a lo abierto.
     */
    @Override
    public ItemStack quickMove(PlayerEntity jugador, int indice) {
        Slot slot = slots.get(indice);
        if (slot == null || !slot.hasStack()) {
            return ItemStack.EMPTY;
        }
        ItemStack original = slot.getStack();
        ItemStack copia = original.copy();
        int abiertos = filas * Mochila.COLUMNAS;
        int total = Mochila.HUECOS;

        if (indice < total) {
            // De la mochila al inventario.
            if (!insertItem(original, total, total + 36, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Del inventario a la mochila, SOLO a las filas abiertas.
            if (!insertItem(original, 0, abiertos, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (original.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        return copia;
    }

    @Override
    public boolean canUse(PlayerEntity jugador) {
        return mochila.canPlayerUse(jugador);
    }

    /**
     * ⚠⚠ AQUI SE GUARDA. Es el unico sitio que se entera de que la pantalla se
     * cerro, venga de la tecla ESC, de cambiar de dimension o de que el
     * servidor lo cierre. Guardar desde la pantalla del cliente no valdria: un
     * cliente modificado simplemente no lo mandaria.
     */
    @Override
    public void onClosed(PlayerEntity jugador) {
        super.onClosed(jugador);
        mochila.onClose(jugador);
        if (jugador instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
            Abiertas.guardarYOlvidar(sp);
        }
    }

    public Inventory mochila() {
        return mochila;
    }

    public int filas() {
        return filas;
    }
}
