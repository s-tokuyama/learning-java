package app.security;

import app.store.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

public class JwtService {
    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);
    
    private final SecretKey secretKey;
    private final int accessTokenTtlSec;
    private final int refreshTokenTtlSec;
    
    public JwtService() {
        String secret = System.getenv("JWT_HS256_SECRET");
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalArgumentException("JWT_HS256_SECRET environment variable is required");
        }
        
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlSec = Integer.parseInt(System.getenv().getOrDefault("JWT_ACCESS_TTL_SEC", "600")); // 10分
        this.refreshTokenTtlSec = Integer.parseInt(System.getenv().getOrDefault("JWT_REFRESH_TTL_SEC", "604800")); // 7日
        
        logger.info("JwtService initialized - Access TTL: {}s, Refresh TTL: {}s", accessTokenTtlSec, refreshTokenTtlSec);
    }
    
    public String issueAccess(User user) {
        logger.debug("Issuing access token for user: {}", user.getUsername());
        
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenTtlSec * 1000L);
        String jti = UUID.randomUUID().toString();
        
        String token = Jwts.builder()
                .setSubject(user.getId())
                .claim("username", user.getUsername())
                .claim("roles", user.getRoles())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .setId(jti)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
        
        logger.debug("Access token issued for user: {} with jti: {}", user.getUsername(), jti);
        return token;
    }
    
    public String issueRefresh(User user) {
        logger.debug("Issuing refresh token for user: {}", user.getUsername());
        
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenTtlSec * 1000L);
        String jti = UUID.randomUUID().toString();
        
        String token = Jwts.builder()
                .setSubject(user.getId())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .setId(jti)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
        
        logger.debug("Refresh token issued for user: {} with jti: {}", user.getUsername(), jti);
        return token;
    }
    
    public Claims verifyAccess(String token) {
        logger.debug("Verifying access token");
        
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            // 時刻ずれ許容（±60秒）
            Date now = new Date();
            Date exp = claims.getExpiration();
            if (exp.before(new Date(now.getTime() - 60000))) {
                logger.warn("Access token expired: {}", exp);
                throw new RuntimeException("Token expired");
            }
            
            logger.debug("Access token verified for subject: {}", claims.getSubject());
            return claims;
        } catch (Exception e) {
            logger.warn("Access token verification failed", e);
            throw new RuntimeException("Invalid access token", e);
        }
    }
    
    public Claims verifyRefresh(String token) {
        logger.debug("Verifying refresh token");
        
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            
            // 時刻ずれ許容（±60秒）
            Date now = new Date();
            Date exp = claims.getExpiration();
            if (exp.before(new Date(now.getTime() - 60000))) {
                logger.warn("Refresh token expired: {}", exp);
                throw new RuntimeException("Token expired");
            }
            
            logger.debug("Refresh token verified for subject: {}", claims.getSubject());
            return claims;
        } catch (Exception e) {
            logger.warn("Refresh token verification failed", e);
            throw new RuntimeException("Invalid refresh token", e);
        }
    }
    
    public int getAccessTokenTtlSec() {
        return accessTokenTtlSec;
    }
    
    public int getRefreshTokenTtlSec() {
        return refreshTokenTtlSec;
    }
}
