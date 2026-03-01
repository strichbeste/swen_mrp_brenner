package mrp.model;

import java.sql.Timestamp;

// datenklasse für bewertung
public class Rating {
    public int id;
    public int mediaId;
    public int userId;
    public int stars;
    public String comment;
    public boolean commentConfirmed;
    public Timestamp createdAt;
    public int likes;

    public Rating() {}
}
