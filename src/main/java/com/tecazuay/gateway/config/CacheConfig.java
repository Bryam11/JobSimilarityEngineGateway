package com.tecazuay.gateway.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // Establecer nombres de caché específicos para diferentes propósitos
        cacheManager.setCacheNames(Arrays.asList("publicKeyCache", "tokenBlacklist"));

        // Configuración de caché específica para claves públicas
        // - Capacidad inicial pequeña ya que solo almacenamos una clave
        // - Tiempo de expiración más corto para garantizar la renovación
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .initialCapacity(5)
                .maximumSize(10)
                .expireAfterWrite(1, TimeUnit.HOURS)); // La caché se renovará cada hora

        // Habilitar modo asíncrono para compatibilidad con WebFlux
        cacheManager.setAsyncCacheMode(true);

        return cacheManager;
    }
}
