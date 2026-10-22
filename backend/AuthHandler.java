import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

/** POST /api/login  and  POST /api/logout */
public class AuthHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        try {
            if (path.equals("/api/logout")) {
                if (Http.rejectMethod(ex, "POST")) return;
                Auth.destroySession(ex);
                Http.json(ex, 200, "{\"ok\":true}");
                return;
            }

            if (!path.equals("/api/login")) { Http.error(ex, 404, "Unknown route"); return; }
            if (Http.rejectMethod(ex, "POST")) return;

            Map<String, String> p = Http.params(ex);
            String email = Http.text(p, "email");
            String password = p.getOrDefault("password", "");
            if (email.isEmpty() || password.isEmpty()) {
                Http.error(ex, 400, "Email and password are required");
                return;
            }

            try (Connection con = Database.connect();
                 PreparedStatement ps = con.prepareStatement(
                     "SELECT user_id, name, email, role FROM users WHERE email = ? AND password = ?")) {
                ps.setString(1, email);
                ps.setString(2, password);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        Http.error(ex, 401, "Wrong email or password");
                        return;
                    }
                    int userId = rs.getInt("user_id");
                    String name = rs.getString("name");
                    String role = rs.getString("role");
                    String mail = rs.getString("email");
                    String token = Auth.createSession(userId, name, role);
                    Http.json(ex, 200,
                        "{\"user_id\":" + userId
                        + ",\"name\":\"" + Database.escape(name)
                        + "\",\"email\":\"" + Database.escape(mail)
                        + "\",\"role\":\"" + Database.escape(role)
                        + "\",\"token\":\"" + token + "\"}");
                }
            }
        } catch (Exception e) {
            Http.error(ex, 500, e.getMessage());
        }
    }
}
