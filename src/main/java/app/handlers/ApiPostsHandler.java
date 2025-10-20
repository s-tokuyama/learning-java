package app.handlers;

import app.store.Post;
import app.store.PostRepo;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.util.HttpStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

public class ApiPostsHandler extends HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiPostsHandler.class);
    
    private final PostRepo postRepo;
    private final ObjectMapper objectMapper;
    
    public ApiPostsHandler(PostRepo postRepo) {
        this.postRepo = postRepo;
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public void service(Request request, Response response) throws Exception {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        
        String path = request.getRequestURI();
        Method method = request.getMethod();
        
        logger.debug("Posts API request: {} {}", method, path);
        
        // CORSヘッダーを設定
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        
        if (method == Method.OPTIONS) {
            response.setStatus(HttpStatus.OK_200);
            return;
        }
        
        try {
            if (path.equals("/api/posts")) {
                if (method == Method.GET) {
                    handleGetPosts(request, response);
                } else if (method == Method.POST) {
                    handlePostMessage(request, response);
                } else {
                    response.setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
                }
            } else if (path.startsWith("/api/posts/")) {
                if (method == Method.DELETE) {
                    String id = path.substring("/api/posts/".length());
                    handleDeletePost(request, response, id);
                } else {
                    response.setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
                }
            } else {
                response.setStatus(HttpStatus.NOT_FOUND_404);
            }
        } catch (Exception e) {
            logger.error("Error handling posts request: {} {}", method, path, e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\": \"Internal server error\"}");
        }
    }
    
    private void handleGetPosts(Request request, Response response) throws Exception {
        logger.debug("Handling GET /api/posts request");
        
        var posts = postRepo.findAll();
        String json = objectMapper.writeValueAsString(posts);
        
        logger.debug("Retrieved {} posts from Redis", posts.size());
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write(json);
    }
    
    private void handlePostMessage(Request request, Response response) throws Exception {
        logger.debug("Handling POST /api/posts request");
        
        // AuthFilterで設定された認証情報を取得
        var claims = (io.jsonwebtoken.Claims) request.getAttribute("auth");
        if (claims == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\": \"Authentication required\"}");
            return;
        }
        
        String userId = claims.getSubject();
        String username = (String) claims.get("username");
        
        Map<String, Object> body = parseJsonBody(request);
        String message = (String) body.get("message");
        
        if (message == null || message.trim().isEmpty()) {
            logger.warn("POST request received with empty message from user: {}", username);
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\": \"Message is required\"}");
            return;
        }
        
        String trimmedMessage = message.trim();
        logger.info("Creating new post with message length: {} from user: {}", trimmedMessage.length(), username);
        
        Post post = new Post(UUID.randomUUID().toString(), trimmedMessage, System.currentTimeMillis(), userId);
        postRepo.save(post);
        
        logger.info("Post created successfully with ID: {} by user: {}", post.getId(), username);
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(post));
    }
    
    private void handleDeletePost(Request request, Response response, String id) throws Exception {
        logger.debug("Handling DELETE /api/posts/{} request", id);
        
        // AuthFilterで設定された認証情報を取得
        var claims = (io.jsonwebtoken.Claims) request.getAttribute("auth");
        if (claims == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\": \"Authentication required\"}");
            return;
        }
        
        String username = (String) claims.get("username");
        @SuppressWarnings("unchecked")
        Set<String> roles = (Set<String>) claims.get("roles");
        
        // admin権限チェック
        if (!roles.contains("admin")) {
            logger.warn("DELETE request denied for non-admin user: {}", username);
            response.setStatus(HttpStatus.FORBIDDEN_403);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\": \"Admin privileges required\"}");
            return;
        }
        
        boolean deleted = postRepo.delete(id);
        if (deleted) {
            logger.info("Post deleted successfully: {} by admin: {}", id, username);
            response.setStatus(HttpStatus.OK_200);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"success\": true}");
        } else {
            logger.warn("Post not found for deletion: {} by admin: {}", id, username);
            response.setStatus(HttpStatus.NOT_FOUND_404);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\": \"Post not found\"}");
        }
    }
    
    private Map<String, Object> parseJsonBody(Request request) throws Exception {
        StringBuilder jsonBuilder = new StringBuilder();
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(request.getInputStream(), "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            jsonBuilder.append(line);
        }
        
        String jsonBody = jsonBuilder.toString();
        logger.debug("JSON body: {}", jsonBody);
        
        @SuppressWarnings("unchecked")
        Map<String, Object> jsonMap = objectMapper.readValue(jsonBody, Map.class);
        return jsonMap;
    }
}
