package mrp.model;

import java.util.List;

// datenklasse für media eintrag
public class MediaEntry {
    public int id;
    public String title;
    public String description;
    public String mediaType;
    public int releaseYear;
    public List<String> genres;
    public int ageRestriction;
    public int creatorId;
    public double averageScore;

    public MediaEntry() {}
}
