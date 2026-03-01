package mrp.model;

// datenklasse für user
public class User {
    public int id;
    public String username;
    public String password;
    public String email;
    public String favoriteGenre;

    public User() {}

    public User(int id, String username, String email, String favoriteGenre) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.favoriteGenre = favoriteGenre;
    }
}
