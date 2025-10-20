package app.handlers;

import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.server.StaticHttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StaticHandler extends HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(StaticHandler.class);
    
    private final StaticHttpHandler staticHttpHandler;
    
    public StaticHandler(String docRoot) {
        this.staticHttpHandler = new StaticHttpHandler(docRoot);
    }
    
    @Override
    public void service(Request request, Response response) throws Exception {
        logger.debug("Static file request: {}", request.getRequestURI());
        
        // 静的ファイルを配信
        staticHttpHandler.service(request, response);
    }
}
