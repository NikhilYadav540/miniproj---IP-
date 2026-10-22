import java.sql.*;

/** Talks to MySQL through JDBC. Every other class uses these helpers instead of writing JDBC code. */
public class Database {
    // Override with LIBRARY_DB_URL / LIBRARY_DB_USER / LIBRARY_DB_PASSWORD if needed.
    // Default: this project's private MySQL on port 3307 (start.bat), user root / password root.
    private static final String URL      = env("LIBRARY_DB_URL", "jdbc:mysql://localhost:3307/smart_library");
    private static final String USER     = env("LIBRARY_DB_USER", "root");
    private static final String PASSWORD = env("LIBRARY_DB_PASSWORD", "root");

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    /** Opens a new connection to the database. */
    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /** Puts the values into the ? placeholders of a query (this also prevents SQL injection). */
    private static void fill(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
    }

    /** Runs a SELECT and returns the rows as a JSON array text, e.g. [{"id":1,"name":"A"},...] */
    public static String select(String sql, Object... params) throws SQLException {
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            fill(ps, params);
            ResultSet rs = ps.executeQuery();
            ResultSetMetaData meta = rs.getMetaData();
            StringBuilder json = new StringBuilder("[");
            boolean firstRow = true;
            while (rs.next()) {
                if (!firstRow) json.append(",");
                firstRow = false;
                json.append("{");
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    if (i > 1) json.append(",");
                    Object value = rs.getObject(i);
                    json.append("\"").append(meta.getColumnLabel(i)).append("\":");
                    if (value == null)                json.append("null");
                    else if (value instanceof Number) json.append(value);
                    else json.append("\"").append(escape(value.toString())).append("\"");
                }
                json.append("}");
            }
            return json.append("]").toString();
        }
    }

    /** First column of the first row as an integer, or null when there is no row. */
    public static Integer queryInt(String sql, Object... params) throws SQLException {
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            fill(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Object v = rs.getObject(1);
                return v == null ? null : ((Number) v).intValue();
            }
        }
    }

    /** First column of the first row as text, or null when there is no row. */
    public static String queryText(String sql, Object... params) throws SQLException {
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            fill(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString(1);
            }
        }
    }

    /** Runs INSERT / UPDATE / DELETE and returns how many rows changed. */
    public static int update(String sql, Object... params) throws SQLException {
        try (Connection con = connect(); PreparedStatement ps = con.prepareStatement(sql)) {
            fill(ps, params);
            return ps.executeUpdate();
        }
    }

    /** Makes text safe to place inside a JSON string. */
    public static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", " ").replace("\n", " ");
    }
}
