import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.Map;

/**
 * Issue / return routes:
 *   GET  /api/stats          numbers for the dashboard
 *   GET  /api/transactions   history of issued books
 *   POST /api/issue          (book_id, user_id)  lend a book for 14 days
 *   POST /api/return         (txn_id)            take a book back, fine = Rs 2 per late day
 */
public class TransactionHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        try {
            if (path.equals("/api/stats")) {
                if (Http.rejectMethod(ex, "GET")) return;
                if (Auth.reject(ex, false)) return;
                Auth.Session me = Auth.sessionOf(ex);
                String rows;
                if ("admin".equals(me.role())) {
                    rows = Database.select(
                        "SELECT (SELECT COUNT(*) FROM books) AS books, " +
                        "(SELECT COUNT(*) FROM users WHERE role = 'member') AS members, " +
                        "(SELECT COUNT(*) FROM transactions WHERE return_date IS NULL) AS issued, " +
                        "(SELECT COALESCE(SUM(available_copies),0) FROM books) AS available, " +
                        "(SELECT COUNT(*) FROM transactions WHERE return_date IS NULL AND due_date < CURDATE()) AS overdue");
                } else {
                    rows = Database.select(
                        "SELECT (SELECT COUNT(*) FROM books) AS books, " +
                        "0 AS members, " +
                        "(SELECT COUNT(*) FROM transactions WHERE user_id = ? AND return_date IS NULL) AS issued, " +
                        "(SELECT COALESCE(SUM(available_copies),0) FROM books) AS available, " +
                        "(SELECT COUNT(*) FROM transactions WHERE user_id = ? AND return_date IS NULL AND due_date < CURDATE()) AS overdue",
                        me.userId(), me.userId());
                }
                Http.json(ex, 200, rows.substring(1, rows.length() - 1));

            } else if (path.equals("/api/transactions")) {
                if (Http.rejectMethod(ex, "GET")) return;
                if (Auth.reject(ex, true)) return;
                Http.json(ex, 200, Database.select(
                    "SELECT t.txn_id, b.title, u.name AS member, t.issue_date, t.due_date, t.return_date, t.fine " +
                    "FROM transactions t JOIN books b ON b.book_id = t.book_id " +
                    "JOIN users u ON u.user_id = t.user_id ORDER BY t.txn_id DESC"));

            } else if (path.equals("/api/mybooks")) {
                if (Http.rejectMethod(ex, "GET")) return;
                Auth.Session me = Auth.sessionOf(ex);
                if (me == null) { Http.error(ex, 401, "Please sign in first"); return; }
                Http.json(ex, 200, Database.select(
                    "SELECT t.txn_id, b.title, b.author, t.issue_date, t.due_date, t.return_date, t.fine " +
                    "FROM transactions t JOIN books b ON b.book_id = t.book_id " +
                    "WHERE t.user_id = ? ORDER BY t.txn_id DESC", me.userId()));

            } else if (path.equals("/api/issue")) {
                if (Http.rejectMethod(ex, "POST")) return;
                if (Auth.reject(ex, true)) return;
                Map<String, String> p = Http.params(ex);
                Integer bookId = Http.intParam(p, "book_id");
                Integer userId = Http.intParam(p, "user_id");
                if (bookId == null || userId == null) {
                    Http.error(ex, 400, "Pick a book and a member");
                    return;
                }
                String role = Database.queryText("SELECT role FROM users WHERE user_id = ?", userId);
                if (role == null) { Http.error(ex, 400, "Member not found"); return; }
                if (!"member".equals(role)) { Http.error(ex, 400, "Books can only be issued to members"); return; }
                Integer already = Database.queryInt(
                    "SELECT COUNT(*) FROM transactions WHERE book_id = ? AND user_id = ? AND return_date IS NULL",
                    bookId, userId);
                if (already != null && already > 0) {
                    Http.error(ex, 400, "This member already has that book");
                    return;
                }
                int taken = Database.update(
                    "UPDATE books SET available_copies = available_copies - 1 " +
                    "WHERE book_id = ? AND available_copies > 0", bookId);
                if (taken == 0) { Http.error(ex, 400, "No copies available"); return; }
                try {
                    Database.update(
                        "INSERT INTO transactions (book_id, user_id, issue_date, due_date) " +
                        "VALUES (?, ?, CURDATE(), CURDATE() + INTERVAL 14 DAY)",
                        bookId, userId);
                } catch (Exception e) {
                    Database.update("UPDATE books SET available_copies = available_copies + 1 WHERE book_id = ?",
                        bookId);
                    throw e;
                }
                Http.json(ex, 200, "{\"ok\":true}");

            } else if (path.equals("/api/return")) {
                if (Http.rejectMethod(ex, "POST")) return;
                if (Auth.reject(ex, true)) return;
                Integer txnId = Http.intParam(Http.params(ex), "txn_id");
                if (txnId == null) { Http.error(ex, 400, "Which loan should be returned?"); return; }
                int closed = Database.update(
                    "UPDATE books b JOIN transactions t ON t.book_id = b.book_id " +
                    "SET t.return_date = CURDATE(), " +
                    "t.fine = GREATEST(DATEDIFF(CURDATE(), t.due_date), 0) * 2, " +
                    "b.available_copies = LEAST(b.available_copies + 1, b.total_copies) " +
                    "WHERE t.txn_id = ? AND t.return_date IS NULL", txnId);
                if (closed == 0) { Http.error(ex, 400, "Already returned"); return; }
                Http.json(ex, 200, "{\"ok\":true}");

            } else {
                Http.error(ex, 404, "Unknown route");
            }
        } catch (Exception e) {
            Http.error(ex, 500, e.getMessage());
        }
    }
}
