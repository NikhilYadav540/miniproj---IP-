import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of who is signed in. /api/login creates a random session token;
 * every other request proves who it is by sending that token back in the
 * "X-Auth-Token" header. Sessions live in memory, so they end when the server restarts.
 */
public class Auth {
    /** One signed-in user: who they are and what they may do. */
    public record Session(int userId, String name, String role) {}

    /** token -> the signed-in user. */
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();

    /** Signs a user in and returns their session token. One live session per user. */
    public static String createSession(int userId, String name, String role) {
        SESSIONS.entrySet().removeIf(e -> e.getValue().userId() == userId);
        String token = UUID.randomUUID().toString();
        SESSIONS.put(token, new Session(userId, name, role));
        return token;
    }

    /** Drops the session on this request (used by Log Out). */
    public static void destroySession(HttpExchange ex) {
        String token = tokenOf(ex);
        if (token != null) SESSIONS.remove(token);
    }

    /** Returns the signed-in user, or null when the request has no valid token. */
    public static Session sessionOf(HttpExchange ex) {
        String token = tokenOf(ex);
        return token == null ? null : SESSIONS.get(token);
    }

    /**
     * Use at the top of a route: stops the request with 401/403 when the caller
     * is not signed in, or (adminOnly) is not an admin. Returns true if rejected.
     */
    public static boolean reject(HttpExchange ex, boolean adminOnly) throws IOException {
        Session s = sessionOf(ex);
        if (s == null)                              { Http.error(ex, 401, "Please sign in first"); return true; }
        if (adminOnly && !s.role().equals("admin")) { Http.error(ex, 403, "Only admins can do this"); return true; }
        return false;
    }

    private static String tokenOf(HttpExchange ex) {
        return ex.getRequestHeaders().getFirst("X-Auth-Token");
    }
}
