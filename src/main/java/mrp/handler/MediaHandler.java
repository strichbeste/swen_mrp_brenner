package mrp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import mrp.db.DatabaseManager;
import mrp.util.JsonUtil;

import java.io.IOException;
import java.sql.*;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

// media crud + suche + filter
public class MediaHandler extends BaseHandler {

    @Override
    public boolean canHandle(String path, String method) {
        // /api/media oder /api/media/{id}
        return path.matches(".*/media(/\\d+)?$");
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!requireAuth(ex)) return;

        String[] parts = getPathParts(ex);
        String method  = ex.getRequestMethod();

        // /api/media/{id} -> parts[3] ist die id
        boolean hasId = parts.length >= 4 && parts[3] != null && !parts[3].isEmpty();

        if (!hasId) {
            if (method.equals("GET")) listMedia(ex);
            else createMedia(ex);
        } else {
            int mediaId = Integer.parseInt(parts[3]);
            switch (method) {
                case "GET"    -> getMediaById(ex, mediaId);
                case "PUT"    -> updateMedia(ex, mediaId);
                case "DELETE" -> deleteMedia(ex, mediaId);
            }
        }
    }

    // liste mit filter/sortierung -- java streams fuer filterung
    private void listMedia(HttpExchange ex) throws IOException {
        try (Connection c = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT m.*, COALESCE(AVG(r.stars),0) as avg_score " +
                 "FROM media m LEFT JOIN ratings r ON m.id=r.media_id GROUP BY m.id")) {

            ResultSet rs = ps.executeQuery();
            Map<String, String> params = parseQuery(ex);

            // alle eintraege laden, dann mit streams filtern
            ArrayNode all = JsonUtil.mapper.createArrayNode();
            while (rs.next()) {
                ObjectNode n = UserHandler.buildMediaNode(rs);
                n.put("averageScore", rs.getDouble("avg_score"));
                all.add(n);
            }

            // stream filter anwenden
            var stream = StreamSupport.stream(all.spliterator(), false);

            if (params.containsKey("title"))
                stream = stream.filter(n -> n.get("title").asText().toLowerCase()
                    .contains(params.get("title").toLowerCase()));

            if (params.containsKey("genre"))
                stream = stream.filter(n -> n.get("genres").asText().toLowerCase()
                    .contains(params.get("genre").toLowerCase()));

            if (params.containsKey("mediaType"))
                stream = stream.filter(n -> n.get("mediaType").asText()
                    .equalsIgnoreCase(params.get("mediaType")));

            if (params.containsKey("releaseYear"))
                stream = stream.filter(n -> n.get("releaseYear").asInt()
                    == Integer.parseInt(params.get("releaseYear")));

            if (params.containsKey("ageRestriction"))
                stream = stream.filter(n -> n.get("ageRestriction").asInt()
                    <= Integer.parseInt(params.get("ageRestriction")));

            if (params.containsKey("rating"))
                stream = stream.filter(n -> n.get("averageScore").asDouble()
                    >= Double.parseDouble(params.get("rating")));

            // sortierung
            String sortBy = params.getOrDefault("sortBy", "title");
            stream = switch (sortBy) {
                case "year"  -> stream.sorted((a, b) -> b.get("releaseYear").asInt() - a.get("releaseYear").asInt());
                case "score" -> stream.sorted((a, b) -> Double.compare(b.get("averageScore").asDouble(), a.get("averageScore").asDouble()));
                default      -> stream.sorted((a, b) -> a.get("title").asText().compareTo(b.get("title").asText()));
            };

            var result = stream.collect(Collectors.toList());
            sendResponse(ex, 200, JsonUtil.mapper.writeValueAsString(result));

        } catch (Exception e) {
            sendResponse(ex, 500, JsonUtil.error(e.getMessage()));
        }
    }

    // media anlegen
    private void createMedia(HttpExchange ex) throws IOException {
        try {
            String loggedIn = getLoggedInUser(ex);
            JsonNode body = JsonUtil.mapper.readTree(readBody(ex));

            // genre liste zu kommasepariertem string
            String genres = "";
            if (body.has("genres")) {
                genres = StreamSupport.stream(body.get("genres").spliterator(), false)
                    .map(JsonNode::asText)
                    .collect(Collectors.joining(","));
            }

            int creatorId = getUserId(loggedIn);

            try (Connection c = DatabaseManager.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO media(title,description,media_type,release_year,genres,age_restriction,creator_id) " +
                     "VALUES(?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, body.get("title").asText());
                ps.setString(2, body.has("description") ? body.get("description").asText() : "");
                ps.setString(3, body.has("mediaType") ? body.get("mediaType").asText() : "");
                ps.setInt(4, body.has("releaseYear") ? body.get("releaseYear").asInt() : 0);
                ps.setString(5, genres);
                ps.setInt(6, body.has("ageRestriction") ? body.get("ageRestriction").asInt() : 0);
                ps.setInt(7, creatorId);
                ps.executeUpdate();

                ResultSet keys = ps.getGeneratedKeys();
                keys.next();
                int newId = keys.getInt(1);
                sendResponse(ex, 201, "{\"id\":" + newId + "}");
            }

        } catch (Exception e) {
            sendResponse(ex, 500, JsonUtil.error(e.getMessage()));
        }
    }

    // media per id holen
    private void getMediaById(HttpExchange ex, int mediaId) throws IOException {
        try (Connection c = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT m.*, COALESCE(AVG(r.stars),0) as avg_score " +
                 "FROM media m LEFT JOIN ratings r ON m.id=r.media_id WHERE m.id=? GROUP BY m.id")) {

            ps.setInt(1, mediaId);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) { sendResponse(ex, 404, JsonUtil.error("nicht gefunden")); return; }

            ObjectNode n = UserHandler.buildMediaNode(rs);
            n.put("averageScore", rs.getDouble("avg_score"));
            sendResponse(ex, 200, JsonUtil.mapper.writeValueAsString(n));

        } catch (Exception e) {
            sendResponse(ex, 500, JsonUtil.error(e.getMessage()));
        }
    }

    // media aktualisieren (nur creator)
    private void updateMedia(HttpExchange ex, int mediaId) throws IOException {
        try {
            String loggedIn = getLoggedInUser(ex);
            int userId = getUserId(loggedIn);

            // ownership pruefen
            if (!isOwner(mediaId, userId)) {
                sendResponse(ex, 403, JsonUtil.error("nicht berechtigt"));
                return;
            }

            JsonNode body = JsonUtil.mapper.readTree(readBody(ex));
            String genres = "";
            if (body.has("genres")) {
                genres = StreamSupport.stream(body.get("genres").spliterator(), false)
                    .map(JsonNode::asText).collect(Collectors.joining(","));
            }

            try (Connection c = DatabaseManager.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE media SET title=?,description=?,media_type=?,release_year=?,genres=?,age_restriction=? WHERE id=?")) {
                ps.setString(1, body.get("title").asText());
                ps.setString(2, body.has("description") ? body.get("description").asText() : "");
                ps.setString(3, body.has("mediaType") ? body.get("mediaType").asText() : "");
                ps.setInt(4, body.has("releaseYear") ? body.get("releaseYear").asInt() : 0);
                ps.setString(5, genres);
                ps.setInt(6, body.has("ageRestriction") ? body.get("ageRestriction").asInt() : 0);
                ps.setInt(7, mediaId);
                ps.executeUpdate();
            }

            sendResponse(ex, 200, JsonUtil.message("aktualisiert"));

        } catch (Exception e) {
            sendResponse(ex, 500, JsonUtil.error(e.getMessage()));
        }
    }

    // media loeschen (nur creator)
    private void deleteMedia(HttpExchange ex, int mediaId) throws IOException {
        try {
            String loggedIn = getLoggedInUser(ex);
            int userId = getUserId(loggedIn);

            if (!isOwner(mediaId, userId)) {
                sendResponse(ex, 403, JsonUtil.error("nicht berechtigt"));
                return;
            }

            try (Connection c = DatabaseManager.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement("DELETE FROM media WHERE id=?")) {
                ps.setInt(1, mediaId);
                ps.executeUpdate();
            }

            sendResponse(ex, 204, "");

        } catch (Exception e) {
            sendResponse(ex, 500, JsonUtil.error(e.getMessage()));
        }
    }

    // hilfsmethode: user id aus username holen
    public static int getUserId(String username) throws Exception {
        try (Connection c = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id FROM users WHERE username=?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("id");
            throw new Exception("user nicht gefunden");
        }
    }

    // hilfsmethode: pruefen ob user creator von media ist
    private boolean isOwner(int mediaId, int userId) throws Exception {
        try (Connection c = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT creator_id FROM media WHERE id=?")) {
            ps.setInt(1, mediaId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt("creator_id") == userId;
        }
    }
}
