import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Serves the frontend files (HTML, CSS, JS) from the "frontend" folder. */
public class StaticHandler implements HttpHandler {
    private static final Path ROOT = Path.of("frontend").toAbsolutePath().normalize();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (Http.rejectMethod(ex, "GET", "HEAD")) return;

        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        String rel = path.startsWith("/") ? path.substring(1) : path;

        Path file = ROOT.resolve(rel).normalize();
        if (!file.startsWith(ROOT) || !Files.isRegularFile(file)) {
            Http.send(ex, 404, "text/plain; charset=utf-8", "Not found".getBytes());
            return;
        }

        String type = rel.endsWith(".css") ? "text/css"
                    : rel.endsWith(".js")  ? "text/javascript"
                    : rel.endsWith(".ico") ? "image/x-icon"
                    : "text/html";
        byte[] data = Files.readAllBytes(file);
        if (ex.getRequestMethod().equalsIgnoreCase("HEAD")) {
            ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
            ex.sendResponseHeaders(200, data.length);
            ex.close();
            return;
        }
        Http.send(ex, 200, type + "; charset=utf-8", data);
    }
}
