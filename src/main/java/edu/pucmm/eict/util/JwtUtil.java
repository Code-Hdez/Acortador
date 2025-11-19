package edu.pucmm.eict.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.SignatureAlgorithm;

import javax.crypto.SecretKey;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JwtUtil {
    // Clave JWT cargada desde variable de entorno o generada de forma segura
    private static final String JWT_SECRET;
    private static final SecretKey secretKey;

    // Tiempo de expiración del token, por ejemplo: 1 día en milisegundos
    public static final long EXPIRATION_TIME = 86400000;

    static {
        JWT_SECRET = loadOrGenerateSecret();
        // Validar que la clave tenga al menos 256 bits (32 bytes = 44 caracteres en Base64)
        if (JWT_SECRET.length() < 32) {
            throw new IllegalStateException(
                "La clave JWT debe tener al menos 256 bits (32 caracteres). " +
                "Clave actual: " + JWT_SECRET.length() + " caracteres."
            );
        }
        secretKey = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        System.out.println("[JWT] Clave JWT cargada correctamente (" + JWT_SECRET.length() + " caracteres)");
    }

    /**
     * Carga el secreto JWT desde variable de entorno o lo genera de forma segura.
     * Prioridad:
        * 1. Variable de entorno JWT_SECRET
        * 2. Archivo .jwt_secret en el directorio de trabajo
        * 3. Generación automática segura y persistencia en archivo
     */
    private static String loadOrGenerateSecret() {
        // 1. Intentar cargar desde variable de entorno
        String envSecret = System.getenv("JWT_SECRET");
        if (envSecret != null && !envSecret.trim().isEmpty()) {
            System.out.println("[JWT] Clave cargada desde variable de entorno JWT_SECRET");
            return envSecret.trim();
        }

        // 2. Intentar cargar desde archivo .jwt_secret
        String secretFilePath = ".jwt_secret";
        File secretFile = new File(secretFilePath);
        if (secretFile.exists()) {
            try {
                String fileSecret = new String(Files.readAllBytes(Paths.get(secretFilePath)), StandardCharsets.UTF_8).trim();
                if (!fileSecret.isEmpty()) {
                    System.out.println("[JWT] Clave cargada desde archivo " + secretFilePath);
                    return fileSecret;
                }
            } catch (IOException e) {
                System.err.println("[JWT] Error al leer el archivo " + secretFilePath + ": " + e.getMessage());
            }
        }

        // 3. Generar nueva clave segura y persistirla
        System.out.println("[JWT] Generando nueva clave JWT segura (256 bits)...");
        String newSecret = generateSecureSecret();
        
        try {
            Files.write(Paths.get(secretFilePath), newSecret.getBytes(StandardCharsets.UTF_8));
            System.out.println("[JWT] Clave generada y guardada en " + secretFilePath);
            System.out.println("[JWT] IMPORTANTE: Agrega este archivo al .gitignore y usa JWT_SECRET en producción");
        } catch (IOException e) {
            System.err.println("[JWT] Advertencia: No se pudo guardar la clave en archivo: " + e.getMessage());
        }
        
        return newSecret;
    }

    /**
     * Genera una clave JWT criptográficamente segura de 256 bits (32 bytes).
     * Utiliza SecureRandom para garantizar aleatoriedad criptográfica.
     */
    private static String generateSecureSecret() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] secretBytes = new byte[32]; // 256 bits
        secureRandom.nextBytes(secretBytes);
        return Base64.getEncoder().encodeToString(secretBytes);
    }


    // Genera el token incluyendo el username y rol
    public static String generateToken(String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                // Usamos signWith con la secretKey; la firma se genera usando HS256
                .signWith(secretKey)
                .compact();
    }

    // Valida el token y retorna los claims; utiliza la sintaxis de la demo con verifyWith()
    public static Claims validateToken(String token) throws JwtException {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }



    // Extrae el username del token (subject)
    public static String extractUsername(String token) {
        return validateToken(token).getSubject();
    }
}
