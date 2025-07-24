package com.tecazuay.gateway.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Order(-1) // Alta prioridad para ejecutar antes que otros filtros
public class RequestResponseLoggingFilter implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // Generar ID de traza único
        String traceId = UUID.randomUUID().toString();
        exchange.getAttributes().put("traceId", traceId);

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();
        String clientIP = request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress().getHostAddress() : "unknown";

        // Registrar inicio de la solicitud con cabeceras
        logger.info("Traza [{}] - Iniciando {} {} desde IP: {}", traceId, method, path, clientIP);
        logger.debug("Traza [{}] - Cabeceras de Request: {}", traceId, request.getHeaders());

        // Capturar tiempo de inicio
        Instant start = Instant.now();

        // Crear decoradores para capturar el contenido
        ServerHttpRequestDecorator requestDecorator = ContentCaptureUtil.createRequestDecorator(request, traceId);
        ServerHttpResponseDecorator responseDecorator = ContentCaptureUtil.createResponseDecorator(exchange.getResponse(), traceId);

        // Crear un nuevo exchange con los decoradores
        ServerWebExchange decoratedExchange = exchange.mutate()
                .request(requestDecorator)
                .response(responseDecorator)
                .build();

        // Decorar el exchange para capturar y registrar la respuesta
        return chain.filter(decoratedExchange)
                .doOnSuccess(aVoid -> logResponse(decoratedExchange, start, traceId, true))
                .doOnError(throwable -> {
                    logResponse(decoratedExchange, start, traceId, false);
                    logger.error("Traza [{}] - Error procesando la solicitud: {}", traceId, throwable.getMessage(), throwable);
                });
    }

    private void logResponse(ServerWebExchange exchange, Instant start, String traceId, boolean success) {
        Duration duration = Duration.between(start, Instant.now());
        long timeElapsed = duration.toMillis();

        int statusCode = exchange.getResponse().getStatusCode() != null ?
                         exchange.getResponse().getStatusCode().value() : 0;

        if (success) {
            logger.info("Traza [{}] - Completada {} {} con status: {} en {}ms",
                    traceId,
                    exchange.getRequest().getMethod().name(),
                    exchange.getRequest().getURI().getPath(),
                    statusCode,
                    timeElapsed);

            logger.debug("Traza [{}] - Cabeceras de Response: {}", traceId, exchange.getResponse().getHeaders());
        } else {
            logger.warn("Traza [{}] - Falló {} {} con status: {} en {}ms",
                    traceId,
                    exchange.getRequest().getMethod().name(),
                    exchange.getRequest().getURI().getPath(),
                    statusCode,
                    timeElapsed);
        }
    }
}
