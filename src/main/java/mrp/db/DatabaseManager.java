package mrp.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// verwaltet db-verbindungen (singleton-pattern)
public class DatabaseManager {

    // verbindungsdaten -- anpassen wenn noetig
    private static final String URL  = "jdbc:postgresql://localhost:5432/mrp";
    private static final String USER = "postgres";
    private static final String PASS = "postgres";

    // singleton instanz
    private static DatabaseManager instance;

    private DatabaseManager() {}

    public static void init() {
        instance = new DatabaseManager();
    }

    // singleton zugriff
    public static DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    // neue verbindung holen (caller muss schliessen!)
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
