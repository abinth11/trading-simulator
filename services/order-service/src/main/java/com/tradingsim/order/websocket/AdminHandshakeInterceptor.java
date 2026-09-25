package com.tradingsim.order.websocket;

import com.tradingsim.order.context.JwtVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Requires an ADMIN access token to open an admin WebSocket.
 * Browsers can't set headers on a WebSocket, so the token comes as the ?token= query parameter.
 */
@Component
@RequiredArgsConstructor
public class AdminHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtVerifier jwtVerifier;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            jwtVerifier.verifyAdmin(token);
            return true;
        } catch (ResponseStatusException e) {
            response.setStatusCode(e.getStatusCode());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
