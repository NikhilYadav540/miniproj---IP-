import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;

/** Starts the web server on port 8080 and connects each URL to its handler. */
public class Main {
    public static void main(String[] args) throws Exception {
        try {
            Database.connect().close();
        } catch (Exception e) {
            System.err.println("Cannot reach MySQL. Start it with start.bat (port 3307), then try again.");
            System.err.println(e.getMessage());
            System.exit(1);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/api/login",        new AuthHandler());
        server.createContext("/api/logout",       new AuthHandler());
        server.createContext("/api/books",        new BookHandler());
        server.createContext("/api/categories",   new BookHandler());
        server.createContext("/api/members",      new MemberHandler());
        server.createContext("/api/transactions", new TransactionHandler());
        server.createContext("/api/mybooks",      new TransactionHandler());
        server.createContext("/api/stats",        new TransactionHandler());
        server.createContext("/api/issue",        new TransactionHandler());
        server.createContext("/api/return",       new TransactionHandler());
        server.createContext("/",                 new StaticHandler());

        server.start();
        System.out.println("Smart Library running -> http://localhost:8080");
    }
}
