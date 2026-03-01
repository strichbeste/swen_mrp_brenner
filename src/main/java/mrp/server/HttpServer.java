package mrp.server;

import mrp.handler.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

// http server basierend auf com.sun.net.httpserver (kein spring/asp)
public class HttpServer {

    private final int port;

    public HttpServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        com.sun.net.httpserver.HttpServer server =
            com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(port), 0);

        // alle anfragen gehen durch einen zentralen router
        Router router = new Router();
        router.register(new AuthHandler());
        router.register(new UserHandler());
        router.register(new MediaHandler());
        router.register(new RatingHandler());
        router.register(new FavoriteHandler());
        router.register(new RecommendationHandler());
        router.register(new LeaderboardHandler());

        server.createContext("/api", router);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
    }
}
