package mrp.util;

import java.util.HashMap;
import java.util.Map;

// speichert aktive tokens im arbeitsspeicher (kein db noetig)
public class TokenStore {

    // token -> username mapping
    private static final Map<String, String> tokens = new HashMap<>();

    // token erstellen und speichern
    public static String createToken(String username) {
        String token = username + "-mrpToken";
        tokens.put(token, username);
        return token;
    }

    // username aus token holen, null wenn ungueltig
    public static String getUsernameFromToken(String token) {
        return tokens.get(token);
    }

    // token ungueltig machen
    public static void removeToken(String token) {
        tokens.remove(token);
    }

    // pruefen ob token gueltig
    public static boolean isValid(String token) {
        return token != null && tokens.containsKey(token);
    }
}
