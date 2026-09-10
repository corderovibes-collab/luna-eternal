package net.pokereport.luna.proteccion;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.shop.Modulos;
import net.pokereport.luna.world.LunaDimensions;

/**
 * LAS PARCELAS SOLO SE PONEN EN EL MUNDO HOGAR.
 *
 * <p>Decision del usuario (2026-09-09). Y encaja con lo que cada dimension es:
 *
 * <ul>
 *   <li><b>Hogar</b> — permanente, es donde se construye. Aqui una parcela
 *       protege lo que has hecho, que es para lo que existe.</li>
 *   <li><b>Salvaje</b> — <b>se reinicia</b> (D-016). Una parcela ahi protege
 *       algo que va a desaparecer solo: el jugador paga por nada y encima se
 *       queda sin el modulo cuando el mundo rote.</li>
 *   <li><b>Ciudadela</b> — la construimos nosotros a mano. Una parcela de un
 *       jugador en mitad de la plaza <b>bloquea a los constructores</b>, y el
 *       unico que podria quitarla es su dueño.</li>
 *   <li><b>Lobby, gimnasios, torre</b> — no son sitios donde se viva.</li>
 * </ul>
 *
 * <h2>&#9888;&#9888;&#9888; SE CORTA AL COLOCAR, NO SE DESHACE DESPUES</h2>
 *
 * Lo otro que se puede hacer --dejar que la parcela se cree y borrarla luego--
 * seria peor de tres maneras: el jugador ya habria <b>gastado el modulo</b>
 * (cuesta hasta 150.000 de Plata), habria que devolverselo a mano, y entre que
 * se crea y se borra <b>la proteccion existe</b>. Cortar antes no deja ningun
 * hueco.
 *
 * <h2>&#9888;&#9888; UN MODULO NO ES «UN OBJETO CON UN ID»</h2>
 *
 * Es un {@code minecraft:player_head} con la etiqueta
 * {@code protectionstones:stone_type} dentro -- ClaimBlocks es <b>solo de
 * servidor</b>, asi que no puede registrar objetos propios: el cliente no los
 * conoceria y no entraria nadie. Comparar por id de objeto cazaria <b>todas</b>
 * las cabezas, incluida la de cualquiera. Se mira la etiqueta.
 */
public final class SoloEnElHogar {

    private SoloEnElHogar() {
    }

    /**
     * Engancha el corte. Se llama una vez al arrancar.
     *
     * <p>&#9888; {@code UseBlockCallback} se dispara en <b>los dos lados</b> y
     * con <b>las dos manos</b>. El corte del cliente evita que se vea el bloque
     * puesto durante un fotograma antes de que el servidor lo quite; el
     * antirrebote evita que el mensaje salga dos veces, que es el mismo fallo
     * que se acaba de arreglar en Oak.
     */
    public static void enganchar() {
        UseBlockCallback.EVENT.register((jugador, mundo, mano, golpe) -> {
            if (!Modulos.esModulo(jugador.getStackInHand(mano))) {
                return ActionResult.PASS;
            }
            if (LunaDimensions.HOGAR.equals(mundo.getRegistryKey())) {
                return ActionResult.PASS;
            }
            if (jugador instanceof ServerPlayerEntity sp
                    && !net.pokereport.luna.ui.Toque.repetido(sp.getUuid(), "parcela")) {
                sp.sendMessage(Text.literal(
                        "§c§lAQUI NO §r§7— las parcelas solo se pueden poner en el "
                        + "§fMundo Hogar§7."), false);
                sp.sendMessage(Text.literal(
                        "§8El Salvaje se reinicia y la ciudadela la construimos "
                        + "entre todos."), false);
            }
            // ⚠ FAIL y no SUCCESS: SUCCESS diria «ya lo he gestionado yo» y
            //   ademas consumiria el uso. FAIL corta y deja el modulo en la
            //   mano, que es lo que tiene que pasar -- el jugador no ha perdido
            //   nada por equivocarse de sitio.
            return ActionResult.FAIL;
        });
        LunaEternal.LOG.info("Protecciones: las parcelas solo se ponen en el Hogar");
    }
}
