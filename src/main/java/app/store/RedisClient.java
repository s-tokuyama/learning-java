package app.store;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RedisClient {
    private static final Logger logger = LoggerFactory.getLogger(RedisClient.class);
    
    private final JedisPool jedisPool;
    
    public RedisClient(String host, int port) {
        logger.debug("Initializing Redis connection pool to {}:{}", host, port);
        
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);
        
        this.jedisPool = new JedisPool(poolConfig, host, port);
        
        // 接続テスト
        try (Jedis jedis = jedisPool.getResource()) {
            String pong = jedis.ping();
            logger.info("Redis connection test successful: {}", pong);
        } catch (Exception e) {
            logger.error("Redis connection test failed", e);
            throw new RuntimeException("Failed to connect to Redis", e);
        }
    }
    
    public JedisPool getJedisPool() {
        return jedisPool;
    }
    
    public void close() {
        logger.info("Closing Redis connection pool");
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            logger.info("Redis connection pool closed successfully");
        }
    }
}
