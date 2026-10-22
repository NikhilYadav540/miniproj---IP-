import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.Map;

/**
 * Book routes:
 *   GET  /api/books?q=text      list / search books
 *   GET  /api/categories        list categories (for the dropdown)
 *   POST /api/books/add         add a book
 *   POST /api/books/update      edit a book
 *   POST /api/books/delete      delete a book
 */
public class BookHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        try {
            if (path.equals("/api/categories")) {
                if (Http.rejectMethod(ex, "GET")) return;
                if (Auth.reject(ex, false)) return;
                Http.json(ex, 200, Database.select("SELECT * FROM categories ORDER BY name"));

            } else if (path.equals("/api/books")) {
                if (Http.rejectMethod(ex, "GET")) return;
                if (Auth.reject(ex, false)) return;
                Map<String, String> p = Http.params(ex);
                String like = "%" + p.getOrDefault("q", "") + "%";
                Http.json(ex, 200, Database.select(
                    "SELECT b.*, c.name AS category FROM books b " +
                    "JOIN categories c ON c.category_id = b.category_id " +
                    "WHERE b.title LIKE ? OR b.author LIKE ? ORDER BY b.title", like, like));

            } else if (path.equals("/api/books/add")) {
                if (Http.rejectMethod(ex, "POST")) return;
                if (Auth.reject(ex, true)) return;
                Map<String, String> p = Http.params(ex);
                String err = validateBook(p, false);
                if (err != null) { Http.error(ex, 400, err); return; }
                int copies = Integer.parseInt(Http.text(p, "copies"));
                Database.update(
                    "INSERT INTO books (title, author, isbn, category_id, total_copies, available_copies) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                    Http.text(p, "title"), Http.text(p, "author"), Http.text(p, "isbn"),
                    Http.intParam(p, "category_id"), copies, copies);
                Http.json(ex, 200, "{\"ok\":true}");

            } else if (path.equals("/api/books/update")) {
                if (Http.rejectMethod(ex, "POST")) return;
                if (Auth.reject(ex, true)) return;
                Map<String, String> p = Http.params(ex);
                String err = validateBook(p, true);
                if (err != null) { Http.error(ex, 400, err); return; }
                int copies = Integer.parseInt(Http.text(p, "copies"));
                Integer bookId = Http.intParam(p, "book_id");
                Integer issued = Database.queryInt(
                    "SELECT total_copies - available_copies FROM books WHERE book_id = ?", bookId);
                if (issued == null) { Http.error(ex, 404, "Book not found"); return; }
                if (copies < issued) {
                    Http.error(ex, 400, "Cannot set copies below the " + issued + " currently issued");
                    return;
                }
                Database.update(
                    "UPDATE books SET title=?, author=?, isbn=?, category_id=?, " +
                    "available_copies = available_copies + (? - total_copies), total_copies=? WHERE book_id=?",
                    Http.text(p, "title"), Http.text(p, "author"), Http.text(p, "isbn"),
                    Http.intParam(p, "category_id"), copies, copies, bookId);
                Http.json(ex, 200, "{\"ok\":true}");

            } else if (path.equals("/api/books/delete")) {
                if (Http.rejectMethod(ex, "POST")) return;
                if (Auth.reject(ex, true)) return;
                Integer bookId = Http.intParam(Http.params(ex), "book_id");
                if (bookId == null) { Http.error(ex, 400, "Which book should be deleted?"); return; }
                Integer open = Database.queryInt(
                    "SELECT COUNT(*) FROM transactions WHERE book_id = ? AND return_date IS NULL", bookId);
                if (open != null && open > 0) {
                    Http.error(ex, 400, "Return all copies before deleting this book");
                    return;
                }
                try {
                    int n = Database.update("DELETE FROM books WHERE book_id = ?", bookId);
                    if (n == 0) { Http.error(ex, 404, "Book not found"); return; }
                    Http.json(ex, 200, "{\"ok\":true}");
                } catch (Exception e) {
                    Http.error(ex, 400, "This book has borrow history, so it can't be deleted");
                }

            } else {
                Http.error(ex, 404, "Unknown route");
            }
        } catch (Exception e) {
            Http.error(ex, 500, "Could not complete request: " + e.getMessage());
        }
    }

    /** Returns an error message, or null when the form looks valid. */
    private static String validateBook(Map<String, String> p, boolean needId) throws Exception {
        if (needId && Http.intParam(p, "book_id") == null) return "Book id is missing";
        if (Http.text(p, "title").isEmpty() || Http.text(p, "author").isEmpty())
            return "Title and author are required";
        Integer copies = Http.intParam(p, "copies");
        if (copies == null || copies < 1) return "Copies must be a number of 1 or more";
        Integer cat = Http.intParam(p, "category_id");
        if (cat == null) return "Pick a category";
        Integer exists = Database.queryInt("SELECT COUNT(*) FROM categories WHERE category_id = ?", cat);
        if (exists == null || exists == 0) return "Unknown category";
        return null;
    }
}
