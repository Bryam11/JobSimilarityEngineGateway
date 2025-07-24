package com.tecazuay.gateway.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter implements WebFilter {

    // Cache to store rate limiters by IP address
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    // Different rate limits for authentication endpoints (to prevent brute force)
    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String clientIp = getClientIP(exchange);
        String path = exchange.getRequest().getURI().getPath();

        Bucket bucket;

        // Use stricter rate limits for authentication endpoints
        if (path.equals("/api/auth/login")) {
            // Limit login attempts to prevent brute force attacks
            bucket = authBuckets.computeIfAbsent(clientIp, ip -> createLoginRateLimiter());
        } else {
            // Standard rate limit for other endpoints
            bucket = buckets.computeIfAbsent(clientIp, ip -> createDefaultRateLimiter());
        }

        // Try to consume a token from the bucket
        if (bucket.tryConsume(1)) {
            return chain.filter(exchange);
        } else {
            // Too many requests, return 429 status
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }
    }

    private Bucket createDefaultRateLimiter() {
        // Allow 50 requests per minute for general endpoints
        Bandwidth limit = Bandwidth.classic(50, Refill.greedy(50, Duration.ofMinutes(1)));
        return Bucket4j.builder().addLimit(limit).build();
    }

    private Bucket createLoginRateLimiter() {
        // Only allow 5 login attempts per minute to prevent brute force
        Bandwidth limit = Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1)));
        return Bucket4j.builder().addLimit(limit).build();
    }

    private String getClientIP(ServerWebExchange exchange) {
        // Try to get the real client IP behind proxies
        String clientIp = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");

        if (clientIp == null || clientIp.isEmpty()) {
            // If no X-Forwarded-For header, use the direct client IP
            clientIp = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        } else {
            // X-Forwarded-For might contain multiple IPs; use the first one (client's IP)
            clientIp = clientIp.split(",")[0].trim();
        }

        return clientIp;
    }
}
