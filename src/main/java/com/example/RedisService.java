package com.example;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

public class RedisService {
    private static final Logger logger = LoggerFactory.getLogger(RedisService.class);
    private JedisPool jedisPool;
    private ObjectMapper objectMapper;
    private static final String POSTS_KEY = "posts";
    
    public RedisService(String host, int port) {
        logger.debug("Initializing Redis connection pool to {}:{}", host, port);
        
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);
        
        this.jedisPool = new JedisPool(poolConfig, host, port);
        this.objectMapper = new ObjectMapper();
        
        // 接続テスト
        try (Jedis jedis = jedisPool.getResource()) {
            String pong = jedis.ping();
            logger.info("Redis connection test successful: {}", pong);
        } catch (Exception e) {
            logger.error("Redis connection test failed", e);
            throw new RuntimeException("Failed to connect to Redis", e);
        }
    }
    
    public void savePost(Post post) {
        logger.debug("Saving post to Redis: {}", post.getId());
        try (Jedis jedis = jedisPool.getResource()) {
            String postJson = objectMapper.writeValueAsString(post);
            jedis.hset(POSTS_KEY, post.getId(), postJson);
            logger.debug("Post saved successfully: {}", post.getId());
        } catch (Exception e) {
            logger.error("Failed to save post: {}", post.getId(), e);
            throw new RuntimeException("Failed to save post", e);
        }
    }
    
    public List<Post> getAllPosts() {
        logger.debug("Retrieving all posts from Redis");
        try (Jedis jedis = jedisPool.getResource()) {
            List<Post> posts = new ArrayList<>();
            
            // Redisから全ての投稿を取得
            Set<String> postIds = jedis.hkeys(POSTS_KEY);
            logger.debug("Found {} post IDs in Redis", postIds.size());
            
            for (String postId : postIds) {
                String postJson = jedis.hget(POSTS_KEY, postId);
                if (postJson != null) {
                    Post post = objectMapper.readValue(postJson, Post.class);
                    posts.add(post);
                }
            }
            
            // タイムスタンプで降順ソート（新しい順）
            posts.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
            
            logger.debug("Retrieved {} posts from Redis", posts.size());
            return posts;
        } catch (Exception e) {
            logger.error("Failed to get posts from Redis", e);
            throw new RuntimeException("Failed to get posts", e);
        }
    }
    
    public boolean deletePost(String postId) {
        logger.debug("Deleting post from Redis: {}", postId);
        try (Jedis jedis = jedisPool.getResource()) {
            Long deleted = jedis.hdel(POSTS_KEY, postId);
            boolean success = deleted > 0;
            logger.debug("Post deletion result for {}: {}", postId, success);
            return success;
        } catch (Exception e) {
            logger.error("Failed to delete post: {}", postId, e);
            throw new RuntimeException("Failed to delete post", e);
        }
    }
    
    public Post getPost(String postId) {
        logger.debug("Retrieving post from Redis: {}", postId);
        try (Jedis jedis = jedisPool.getResource()) {
            String postJson = jedis.hget(POSTS_KEY, postId);
            if (postJson != null) {
                Post post = objectMapper.readValue(postJson, Post.class);
                logger.debug("Post retrieved successfully: {}", postId);
                return post;
            }
            logger.debug("Post not found: {}", postId);
            return null;
        } catch (Exception e) {
            logger.error("Failed to get post: {}", postId, e);
            throw new RuntimeException("Failed to get post", e);
        }
    }
    
    public void close() {
        logger.info("Closing Redis connection pool");
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            logger.info("Redis connection pool closed successfully");
        }
    }
}
