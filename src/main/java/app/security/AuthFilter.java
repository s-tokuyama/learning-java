package app.security;

import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.HttpStatus;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class AuthFilter extends HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(AuthFilter.class);
    
    private final JwtService jwtService;
    private final HttpHandler nextHandler;
    
    // 公開パス（認証不要）
    private static final Set<String> PUBLIC_PATHS = Set.of(
        "/api/auth/signup",
        "/api/auth/signin",
        "/api/auth/refresh",
        "/api/posts" // GET /api/posts は公開
    );
    
    public AuthFilter(JwtService jwtService, HttpHandler nextHandler) {
        this.jwtService = jwtService;
        this.nextHandler = nextHandler;
    }
    
    @Override
    public void service(Request request, Response response) throws Exception {
        String path = request.getRequestURI();
        String method = request.getMethod().getMethodString();
        
        logger.debug("AuthFilter processing: {} {}", method, path);
        
        // 公開パスのチェック
        if (isPublicPath(method, path)) {
            logger.debug("Public path, skipping authentication: {} {}", method, path);
            nextHandler.service(request, response);
            return;
        }
        
        // Authorizationヘッダーをチェック
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("Missing or invalid Authorization header for: {} {}", method, path);
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\": \"token_expired\"}");
            return;
        }
        
        String token = authHeader.substring(7); // "Bearer " を除去
        
        try {
            // アクセストークンを検証
            Claims claims = jwtService.verifyAccess(token);
            
            // リクエストに認証情報を設定
            request.setAttribute("auth", claims);
            request.setAttribute("userId", claims.getSubject());
            request.setAttribute("username", claims.get("username"));
            request.setAttribute("roles", claims.get("roles"));
            
            logger.debug("Authentication successful for user: {} on path: {}", 
                        claims.get("username"), path);
            
            // 次のハンドラーに処理を委譲
            nextHandler.service(request, response);
            
        } catch (Exception e) {
            logger.warn("Authentication failed for: {} {} - {}", method, path, e.getMessage());
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\": \"token_expired\"}");
        }
    }
    
    private boolean isPublicPath(String method, String path) {
        // 認証APIは全て公開
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        
        // GET /api/posts は公開
        if ("GET".equals(method) && "/api/posts".equals(path)) {
            return true;
        }
        
        return false;
    }
}
