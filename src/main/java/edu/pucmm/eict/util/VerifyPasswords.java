package edu.pucmm.eict.util;

import edu.pucmm.eict.services.UserService;
import edu.pucmm.eict.modelos.Usuario;
import java.util.Collection;

// Utilidad para verificar el estado de las contraseñas en la base de datos

public class VerifyPasswords {
    public static void main(String[] args) {
        System.out.println("VERIFICACIÓN DE CONTRASEÑAS EN LA BASE DE DATOS");
        System.out.println("==================================================");
        System.out.println();
        
        // Inicializar base de datos
        Database.init();
        
        // Obtener servicio de usuarios
        UserService userService = new UserService();
        
        // Obtener todos los usuarios
        Collection<Usuario> users = userService.getAllUsers();
        
        System.out.println("Total de usuarios: " + users.size());
        System.out.println();
        System.out.println("Estado de las contraseñas:");
        System.out.println("─────────────────────────────────────────────────");
        
        for (Usuario user : users) {
            String password = user.getPassword();
            boolean isBcrypt = password.startsWith("$2a$") ||
                              password.startsWith("$2b$") ||
                              password.startsWith("$2y$");
            
            String status = isBcrypt ? "HASHEADA (BCrypt)" : "TEXTO PLANO";
            String preview = isBcrypt ? password.substring(0, Math.min(30, password.length())) + "..."
                                      : "[OCULTO POR SEGURIDAD]";
            
            System.out.printf("Usuario: %-15s | Rol: %-10s | Estado: %s%n", 
                            user.getUsername(), 
                            user.getRole(), 
                            status);
            System.out.printf("  Longitud: %d caracteres | Vista previa: %s%n", 
                            password.length(), 
                            preview);
            System.out.println();
        }
        
        System.out.println("==================================================");
        System.out.println("RESUMEN DE SEGURIDAD:");
        long hashedCount = users.stream()
            .filter(u -> u.getPassword().startsWith("$2a$") || 
                        u.getPassword().startsWith("$2b$") || 
                        u.getPassword().startsWith("$2y$"))
            .count();
        long plainTextCount = users.size() - hashedCount;
        
        System.out.println("   Contraseñas hasheadas: " + hashedCount);
        System.out.println("   Contraseñas en texto plano: " + plainTextCount);
        
        if (plainTextCount > 0) {
            System.out.println();
            System.out.println("️  ALERTA: Se encontraron contraseñas en texto plano!");
            System.out.println("  Ejecuta la migración con userService.migratePasswordsToHash()");
        } else {
            System.out.println();
            System.out.println("¡EXCELENTE! Todas las contraseñas están protegidas con BCrypt.");
        }
    }
}
