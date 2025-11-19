package edu.pucmm.eict.services;

import edu.pucmm.eict.modelos.Usuario;
import edu.pucmm.eict.util.Database;
import org.mindrot.jbcrypt.BCrypt;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class UserService {
    private final DataSource ds;

    public UserService() {
        this.ds = Database.getDataSource();
    }

    /**
     * Hashea una contraseña usando BCrypt con salt automático
        * @param plainPassword Contraseña en texto plano
        * @return Hash seguro de la contraseña
     */
    private String hashPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
    }

    /**
     * Verifica si una contraseña en texto plano coincide con un hash
        * @param plainPassword Contraseña en texto plano
        * @param hashedPassword Hash almacenado en BD
        * @return true si coinciden, false en caso contrario
     */
    private boolean verifyPassword(String plainPassword, String hashedPassword) {
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (IllegalArgumentException e) {
            // El hash no es válido (posiblemente texto plano antiguo)
            return false;
        }
    }

    public Usuario getUserByUsername(String username) {
        String sql = "SELECT id, username, password, role FROM usuarios WHERE username = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Usuario u = new Usuario(rs.getString("username"), rs.getString("password"), rs.getString("role"));
                    u.setId(rs.getLong("id"));
                    return u;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public boolean register(String username, String password) {
        // evita duplicados por UNIQUE
        String sql = "INSERT INTO usuarios(username, password, role) VALUES(?,?,?)";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hashPassword(password)); // ✅ Hash seguro
            ps.setString(3, "user");
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            // violación de unique = ya existe
            return false;
        }
    }

    public boolean registerAdmin(String username, String password) {
        String sql = "INSERT INTO usuarios(username, password, role) VALUES(?,?,?)";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hashPassword(password)); // ✅ Hash seguro
            ps.setString(3, "admin");
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public boolean authenticate(String username, String password) {
        // ✅ Cambio: recuperar el hash y verificar con BCrypt
        String sql = "SELECT password FROM usuarios WHERE username = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password");
                    return verifyPassword(password, storedHash);
                }
                return false;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Collection<Usuario> getAllUsers() {
        String sql = "SELECT id, username, password, role FROM usuarios ORDER BY username";
        List<Usuario> users = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Usuario u = new Usuario(rs.getString("username"), rs.getString("password"), rs.getString("role"));
                u.setId(rs.getLong("id"));
                users.add(u);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return users;
    }

    public boolean updateRole(String username, String newRole) {
        String sql = "UPDATE usuarios SET role = ? WHERE username = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newRole);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean deleteUser(String username) {
        // evitar borrar admin
        String check = "SELECT role FROM usuarios WHERE username = ?";
        try (Connection c = ds.getConnection(); PreparedStatement cps = c.prepareStatement(check)) {
            cps.setString(1, username);
            try (ResultSet rs = cps.executeQuery()) {
                if (rs.next() && "admin".equals(rs.getString("role"))) {
                    return false;
                }
            }
            try (PreparedStatement dps = c.prepareStatement("DELETE FROM usuarios WHERE username = ?")) {
                dps.setString(1, username);
                return dps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void createDefaultAdmin() {
        if (getUserByUsername("admin") == null) {
            registerAdmin("admin", "admin");
            System.out.println("✅ Default admin created with secure password.");
        } else {
            System.out.println("ℹ️  Admin user already exists.");
        }
    }

    public boolean updateUser(String username, String password, String role) {
        String sql = "UPDATE usuarios SET password = ?, role = ? WHERE username = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, hashPassword(password)); // ✅ Hash seguro
            ps.setString(2, role);
            ps.setString(3, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
