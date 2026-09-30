package net.pokereport.luna.client;

import net.pokereport.luna.net.Red;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Contrato del seguro cliente contra paquetes de lobby atrasados. */
class EstadoClientePuertaTest {
    @AfterEach
    void limpiar() {
        EstadoCliente.olvidar();
    }

    @Test
    void marcaDeLobbyAtrasadaSeLiberaAlVerCiudadela() {
        EstadoCliente.guardar(new Red.EstadoPuerta(true));
        assertTrue(EstadoCliente.enLobby());

        assertTrue(EstadoCliente.reconciliarPuertaConMundo(false));
        assertFalse(EstadoCliente.enLobby());
    }

    @Test
    void clienteNuncaInventaBloqueoAlVerLobby() {
        EstadoCliente.guardar(new Red.EstadoPuerta(false));

        assertFalse(EstadoCliente.reconciliarPuertaConMundo(true));
        assertFalse(EstadoCliente.enLobby());
    }
}
