package net.pokereport.luna.buhonero;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;

/** Segundo recibo junto al mundo: permite recuperar SQL aunque el Pokémon ya se intercambió. */
public final class ReciboEntrega {
    private ReciboEntrega() {}
    public static boolean existe(Path carpeta,UUID uuid) throws IOException {
        Path archivo=carpeta.resolve(uuid+".receipt");
        return Files.isRegularFile(archivo) && Files.readString(archivo).equals(uuid.toString());
    }
    public static void escribir(Path carpeta,UUID uuid) throws IOException {
        Files.createDirectories(carpeta);
        Path temporal=carpeta.resolve(uuid+".tmp"),destino=carpeta.resolve(uuid+".receipt");
        try(var canal=FileChannel.open(temporal,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
            ByteBuffer datos=StandardCharsets.UTF_8.encode(uuid.toString());
            while(datos.hasRemaining()) canal.write(datos);
            canal.force(true);
        }
        try {Files.move(temporal,destino,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(temporal,destino,StandardCopyOption.REPLACE_EXISTING);}
    }
}
