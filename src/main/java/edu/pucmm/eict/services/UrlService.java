package edu.pucmm.eict.services;

import edu.pucmm.eict.modelos.AccessDetail;
import edu.pucmm.eict.modelos.Url;
import edu.pucmm.eict.modelos.Usuario;
import edu.pucmm.eict.util.Database;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class UrlService {
    private final DataSource ds;
    private static final int ANONYMOUS_TTL_SECONDS = 3600; // 1 hora

    public UrlService() {
        this.ds = Database.getDataSource();
    }

    private String generateShortUrl() {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random rand = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(rand.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String generateUniqueShortCode(Connection c) throws SQLException {
        for (int attempts = 0; attempts < 10; attempts++) {
            String candidate = generateShortUrl();
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM urls WHERE short_url = ?")) {
                ps.setString(1, candidate);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return candidate;
                }
            }
        }
        throw new SQLException("No se pudo generar short_url único tras varios intentos");
    }

    // Guarda y devuelve Url con datos básicos; si user es anónimo, setea expires_at
    public Url saveUrl(String originalUrl, Usuario user) {
        try (Connection c = ds.getConnection()) {
            Timestamp now = new Timestamp(System.currentTimeMillis());
            Timestamp expires = null;
            if (user != null && "anonymous".equals(user.getRole())) {
                expires = new Timestamp(System.currentTimeMillis() + ANONYMOUS_TTL_SECONDS * 1000L);
            }

            Long userId = null;
            if (user != null && user.getId() != null) {
                userId = user.getId();
            } else if (user != null && user.getUsername() != null && !user.getUsername().startsWith("anon-")) {
                // intentar resolver id por username
                try (PreparedStatement us = c.prepareStatement("SELECT id FROM usuarios WHERE username = ?")) {
                    us.setString(1, user.getUsername());
                    try (ResultSet rs = us.executeQuery()) {
                        if (rs.next()) userId = rs.getLong(1);
                    }
                }
            }

            String sql = "INSERT INTO urls(original_url, short_url, access_count, user_id, created_at, expires_at) VALUES(?,?,?,?,?,?)";

            for (int attempt = 0; attempt < 5; attempt++) {
                String shortCode = generateUniqueShortCode(c);
                try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, originalUrl);
                    ps.setString(2, shortCode);
                    ps.setInt(3, 0);
                    if (userId == null) ps.setNull(4, Types.BIGINT); else ps.setLong(4, userId);
                    ps.setTimestamp(5, now);
                    if (expires == null) ps.setNull(6, Types.TIMESTAMP); else ps.setTimestamp(6, expires);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) {
                            long id = keys.getLong(1);
                            Url url = new Url(originalUrl, shortCode);
                            url.setId(id);
                            url.setUser(user);
                            url.setCreatedAt(new java.util.Date(now.getTime()));
                            url.setExpiresAt(expires != null ? new java.util.Date(expires.getTime()) : null);
                            return url;
                        }
                    }
                } catch (SQLIntegrityConstraintViolationException dup) {
                    // colisión por unique: reintentar
                    continue;
                }
            }
            throw new SQLException("No se pudo insertar URL por colisiones");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String getOriginalUrl(String shortUrl) {
        // Devuelve original y actualiza métricas básicas (access_count y accessTimes)
        try (Connection c = ds.getConnection()) {
            // Chequear expiración
            String q = "SELECT id, original_url, expires_at FROM urls WHERE short_url = ?";
            try (PreparedStatement ps = c.prepareStatement(q)) {
                ps.setString(1, shortUrl);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long id = rs.getLong("id");
                        Timestamp exp = rs.getTimestamp("expires_at");
                        if (exp != null && exp.before(new Timestamp(System.currentTimeMillis()))) {
                            // expirado: eliminar registro
                            deleteById(c, id);
                            return null;
                        }
                        String original = rs.getString("original_url");
                        // incrementar access_count
                        try (PreparedStatement up = c.prepareStatement("UPDATE urls SET access_count = access_count + 1 WHERE id = ?")) {
                            up.setLong(1, id);
                            up.executeUpdate();
                        }
                        // registrar marca de tiempo simple en access_details como evento sin otros datos
                        try (PreparedStatement ins = c.prepareStatement("INSERT INTO access_details(url_id, timestamp) VALUES(?,?)")) {
                            ins.setLong(1, id);
                            ins.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                            ins.executeUpdate();
                        }
                        return original;
                    }
                }
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Url getUrl(String shortUrl) {
        try (Connection c = ds.getConnection()) {
            String q = "SELECT u.id, u.original_url, u.short_url, u.access_count, u.created_at, u.expires_at, u.user_id, uu.username, uu.password, uu.role " +
                    "FROM urls u LEFT JOIN usuarios uu ON u.user_id = uu.id WHERE u.short_url = ?";
            try (PreparedStatement ps = c.prepareStatement(q)) {
                ps.setString(1, shortUrl);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Url url = new Url(rs.getString("original_url"), rs.getString("short_url"));
                        url.setId(rs.getLong("id"));
                        url.setAccessCount(rs.getInt("access_count"));
                        Timestamp cat = rs.getTimestamp("created_at");
                        if (cat != null) url.setCreatedAt(new java.util.Date(cat.getTime()));
                        Timestamp eat = rs.getTimestamp("expires_at");
                        if (eat != null) url.setExpiresAt(new java.util.Date(eat.getTime()));
                        Long userId = (Long) rs.getObject("user_id");
                        if (userId != null) {
                            Usuario u = new Usuario(rs.getString("username"), rs.getString("password"), rs.getString("role"));
                            u.setId(userId);
                            url.setUser(u);
                        }
                        // cargar access details
                        loadAccessData(c, url);
                        return url;
                    }
                }
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Collection<Url> getAllUrls() {
        List<Url> list = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT u.id, u.original_url, u.short_url, u.access_count, u.created_at, u.expires_at, u.user_id, uu.username, uu.password, uu.role FROM urls u LEFT JOIN usuarios uu ON u.user_id = uu.id ORDER BY u.id DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Url url = new Url(rs.getString("original_url"), rs.getString("short_url"));
                url.setId(rs.getLong("id"));
                url.setAccessCount(rs.getInt("access_count"));
                Timestamp cat = rs.getTimestamp("created_at");
                if (cat != null) url.setCreatedAt(new java.util.Date(cat.getTime()));
                Timestamp eat = rs.getTimestamp("expires_at");
                if (eat != null) url.setExpiresAt(new java.util.Date(eat.getTime()));
                Long userId = (Long) rs.getObject("user_id");
                if (userId != null) {
                    Usuario u = new Usuario(rs.getString("username"), rs.getString("password"), rs.getString("role"));
                    u.setId(userId);
                    url.setUser(u);
                }
                loadAccessData(c, url);
                list.add(url);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    private void loadAccessData(Connection c, Url url) throws SQLException {
        String q = "SELECT timestamp, browser, ip, client_domain, platform FROM access_details WHERE url_id = ? ORDER BY timestamp";
        try (PreparedStatement ps = c.prepareStatement(q)) {
            ps.setLong(1, url.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("timestamp");
                    String browser = rs.getString("browser");
                    String ip = rs.getString("ip");
                    String client = rs.getString("client_domain");
                    String platform = rs.getString("platform");
                    AccessDetail d = new AccessDetail(new java.util.Date(ts.getTime()), browser, ip, client, platform);
                    url.getAccessDetails().add(d);
                    url.getAccessTimes().add(new java.util.Date(ts.getTime()));
                }
            }
        }
    }

    public void recordAccess(Url url, AccessDetail detail) {
        try (Connection c = ds.getConnection()) {
            try (PreparedStatement up = c.prepareStatement("UPDATE urls SET access_count = access_count + 1 WHERE id = ?")) {
                up.setLong(1, url.getId());
                up.executeUpdate();
            }
            String ins = "INSERT INTO access_details(url_id, timestamp, browser, ip, client_domain, platform) VALUES(?,?,?,?,?,?)";
            try (PreparedStatement ps = c.prepareStatement(ins)) {
                ps.setLong(1, url.getId());
                ps.setTimestamp(2, new Timestamp(detail.getTimestamp().getTime()));
                ps.setString(3, detail.getBrowser());
                ps.setString(4, detail.getIp());
                ps.setString(5, detail.getClientDomain());
                ps.setString(6, detail.getPlatform());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean deleteUrl(String shortUrl) {
        try (Connection c = ds.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM urls WHERE short_url = ?")) {
                ps.setString(1, shortUrl);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long id = rs.getLong(1);
                        deleteById(c, id);
                        return true;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteById(Connection c, long id) throws SQLException {
        try (PreparedStatement ps1 = c.prepareStatement("DELETE FROM access_details WHERE url_id = ?")) {
            ps1.setLong(1, id);
            ps1.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM urls WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    public boolean updateShortUrl(String originalShort, String newShort) {
        try (Connection c = ds.getConnection()) {
            // verificar colisión
            try (PreparedStatement chk = c.prepareStatement("SELECT 1 FROM urls WHERE short_url = ?")) {
                chk.setString(1, newShort);
                try (ResultSet rs = chk.executeQuery()) {
                    if (rs.next()) return false; // ya existe
                }
            }
            try (PreparedStatement ps = c.prepareStatement("UPDATE urls SET short_url = ? WHERE short_url = ?")) {
                ps.setString(1, newShort);
                ps.setString(2, originalShort);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }

}
