package mrp.handler;

import com.sun.net.httpserver.HttpExchange;
import mrp.util.JsonUtil;
import mrp.util.TokenStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

// basisklasse fuer alle handler -- hilfsmethoden
public abstract class BaseHandler {

    // pruefen ob dieser handler die anfrage uebernimmt
    public abstract boolean canHandle(String path, String method);

    // anfrage verarbeiten
    public abstract void handle(HttpExchange exchange) throws IOException;

    // request body als string lesen
    protected String readBody(HttpExchange ex) throws IOException {
        InputStream is = ex.getRequestBody();
        return new String(is.readAllBytes());
    }

    // antwort schicken
    protected void sendResponse(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes();
        ex.sendResponseHeaders(status, bytes.length);
        OutputStream os = ex.getResponseBody();
        os.write(bytes);
        os.close();
    }

    // token aus authorization header holen
    protected String getToken(HttpExchange ex) {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) return null;
        return auth.substring(7).trim();
    }

    // eingeloggten usernamen holen, null wenn nicht eingeloggt
    protected String getLoggedInUser(HttpExchange ex) {
        String token = getToken(ex);
        if (!TokenStore.isValid(token)) return null;
        return TokenStore.getUsernameFromToken(token);
    }

    // query parameter aus url parsen
    protected Map<String, String> parseQuery(HttpExchange ex) {
        Map<String, String> params = new HashMap<>();
        String query = ex.getRequestURI().getQuery();
        if (query == null) return params;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) params.put(kv[0], kv[1]);
        }
        return params;
    }

    // pfad segmente holen
    protected String[] getPathParts(HttpExchange ex) {
        String path = ex.getRequestURI().getPath();
        return path.split("/");
    }

    // auth check -- sendet 401 wenn nicht eingeloggt
    protected boolean requireAuth(HttpExchange ex) throws IOException {
        if (getLoggedInUser(ex) == null) {
            sendResponse(ex, 401, JsonUtil.error("nicht eingeloggt"));
            return false;
        }
        return true;
    }
}
