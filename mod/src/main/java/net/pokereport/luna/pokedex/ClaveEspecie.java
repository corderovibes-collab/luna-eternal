package net.pokereport.luna.pokedex;

/**
 * EL IDENTIFICADOR DE UNA ESPECIE, Y SOLO DE UN SITIO.
 *
 * <h2>⚠⚠⚠ EL NOMBRE VISIBLE NO ES UN IDENTIFICADOR, Y CASI SIEMPRE LO PARECE</h2>
 *
 * Durante meses estos tres sitios —las cazas, la Pokédex y la eclosión—
 * guardaban {@code species.getName().toLowerCase()}. Para 247 de las 251
 * especies de Kanto y Johto eso da exactamente el identificador bueno, así que
 * funcionaba. Para cuatro, no:
 *
 * <pre>
 *   Mr. Mime     -> "mr. mime"     un ESPACIO
 *   Farfetch'd   -> "farfetch’d"   un apostrofo TIPOGRAFICO (U+2019)
 *   Nidoran-F    -> "nidoran-f"    un guion
 *   Nidoran-M    -> "nidoran-m"
 * </pre>
 *
 * <h2>⚠⚠⚠ Y LOS DOS PRIMEROS FALLAN DISTINTO QUE LOS DOS SEGUNDOS</h2>
 *
 * <ul>
 *   <li>El espacio y el apostrofo <b>no son válidos</b> en la ruta de un
 *       {@code Identifier}, y {@code PokemonSpecies.getByName} construye uno
 *       —{@code cobblemonResource(name)}, leído del bytecode— así que
 *       <b>LANZA</b>. Eso tumbó el autotest entero en la comprobación 171 y se
 *       llevó por delante las 300 siguientes.</li>
 *   <li>El guion <b>sí es válido</b>, así que no lanza: {@code getByName}
 *       devuelve <b>null</b> y no lo dice nadie. Dos especies llevaban meses
 *       en la Pokédex de alguien apuntadas con un identificador que no
 *       resuelve, sin una sola línea en el log.</li>
 * </ul>
 *
 * <p>⚠⚠ El segundo es el peor de los dos, y es el que nadie habría encontrado.
 * Un fallo ruidoso se arregla el día que aparece; uno mudo se queda.
 *
 * <h2>Por qué se pregunta al registro y no se limpia la cadena</h2>
 *
 * Quitar lo que no sea letra o dígito acierta con estas cuatro —la pantalla del
 * GTS lo hacía así y por eso nunca falló—, pero es una <b>conjetura</b>: acierta
 * mientras el nombre visible y el identificador solo se diferencien en signos
 * de puntuación. {@code getResourceIdentifier()} <b>es</b> el identificador,
 * así que no puede equivocarse aunque Cobblemon renombre algo.
 */
public final class ClaveEspecie {

    private ClaveEspecie() {}

    /** El identificador canónico de una especie. Nunca el nombre visible. */
    public static String de(com.cobblemon.mod.common.pokemon.Species species) {
        if (species == null) return "";
        var id = species.getResourceIdentifier();
        return id == null ? "" : id.getPath();
    }

    /** Lo mismo, partiendo del Pokémon. */
    public static String de(com.cobblemon.mod.common.pokemon.Pokemon pokemon) {
        return pokemon == null ? "" : de(pokemon.getSpecies());
    }

    /**
     * Resuelve una especie SIN LANZAR, para lo que ya está guardado.
     *
     * <p>⚠⚠ Hace falta aunque los tres sitios de arriba estén arreglados: en la
     * base hay filas viejas con los identificadores malos, y una de ellas es lo
     * que abortó el autotest. Que un dato antiguo no pueda tumbar una
     * comprobación es justo lo que hace que las comprobaciones sirvan.
     */
    public static com.cobblemon.mod.common.pokemon.Species buscar(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            return com.cobblemon.mod.common.api.pokemon.PokemonSpecies.INSTANCE
                    .getByName(id);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Normaliza una clave vieja al identificador probable. Solo para migrar. */
    public static String normalizar(String id) {
        if (id == null) return "";
        var sb = new StringBuilder();
        for (char c : id.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }
}
