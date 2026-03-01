package mrp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import mrp.db.DatabaseManager;
import mrp.util.JsonUtil;

import java.io.IOException;
import java.sql.*;

// user profil, bewertungshistorie, favoriten
public class UserHandler extends BaseHandler {

    @Override
    public boolean canHandle(String path, String method) {
        return path.matches(".*/users/\\d+/(profile|ratings|favorites)");
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!requireAuth(ex)) return;

        String[] parts = getPathParts(ex);
        // parts: ["","api","users","1","profile"]
        int userId = Integer.parseInt(parts[3]);
        String action = parts[4];
        String method = ex.getRequestMethod();

        switch (action) {
            case "profile" -> {
                if (method.equals("GET")) getProfile(ex, userId);
                else updateProfile(ex, userId);
            }
            case "ratings"   -> getRatingHistory(ex, userId);
            case "favorites" -> getFavorites(ex, userId);
        }
    }

    // profil mit statistiken laden
    private void getProfile(HttpExchange ex, int userId) throws IOException {
        try (Connection c = DatabaseManager.getInstance().getConnection()) {
            PreparedStatement ps = c.prepareStatement(
                "SELECT username, email, favorite_genre FROM users WHERE id=?");
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) { sendResponse(ex, 404, JsonUtil.error("user nicht gefunden")); return; }

            ObjectNode node = JsonUtil.mapper.createObjectNode();
            node.put("id", userId);
            node.put("username", rs.getString("username"));
            node.put("email", rs.getString("email") != null ? rs.getString("email") : "");
            node.put("favoriteGenre", rs.getString("favorite_genre") != null ? rs.getString("favorite_genre") : "");

            // statistiken: anzahl ratings, durchschnitt
            PreparedStatement ps2 = c.prepareStatement(
                "SELECT COUNT(*) as cnt, AVG(stars) as avg FROM ratings WHERE user_id=?");
            ps2.setInt(1, userId);
            ResultSet rs2 = ps2.executeQuery();
            if (rs2.next()) {
                node.put("totalRatings", rs2.getInt("cnt"));
                node.put("averageScore", rs2.getObject("avg") != null ? rs2.getDouble("avg") : 0.0);
            }

            sendResponse(ex, 200, JsonUtil.mapper.writeValueAsString(node));

        } catch (Exception e) {
            sendResponse(ex, 500, JsonUtil.error(e.getMessage()));
        }
    }

    // profil aktualisieren (email, lieblingsgenre)
    private void updateProfile(HttpExchange ex, int userId) throws IOException {
        try {
            JsonNode body = JsonUtil.mapper.readTree(readBody(ex));
            String email = body.has("email") ? body.get("email").asText() : null;
            String genre = body.has("favoriteGenre") ? body.get("favoriteGenre").asText() : null;

            try (Connection c = DatabaseManager.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE users SET email=COALESCE(?,email), favorite_genre=COALESCE(?,favorite_genre) WHERE id=?")) {
                ps.setString(1, email);
                ps.setString(2, genre);
                ps.setInt(3, userId);
                ps.executeUpdate();
            }

            sendResponse(ex, 200, JsonUtil.message("profil aktualisiert"));

        } catch (Exception e) {
            sendResponse(ex, 500, JsonUtil.error(e.getMessage()));
        }
    }

    // bewertungshistorie des users holen
    private void getRatingHistory(HttpExchange ex, int userId) throws IOException {
        try (Connection c = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT r.id, r.media_id, r.stars, r.comment, r.comment_confirmed, r.created_at, m.title " +
                 "FROM ratings r JOIN media m ON r.media_id=m.id WHERE r.user_id=? ORDER BY r.created_at DESC")) {

            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            ArrayNode arr = JsonUtil.mapper.createArrayNode();
            while (rs.next()) {
                ObjectNode n = JsonUtil.mapper.createObjectNode();
                n.put("id", rs.getInt("id"));
                n.put("mediaId", rs.getInt("media_id"));
                n.put("mediaTitle", rs.getString("title"));
                n.put("stars", rs.getInt("stars"));
                // kommentar nur zeigen wenn bestaetigt
                n.put("comment", rs.getBoolean("comment_confirmed") ? rs.getString("comment") : "");
                n.put("createdAt", rs.getTimestamp("created_at").toString());
                arr.add(n);
            }

            sendResponse(ex, 200, JsonUtil.mapper.writeValueAsString(arr));

        } catch (Exception e) {
            sendResponse(ex, 500, JsonUtil.error(e.getMessage()));
        }
    }

    // favoriten des users holen
    private void getFavorites(HttpExchange ex, int userId) throws IOException {
        try (Connection c = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT m.* FROM favorites f JOIN media m ON f.media_id=m.id WHERE f.user_id=?")) {

            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            ArrayNode arr = JsonUtil.mapper.createArrayNode();
            while (rs.next()) {
                arr.add(buildMediaNode(rs));
            }

            sendResponse(ex, 200, JsonUtil.mapper.writeValueAsString(arr));

        } catch (Exception e) {
            sendResponse(ex, 500, JsonUtil.error(e.getMessage()));
        }
    }

    // hilfsmethode: resultset -> media json node
    public static ObjectNode buildMediaNode(ResultSet rs) throws SQLException {
        ObjectNode n = JsonUtil.mapper.createObjectNode();
        n.put("id", rs.getInt("id"));
        n.put("title", rs.getString("title"));
        n.put("description", rs.getString("description"));
        n.put("mediaType", rs.getString("media_type"));
        n.put("releaseYear", rs.getInt("release_year"));
        n.put("genres", rs.getString("genres"));
        n.put("ageRestriction", rs.getInt("age_restriction"));
        return n;
    }
}
