package mrp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import mrp.db.DatabaseManager;
import mrp.util.JsonUtil;

import java.io.IOException;
import java.sql.*;

// bewertungen erstellen, bearbeiten, loeschen, liken, bestaetigen
public class RatingHandler extends BaseHandler {

    @Override
    public boolean canHandle(String path, String method) {
        return path.matches(".*/media/\\d+/rate") ||
               path.matches(".*/ratings/\\d+(/like|/confirm)?");
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!requireAuth(ex)) return;

        String path   = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        String[] parts = getPathParts(ex);

        if (path.matches(".*/media/\\d+/rate")) {
            // /api/media/{mediaId}/rate
            int mediaId = Integer.parseInt(parts[3]);
            createRating(ex, mediaId);

        } else if (path.matches(".*/ratings/\\d+/like")) {
            int ratingId = Integer.parseInt(parts[3]);
            likeRating(ex, ratingId);

        } else if (path.matches(".*/ratings/\\d+/confirm")) {
            int ratingId = Integer.parseInt(parts[3]);
            confirmRating(ex, ratingId);

        } else {
            // /api/ratings/{id}
            int ratingId = Integer.parseInt(parts[3]);
            if (method.equals("PUT")) updateRating(ex, ratingId);
            else deleteRating(ex, ratingId);
        }
    }

    // neue bewertung anlegen
    private void createRating(HttpExchange ex, int mediaId) throws IOException {
        try {
            String loggedIn = getLoggedInUser(ex);
            int userId = MediaHandler.getUserId(loggedIn);
            JsonNode body = JsonUtil.mapper.readTree(readBody(ex));

            int stars = body.get("stars").asInt();
            // stars validierung
            if (stars < 1 || stars > 5) {
                sendResponse(ex, 400, JsonUtil.error("stars muss zwischen 1 und 5 sein"));
                return;
            }

            String comment = body.has("comment") ? body.get("comment").asText() : null;

            try (Connection c = DatabaseManager.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO ratings(media_id,user_id,stars,comment) VALUES(?,?,?,?) " +
                     "ON CONFLICT(media_id,user_id) DO UPDATE SET stars=EXCLUDED.stars, comment=EXCLUDED.comment, comment_confirmed=FALSE")) {
                ps.setInt(1, mediaId);
                ps.setInt(2, userId);
                ps.setInt(3, stars);
                ps.setString(4, comment);
                ps.executeUpdate();
            }

            sendResponse(ex, 201, JsonUtil.message("bewertet"));

        } catch (Exception e) {
            sendResponse(ex, 500, JsonUtil.error(e.getMessage()));
        }
    }

    // rating liken (einmal pro user)
    private void likeRating(HttpExchange ex, int ratingId) throws IOException {
        try {
            String loggedIn = getLoggedInUser(ex);
            int userId = MediaHandler.getUserId(loggedIn);

            try (Connection c = DatabaseManager.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO rating_likes(rating_id,user_id) VALUES(?,?) ON CONFLICT DO NOTHING")) {
                ps.setInt(1, ratingId);
                ps.setInt(2, userId);
                ps.executeUpdate();
            }

            sendResponse(ex, 200, JsonUtil.message("geliked"));

        } catch (Exception e) {
            sendResponse(ex, 500, JsonUtil.error(e.getMessage()));
        }
    }

    // bewertung aktualisieren (nur ersteller)
    private void updateRating(HttpExchange ex, int ratingId) throws IOException {
        try {
            String loggedIn = getLoggedInUser(ex);
            int userId = MediaHandler.getUserId(loggedIn);

            if (!isRatingOwner(ratingId, userId)) {
                sendResponse(ex, 403, JsonUtil.error("nicht berechtigt"));
                return;
            }

            JsonNode body = JsonUtil.mapper.readTree(readBody(ex));
            int stars = body.get("stars").asInt();
            String comment = body.has("comment") ? body.get("comment").asText() : null;

            try (Connection c = DatabaseManager.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE ratings SET stars=?, comment=?, comment_confirmed=FALSE WHERE id=?")) {
                ps.setInt(1, stars);
                ps.setString(2, comment);
                ps.setInt(3, ratingId);
                ps.executeUpdate();
            }

            sendResponse(ex, 200, JsonUtil.message("aktualisiert"));

        } catch (Exception e) {
            sendResponse(ex, 500, JsonUtil.error(e.getMessage()));
        }
    }

    // bewertung loeschen
    private void deleteRating(HttpExchange ex, int ratingId) throws IOException {
        try {
            String loggedIn = getLoggedInUser(ex);
            int userId = MediaHandler.getUserId(loggedIn);

            if (!isRatingOwner(ratingId, userId)) {
                sendResponse(ex, 403, JsonUtil.error("nicht berechtigt"));
                return;
            }

            try (Connection c = DatabaseManager.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement("DELETE FROM ratings WHERE id=?")) {
                ps.setInt(1, ratingId);
                ps.executeUpdate();
            }

            sendResponse(ex, 204, "");

        } catch (Exception e) {
            sendResponse(ex, 500, JsonUtil.error(e.getMessage()));
        }
    }

    // kommentar sichtbar machen (moderation)
    private void confirmRating(HttpExchange ex, int ratingId) throws IOException {
        try {
            String loggedIn = getLoggedInUser(ex);
            int userId = MediaHandler.getUserId(loggedIn);

            if (!isRatingOwner(ratingId, userId)) {
                sendResponse(ex, 403, JsonUtil.error("nicht berechtigt"));
                return;
            }

            try (Connection c = DatabaseManager.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE ratings SET comment_confirmed=TRUE WHERE id=?")) {
                ps.setInt(1, ratingId);
                ps.executeUpdate();
            }

            sendResponse(ex, 200, JsonUtil.message("kommentar bestaetigt"));

        } catch (Exception e) {
            sendResponse(ex, 500, JsonUtil.error(e.getMessage()));
        }
    }

    // pruefen ob user besitzer des ratings ist
    private boolean isRatingOwner(int ratingId, int userId) throws Exception {
        try (Connection c = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT user_id FROM ratings WHERE id=?")) {
            ps.setInt(1, ratingId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt("user_id") == userId;
        }
    }
}
