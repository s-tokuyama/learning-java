package com.example;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.StaticHttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final int PORT = 8080;
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;
    
    private static RedisService redisService;
    
    public static void main(String[] args) throws IOException {
        logger.info("Starting Mini Bulletin Board Application");
        logger.info("Configuration - Port: {}, Redis Host: {}, Redis Port: {}", PORT, REDIS_HOST, REDIS_PORT);
        
        try {
            // Redis接続を初期化
            logger.debug("Initializing Redis connection to {}:{}", REDIS_HOST, REDIS_PORT);
            redisService = new RedisService(REDIS_HOST, REDIS_PORT);
            logger.info("Redis connection initialized successfully");
            
            // HTTPサーバーを作成
            logger.debug("Creating HTTP server on port {}", PORT);
            HttpServer server = HttpServer.createSimpleServer(null, PORT);
            
            // 静的ファイルハンドラーを設定
            logger.debug("Setting up static file handler for web resources");
            StaticHttpHandler staticHandler = new StaticHttpHandler("src/main/resources/web/");
            server.getServerConfiguration().addHttpHandler(staticHandler, "/");
            
            // APIエンドポイントを設定
            logger.debug("Setting up API handler");
            server.getServerConfiguration().addHttpHandler(new ApiHandler(redisService), "/api");
            
            // サーバーを開始
            server.start();
            logger.info("Server started successfully on http://localhost:{}", PORT);
            
            // シャットダウンフックを追加
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received, stopping server...");
                try {
                    server.shutdownNow();
                    if (redisService != null) {
                        redisService.close();
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
