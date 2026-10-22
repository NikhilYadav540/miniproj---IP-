import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Small helpers shared by all handlers: read request values, send a reply. */
public class Http {

    /** Reads values from the URL (?q=java) or from a POSTed form (name=Ravi&email=...). */
    public static Map<String, String> params(HttpExchange ex) throws IOException {
        Map<String, String> map = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (ex.getRequestMethod().equalsIgnoreCase("POST")) {
            raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        }
        if (raw == null || raw.isEmpty()) return map;
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(key, value);
        }
        return map;
    }

    /** Returns null when the named value is missing or not a whole number. */
    public static Integer intParam(Map<String, String> p, String key) {
        try { return Integer.valueOf(p.getOrDefault(key, "").trim()); }
        catch (Exception e) { return null; }
    }

    /** Trimmed form value, or empty string when the field is missing. */
    public static String text(Map<String, String> p, String key) {
        String v = p.get(key);
        return v == null ? "" : v.trim();
    }

    /** Stops the request when the HTTP method is not one of the allowed ones. */
    public static boolean rejectMethod(HttpExchange ex, String... allowed) throws IOException {
        String method = ex.getRequestMethod();
        for (String a : allowed) if (a.equalsIgnoreCase(method)) return false;
        error(ex, 405, "Use a different request method");
        return true;
    }

    /** Sends a JSON reply with the given status code (200 = OK, 400/500 = error). */
    public static void json(HttpExchange ex, int status, String body) throws IOException {
        send(ex, status, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    /** Sends an error as {"error":"message"}. */
    public static void error(HttpExchange ex, int status, String message) throws IOException {
        json(ex, status, "{\"error\":\"" + Database.escape(String.valueOf(message)) + "\"}");
    }

    /** Sends raw bytes (used for HTML/CSS/JS files too). */
    public static void send(HttpExchange ex, int status, String type, byte[] data) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(data); }
    }
}
