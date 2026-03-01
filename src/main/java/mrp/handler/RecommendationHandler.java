package mrp.handler;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import mrp.db.DatabaseManager;
import mrp.util.JsonUtil;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.stream.*;

// empfehlungen basierend auf genre oder inhalt (strategy-pattern)
public class RecommendationHandler extends BaseHandler {

    @Override
    public boolean canHandle(String path, String method) {
        return path.matches(".*/users/\\d+/recommendations");
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!requireAuth(ex)) return;

        String[] parts = getPathParts(ex);
        int userId = Integer.parseInt(parts[3]);
        Map<String, String> params = parseQuery(ex);
        String type = params.getOrDefault("type", "genre");

        try {
            ArrayNode result = type.equals("content") ?
                getContentRecs(userId) :
                getGenreRecs(userId);

            sendResponse(ex, 200, JsonUtil.mapper.writeValueAsString(result));

        } catch (Exception e) {
            sendResponse(ex, 500, JsonUtil.error(e.getMessage()));
        }
    }

    // genre-basierte empfehlungen: genres aus hoch bewerteten einträgen holen
    private ArrayNode getGenreRecs(int userId) throws Exception {
        Set<String> likedGenres = new HashSet<>();
        try (Connection c = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT m.genres FROM ratings r JOIN media m ON r.media_id=m.id " +
                 "WHERE r.user_id=? AND r.stars >= 4")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                if (rs.getString("genres") == null) continue;
                Arrays.stream(rs.getString("genres").split(","))
                    .map(String::trim).forEach(likedGenres::add);
            }
        }
        return findMediaByGenres(userId, likedGenres);
    }

    // inhaltsbasierte empfehlungen: media_type + age_restriction matching
    private ArrayNode getContentRecs(int userId) throws Exception {
        try (Connection c = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT m.media_type, m.age_restriction, m.genres FROM ratings r " +
                 "JOIN media m ON r.media_id=m.id WHERE r.user_id=? AND r.stars >= 4 LIMIT 5")) {

            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            Set<String> genres = new HashSet<>();
            Set<String> types  = new HashSet<>();
            int maxAge = 0;

            while (rs.next()) {
                if (rs.getString("genres") != null)
                    Arrays.stream(rs.getString("genres").split(",")).map(String::trim).forEach(genres::add);
                if (rs.getString("media_type") != null)
                    types.add(rs.getString("media_type"));
                maxAge = Math.max(maxAge, rs.getInt("age_restriction"));
            }

            return findMediaByContent(userId, types, genres, maxAge);
        }
    }

    // hilfsmethode: media nach genres filtern
    private ArrayNode findMediaByGenres(int userId, Set<String> genres) throws Exception {
        ArrayNode arr = JsonUtil.mapper.createArrayNode();
        if (genres.isEmpty()) return arr;

        try (Connection c = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT * FROM media WHERE id NOT IN " +
                 "(SELECT media_id FROM ratings WHERE user_id=?) LIMIT 10")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String g = rs.getString("genres");
                if (g == null) continue;
                boolean matches = genres.stream().anyMatch(genre -> g.toLowerCase().contains(genre.toLowerCase()));
                if (matches) arr.add(UserHandler.buildMediaNode(rs));
            }
        }
        return arr;
    }

    // hilfsmethode: media nach inhalt filtern
    private ArrayNode findMediaByContent(int userId, Set<String> types, Set<String> genres, int maxAge) throws Exception {
        ArrayNode arr = JsonUtil.mapper.createArrayNode();

        try (Connection c = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT * FROM media WHERE id NOT IN " +
                 "(SELECT media_id FROM ratings WHERE user_id=?) LIMIT 20")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String type = rs.getString("media_type");
                String g    = rs.getString("genres");
                int age     = rs.getInt("age_restriction");

                boolean typeMatch  = types.stream().anyMatch(t -> t.equalsIgnoreCase(type));
                boolean genreMatch = g != null && genres.stream().anyMatch(genre -> g.toLowerCase().contains(genre.toLowerCase()));

                if ((typeMatch || genreMatch) && age <= maxAge + 2) {
                    arr.add(UserHandler.buildMediaNode(rs));
                }
            }
        }
        return arr;
    }
}
