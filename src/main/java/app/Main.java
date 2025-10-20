package app;

import app.handlers.StaticHandler;
import app.handlers.ApiAuthHandler;
import app.handlers.ApiPostsHandler;
import app.security.JwtService;
import app.security.RefreshService;
import app.security.AuthFilter;
import app.store.RedisClient;
import app.store.UserRepo;
import app.store.PostRepo;
import org.glassfish.grizzly.http.server.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final int PORT = 8080;
    
    // 環境変数から設定を取得
    private static final String REDIS_HOST = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    private static final int REDIS_PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
    
    private static RedisClient redisClient;
    private static JwtService jwtService;
    private static RefreshService refreshService;
    private static UserRepo userRepo;
    private static PostRepo postRepo;
    
    public static void main(String[] args) throws IOException {
        logger.info("Starting Mini Bulletin Board Application with JWT Authentication");
        logger.info("Configuration - Port: {}, Redis Host: {}, Redis Port: {}", PORT, REDIS_HOST, REDIS_PORT);
        
        try {
            // Redis接続を初期化
            logger.debug("Initializing Redis connection to {}:{}", REDIS_HOST, REDIS_PORT);
            redisClient = new RedisClient(REDIS_HOST, REDIS_PORT);
            logger.info("Redis connection initialized successfully");
            
            // サービスとリポジトリを初期化
            jwtService = new JwtService();
            userRepo = new UserRepo(redisClient.getJedisPool());
            postRepo = new PostRepo(redisClient.getJedisPool());
            refreshService = new RefreshService(jwtService, userRepo, redisClient.getJedisPool());
            
            logger.info("Services and repositories initialized successfully");
            
            // HTTPサーバーを作成
            logger.debug("Creating HTTP server on port {}", PORT);
            HttpServer server = HttpServer.createSimpleServer(null, PORT);
            
            // 静的ファイルハンドラーを設定
            logger.debug("Setting up static file handler for web resources");
            server.getServerConfiguration().addHttpHandler(new StaticHandler("src/main/resources/web/"), "/");
            
            // 認証APIエンドポイントを設定
            logger.debug("Setting up auth API handler");
            server.getServerConfiguration().addHttpHandler(new ApiAuthHandler(jwtService, refreshService, userRepo), "/api/auth");
            
            // 投稿APIエンドポイントを設定（認証フィルター付き）
            logger.debug("Setting up posts API handler with auth filter");
            ApiPostsHandler postsHandler = new ApiPostsHandler(postRepo);
            AuthFilter authFilter = new AuthFilter(jwtService, postsHandler);
            server.getServerConfiguration().addHttpHandler(authFilter, "/api/posts");
            
            // サーバーを開始
            server.start();
            logger.info("Server started successfully on http://localhost:{}", PORT);
            logger.info("Available endpoints:");
            logger.info("  - Static files: http://localhost:{}/", PORT);
            logger.info("  - Auth API: http://localhost:{}/api/auth/*", PORT);
            logger.info("  - Posts API: http://localhost:{}/api/posts/*", PORT);
            
            // シャットダウンフックを追加
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received, stopping server...");
                try {
                    server.shutdownNow();
                    if (redisClient != null) {
                        redisClient.close();
                    }
                    logger.info("Server stopped successfully");
                } catch (Exception e) {
                    logger.error("Error during shutdown", e);
                }
            }));
            
            // サーバーが停止するまで待機
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                logger.warn("Server interrupted: {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            logger.error("Failed to start application", e);
            throw e;
        }
    }
}
