package mrp;

import mrp.db.DatabaseManager;
import mrp.server.HttpServer;

// einstiegspunkt
public class Main {
    public static void main(String[] args) throws Exception {
        // db verbindung initialisieren
        DatabaseManager.init();
        // server starten auf port 8080
        HttpServer server = new HttpServer(8080);
        server.start();
        System.out.println("mrp server laeuft auf port 8080");
    }
}
