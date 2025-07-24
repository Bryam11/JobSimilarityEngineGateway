package com.tecazuay.gateway.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Filtro global para el gateway que intercepta todas las peticiones HTTP y registra
 * información detallada sobre ellas, incluyendo cabeceras, cuerpo, tiempo de respuesta
 * y códigos de estado.
 */
@Component
public class GatewayLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(GatewayLoggingFilter.class);
    private static final DataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();
    private static final String TRACE_ID_KEY = "traceId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Usar el trace ID existente si ya está presente (para evitar duplicaciones)
        String traceId = (String) exchange.getAttribute(TRACE_ID_KEY);
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
            exchange.getAttributes().put(TRACE_ID_KEY, traceId);
        }

        // Guardar el tiempo de inicio para calcular duración
        Instant start = Instant.now();

        // Capturar información de la petición
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();
        String clientIP = request.getRemoteAddress() != null ?
                          request.getRemoteAddress().getAddress().getHostAddress() : "unknown";

        // Log de inicio de la petición con cabeceras
        logger.info("TRAZA [{}] - INICIO {} {} desde IP: {}", traceId, method, path, clientIP);
        logger.debug("TRAZA [{}] - CABECERAS REQUEST: {}", traceId, request.getHeaders());

        // Capturar el cuerpo de la petición
        AtomicReference<String> requestBody = new AtomicReference<>("");

        String finalTraceId = traceId;
        ServerHttpRequestDecorator requestDecorator = new ServerHttpRequestDecorator(request) {
            @Override
            public Flux<DataBuffer> getBody() {
                return super.getBody().doOnNext(dataBuffer -> {
                    byte[] content = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(content);
                    requestBody.set(new String(content, StandardCharsets.UTF_8));
                    DataBuffer buffer = BUFFER_FACTORY.wrap(content);
                    dataBuffer.readPosition(0);
                }).doOnComplete(() -> {
                    if (!requestBody.get().isEmpty()) {
                        logger.info("TRAZA [{}] - BODY REQUEST: {}", finalTraceId, requestBody.get());
                    }
                });
            }
        };

        // Capturar el cuerpo de la respuesta
        AtomicReference<String> responseBody = new AtomicReference<>("");

        String finalTraceId1 = traceId;
        ServerHttpResponseDecorator responseDecorator = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux) {
                    return super.writeWith(
                        ((Flux<? extends DataBuffer>) body)
                            .collectList()
                            .flatMapMany(dataBuffers -> {
                                StringBuilder builder = new StringBuilder();
                                dataBuffers.forEach(buffer -> {
                                    byte[] content = new byte[buffer.readableByteCount()];
                                    buffer.read(content);
                                    builder.append(new String(content, StandardCharsets.UTF_8));
                                });
                                responseBody.set(builder.toString());

                                // Log del cuerpo de la respuesta
                                if (!responseBody.get().isEmpty()) {
                                    logger.info("TRAZA [{}] - BODY RESPONSE: {}", finalTraceId1, responseBody.get());
                                }

                                return Flux.fromIterable(dataBuffers)
                                    .map(buffer -> {
                                        byte[] content = new byte[buffer.readableByteCount()];
                                        buffer.read(content);
                                        return BUFFER_FACTORY.wrap(content);
                                    });
                            })
                    );
                }
                return super.writeWith(body);
            }

            @Override
            public Mono<Void> writeAndFlushWith(
                    org.reactivestreams.Publisher<? extends org.reactivestreams.Publisher<? extends DataBuffer>> body) {
                return writeWith(Flux.from(body).flatMap(p -> p));
            }
        };

        ServerWebExchange decoratedExchange = exchange.mutate()
                .request(requestDecorator)
                .response(responseDecorator)
                .build();

        String finalTraceId2 = traceId;
        return chain.filter(decoratedExchange)
                .doFinally(signalType -> {
                    // Registrar finalización de la petición con estadísticas
                    Duration duration = Duration.between(start, Instant.now());
                    int statusCode = decoratedExchange.getResponse().getStatusCode() != null ?
                            decoratedExchange.getResponse().getStatusCode().value() : 0;

                    logger.info("TRAZA [{}] - FIN {} {} - status: {} - duración: {}ms",
                            finalTraceId2, method, path, statusCode, duration.toMillis());

                    logger.debug("TRAZA [{}] - CABECERAS RESPONSE: {}", finalTraceId2,
                            decoratedExchange.getResponse().getHeaders());
                });
    }

    @Override
    public int getOrder() {
        // Alta prioridad para ejecutar antes que otros filtros
        return -1;
    }
}
