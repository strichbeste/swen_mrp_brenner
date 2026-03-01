package mrp.handler;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import mrp.db.DatabaseManager;
import mrp.util.JsonUtil;

import java.io.IOException;
import java.sql.*;

// bestenliste: user mit meisten bewertungen
public class LeaderboardHandler extends BaseHandler {

    @Override
    public boolean canHandle(String path, String method) {
        return path.endsWith("/leaderboard");
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        // leaderboard ist öffentlich — kein auth check
        try (Connection c = DatabaseManager.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT u.username, COUNT(r.id) as rating_count " +
                 "FROM users u LEFT JOIN ratings r ON u.id=r.user_id " +
                 "GROUP BY u.id, u.username ORDER BY rating_count DESC LIMIT 20")) {

            ResultSet rs = ps.executeQuery();
            ArrayNode arr = JsonUtil.mapper.createArrayNode();

            while (rs.next()) {
                ObjectNode n = JsonUtil.mapper.createObjectNode();
                n.put("username", rs.getString("username"));
                n.put("ratingCount", rs.getInt("rating_count"));
                arr.add(n);
            }

            sendResponse(ex, 200, JsonUtil.mapper.writeValueAsString(arr));

        } catch (Exception e) {
            sendResponse(ex, 500, JsonUtil.error(e.getMessage()));
        }
    }
}
