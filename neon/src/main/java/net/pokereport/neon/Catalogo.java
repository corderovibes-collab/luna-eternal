package net.pokereport.neon;

import net.minecraft.block.MapColor;

/**
 * Los 126 materiales de obra de la ciudadela.
 *
 * <p><b>GENERADO por {@code tools/gen_bloques.py} — no editar a mano.</b> Las
 * tablas vivas están en {@code tools/bloques/ciudad.py}, que además dibuja las
 * texturas y escribe los modelos. Tenerlas en dos sitios es como se registra un
 * bloque cuya textura no existe, que en la pantalla del jugador es el cubo
 * negro y morado.
 *
 * <p>Cada material se despliega en las formas de su familia, y no son las
 * mismas: el hormigón lleva muro porque un parapeto de azotea es de hormigón, y
 * el metal lleva pilar porque una viga es de metal.
 *
 * @param id      raíz del identificador: {@code <id>}, {@code <id>_losa}...
 * @param familia de dónde salen la dureza, el sonido y la capa de dibujado
 * @param mapa    color en el mapa y en la brújula de localizador
 * @param formas  sufijos que hay que registrar, en orden
 */
public record Catalogo(String id, Ciudad.Familia familia, MapColor mapa,
                       String[] formas) {

    public static final String[] HORMIGON = {"", "_losa", "_escalera", "_muro", "_valla"};
    public static final String[] METAL = {"", "_losa", "_escalera", "_pilar", "_valla"};
    public static final String[] REJILLA = {"", "_losa", "_panel"};
    public static final String[] VIDRIO = {"", "_panel"};
    public static final String[] PAVIMENTO = {"", "_losa", "_escalera"};

    public static final Catalogo[] MATERIALES = {
        new Catalogo("hormigon_pulido_blanco",       Ciudad.Familia.HORMIGON, MapColor.WHITE, HORMIGON),
        new Catalogo("hormigon_rayado_blanco",       Ciudad.Familia.HORMIGON, MapColor.WHITE, HORMIGON),
        new Catalogo("hormigon_panel_blanco",        Ciudad.Familia.HORMIGON, MapColor.WHITE, HORMIGON),
        new Catalogo("hormigon_pulido_gris_claro",   Ciudad.Familia.HORMIGON, MapColor.LIGHT_GRAY, HORMIGON),
        new Catalogo("hormigon_rayado_gris_claro",   Ciudad.Familia.HORMIGON, MapColor.LIGHT_GRAY, HORMIGON),
        new Catalogo("hormigon_panel_gris_claro",    Ciudad.Familia.HORMIGON, MapColor.LIGHT_GRAY, HORMIGON),
        new Catalogo("hormigon_pulido_gris",         Ciudad.Familia.HORMIGON, MapColor.GRAY, HORMIGON),
        new Catalogo("hormigon_rayado_gris",         Ciudad.Familia.HORMIGON, MapColor.GRAY, HORMIGON),
        new Catalogo("hormigon_panel_gris",          Ciudad.Familia.HORMIGON, MapColor.GRAY, HORMIGON),
        new Catalogo("hormigon_pulido_negro",        Ciudad.Familia.HORMIGON, MapColor.BLACK, HORMIGON),
        new Catalogo("hormigon_rayado_negro",        Ciudad.Familia.HORMIGON, MapColor.BLACK, HORMIGON),
        new Catalogo("hormigon_panel_negro",         Ciudad.Familia.HORMIGON, MapColor.BLACK, HORMIGON),
        new Catalogo("hormigon_pulido_marron",       Ciudad.Familia.HORMIGON, MapColor.BROWN, HORMIGON),
        new Catalogo("hormigon_rayado_marron",       Ciudad.Familia.HORMIGON, MapColor.BROWN, HORMIGON),
        new Catalogo("hormigon_panel_marron",        Ciudad.Familia.HORMIGON, MapColor.BROWN, HORMIGON),
        new Catalogo("hormigon_pulido_rojo",         Ciudad.Familia.HORMIGON, MapColor.RED, HORMIGON),
        new Catalogo("hormigon_rayado_rojo",         Ciudad.Familia.HORMIGON, MapColor.RED, HORMIGON),
        new Catalogo("hormigon_panel_rojo",          Ciudad.Familia.HORMIGON, MapColor.RED, HORMIGON),
        new Catalogo("hormigon_pulido_naranja",      Ciudad.Familia.HORMIGON, MapColor.ORANGE, HORMIGON),
        new Catalogo("hormigon_rayado_naranja",      Ciudad.Familia.HORMIGON, MapColor.ORANGE, HORMIGON),
        new Catalogo("hormigon_panel_naranja",       Ciudad.Familia.HORMIGON, MapColor.ORANGE, HORMIGON),
        new Catalogo("hormigon_pulido_amarillo",     Ciudad.Familia.HORMIGON, MapColor.YELLOW, HORMIGON),
        new Catalogo("hormigon_rayado_amarillo",     Ciudad.Familia.HORMIGON, MapColor.YELLOW, HORMIGON),
        new Catalogo("hormigon_panel_amarillo",      Ciudad.Familia.HORMIGON, MapColor.YELLOW, HORMIGON),
        new Catalogo("hormigon_pulido_lima",         Ciudad.Familia.HORMIGON, MapColor.LIME, HORMIGON),
        new Catalogo("hormigon_rayado_lima",         Ciudad.Familia.HORMIGON, MapColor.LIME, HORMIGON),
        new Catalogo("hormigon_panel_lima",          Ciudad.Familia.HORMIGON, MapColor.LIME, HORMIGON),
        new Catalogo("hormigon_pulido_verde",        Ciudad.Familia.HORMIGON, MapColor.GREEN, HORMIGON),
        new Catalogo("hormigon_rayado_verde",        Ciudad.Familia.HORMIGON, MapColor.GREEN, HORMIGON),
        new Catalogo("hormigon_panel_verde",         Ciudad.Familia.HORMIGON, MapColor.GREEN, HORMIGON),
        new Catalogo("hormigon_pulido_cian",         Ciudad.Familia.HORMIGON, MapColor.CYAN, HORMIGON),
        new Catalogo("hormigon_rayado_cian",         Ciudad.Familia.HORMIGON, MapColor.CYAN, HORMIGON),
        new Catalogo("hormigon_panel_cian",          Ciudad.Familia.HORMIGON, MapColor.CYAN, HORMIGON),
        new Catalogo("hormigon_pulido_azul_claro",   Ciudad.Familia.HORMIGON, MapColor.LIGHT_BLUE, HORMIGON),
        new Catalogo("hormigon_rayado_azul_claro",   Ciudad.Familia.HORMIGON, MapColor.LIGHT_BLUE, HORMIGON),
        new Catalogo("hormigon_panel_azul_claro",    Ciudad.Familia.HORMIGON, MapColor.LIGHT_BLUE, HORMIGON),
        new Catalogo("hormigon_pulido_azul",         Ciudad.Familia.HORMIGON, MapColor.BLUE, HORMIGON),
        new Catalogo("hormigon_rayado_azul",         Ciudad.Familia.HORMIGON, MapColor.BLUE, HORMIGON),
        new Catalogo("hormigon_panel_azul",          Ciudad.Familia.HORMIGON, MapColor.BLUE, HORMIGON),
        new Catalogo("hormigon_pulido_morado",       Ciudad.Familia.HORMIGON, MapColor.PURPLE, HORMIGON),
        new Catalogo("hormigon_rayado_morado",       Ciudad.Familia.HORMIGON, MapColor.PURPLE, HORMIGON),
        new Catalogo("hormigon_panel_morado",        Ciudad.Familia.HORMIGON, MapColor.PURPLE, HORMIGON),
        new Catalogo("hormigon_pulido_magenta",      Ciudad.Familia.HORMIGON, MapColor.MAGENTA, HORMIGON),
        new Catalogo("hormigon_rayado_magenta",      Ciudad.Familia.HORMIGON, MapColor.MAGENTA, HORMIGON),
        new Catalogo("hormigon_panel_magenta",       Ciudad.Familia.HORMIGON, MapColor.MAGENTA, HORMIGON),
        new Catalogo("hormigon_pulido_rosa",         Ciudad.Familia.HORMIGON, MapColor.PINK, HORMIGON),
        new Catalogo("hormigon_rayado_rosa",         Ciudad.Familia.HORMIGON, MapColor.PINK, HORMIGON),
        new Catalogo("hormigon_panel_rosa",          Ciudad.Familia.HORMIGON, MapColor.PINK, HORMIGON),
        new Catalogo("metal_acero_liso",             Ciudad.Familia.METAL, MapColor.IRON_GRAY, METAL),
        new Catalogo("metal_acero_cepillado",        Ciudad.Familia.METAL, MapColor.IRON_GRAY, METAL),
        new Catalogo("metal_acero_estriado",         Ciudad.Familia.METAL, MapColor.IRON_GRAY, METAL),
        new Catalogo("metal_acero_remachado",        Ciudad.Familia.METAL, MapColor.IRON_GRAY, METAL),
        new Catalogo("metal_acero_oscuro_liso",      Ciudad.Familia.METAL, MapColor.DEEPSLATE_GRAY, METAL),
        new Catalogo("metal_acero_oscuro_cepillado", Ciudad.Familia.METAL, MapColor.DEEPSLATE_GRAY, METAL),
        new Catalogo("metal_acero_oscuro_estriado",  Ciudad.Familia.METAL, MapColor.DEEPSLATE_GRAY, METAL),
        new Catalogo("metal_acero_oscuro_remachado", Ciudad.Familia.METAL, MapColor.DEEPSLATE_GRAY, METAL),
        new Catalogo("metal_aluminio_liso",          Ciudad.Familia.METAL, MapColor.WHITE_GRAY, METAL),
        new Catalogo("metal_aluminio_cepillado",     Ciudad.Familia.METAL, MapColor.WHITE_GRAY, METAL),
        new Catalogo("metal_aluminio_estriado",      Ciudad.Familia.METAL, MapColor.WHITE_GRAY, METAL),
        new Catalogo("metal_aluminio_remachado",     Ciudad.Familia.METAL, MapColor.WHITE_GRAY, METAL),
        new Catalogo("metal_titanio_liso",           Ciudad.Familia.METAL, MapColor.STONE_GRAY, METAL),
        new Catalogo("metal_titanio_cepillado",      Ciudad.Familia.METAL, MapColor.STONE_GRAY, METAL),
        new Catalogo("metal_titanio_estriado",       Ciudad.Familia.METAL, MapColor.STONE_GRAY, METAL),
        new Catalogo("metal_titanio_remachado",      Ciudad.Familia.METAL, MapColor.STONE_GRAY, METAL),
        new Catalogo("metal_cromo_liso",             Ciudad.Familia.METAL, MapColor.WHITE_GRAY, METAL),
        new Catalogo("metal_cromo_cepillado",        Ciudad.Familia.METAL, MapColor.WHITE_GRAY, METAL),
        new Catalogo("metal_cromo_estriado",         Ciudad.Familia.METAL, MapColor.WHITE_GRAY, METAL),
        new Catalogo("metal_cromo_remachado",        Ciudad.Familia.METAL, MapColor.WHITE_GRAY, METAL),
        new Catalogo("metal_cobre_liso",             Ciudad.Familia.METAL, MapColor.ORANGE, METAL),
        new Catalogo("metal_cobre_cepillado",        Ciudad.Familia.METAL, MapColor.ORANGE, METAL),
        new Catalogo("metal_cobre_estriado",         Ciudad.Familia.METAL, MapColor.ORANGE, METAL),
        new Catalogo("metal_cobre_remachado",        Ciudad.Familia.METAL, MapColor.ORANGE, METAL),
        new Catalogo("metal_laton_liso",             Ciudad.Familia.METAL, MapColor.GOLD, METAL),
        new Catalogo("metal_laton_cepillado",        Ciudad.Familia.METAL, MapColor.GOLD, METAL),
        new Catalogo("metal_laton_estriado",         Ciudad.Familia.METAL, MapColor.GOLD, METAL),
        new Catalogo("metal_laton_remachado",        Ciudad.Familia.METAL, MapColor.GOLD, METAL),
        new Catalogo("metal_grafito_liso",           Ciudad.Familia.METAL, MapColor.BLACK, METAL),
        new Catalogo("metal_grafito_cepillado",      Ciudad.Familia.METAL, MapColor.BLACK, METAL),
        new Catalogo("metal_grafito_estriado",       Ciudad.Familia.METAL, MapColor.BLACK, METAL),
        new Catalogo("metal_grafito_remachado",      Ciudad.Familia.METAL, MapColor.BLACK, METAL),
        new Catalogo("rejilla_acero",                Ciudad.Familia.REJILLA, MapColor.IRON_GRAY, REJILLA),
        new Catalogo("rejilla_acero_oscuro",         Ciudad.Familia.REJILLA, MapColor.DEEPSLATE_GRAY, REJILLA),
        new Catalogo("rejilla_aluminio",             Ciudad.Familia.REJILLA, MapColor.WHITE_GRAY, REJILLA),
        new Catalogo("rejilla_titanio",              Ciudad.Familia.REJILLA, MapColor.STONE_GRAY, REJILLA),
        new Catalogo("rejilla_cromo",                Ciudad.Familia.REJILLA, MapColor.WHITE_GRAY, REJILLA),
        new Catalogo("rejilla_cobre",                Ciudad.Familia.REJILLA, MapColor.ORANGE, REJILLA),
        new Catalogo("rejilla_laton",                Ciudad.Familia.REJILLA, MapColor.GOLD, REJILLA),
        new Catalogo("rejilla_grafito",              Ciudad.Familia.REJILLA, MapColor.BLACK, REJILLA),
        new Catalogo("vidrio_claro_blanco",          Ciudad.Familia.VIDRIO, MapColor.WHITE, VIDRIO),
        new Catalogo("vidrio_polarizado_blanco",     Ciudad.Familia.VIDRIO, MapColor.WHITE, VIDRIO),
        new Catalogo("vidrio_claro_gris_claro",      Ciudad.Familia.VIDRIO, MapColor.LIGHT_GRAY, VIDRIO),
        new Catalogo("vidrio_polarizado_gris_claro", Ciudad.Familia.VIDRIO, MapColor.LIGHT_GRAY, VIDRIO),
        new Catalogo("vidrio_claro_gris",            Ciudad.Familia.VIDRIO, MapColor.GRAY, VIDRIO),
        new Catalogo("vidrio_polarizado_gris",       Ciudad.Familia.VIDRIO, MapColor.GRAY, VIDRIO),
        new Catalogo("vidrio_claro_negro",           Ciudad.Familia.VIDRIO, MapColor.BLACK, VIDRIO),
        new Catalogo("vidrio_polarizado_negro",      Ciudad.Familia.VIDRIO, MapColor.BLACK, VIDRIO),
        new Catalogo("vidrio_claro_marron",          Ciudad.Familia.VIDRIO, MapColor.BROWN, VIDRIO),
        new Catalogo("vidrio_polarizado_marron",     Ciudad.Familia.VIDRIO, MapColor.BROWN, VIDRIO),
        new Catalogo("vidrio_claro_rojo",            Ciudad.Familia.VIDRIO, MapColor.RED, VIDRIO),
        new Catalogo("vidrio_polarizado_rojo",       Ciudad.Familia.VIDRIO, MapColor.RED, VIDRIO),
        new Catalogo("vidrio_claro_naranja",         Ciudad.Familia.VIDRIO, MapColor.ORANGE, VIDRIO),
        new Catalogo("vidrio_polarizado_naranja",    Ciudad.Familia.VIDRIO, MapColor.ORANGE, VIDRIO),
        new Catalogo("vidrio_claro_amarillo",        Ciudad.Familia.VIDRIO, MapColor.YELLOW, VIDRIO),
        new Catalogo("vidrio_polarizado_amarillo",   Ciudad.Familia.VIDRIO, MapColor.YELLOW, VIDRIO),
        new Catalogo("vidrio_claro_lima",            Ciudad.Familia.VIDRIO, MapColor.LIME, VIDRIO),
        new Catalogo("vidrio_polarizado_lima",       Ciudad.Familia.VIDRIO, MapColor.LIME, VIDRIO),
        new Catalogo("vidrio_claro_verde",           Ciudad.Familia.VIDRIO, MapColor.GREEN, VIDRIO),
        new Catalogo("vidrio_polarizado_verde",      Ciudad.Familia.VIDRIO, MapColor.GREEN, VIDRIO),
        new Catalogo("vidrio_claro_cian",            Ciudad.Familia.VIDRIO, MapColor.CYAN, VIDRIO),
        new Catalogo("vidrio_polarizado_cian",       Ciudad.Familia.VIDRIO, MapColor.CYAN, VIDRIO),
        new Catalogo("vidrio_claro_azul_claro",      Ciudad.Familia.VIDRIO, MapColor.LIGHT_BLUE, VIDRIO),
        new Catalogo("vidrio_polarizado_azul_claro", Ciudad.Familia.VIDRIO, MapColor.LIGHT_BLUE, VIDRIO),
        new Catalogo("vidrio_claro_azul",            Ciudad.Familia.VIDRIO, MapColor.BLUE, VIDRIO),
        new Catalogo("vidrio_polarizado_azul",       Ciudad.Familia.VIDRIO, MapColor.BLUE, VIDRIO),
        new Catalogo("vidrio_claro_morado",          Ciudad.Familia.VIDRIO, MapColor.PURPLE, VIDRIO),
        new Catalogo("vidrio_polarizado_morado",     Ciudad.Familia.VIDRIO, MapColor.PURPLE, VIDRIO),
        new Catalogo("vidrio_claro_magenta",         Ciudad.Familia.VIDRIO, MapColor.MAGENTA, VIDRIO),
        new Catalogo("vidrio_polarizado_magenta",    Ciudad.Familia.VIDRIO, MapColor.MAGENTA, VIDRIO),
        new Catalogo("vidrio_claro_rosa",            Ciudad.Familia.VIDRIO, MapColor.PINK, VIDRIO),
        new Catalogo("vidrio_polarizado_rosa",       Ciudad.Familia.VIDRIO, MapColor.PINK, VIDRIO),
        new Catalogo("pavimento_asfalto",            Ciudad.Familia.PAVIMENTO, MapColor.BLACK, PAVIMENTO),
        new Catalogo("pavimento_asfalto_claro",      Ciudad.Familia.PAVIMENTO, MapColor.DEEPSLATE_GRAY, PAVIMENTO),
        new Catalogo("pavimento_terrazo_claro",      Ciudad.Familia.PAVIMENTO, MapColor.WHITE_GRAY, PAVIMENTO),
        new Catalogo("pavimento_terrazo_oscuro",     Ciudad.Familia.PAVIMENTO, MapColor.STONE_GRAY, PAVIMENTO),
        new Catalogo("pavimento_losa_grande",        Ciudad.Familia.PAVIMENTO, MapColor.STONE_GRAY, PAVIMENTO),
        new Catalogo("pavimento_adoquin_fino",       Ciudad.Familia.PAVIMENTO, MapColor.STONE_GRAY, PAVIMENTO),
    };
}
