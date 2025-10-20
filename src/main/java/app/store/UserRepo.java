package app.store;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.HashSet;

public class UserRepo {
    private static final Logger logger = LoggerFactory.getLogger(UserRepo.class);
    
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    
    public UserRepo(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        this.objectMapper = new ObjectMapper();
    }
    
    public void save(User user) {
        logger.debug("Saving user to Redis: {}", user.getUsername());
        
        try (Jedis jedis = jedisPool.getResource()) {
            String userKey = "user:" + user.getId();
            String usernameKey = "user:byname:" + user.getUsername();
            
            // ユーザー情報を保存
            jedis.hset(userKey, "id", user.getId());
            jedis.hset(userKey, "username", user.getUsername());
            jedis.hset(userKey, "email", user.getEmail());
            jedis.hset(userKey, "pass_hash", user.getPassHash());
            
            // ロールを保存
            for (String role : user.getRoles()) {
                jedis.sadd(userKey + ":roles", role);
            }
            
            // ユーザー名からIDへのマッピングを保存
            jedis.set(usernameKey, user.getId());
            
            logger.debug("User saved successfully: {}", user.getUsername());
        } catch (Exception e) {
            logger.error("Failed to save user: {}", user.getUsername(), e);
            throw new RuntimeException("Failed to save user", e);
        }
    }
    
    public User findById(String userId) {
        logger.debug("Finding user by ID: {}", userId);
        
        try (Jedis jedis = jedisPool.getResource()) {
            String userKey = "user:" + userId;
            
            if (!jedis.exists(userKey)) {
                logger.debug("User not found: {}", userId);
                return null;
            }
            
            User user = new User();
            user.setId(jedis.hget(userKey, "id"));
            user.setUsername(jedis.hget(userKey, "username"));
            user.setEmail(jedis.hget(userKey, "email"));
            user.setPassHash(jedis.hget(userKey, "pass_hash"));
            
            // ロールを取得
            Set<String> roles = jedis.smembers(userKey + ":roles");
            user.setRoles(roles);
            
            logger.debug("User found: {}", user.getUsername());
            return user;
        } catch (Exception e) {
            logger.error("Failed to find user by ID: {}", userId, e);
            throw new RuntimeException("Failed to find user", e);
        }
    }
    
    public User findByUsername(String username) {
        logger.debug("Finding user by username: {}", username);
        
        try (Jedis jedis = jedisPool.getResource()) {
            String usernameKey = "user:byname:" + username;
            String userId = jedis.get(usernameKey);
            
            if (userId == null) {
                logger.debug("User not found by username: {}", username);
                return null;
            }
            
            return findById(userId);
        } catch (Exception e) {
            logger.error("Failed to find user by username: {}", username, e);
            throw new RuntimeException("Failed to find user", e);
        }
    }
    
    public boolean existsByUsername(String username) {
        logger.debug("Checking if user exists by username: {}", username);
        
        try (Jedis jedis = jedisPool.getResource()) {
            String usernameKey = "user:byname:" + username;
            boolean exists = jedis.exists(usernameKey);
            logger.debug("User exists by username {}: {}", username, exists);
            return exists;
        } catch (Exception e) {
            logger.error("Failed to check user existence: {}", username, e);
            throw new RuntimeException("Failed to check user existence", e);
        }
    }
    
    public boolean existsByEmail(String email) {
        logger.debug("Checking if user exists by email: {}", email);
        
        try (Jedis jedis = jedisPool.getResource()) {
            // 全てのユーザーをチェック（効率は良くないが、シンプルな実装）
            Set<String> userKeys = jedis.keys("user:*");
            for (String userKey : userKeys) {
                if (userKey.endsWith(":roles")) {
                    continue; // ロールキーはスキップ
                }
                
                String storedEmail = jedis.hget(userKey, "email");
                if (email.equals(storedEmail)) {
                    logger.debug("User exists by email: {}", email);
                    return true;
                }
            }
            
            logger.debug("User does not exist by email: {}", email);
            return false;
        } catch (Exception e) {
            logger.error("Failed to check user existence by email: {}", email, e);
            throw new RuntimeException("Failed to check user existence", e);
        }
    }
}
