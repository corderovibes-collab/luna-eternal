-- =====================================================================
-- V013 · CLANES
--
-- Peticion del usuario: crear un clan desde cero, unirse a uno que exista,
-- enviar invitaciones, "tipo MMORPG como Albion". Y que al escanear a un
-- jugador con la Pokedex se vea si tiene clan y cual.
--
-- ⚠ EL PROTOCOLO YA LO ESPERABA. D-038 dejo `clan` viajando en el paquete
--   `Ficha` con la cadena vacia, y lo dejo escrito: "tenerlos ya en el
--   protocolo hace que encenderlos sea rellenar tres lineas en vez de
--   tocar paquete, codec, cache y dibujado". Se cumplio.
-- =====================================================================

CREATE TABLE IF NOT EXISTS clan (
    clan_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,

    -- ⚠ EL NOMBRE Y LA ETIQUETA SON UNICOS, y los dos hacen falta. El nombre
    --   es lo que se lee; la etiqueta es lo que cabe delante de un nombre en
    --   el chat y en el tablist. Sin unicidad, dos clanes con la misma
    --   etiqueta serian indistinguibles justo donde mas se ven.
    --
    -- ⚠ Y SE GUARDA TAMBIEN EN MINUSCULAS. MySQL compara sin distinguir
    --   mayusculas segun el collation, y depender de eso es fragil: si el
    --   servidor se restaura con otro collation, "Luna" y "LUNA" pasarian a
    --   ser dos clanes. La columna normalizada lo hace explicito.
    name        VARCHAR(24)     NOT NULL,
    name_lower  VARCHAR(24)     NOT NULL,
    tag         VARCHAR(5)      NOT NULL,
    tag_lower   VARCHAR(5)      NOT NULL,

    -- El color de la etiqueta, como codigo de chat sin el §: 'a', 'c', '6'...
    color       CHAR(1)         NOT NULL DEFAULT 'b',
    description VARCHAR(140)    NOT NULL DEFAULT '',

    leader_id   BIGINT UNSIGNED NOT NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    -- ⚠ EL TESORO VIVE AQUI Y NO EN OTRA TABLA. `data-model.md` nombraba un
    --   `clan_treasury` aparte, y para UN saldo es una union mas por cada
    --   lectura sin ganar nada: no hay varias monedas de clan ni historial
    --   propio --el historial es `ledger_entry`, que ya existe--.
    --   Si algun dia el clan tiene tres monedas, entonces si.
    --
    -- ⚠ BIGINT, nunca FLOAT (R2). Es dinero.
    treasury    BIGINT          NOT NULL DEFAULT 0,

    UNIQUE KEY uk_clan_name (name_lower),
    UNIQUE KEY uk_clan_tag (tag_lower),
    CONSTRAINT fk_clan_leader FOREIGN KEY (leader_id)
        REFERENCES player(player_id) ON DELETE RESTRICT,
    CONSTRAINT ck_clan_treasury CHECK (treasury >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE IF NOT EXISTS clan_member (
    -- ⚠ LA CLAVE PRIMARIA ES EL JUGADOR, NO EL PAR (clan, jugador). Eso es lo
    --   que impide estar en DOS clanes a la vez, y lo impide la base en vez
    --   del codigo: una comprobacion previa se la puede saltar una carrera
    --   entre dos invitaciones aceptadas a la vez.
    player_id   BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    clan_id     BIGINT UNSIGNED NOT NULL,

    -- ⚠ ENUM, y el orden IMPORTA: MariaDB guarda el indice, no el texto.
    --   Reordenar estos tres convertiria a todos los lideres en miembros.
    --   Van de mas a menos permisos y asi se quedan.
    role        ENUM('LIDER','OFICIAL','MIEMBRO') NOT NULL DEFAULT 'MIEMBRO',
    joined_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    KEY ix_member_clan (clan_id),
    CONSTRAINT fk_member_clan FOREIGN KEY (clan_id)
        REFERENCES clan(clan_id) ON DELETE CASCADE,
    CONSTRAINT fk_member_player FOREIGN KEY (player_id)
        REFERENCES player(player_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE IF NOT EXISTS clan_invite (
    clan_id     BIGINT UNSIGNED NOT NULL,
    player_id   BIGINT UNSIGNED NOT NULL,
    invited_by  BIGINT UNSIGNED NOT NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    -- ⚠ CADUCAN, y no es un adorno. Sin caducidad, una invitacion de hace seis
    --   meses sigue siendo valida: alguien la acepta y entra en un clan que ya
    --   no le queria. Es el mismo problema que los listados del GTS, que ya
    --   costo dejar objetos perdidos para siempre.
    expires_at  DATETIME(3)     NOT NULL,

    PRIMARY KEY (clan_id, player_id),
    KEY ix_invite_player (player_id),
    CONSTRAINT fk_invite_clan FOREIGN KEY (clan_id)
        REFERENCES clan(clan_id) ON DELETE CASCADE,
    CONSTRAINT fk_invite_player FOREIGN KEY (player_id)
        REFERENCES player(player_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


INSERT INTO schema_version (version, description)
VALUES (13, 'clanes: clan, miembros e invitaciones')
ON DUPLICATE KEY UPDATE version = version;
