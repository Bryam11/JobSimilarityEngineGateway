package com.tecazuay.gateway.logging;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Clase utilitaria mejorada para capturar el cuerpo
 * de peticiones y respuestas en un entorno reactivo.
 */
public class ContentCaptureUtil {
    private static final Logger logger = LoggerFactory.getLogger(ContentCaptureUtil.class);
    private static final DataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    /**
     * Crea un decorador para capturar el contenido del cuerpo de la petición.
     */
    public static ServerHttpRequestDecorator createRequestDecorator(ServerHttpRequest request, String traceId) {
        return new ServerHttpRequestDecorator(request) {
            @Override
            public Flux<DataBuffer> getBody() {
                return DataBufferUtils.join(super.getBody())
                    .map(dataBuffer -> {
                        byte[] content = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(content);

                        // Registra el cuerpo de la petición siempre (no solo en DEBUG)
                        String bodyContent = new String(content, StandardCharsets.UTF_8);
                        logger.info("Traza [{}] - Cuerpo de la petición: {}", traceId, bodyContent);

                        // Devuelve un nuevo DataBuffer con el contenido, ya que el original fue consumido
                        DataBuffer buffer = BUFFER_FACTORY.wrap(content);
                        return buffer;
                    })
                    .flux();
            }
        };
    }

    /**
     * Crea un decorador para capturar el contenido del cuerpo de la respuesta.
     */
    public static ServerHttpResponseDecorator createResponseDecorator(ServerHttpResponse response, String traceId) {
        return new ServerHttpResponseDecorator(response) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux) {
                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;

                    return super.writeWith(
                        DataBufferUtils.join(fluxBody)
                            .map(dataBuffer -> {
                                byte[] content = new byte[dataBuffer.readableByteCount()];
                                dataBuffer.read(content);

                                // Registra el cuerpo de la respuesta siempre (no solo en DEBUG)
                                String bodyContent = new String(content, StandardCharsets.UTF_8);
                                logger.info("Traza [{}] - Cuerpo de la respuesta: {}", traceId, bodyContent);

                                // Devuelve un nuevo DataBuffer con el contenido, ya que el original fue consumido
                                DataBuffer buffer = BUFFER_FACTORY.wrap(content);
                                return buffer;
                            })
                            .flux()
                    );
                }
                // Si no es un Flux, delegamos al comportamiento por defecto
                return super.writeWith(body);
            }

            @Override
            public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
                return writeWith(Flux.from(body).flatMap(p -> p));
            }
        };
    }
}
