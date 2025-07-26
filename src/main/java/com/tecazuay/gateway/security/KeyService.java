package com.tecazuay.gateway.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Service
public class KeyService {

    private final KeyPair keyPair;
    private final CacheManager cacheManager;
    private static final String PUBLIC_KEY_CACHE = "publicKeyCache";
    private static final String PUBLIC_KEY_CACHE_KEY = "authServicePublicKey";
    private final WebClient webClient;

    @Autowired
    public KeyService(CacheManager cacheManager) {
        this.keyPair = generateRsaKey();
        this.cacheManager = cacheManager;
        this.webClient = WebClient.builder().build();
    }

    public RSAPublicKey getPublicKey() {
        return (RSAPublicKey) keyPair.getPublic();
    }

    public RSAPrivateKey getPrivateKey() {
        return (RSAPrivateKey) keyPair.getPrivate();
    }

    /**
     * Obtiene la llave pública del servicio de autenticación.
     * Primero intenta obtenerla de la caché, y si no está disponible,
     * la solicita al endpoint y la almacena en caché.
     *
     * @return La llave pública RSA del servicio de autenticación
     */
    @Cacheable(value = PUBLIC_KEY_CACHE, key = "'authServicePublicKey'")
    public Mono<RSAPublicKey> getAuthServicePublicKey() {
        // Primero intentamos obtener la llave de la caché
        Cache cache = cacheManager.getCache(PUBLIC_KEY_CACHE);
        if (cache != null) {
            RSAPublicKey cachedKey = cache.get(PUBLIC_KEY_CACHE_KEY, RSAPublicKey.class);
            if (cachedKey != null) {
                return Mono.just(cachedKey);
            }
        }

        // Si no está en caché, la solicitamos al servicio de autenticación
        return webClient.get()
                .uri("https://auth-pajw42smtq-ew.a.run.app/api/auth/public-key")
                .retrieve()
                .bodyToMono(String.class)
                .map(this::convertToPublicKey)
                .doOnSuccess(publicKey -> {
                    // Almacenar la llave en caché
                    if (cache != null) {
                        cache.put(PUBLIC_KEY_CACHE_KEY, publicKey);
                    }
                });
    }

    private RSAPublicKey convertToPublicKey(String base64PublicKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Error converting public key", e);
        }
    }

    private KeyPair generateRsaKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error generating RSA keys", e);
        }
    }
}
