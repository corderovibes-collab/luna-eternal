package net.pokereport.luna.quest;

import net.pokereport.luna.progression.Path;

import java.time.LocalDate;

/**
 * Definición de una misión, cargada del JSON.
 *
 * <p>Las definiciones viven en el catálogo y el progreso en la base. Así el
 * catálogo se puede reescribir entero sin migrar nada.
 */
public record Quest(String id, String chain, int order, String requires,
                    String name, String description,
                    Objective objective, Rewards rewards, Period period) {

    /** Qué hay que hacer. */
    public record Objective(Type type, long amount) {
        public enum Type {
            /** Elegir el inicial. */
            STARTER,
            /** Capturar N Pokémon. */
            CATCH,
            /** Tener N especies registradas. */
            POKEDEX,
            /** Registrar N especies nuevas (cuenta desde que empieza). */
            POKEDEX_NEW,
            /** Comprar N veces en la tienda. */
            SHOP_BUY,
            /** Vender N veces en el GTS. */
            GTS_SELL,
            /** Alcanzar nivel N en cualquier vía. */
            PATH_LEVEL,
            /** Ganar N PokéDólares en total. */
            EARN,

            // ---- los que llegaron con los OFICIOS ------------------------
            //
            // ⚠ AÑADIR UN TIPO NO NECESITA MIGRACION, y conviene saber por que:
            //   `quest_progress` guarda `quest_id VARCHAR(48)` y el progreso, y
            //   NO el tipo de objetivo. El tipo vive solo en el catalogo JSON.
            //   (Al reves de `player_path.path`, que SI es un ENUM de SQL y
            //   costo la migracion V012 por tres nombres.)

            /** Picar N bloques que cuenten para Minero. */
            MINE,
            /** Recoger la caña N veces. */
            FISH,
            /** Cosechar N bayas, bellotas o cultivos. */
            HARVEST,
            /** Eclosionar N huevos. */
            HATCH,
            /** Ganar N combates. */
            BATTLE_WIN;

            /**
             * ¿El progreso es acumulativo o se consulta al vuelo?
             *
             * <p>Distinguirlo importa: {@code CATCH} suma cada vez, pero
             * {@code POKEDEX} es una foto del total. Tratar el segundo como
             * acumulativo daría números imposibles al recontar.
             */
            public boolean acumulativo() {
                return this == CATCH || this == SHOP_BUY || this == GTS_SELL
                    || this == EARN || this == POKEDEX_NEW
                    // Los oficios cuentan HECHOS, uno a uno, igual que CATCH:
                    // cada bloque picado suma. No son una foto de un total.
                    || this == MINE || this == FISH || this == HARVEST
                    || this == HATCH || this == BATTLE_WIN;
            }
        }
    }

    /** Qué se lleva. */
    public record Rewards(long pokedollar, long mark, Path path, long xp) {}

    /** Cada cuánto se repite. */
    public enum Period {
        ONCE, DAY, WEEK;

        /**
         * Clave del periodo actual. Es lo que permite que una diaria vuelva a
         * estar disponible mañana sin borrar el historial de ayer.
         */
        public String key() {
            LocalDate hoy = LocalDate.now();
            return switch (this) {
                case ONCE -> "";
                case DAY  -> hoy.toString();
                case WEEK -> hoy.getYear() + "-W"
                    + hoy.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
            };
        }
    }

    public boolean repeatable() {
        return period != Period.ONCE;
    }
}
