import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.Map;

/**
 * Member routes:
 *   GET  /api/members       list everyone
 *   POST /api/members/add   register a new member (role is always "member")
 */
public class MemberHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        try {
            if (path.equals("/api/members")) {
                if (Http.rejectMethod(ex, "GET")) return;
                if (Auth.reject(ex, true)) return;
                Http.json(ex, 200, Database.select(
                    "SELECT user_id, name, email, role, joined_on FROM users ORDER BY name"));

            } else if (path.equals("/api/members/add")) {
                if (Http.rejectMethod(ex, "POST")) return;
                Map<String, String> p = Http.params(ex);
                String name = Http.text(p, "name");
                String email = Http.text(p, "email");
                String password = p.getOrDefault("password", "");
                if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                    Http.error(ex, 400, "Name, email and password are required");
                    return;
                }
                if (!email.contains("@") || email.length() > 100 || name.length() > 80) {
                    Http.error(ex, 400, "Please enter a valid name and email");
                    return;
                }
                if (password.length() < 4) {
                    Http.error(ex, 400, "Password must be at least 4 characters");
                    return;
                }
                Integer taken = Database.queryInt("SELECT COUNT(*) FROM users WHERE email = ?", email);
                if (taken != null && taken > 0) {
                    Http.error(ex, 400, "That email is already registered");
                    return;
                }
                Database.update("INSERT INTO users (name, email, password, role) VALUES (?, ?, ?, 'member')",
                    name, email, password);
                Http.json(ex, 200, "{\"ok\":true}");

            } else {
                Http.error(ex, 404, "Unknown route");
            }
        } catch (Exception e) {
            Http.error(ex, 500, "Could not save member");
        }
    }
}
