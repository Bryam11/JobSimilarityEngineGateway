package com.tecazuay.gateway.config;

import com.tecazuay.gateway.logging.RequestResponseLoggingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración para componentes de logging
 */
@Configuration
public class LoggingConfig {

    /**
     * Crea y registra el filtro de logging de peticiones y respuestas como un bean
     */
    @Bean
    public RequestResponseLoggingFilter requestResponseLoggingFilter() {
        return new RequestResponseLoggingFilter();
    }
}
