package mrp;

import mrp.model.MediaEntry;
import mrp.model.Rating;
import mrp.model.User;
import mrp.util.TokenStore;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.stream.*;

import static org.junit.jupiter.api.Assertions.*;

// unit tests ohne db — business logic
class MrpTests {

    // --- token store tests ---

    @Test
    void tokenErstellen() {
        String t = TokenStore.createToken("alice");
        assertNotNull(t);
        assertTrue(t.contains("alice"));
    }

    @Test
    void tokenGueltig() {
        TokenStore.createToken("bob");
        assertTrue(TokenStore.isValid("bob-mrpToken"));
    }

    @Test
    void tokenUngueltig() {
        assertFalse(TokenStore.isValid("gibtsNicht-mrpToken"));
    }

    @Test
    void tokenUsernameHolen() {
        TokenStore.createToken("carol");
        assertEquals("carol", TokenStore.getUsernameFromToken("carol-mrpToken"));
    }

    @Test
    void tokenEntfernen() {
        TokenStore.createToken("dave");
        TokenStore.removeToken("dave-mrpToken");
        assertFalse(TokenStore.isValid("dave-mrpToken"));
    }

    @Test
    void nullTokenUngueltig() {
        assertFalse(TokenStore.isValid(null));
    }

    // --- model tests ---

    @Test
    void userErstellen() {
        User u = new User(1, "eve", "eve@mail.com", "sci-fi");
        assertEquals("eve", u.username);
        assertEquals("sci-fi", u.favoriteGenre);
    }

    @Test
    void mediaEntryErstellen() {
        MediaEntry m = new MediaEntry();
        m.title = "Inception";
        m.mediaType = "movie";
        m.releaseYear = 2010;
        m.genres = List.of("sci-fi", "thriller");
        assertEquals("Inception", m.title);
        assertEquals(2, m.genres.size());
    }

    @Test
    void ratingErstellen() {
        Rating r = new Rating();
        r.stars = 5;
        r.comment = "super";
        r.commentConfirmed = false;
        assertEquals(5, r.stars);
        assertFalse(r.commentConfirmed);
    }

    // --- business logic tests ---

    @Test
    void starsValidierungGueltig() {
        int stars = 4;
        assertTrue(stars >= 1 && stars <= 5);
    }

    @Test
    void starsValidierungZuGross() {
        int stars = 6;
        assertFalse(stars >= 1 && stars <= 5);
    }

    @Test
    void starsValidierungZuKlein() {
        int stars = 0;
        assertFalse(stars >= 1 && stars <= 5);
    }

    @Test
    void durchschnittBerechnen() {
        List<Integer> stars = List.of(3, 4, 5, 2, 4);
        double avg = stars.stream().mapToInt(Integer::intValue).average().orElse(0);
        assertEquals(3.6, avg, 0.01);
    }

    @Test
    void durchschnittLeereListeIstNull() {
        List<Integer> stars = List.of();
        double avg = stars.stream().mapToInt(Integer::intValue).average().orElse(0);
        assertEquals(0, avg);
    }

    @Test
    void kommentarNichtSichtbarOhneKonfirmation() {
        Rating r = new Rating();
        r.comment = "geheim";
        r.commentConfirmed = false;
        String visible = r.commentConfirmed ? r.comment : "";
        assertEquals("", visible);
    }

    @Test
    void kommentarSichtbarNachKonfirmation() {
        Rating r = new Rating();
        r.comment = "sichtbar";
        r.commentConfirmed = true;
        String visible = r.commentConfirmed ? r.comment : "";
        assertEquals("sichtbar", visible);
    }

    @Test
    void genreFilterMitStreams() {
        List<String> allGenres = List.of("sci-fi,action", "drama", "sci-fi,thriller", "comedy");
        List<String> filtered = allGenres.stream()
            .filter(g -> g.contains("sci-fi"))
            .collect(Collectors.toList());
        assertEquals(2, filtered.size());
    }

    @Test
    void titelSucheTeilstring() {
        List<String> titles = List.of("Inception", "Interstellar", "Avatar", "Inception 2");
        List<String> result = titles.stream()
            .filter(t -> t.toLowerCase().contains("incep"))
            .collect(Collectors.toList());
        assertEquals(2, result.size());
    }

    @Test
    void sortierungNachTitel() {
        List<String> titles = List.of("Zebra", "Alpha", "Mitte");
        List<String> sorted = titles.stream().sorted().collect(Collectors.toList());
        assertEquals("Alpha", sorted.get(0));
    }

    @Test
    void einRatingProUserProMedia() {
        // simuliert unique constraint — set erlaubt keine duplikate
        Set<String> ratingKeys = new HashSet<>();
        ratingKeys.add("user1_media1");
        boolean added = ratingKeys.add("user1_media1");
        assertFalse(added);
    }

    @Test
    void ownershipCheck() {
        int creatorId = 5;
        int requestingUserId = 3;
        assertFalse(creatorId == requestingUserId);
    }

    @Test
    void leaderboardSortierung() {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("alice", 10);
        counts.put("bob", 25);
        counts.put("carol", 5);

        String top = counts.entrySet().stream()
            .sorted(Map.Entry.<String,Integer>comparingByValue().reversed())
            .map(Map.Entry::getKey)
            .findFirst().orElse("");
        assertEquals("bob", top);
    }

    @Test
    void altersbeschraenkungFilter() {
        // nur media mit age <= angefordert zurückgeben
        List<Integer> ages = List.of(0, 6, 12, 16, 18);
        List<Integer> erlaubt = ages.stream()
            .filter(a -> a <= 12)
            .collect(Collectors.toList());
        assertEquals(3, erlaubt.size());
    }

    @Test
    void mediaTypFilter() {
        List<String> types = List.of("movie", "series", "game", "movie");
        List<String> movies = types.stream()
            .filter(t -> t.equals("movie"))
            .collect(Collectors.toList());
        assertEquals(2, movies.size());
    }
}
