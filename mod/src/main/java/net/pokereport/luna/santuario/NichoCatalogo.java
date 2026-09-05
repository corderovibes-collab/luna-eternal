package net.pokereport.luna.santuario;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.BlockPos;
import net.pokereport.luna.LunaEternal;

/**
 * Donde estan los nichos del Santuario, leido de
 * {@code config/lunaeternal/santuario.json}.
 *
 * <h2>⚠⚠ LAS COORDENADAS VIVEN AQUI, NO EN LA BASE, y esa separacion es la regla</h2>
 *
 * La tabla {@code santuario} guarda SOLO la reclamacion (quien, hasta cuando,
 * que memorial). Donde esta cada nicho en el mundo lo dice este fichero. Asi un
 * constructor puede mover un nicho dos bloques y la economia no se entera, y
 * una reclamacion sobrevive a que se redecore Monumentos. Separados, ningun
 * cambio de uno rompe al otro.
 *
 * <h2>⚠⚠ SI EL FICHERO NO EXISTE, EL CATALOGO ESTA VACIO -- y no es un error</h2>
 *
 * Los nichos no estan construidos todavia (los hace el equipo de construccion).
 * Un servidor sin fichero arranca con el Santuario «aun no abierto», y la
 * pantalla lo dice. Es distinto de un fichero MAL ESCRITO: ese si revienta el
 * arranque, porque una coordenada mal puesta significa una zona protegida que
 * no es la construida -- y eso no da error, da un hueco que alguien descubre
 * rompiendo el memorial de otro.
 */
public final class NichoCatalogo {

    /** Un nicho, tal y como lo declara la config. */
    public record Nicho(String id, String nombre, BlockPos min, BlockPos max,
                        BlockPos proyector) {

        /** ¿Este bloque cae dentro del nicho? */
        public boolean contiene(BlockPos p) {
            return p.getX() >= min.getX() && p.getX() <= max.getX()
                    && p.getY() >= min.getY() && p.getY() <= max.getY()
                    && p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
        }
    }

    private final List<Nicho> nichos;

    /** De paquete: fuera de aqui los catalogos nacen de {@link #load()}. */
    NichoCatalogo(List<Nicho> nichos) {
        this.nichos = List.copyOf(nichos);
    }

    public List<Nicho> todos() {
        return nichos;
    }

    /** {@code false} mientras no haya ningun nicho declarado. */
    public boolean hay() {
        return !nichos.isEmpty();
    }

    /** El nicho con ese id, o {@code null}. */
    public Nicho de(String id) {
        for (Nicho n : nichos) {
            if (n.id().equals(id)) {
                return n;
            }
        }
        return null;
    }

    /** El nicho que contiene ese bloque, o {@code null}. */
    public Nicho en(BlockPos pos) {
        for (Nicho n : nichos) {
            if (n.contiene(pos)) {
                return n;
            }
        }
        return null;
    }

    /** Donde vive la config, para que los comandos de staff puedan regenerarla. */
    public static Path ruta() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("lunaeternal/santuario.json");
    }

    // ----------------------------------------------------------------- cargar

    public static NichoCatalogo load() {
        Path file = ruta();
        if (!Files.exists(file)) {
            LunaEternal.LOG.info("Santuario: no hay config de nichos, el santuario "
                    + "aun no esta abierto ({})", file);
            return new NichoCatalogo(List.of());
        }
        List<Nicho> leidos;
        try {
            String texto = Files.readString(file, StandardCharsets.UTF_8);
            leidos = parsear(texto);
        } catch (IOException | IllegalStateException e) {
            throw new IllegalStateException(
                    "Santuario: la config de nichos no se puede leer (" + file
                    + "): " + e.getMessage(), e);
        }
        validar(leidos);
        LunaEternal.LOG.info("Santuario: {} nichos en la config", leidos.size());
        return new NichoCatalogo(leidos);
    }

    /** Del JSON a la lista, sin validar todavia. */
    static List<Nicho> parsear(String texto) {
        var salida = new ArrayList<Nicho>();
        JsonObject root;
        try {
            root = JsonParser.parseString(texto).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IllegalStateException("no es JSON: " + e.getMessage(), e);
        }
        JsonElement arr = root.get("nichos");
        if (arr == null || !arr.isJsonArray()) {
            throw new IllegalStateException("falta la lista \"nichos\"");
        }
        for (JsonElement e : arr.getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject();
            String id = o.has("id") ? o.get("id").getAsString() : "";
            String nombre = o.has("nombre") ? o.get("nombre").getAsString() : "";
            int[] mn = tres(o, "min");
            int[] mx = tres(o, "max");
            int[] pr = tres(o, "proyector");
            salida.add(new Nicho(id, nombre,
                    new BlockPos(mn[0], mn[1], mn[2]),
                    new BlockPos(mx[0], mx[1], mx[2]),
                    new BlockPos(pr[0], pr[1], pr[2])));
        }
        return salida;
    }

    private static int[] tres(JsonObject o, String clave) {
        JsonElement e = o.get(clave);
        if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() != 3) {
            throw new IllegalStateException(
                    "el nicho \"" + o.get("id") + "\" no tiene " + clave
                    + " como [x, y, z]");
        }
        var a = e.getAsJsonArray();
        return new int[] {a.get(0).getAsInt(), a.get(1).getAsInt(),
                a.get(2).getAsInt()};
    }

    /**
     * Las reglas que no pueden fallar en silencio.
     *
     * <p>⚠⚠ LAS TRES SON «NO DARIA NINGUN ERROR»:
     * <ul>
     *   <li>dos nichos con el mismo id: la base guardaria un solo estado y el
     *       segundo nicho del mundo lo compartiria -- alquilar uno cobraria dos
     *       sitios, o el memorial saldria en el de al lado;</li>
     *   <li>dos nichos solapados: el bloque compartido estaria protegido por
     *       dos duenos, y a quien rompiera ahi se le diria que es de otro con
     *       razon a medias;</li>
     *   <li>un proyector fuera de su caja: se protegeria un 3x3 que no es el
     *       que tiene el proyector, y el memorial se abriria desde un bloque
     *       que no es el que se ve.</li>
     * </ul>
     */
    public static void validar(List<Nicho> lista) {        Set<String> ids = new HashSet<>();
        for (Nicho n : lista) {
            if (!n.id().matches("[a-z0-9_-]{1,32}")) {
                throw new IllegalStateException(
                        "id de nicho invalido: \"" + n.id() + "\"");
            }
            if (n.nombre().isBlank() || n.nombre().length() > 40
                    || n.nombre().indexOf('§') >= 0) {
                throw new IllegalStateException(
                        "nombre de nicho invalido: \"" + n.nombre() + "\"");
            }
            if (!ids.add(n.id())) {
                throw new IllegalStateException(
                        "id de nicho repetido: \"" + n.id() + "\"");
            }
            if (n.min().getX() > n.max().getX()
                    || n.min().getY() > n.max().getY()
                    || n.min().getZ() > n.max().getZ()) {
                throw new IllegalStateException(
                        "el nicho \"" + n.id() + "\" tiene min mayor que max");
            }
            if (!n.contiene(n.proyector())) {
                throw new IllegalStateException(
                        "el proyector del nicho \"" + n.id()
                        + "\" cae fuera de su caja");
            }
        }
        for (int i = 0; i < lista.size(); i++) {
            for (int j = i + 1; j < lista.size(); j++) {
                if (seTocan(lista.get(i), lista.get(j))) {
                    throw new IllegalStateException("los nichos \""
                            + lista.get(i).id() + "\" y \"" + lista.get(j).id()
                            + "\" se solapan");
                }
            }
        }
    }

    private static boolean seTocan(Nicho a, Nicho b) {
        return a.max().getX() >= b.min().getX() && b.max().getX() >= a.min().getX()
                && a.max().getY() >= b.min().getY() && b.max().getY() >= a.min().getY()
                && a.max().getZ() >= b.min().getZ() && b.max().getZ() >= a.min().getZ();
    }
}
