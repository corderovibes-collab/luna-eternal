package net.pokereport.luna.quest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.db.Database;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.progression.Path;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Misiones: catálogo, progreso y recompensas.
 *
 * <p>La separación entre <b>completar</b> y <b>cobrar</b> es deliberada. Si
 * fueran el mismo momento, una caída del servidor durante la entrega perdería
 * la recompensa o la daría dos veces — el mismo problema que resolvió la
 * entrega diferida del GTS.
 */
public final class QuestService {

    private final Database db;
    private final Map<String, Quest> catalogo = new LinkedHashMap<>();

    public QuestService(Database db) {
        this.db = db;
        cargarCatalogo();
    }

    public List<Quest> catalogo() { return List.copyOf(catalogo.values()); }
    public Quest byId(String id) { return catalogo.get(id); }

    /** Estado de una misión para un jugador. */
    public record State(Quest quest, long progress, boolean completed, boolean claimed) {
        public boolean claimable() { return completed && !claimed; }
        public double fraction() {
            long meta = quest.objective().amount();
            return meta <= 0 ? 1 : Math.min(1.0, (double) progress / meta);
        }
    }

    // ------------------------------------------------------------ catálogo

    private void cargarCatalogo() {
        try (InputStream in = getClass().getResourceAsStream("/data/lunaeternal/quests.json")) {
            if (in == null) throw new IllegalStateException("Falta quests.json");
            JsonObject root = JsonParser.parseReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();

            for (var el : root.getAsJsonArray("quests")) {
                JsonObject q = el.getAsJsonObject();
                JsonObject obj = q.getAsJsonObject("objective");
                JsonObject rw = q.getAsJsonObject("rewards");

                Quest quest = new Quest(
                    q.get("id").getAsString(),
                    q.has("chain") ? q.get("chain").getAsString() : "otros",
                    q.has("order") ? q.get("order").getAsInt() : 0,
                    q.has("requires") ? q.get("requires").getAsString() : null,
                    q.get("name").getAsString(),
                    q.get("description").getAsString(),
                    new Quest.Objective(
                        Quest.Objective.Type.valueOf(obj.get("type").getAsString()),
                        obj.get("amount").getAsLong()),
                    new Quest.Rewards(
                        rw.has("pokedollar") ? rw.get("pokedollar").getAsLong() : 0,
                        rw.has("mark") ? rw.get("mark").getAsLong() : 0,
                        rw.has("path") ? Path.valueOf(rw.get("path").getAsString()) : null,
                        rw.has("xp") ? rw.get("xp").getAsLong() : 0),
                    q.has("period") ? Quest.Period.valueOf(q.get("period").getAsString())
                                    : Quest.Period.ONCE);

                catalogo.put(quest.id(), quest);
            }
            LunaEternal.LOG.info("Misiones: {} cargadas", catalogo.size());

        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar el catálogo de misiones", e);
        }
    }

    // ------------------------------------------------------------ progreso

    /**
     * Suma progreso a todas las misiones activas de un tipo.
     *
     * <p>Se llama desde los eventos del juego. Es deliberadamente barato: si
     * no hay ninguna misión de ese tipo activa, no toca la base.
     */
    public void advance(long playerId, Quest.Objective.Type tipo, long cantidad) {
        if (cantidad <= 0) return;
        try {
            for (Quest q : catalogo.values()) {
                if (q.objective().type() != tipo) continue;
                if (!q.objective().type().acumulativo()) continue;
                if (!disponible(playerId, q)) continue;
                sumar(playerId, q, cantidad);
            }
        } catch (Exception e) {
            LunaEternal.LOG.error("No se pudo avanzar misiones de tipo {}", tipo, e);
        }
    }

    /** Fija el progreso de una misión no acumulativa (una foto del total). */
    public void setProgress(long playerId, Quest.Objective.Type tipo, long valor) {
        try {
            for (Quest q : catalogo.values()) {
                if (q.objective().type() != tipo) continue;
                if (q.objective().type().acumulativo()) continue;
                if (!disponible(playerId, q)) continue;
                fijar(playerId, q, valor);
            }
        } catch (Exception e) {
            LunaEternal.LOG.error("No se pudo fijar progreso de tipo {}", tipo, e);
        }
    }

    private boolean disponible(long playerId, Quest q) throws SQLException {
        // Ya cobrada en este periodo: no acumula más.
        State s = state(playerId, q);
        if (s.claimed()) return false;
        // Con requisito previo sin cobrar: todavía no cuenta.
        if (q.requires() != null) {
            Quest previa = catalogo.get(q.requires());
            if (previa != null && !state(playerId, previa).claimed()) return false;
        }
        return true;
    }

    private void sumar(long playerId, Quest q, long cantidad) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                INSERT INTO quest_progress (player_id, quest_id, period_key, progress)
                VALUES (?,?,?,?)
                ON DUPLICATE KEY UPDATE progress = progress + VALUES(progress)
                """)) {
            ps.setLong(1, playerId);
            ps.setString(2, q.id());
            ps.setString(3, q.period().key());
            ps.setLong(4, cantidad);
            ps.executeUpdate();
        }
        marcarSiCompleta(playerId, q);
    }

    private void fijar(long playerId, Quest q, long valor) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                INSERT INTO quest_progress (player_id, quest_id, period_key, progress)
                VALUES (?,?,?,?)
                ON DUPLICATE KEY UPDATE progress = GREATEST(progress, VALUES(progress))
                """)) {
            ps.setLong(1, playerId);
            ps.setString(2, q.id());
            ps.setString(3, q.period().key());
            ps.setLong(4, valor);
            ps.executeUpdate();
        }
        marcarSiCompleta(playerId, q);
    }

    private void marcarSiCompleta(long playerId, Quest q) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                UPDATE quest_progress SET completed_at = CURRENT_TIMESTAMP(3)
                WHERE player_id = ? AND quest_id = ? AND period_key = ?
                  AND completed_at IS NULL AND progress >= ?
                """)) {
            ps.setLong(1, playerId);
            ps.setString(2, q.id());
            ps.setString(3, q.period().key());
            ps.setLong(4, q.objective().amount());
            ps.executeUpdate();
        }
    }

    public State state(long playerId, Quest q) throws SQLException {
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT progress, completed_at, claimed_at FROM quest_progress "
               + "WHERE player_id = ? AND quest_id = ? AND period_key = ?")) {
            ps.setLong(1, playerId);
            ps.setString(2, q.id());
            ps.setString(3, q.period().key());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new State(q, 0, false, false);
                return new State(q, rs.getLong(1),
                    rs.getTimestamp(2) != null, rs.getTimestamp(3) != null);
            }
        }
    }

    public List<State> allStates(long playerId) throws SQLException {
        List<State> out = new ArrayList<>();
        for (Quest q : catalogo.values()) out.add(state(playerId, q));
        return out;
    }

    // ------------------------------------------------------------ cobrar

    /**
     * Cobra la recompensa.
     *
     * <p>La marca de cobro se pone <b>en la misma sentencia condicional</b> que
     * la comprueba: {@code claimed_at IS NULL} en el WHERE. Si dos clics llegan
     * a la vez, el segundo actualiza cero filas y no cobra.
     */
    public boolean claim(long playerId, Quest q) throws SQLException {
        int filas;
        try (Connection c = db.connection();
             PreparedStatement ps = c.prepareStatement("""
                UPDATE quest_progress SET claimed_at = CURRENT_TIMESTAMP(3)
                WHERE player_id = ? AND quest_id = ? AND period_key = ?
                  AND completed_at IS NOT NULL AND claimed_at IS NULL
                """)) {
            ps.setLong(1, playerId);
            ps.setString(2, q.id());
            ps.setString(3, q.period().key());
            filas = ps.executeUpdate();
        }
        if (filas == 0) return false;

        var r = q.rewards();
        String key = UUID.randomUUID().toString();
        try {
            if (r.pokedollar() > 0) {
                LunaEternal.economy().credit(playerId, Currency.POKEDOLLAR,
                    r.pokedollar(), "quest_reward", key + ":pd");
            }
            if (r.mark() > 0) {
                LunaEternal.economy().credit(playerId, Currency.MARK,
                    r.mark(), "quest_reward", key + ":mk");
            }
            if (r.path() != null && r.xp() > 0) {
                LunaEternal.progression().grant(playerId, r.path(), r.xp());
            }
        } catch (Exception e) {
            // La marca ya está puesta. Se deja constancia para revisarlo a
            // mano: el libro de asientos permite reconstruir qué falto.
            LunaEternal.LOG.error(
                "RECOMPENSA FALLIDA quest={} player_id={}", q.id(), playerId, e);
        }
        return true;
    }
}
