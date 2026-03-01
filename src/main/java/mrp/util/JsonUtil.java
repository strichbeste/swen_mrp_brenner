package mrp.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

// hilfsklasse fuer json
public class JsonUtil {

    public static final ObjectMapper mapper = new ObjectMapper();

    // einfache fehlerantwort erstellen
    public static String error(String msg) {
        try {
            ObjectNode n = mapper.createObjectNode();
            n.put("error", msg);
            return mapper.writeValueAsString(n);
        } catch (Exception e) {
            return "{\"error\":\"unknown\"}";
        }
    }

    // einfache nachricht erstellen
    public static String message(String msg) {
        try {
            ObjectNode n = mapper.createObjectNode();
            n.put("message", msg);
            return mapper.writeValueAsString(n);
        } catch (Exception e) {
            return "{\"message\":\"ok\"}";
        }
    }
}
