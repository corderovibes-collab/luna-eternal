package net.pokereport.luna.buhonero;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class ReciboEntregaTest {
    @TempDir Path carpeta;
    @Test void reciboPersistenteYRepetible() throws Exception {
        var id=UUID.randomUUID();assertFalse(ReciboEntrega.existe(carpeta,id));
        ReciboEntrega.escribir(carpeta,id);assertTrue(ReciboEntrega.existe(carpeta,id));
        ReciboEntrega.escribir(carpeta,id);assertTrue(ReciboEntrega.existe(carpeta,id));
        assertFalse(ReciboEntrega.existe(carpeta,UUID.randomUUID()));
    }
    @Test void parcialOTemporalNoConfirmaEntrega() throws Exception {
        var id=UUID.randomUUID();
        Files.writeString(carpeta.resolve(id+".tmp"),id.toString());
        assertFalse(ReciboEntrega.existe(carpeta,id));
        Files.writeString(carpeta.resolve(id+".receipt"),"incompleto");
        assertFalse(ReciboEntrega.existe(carpeta,id));
    }
}
