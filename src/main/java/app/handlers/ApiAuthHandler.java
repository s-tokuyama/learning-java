package app.handlers;

import app.security.JwtService;
import app.security.RefreshService;
import app.store.User;
import app.store.UserRepo;
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
import org.mindrot.jbcrypt.BCrypt;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class ApiAuthHandler extends HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiAuthHandler.class);
    
    private final JwtService jwtService;
    private final RefreshService refreshService;
    private final UserRepo userRepo;
    private final ObjectMapper objectMapper;
    
    public ApiAuthHandler(JwtService jwtService, RefreshService refreshService, UserRepo userRepo) {
        this.jwtService = jwtService;
        this.refreshService = refreshService;
        this.userRepo = userRepo;
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public void service(Request request, Response response) throws Exception {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        
        String path = request.getRequestURI();
        Method method = request.getMethod();
        
        logger.debug("Auth API request: {} {}", method, path);
        
        // CORSヘッダーを設定
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        
        if (method == Method.OPTIONS) {
            response.setStatus(HttpStatus.OK_200);
            return;
        }
        
        try {
            if (path.equals("/api/auth/signup")) {
                if (method == Method.POST) {
                    handleSignup(request, response);
                } else {
                    response.setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
                }
            } else if (path.equals("/api/auth/signin")) {
                if (method == Method.POST) {
                    handleSignin(request, response);
                } else {
                    response.setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
                }
            } else if (path.equals("/api/auth/refresh")) {
                if (method == Method.POST) {
                    handleRefresh(request, response);
                } else {
                    response.setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
                }
            } else if (path.equals("/api/auth/signout")) {
                if (method == Method.POST) {
                    handleSignout(request, response);
                } else {
                    response.setStatus(HttpStatus.METHOD_NOT_ALLOWED_405);
                }
            } else {
                response.setStatus(HttpStatus.NOT_FOUND_404);
            }
        } catch (Exception e) {
            logger.error("Error handling auth request: {} {}", method, path, e);
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\": \"Internal server error\"}");
        }
    }
    
    private void handleSignup(Request request, Response response) throws Exception {
        logger.debug("Handling signup request");
        
        Map<String, Object> body = parseJsonBody(request);
        String username = (String) body.get("username");
        String email = (String) body.get("email");
        String password = (String) body.get("password");
        
        if (username == null || email == null || password == null) {
            logger.warn("Signup request missing required fields");
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\": \"Username, email, and password are required\"}");
            return;
        }
        
        // ユーザー名とメールアドレスの重複チェック
        if (userRepo.existsByUsername(username)) {
            logger.warn("Signup failed: username already exists: {}", username);
            response.setStatus(HttpStatus.CONFLICT_409);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\": \"Username already exists\"}");
            return;
        }
        
        if (userRepo.existsByEmail(email)) {
            logger.warn("Signup failed: email already exists: {}", email);
            response.setStatus(HttpStatus.CONFLICT_409);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\": \"Email already exists\"}");
            return;
        }
        
        // パスワードをハッシュ化
        String passHash = BCrypt.hashpw(password, BCrypt.gensalt());
        
        // ユーザーを作成
        User user = new User(UUID.randomUUID().toString(), username, email, passHash);
        userRepo.save(user);
        
        logger.info("User created successfully: {}", username);
        response.setStatus(HttpStatus.CREATED_201);
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write("{\"message\": \"User created successfully\"}");
    }
    
    private void handleSignin(Request request, Response response) throws Exception {
        logger.debug("Handling signin request");
        
        Map<String, Object> body = parseJsonBody(request);
        String username = (String) body.get("username");
        String password = (String) body.get("password");
        
        if (username == null || password == null) {
            logger.warn("Signin request missing required fields");
            response.setStatus(HttpStatus.BAD_REQUEST_400);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\": \"Username and password are required\"}");
            return;
        }
        
        // ユーザーを検索
        User user = userRepo.findByUsername(username);
        if (user == null) {
            logger.warn("Signin failed: user not found: {}", username);
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\": \"Invalid credentials\"}");
            return;
        }
        
        // パスワードを検証
        if (!BCrypt.checkpw(password, user.getPassHash())) {
            logger.warn("Signin failed: invalid password for user: {}", username);
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\": \"Invalid credentials\"}");
            return;
        }
        
        // トークンを発行
        String accessToken = jwtService.issueAccess(user);
        String refreshToken = jwtService.issueRefresh(user);
        
        // リフレッシュトークンをRedisに登録
        try (Jedis jedis = refreshService.getJedisPool().getResource()) {
            String jti = jwtService.verifyRefresh(refreshToken).getId();
            String activeKey = "rt:active:" + jti;
            jedis.setex(activeKey, jwtService.getRefreshTokenTtlSec(), user.getId());
        }
        
        // アクセストークンをレスポンスボディに、リフレッシュトークンをCookieに設定
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Set-Cookie", 
            "refreshToken=" + refreshToken + 
            "; HttpOnly; Secure; SameSite=Lax; Max-Age=" + jwtService.getRefreshTokenTtlSec());
        
        String responseBody = "{\"accessToken\": \"" + accessToken + "\"}";
        response.getWriter().write(responseBody);
        
        logger.info("User signed in successfully: {}", username);
    }
    
    private void handleRefresh(Request request, Response response) throws Exception {
        logger.debug("Handling refresh request");
        
        // Cookieからリフレッシュトークンを取得
        String refreshToken = null;
        String cookieHeader = request.getHeader("Cookie");
        if (cookieHeader != null) {
            String[] cookies = cookieHeader.split(";");
            for (String cookie : cookies) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2 && "refreshToken".equals(parts[0])) {
                    refreshToken = parts[1];
                    break;
                }
            }
        }
        
        if (refreshToken == null) {
            logger.warn("Refresh request missing refresh token cookie");
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\": \"Refresh token required\"}");
            return;
        }
        
        try {
            // リフレッシュトークンをローテーション
            RefreshService.RefreshResult refreshResult = refreshService.rotate(refreshToken);
            
            // 新しいリフレッシュトークンをCookieに設定
            response.setHeader("Set-Cookie", 
                "refreshToken=" + refreshResult.getRefreshToken() + 
                "; HttpOnly; Secure; SameSite=Lax; Max-Age=" + jwtService.getRefreshTokenTtlSec());
            
            // 新しいアクセストークンを返却
            response.setContentType("application/json; charset=UTF-8");
            String responseBody = "{\"accessToken\": \"" + refreshResult.getAccessToken() + "\"}";
            response.getWriter().write(responseBody);
            
            logger.info("Token refreshed successfully");
        } catch (Exception e) {
            logger.warn("Refresh failed: {}", e.getMessage());
            response.setStatus(HttpStatus.UNAUTHORIZED_401);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"error\": \"Invalid refresh token\"}");
        }
    }
    
    private void handleSignout(Request request, Response response) throws Exception {
        logger.debug("Handling signout request");
        
        // Cookieからリフレッシュトークンを取得
        String refreshToken = null;
        String cookieHeader = request.getHeader("Cookie");
        if (cookieHeader != null) {
            String[] cookies = cookieHeader.split(";");
            for (String cookie : cookies) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2 && "refreshToken".equals(parts[0])) {
                    refreshToken = parts[1];
                    break;
                }
            }
        }
        
        if (refreshToken != null) {
            try {
                // リフレッシュトークンを検証してJTIを取得
                var claims = jwtService.verifyRefresh(refreshToken);
                String jti = claims.getId();
                
                // リフレッシュトークンを失効
                refreshService.revokeRefreshToken(jti);
                logger.info("Refresh token revoked: {}", jti);
            } catch (Exception e) {
                logger.warn("Failed to revoke refresh token: {}", e.getMessage());
            }
        }
        
        // Cookieをクリア
        response.setHeader("Set-Cookie", "refreshToken=; HttpOnly; Secure; SameSite=Lax; Max-Age=0");
        
        response.setStatus(HttpStatus.OK_200);
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write("{\"message\": \"Signed out successfully\"}");
        
        logger.info("User signed out successfully");
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
