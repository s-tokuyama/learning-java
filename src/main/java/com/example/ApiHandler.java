package com.example;

import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.util.HttpStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import java.util.Map;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ApiHandler extends HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiHandler.class);
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    
    public ApiHandler(RedisService redisService) {
        this.redisService = redisService;
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public void service(Request request, Response response) throws Exception {
        // 文字エンコーディングを明示的に設定
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        
        String path = request.getRequestURI();
        Method method = request.getMethod();
        String clientIP = request.getRemoteAddr();
        
        logger.debug("API request received: {} {} from {}", method, path, clientIP);
        
        // 開発モードでリクエスト詳細をログ出力
        boolean isDevMode = "dev".equals(System.getProperty("spring.profiles.active")) || 
                           "true".equals(System.getProperty("dev.mode"));
        
        if (isDevMode) {
            logger.info("=== DETAILED REQUEST INFO ===");
            logger.info("Method: {}", request.getMethod());
            logger.info("Path: {}", request.getRequestURI());
            logger.info("Client IP: {}", request.getRemoteAddr());
            logger.info("Content-Type: {}", request.getContentType());
        } else {
            // 本番モードでは簡易ログのみ
            logger.info("Request: {} {}", request.getMethod(), request.getRequestURI());
        }
        
        // CORSヘッダーを設定
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        
        if (method == Method.OPTIONS) {
            logger.debug("Handling OPTIONS request for {}", path);
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
                    logger.warn("Method not allowed: {} for path {}", method, path);
                    response.setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
                }
            } else if (path.startsWith("/api/posts/")) {
                if (method == Method.DELETE) {
                    String id = path.substring("/api/posts/".length());
                    handleDeletePost(request, response, id);
                } else {
                    logger.warn("Method not allowed: {} for path {}", method, path);
                    response.setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
                }
            } else {
                logger.warn("Path not found: {}", path);
                response.setStatus(HttpStatus.NOT_FOUND_404);
            }
        } catch (Exception e) {
            logger.error("Error handling API request: {} {}", method, path, e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            response.getWriter().write("{\"error\": \"Internal server error\"}");
        }
    }
    
    private void handleGetPosts(Request request, Response response) throws Exception {
        logger.debug("Handling GET /api/posts request");
        var posts = redisService.getAllPosts();
        String json = objectMapper.writeValueAsString(posts);
        
        logger.debug("Retrieved {} posts from Redis", posts.size());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json);
    }
    
    private void handlePostMessage(Request request, Response response) throws Exception {
        logger.debug("Handling POST /api/posts request");
        
        // デバッグ用：リクエストの詳細確認
        logger.info("Content-Type: {}", request.getContentType());
        logger.info("Content-Length: {}", request.getContentLength());
        
        String message = null;
        
        // Content-Typeに応じて適切にメッセージを取得
        String contentType = request.getContentType();
        if (contentType != null && contentType.contains("application/json")) {
            // JSONリクエストボディから取得
            message = getMessageFromJsonBody(request);
            logger.info("Retrieved message from JSON body: '{}'", message);
        } else {
            // フォームパラメータから取得
            Map<String, String[]> parameterMap = request.getParameterMap();
            logger.info("Parameter map size: {}", parameterMap.size());
            for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
                logger.info("Parameter '{}': [{}]", entry.getKey(), String.join(", ", entry.getValue()));
            }
            
            message = request.getParameter("message");
            logger.info("Retrieved message parameter: '{}'", message);
        }
        
        if (message == null || message.trim().isEmpty()) {
            logger.warn("POST request received with empty message");
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.getWriter().write("{\"error\": \"Message is required\"}");
            return;
        }
        
        String trimmedMessage = message.trim();
        logger.info("Creating new post with message length: {}", trimmedMessage.length());
        
        Post post = new Post(UUID.randomUUID().toString(), trimmedMessage, System.currentTimeMillis());
        redisService.savePost(post);
        
        logger.info("Post created successfully with ID: {}", post.getId());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(post));
    }
    
    private void handleDeletePost(Request request, Response response, String id) throws Exception {
        logger.debug("Handling DELETE /api/posts/{} request", id);
        
        boolean deleted = redisService.deletePost(id);
        if (deleted) {
            logger.info("Post deleted successfully: {}", id);
            response.setStatus(HttpStatus.OK_200);
            response.getWriter().write("{\"success\": true}");
        } else {
            logger.warn("Post not found for deletion: {}", id);
            response.setStatus(HttpStatus.NOT_FOUND_404);
            response.getWriter().write("{\"error\": \"Post not found\"}");
        }
    }
    
    /**
     * JSONリクエストボディからメッセージを取得
     */
    private String getMessageFromJsonBody(Request request) throws Exception {
        try {
            // リクエストボディを読み取り（UTF-8で明示的に設定）
            StringBuilder jsonBuilder = new StringBuilder();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(request.getInputStream(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
            
            String jsonBody = jsonBuilder.toString();
            logger.debug("JSON body: {}", jsonBody);
            
            // JSONをパースしてメッセージを取得
            @SuppressWarnings("unchecked")
            Map<String, Object> jsonMap = objectMapper.readValue(jsonBody, Map.class);
            return (String) jsonMap.get("message");
            
        } catch (Exception e) {
            logger.error("Failed to parse JSON body", e);
            throw new RuntimeException("Invalid JSON format", e);
        }
    }
}
