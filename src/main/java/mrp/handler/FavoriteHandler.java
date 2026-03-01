package mrp.handler;

import com.sun.net.httpserver.HttpExchange;
import mrp.db.DatabaseManager;
import mrp.util.JsonUtil;

import java.io.IOException;
import java.sql.*;

// favoriten hinzufügen und entfernen
public class FavoriteHandler extends BaseHandler {

    @Override
    public boolean canHandle(String path, String method) {
        return path.matches(".*/media/\\d+/favorite");
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!requireAuth(ex)) return;

        String[] parts = getPathParts(ex);
        int mediaId = Integer.parseInt(parts[3]);
        String method = ex.getRequestMethod();

        if (method.equals("POST"))   addFavorite(ex, mediaId);
        else                         removeFavorite(ex, mediaId);
    }

    // favorit hinzufügen
    private void addFavorite(HttpExchange ex, int mediaId) throws IOException {
        try {
            int userId = MediaHandler.getUserId(getLoggedInUser(ex));

            try (Connection c = DatabaseManager.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO favorites(user_id,media_id) VALUES(?,?) ON CONFLICT DO NOTHING")) {
                ps.setInt(1, userId);
                ps.setInt(2, mediaId);
                ps.executeUpdate();
            }

            sendResponse(ex, 200, JsonUtil.message("favorit hinzugefügt"));

        } catch (Exception e) {
            sendResponse(ex, 500, JsonUtil.error(e.getMessage()));
        }
    }

    // favorit entfernen
    private void removeFavorite(HttpExchange ex, int mediaId) throws IOException {
        try {
            int userId = MediaHandler.getUserId(getLoggedInUser(ex));

            try (Connection c = DatabaseManager.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM favorites WHERE user_id=? AND media_id=?")) {
                ps.setInt(1, userId);
                ps.setInt(2, mediaId);
                ps.executeUpdate();
            }

            sendResponse(ex, 204, "");

        } catch (Exception e) {
            sendResponse(ex, 500, JsonUtil.error(e.getMessage()));
        }
    }
}
