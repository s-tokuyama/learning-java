package app.security;

import app.store.User;
import app.store.UserRepo;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class RefreshService {
    private static final Logger logger = LoggerFactory.getLogger(RefreshService.class);
    
    private final JwtService jwtService;
    private final UserRepo userRepo;
    private final JedisPool jedisPool;
    
    public RefreshService(JwtService jwtService, UserRepo userRepo, JedisPool jedisPool) {
        this.jwtService = jwtService;
        this.userRepo = userRepo;
        this.jedisPool = jedisPool;
    }
    
    public RefreshResult rotate(String refreshToken) {
        logger.debug("Starting refresh token rotation");
        
        try (Jedis jedis = jedisPool.getResource()) {
            // リフレッシュトークンを検証
            Claims claims = jwtService.verifyRefresh(refreshToken);
            String jti = claims.getId();
            String userId = claims.getSubject();
            
            logger.debug("Refresh token verified for user: {} with jti: {}", userId, jti);
            
            // アクティブなリフレッシュトークンかチェック
            String activeKey = "rt:active:" + jti;
            String storedUserId = jedis.get(activeKey);
            
            if (storedUserId == null || !storedUserId.equals(userId)) {
                logger.warn("Refresh token not found in active list or user mismatch: {}", jti);
                throw new RuntimeException("Invalid refresh token");
            }
            
            // ユーザー情報を取得
            User user = userRepo.findById(userId);
            if (user == null) {
                logger.warn("User not found: {}", userId);
                throw new RuntimeException("User not found");
            }
            
            // 旧リフレッシュトークンを失効（ブラックリストに移動）
            String blackKey = "rt:black:" + jti;
            jedis.setex(blackKey, jwtService.getRefreshTokenTtlSec(), "1");
            jedis.del(activeKey);
            
            logger.debug("Old refresh token blacklisted: {}", jti);
            
            // 新しいトークンペアを発行
            String newAccessToken = jwtService.issueAccess(user);
            String newRefreshToken = jwtService.issueRefresh(user);
            
            // 新しいリフレッシュトークンのJTIを取得
            Claims newRefreshClaims = jwtService.verifyRefresh(newRefreshToken);
            String newJti = newRefreshClaims.getId();
            
            // 新しいリフレッシュトークンをアクティブリストに登録
            String newActiveKey = "rt:active:" + newJti;
            jedis.setex(newActiveKey, jwtService.getRefreshTokenTtlSec(), userId);
            
            logger.info("Refresh token rotated successfully for user: {}", userId);
            
            return new RefreshResult(newAccessToken, newRefreshToken, newJti);
            
        } catch (Exception e) {
            logger.error("Refresh token rotation failed", e);
            throw new RuntimeException("Refresh token rotation failed", e);
        }
    }
    
    public JedisPool getJedisPool() {
        return jedisPool;
    }
    
    public void revokeRefreshToken(String jti) {
        logger.debug("Revoking refresh token: {}", jti);
        
        try (Jedis jedis = jedisPool.getResource()) {
            String activeKey = "rt:active:" + jti;
            String blackKey = "rt:black:" + jti;
            
            // アクティブリストから削除してブラックリストに移動
            String userId = jedis.get(activeKey);
            if (userId != null) {
                jedis.del(activeKey);
                jedis.setex(blackKey, jwtService.getRefreshTokenTtlSec(), "1");
                logger.info("Refresh token revoked: {} for user: {}", jti, userId);
            } else {
                logger.warn("Refresh token not found in active list: {}", jti);
            }
        } catch (Exception e) {
            logger.error("Failed to revoke refresh token: {}", jti, e);
            throw new RuntimeException("Failed to revoke refresh token", e);
        }
    }
    
    public static class RefreshResult {
        private final String accessToken;
        private final String refreshToken;
        private final String refreshJti;
        
        public RefreshResult(String accessToken, String refreshToken, String refreshJti) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.refreshJti = refreshJti;
        }
        
        public String getAccessToken() {
            return accessToken;
        }
        
        public String getRefreshToken() {
            return refreshToken;
        }
        
        public String getRefreshJti() {
            return refreshJti;
        }
    }
}
