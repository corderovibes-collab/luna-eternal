package net.pokereport.luna;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuración del mod, leída de {@code config/lunaeternal.properties}.
 *
 * <p>Las credenciales NUNCA se compilan en el jar ni entran al repositorio
 * (CLAUDE.md §9). Si el fichero no existe se genera una plantilla y el
 * arranque falla de forma explícita, en vez de arrancar a medias.
 */
public final class LunaConfig {

    public final String  jdbcUrl;
    public final String  user;
    public final String  password;
    public final int     poolSize;
    public final boolean failFast;

    /** Nombres visibles de las monedas (D-018). */
    public final String  nameePokedollar;
    public final String  nameMark;
    public final String  namePremium;

    /**
     * Clave de invitación de constructor. Vacía = el comando no existe.
     *
     * <p>Existe por un problema muy concreto: para construir hace falta OP de
     * nivel 2 —lo exigen Axiom y WorldEdit—, y darlo requiere saber el nombre
     * de cada persona <b>de antemano</b>. Cuando el equipo entra mientras el
     * dueño no está, eso bloquea a todo el mundo.
     *
     * <p>Con esto, el dueño reparte una clave por privado y cada constructor se
     * da de alta él mismo. <b>Nunca da nivel 4</b>: un constructor no puede
     * apagar el servidor ni quitarle el OP a nadie.
     */
    public final String  builderKey;

    private LunaConfig(Properties p) {
        String host = req(p, "db.host");
        String port = p.getProperty("db.port", "3306");
        String name = req(p, "db.name");

        this.jdbcUrl  = "jdbc:mariadb://" + host + ":" + port + "/" + name
                      + "?useUnicode=true&characterEncoding=utf8";
        this.user     = req(p, "db.user");
        this.password = req(p, "db.password");
        this.poolSize = Integer.parseInt(p.getProperty("db.pool.size", "6"));
        this.failFast = Boolean.parseBoolean(p.getProperty("db.failFast", "true"));

        this.nameePokedollar = p.getProperty("currency.pokedollar", "PokéDólares");
        this.nameMark        = p.getProperty("currency.mark", "Marcas");
        this.namePremium     = p.getProperty("currency.premium", "ReportCoins");
        this.builderKey      = p.getProperty("builder.key", "").trim();
    }

    private static String req(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException(
                "Falta '" + key + "' en config/lunaeternal.properties");
        }
        return v;
    }

    public static LunaConfig load() throws IOException {
        Path file = FabricLoader.getInstance()
                                .getConfigDir()
                                .resolve("lunaeternal.properties");

        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, TEMPLATE);
            throw new IllegalStateException(
                "Se ha creado config/lunaeternal.properties. "
              + "Rellena las credenciales de la base de datos y reinicia.");
        }

        Properties p = new Properties();
        try (var in = Files.newInputStream(file)) {
            p.load(in);
        }
        return new LunaConfig(p);
    }

    private static final String TEMPLATE = """
        # Luna Eternal — configuracion del servidor
        # Este fichero contiene credenciales: NO subir al repositorio.

        db.host=
        db.port=3306
        db.name=
        db.user=
        db.password=

        # Conexiones del pool. 6 sobra para un servidor de este tamano.
        db.pool.size=6

        # true  = si la base de datos no responde, el servidor NO arranca.
        # Es lo correcto: arrancar sin persistencia significa perder datos
        # de los jugadores en silencio. Ver data-model.md §7.
        db.failFast=true

        # Nombres visibles de las monedas. El identificador interno no cambia,
        # asi que renombrar NO requiere migracion de base de datos (D-018).
        # La moneda premium sigue sin nombre decidido: ReportCoins o LunaCoins.
        currency.pokedollar=PokeDolares
        currency.mark=Marcas
        currency.premium=ReportCoins
        """;
}
