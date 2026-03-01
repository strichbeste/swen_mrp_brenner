package mrp.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import mrp.db.DatabaseManager;
import mrp.util.JsonUtil;
import mrp.util.TokenStore;

import java.io.IOException;
import java.sql.*;

// registrierung und login
public class AuthHandler extends BaseHandler {

    @Override
    public boolean canHandle(String path, String method) {
        return path.endsWith("/users/register") || path.endsWith("/users/login");
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.endsWith("/register")) handleRegister(ex);
        else handleLogin(ex);
    }

    // user registrieren
    private void handleRegister(HttpExchange ex) throws IOException {
        try {
            JsonNode body = JsonUtil.mapper.readTree(readBody(ex));
            String username = body.get("username").asText();
            String password = body.get("password").asText();

            // validierung
            if (username.isBlank() || password.isBlank()) {
                sendResponse(ex, 400, JsonUtil.error("username und passwort erforderlich"));
                return;
            }

            // in db speichern -- prepared statement gegen sql injection
            try (Connection c = DatabaseManager.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO users(username, password) VALUES(?,?)")) {
                ps.setString(1, username);
                ps.setString(2, password);
                ps.executeUpdate();
            }

            sendResponse(ex, 201, JsonUtil.message("registriert"));

        } catch (Exception e) {
            // username schon vorhanden
            if (e.getMessage() != null && e.getMessage().contains("duplicate")) {
                sendResponse(ex, 409, JsonUtil.error("username bereits vergeben"));
            } else {
                sendResponse(ex, 500, JsonUtil.error("server fehler: " + e.getMessage()));
            }
        }
    }

    // login und token generieren
    private void handleLogin(HttpExchange ex) throws IOException {
        try {
            JsonNode body = JsonUtil.mapper.readTree(readBody(ex));
            String username = body.get("username").asText();
            String password = body.get("password").asText();

            // user in db suchen
            try (Connection c = DatabaseManager.getInstance().getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT id FROM users WHERE username=? AND password=?")) {
                ps.setString(1, username);
                ps.setString(2, password);
                ResultSet rs = ps.executeQuery();

                if (!rs.next()) {
                    sendResponse(ex, 401, JsonUtil.error("falsche zugangsdaten"));
                    return;
                }
            }

            // token erstellen
            String token = TokenStore.createToken(username);
            sendResponse(ex, 200, "{\"token\":\"" + token + "\"}");

        } catch (Exception e) {
            sendResponse(ex, 500, JsonUtil.error("server fehler: " + e.getMessage()));
        }
    }
}
