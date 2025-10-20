package app.store;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;

public class PostRepo {
    private static final Logger logger = LoggerFactory.getLogger(PostRepo.class);
    
    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private static final String POSTS_KEY = "posts";
    private static final String POSTS_ZSET_KEY = "posts_zset";
    
    public PostRepo(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
        this.objectMapper = new ObjectMapper();
    }
    
    public void save(Post post) {
        logger.debug("Saving post to Redis: {}", post.getId());
        
        try (Jedis jedis = jedisPool.getResource()) {
            String postKey = "post:" + post.getId();
            
            // 投稿情報を保存
            jedis.hset(postKey, "id", post.getId());
            jedis.hset(postKey, "message", post.getMessage());
            jedis.hset(postKey, "created", String.valueOf(post.getCreated()));
            jedis.hset(postKey, "userId", post.getUserId());
            
            // ソート済みセットに追加（作成時刻でソート）
            jedis.zadd(POSTS_ZSET_KEY, post.getCreated(), post.getId());
            
            logger.debug("Post saved successfully: {}", post.getId());
        } catch (Exception e) {
            logger.error("Failed to save post: {}", post.getId(), e);
            throw new RuntimeException("Failed to save post", e);
        }
    }
    
    public List<Post> findAll() {
        logger.debug("Retrieving all posts from Redis");
        
        try (Jedis jedis = jedisPool.getResource()) {
            List<Post> posts = new ArrayList<>();
            
            // ソート済みセットから投稿IDを取得（降順）
            java.util.List<String> postIds = jedis.zrevrange(POSTS_ZSET_KEY, 0, -1);
            logger.debug("Found {} post IDs in Redis", postIds.size());
            
            for (String postId : postIds) {
                Post post = findById(postId);
                if (post != null) {
                    posts.add(post);
                }
            }
            
            logger.debug("Retrieved {} posts from Redis", posts.size());
            return posts;
        } catch (Exception e) {
            logger.error("Failed to get posts from Redis", e);
            throw new RuntimeException("Failed to get posts", e);
        }
    }
    
    public Post findById(String postId) {
        logger.debug("Retrieving post from Redis: {}", postId);
        
        try (Jedis jedis = jedisPool.getResource()) {
            String postKey = "post:" + postId;
            
            if (!jedis.exists(postKey)) {
                logger.debug("Post not found: {}", postId);
                return null;
            }
            
            Post post = new Post();
            post.setId(jedis.hget(postKey, "id"));
            post.setMessage(jedis.hget(postKey, "message"));
            post.setCreated(Long.parseLong(jedis.hget(postKey, "created")));
            post.setUserId(jedis.hget(postKey, "userId"));
            
            logger.debug("Post retrieved successfully: {}", postId);
            return post;
        } catch (Exception e) {
            logger.error("Failed to get post: {}", postId, e);
            throw new RuntimeException("Failed to get post", e);
        }
    }
    
    public boolean delete(String postId) {
        logger.debug("Deleting post from Redis: {}", postId);
        
        try (Jedis jedis = jedisPool.getResource()) {
            String postKey = "post:" + postId;
            
            if (!jedis.exists(postKey)) {
                logger.debug("Post not found for deletion: {}", postId);
                return false;
            }
            
            // 投稿データを削除
            jedis.del(postKey);
            
            // ソート済みセットからも削除
            jedis.zrem(POSTS_ZSET_KEY, postId);
            
            logger.debug("Post deleted successfully: {}", postId);
            return true;
        } catch (Exception e) {
            logger.error("Failed to delete post: {}", postId, e);
            throw new RuntimeException("Failed to delete post", e);
        }
    }
}
