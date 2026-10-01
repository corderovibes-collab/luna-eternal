-- Apariencia segura y extensible del tag de clan.
ALTER TABLE clan
    ADD COLUMN tag_color_start CHAR(7) NULL AFTER color,
    ADD COLUMN tag_color_end CHAR(7) NULL AFTER tag_color_start,
    ADD COLUMN tag_bold BOOLEAN NOT NULL DEFAULT FALSE AFTER tag_color_end,
    ADD COLUMN tag_italic BOOLEAN NOT NULL DEFAULT FALSE AFTER tag_bold;

UPDATE clan
SET tag_color_start = CASE color
        WHEN 'a' THEN '#55FF55' WHEN 'b' THEN '#55FFFF'
        WHEN 'c' THEN '#FF5555' WHEN 'd' THEN '#FF55FF'
        WHEN 'e' THEN '#FFFF55' WHEN '6' THEN '#FFAA00'
        ELSE '#AAAAAA' END,
    tag_color_end = CASE color
        WHEN 'a' THEN '#55FF55' WHEN 'b' THEN '#55FFFF'
        WHEN 'c' THEN '#FF5555' WHEN 'd' THEN '#FF55FF'
        WHEN 'e' THEN '#FFFF55' WHEN '6' THEN '#FFAA00'
        ELSE '#AAAAAA' END
WHERE tag_color_start IS NULL OR tag_color_end IS NULL;

ALTER TABLE clan
    MODIFY tag_color_start CHAR(7) NOT NULL DEFAULT '#55FFFF',
    MODIFY tag_color_end CHAR(7) NOT NULL DEFAULT '#55FFFF';

INSERT INTO schema_version (version, description)
VALUES (52, 'apariencia rgb y estilos seguros del tag de clan')
ON DUPLICATE KEY UPDATE version = version;
