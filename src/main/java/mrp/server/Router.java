package mrp.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import mrp.handler.BaseHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// zentraler router -- verteilt anfragen an handler
public class Router implements HttpHandler {

    private final List<BaseHandler> handlers = new ArrayList<>();

    public void register(BaseHandler h) {
        handlers.add(h);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path   = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        // content-type und cors setzen
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        // ersten passenden handler aufrufen
        for (BaseHandler h : handlers) {
            if (h.canHandle(path, method)) {
                h.handle(exchange);
                return;
            }
        }

        // kein handler gefunden -> 404
        String body = "{\"error\":\"not found\"}";
        exchange.sendResponseHeaders(404, body.length());
        exchange.getResponseBody().write(body.getBytes());
        exchange.getResponseBody().close();
    }
}
