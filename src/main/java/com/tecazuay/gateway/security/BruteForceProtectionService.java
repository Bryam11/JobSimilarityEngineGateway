package com.tecazuay.gateway.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BruteForceProtectionService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int BLOCK_DURATION_MINUTES = 30;

    // Cache para almacenar intentos fallidos por nombre de usuario
    private final Cache<String, AtomicInteger> usernameAttemptCache;

    // Cache para almacenar intentos fallidos por dirección IP
    private final Cache<String, AtomicInteger> ipAttemptCache;

    // Cache para almacenar los bloqueos por nombre de usuario
    private final Cache<String, Boolean> usernameBlockCache;

    // Cache para almacenar los bloqueos por dirección IP
    private final Cache<String, Boolean> ipBlockCache;

    public BruteForceProtectionService() {
        // Configuramos las cachés con expiración para intentos fallidos
        this.usernameAttemptCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .build();

        this.ipAttemptCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofHours(1))
                .build();

        // Configuramos las cachés con expiración para bloqueos
        this.usernameBlockCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(BLOCK_DURATION_MINUTES))
                .build();

        this.ipBlockCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(BLOCK_DURATION_MINUTES))
                .build();
    }

    /**
     * Registra un intento fallido de inicio de sesión
     * @param username El nombre de usuario utilizado
     * @param ipAddress La dirección IP de la solicitud
     * @return Mono<Boolean> - true si la cuenta/IP debe ser bloqueada
     */
    public Mono<Boolean> registerFailedLogin(String username, String ipAddress) {
        // Incrementamos los contadores de intentos fallidos
        AtomicInteger usernameAttempts = getUsernameAttempts(username);
        AtomicInteger ipAttempts = getIpAttempts(ipAddress);

        boolean shouldBlockUsername = usernameAttempts.incrementAndGet() >= MAX_ATTEMPTS;
        boolean shouldBlockIp = ipAttempts.incrementAndGet() >= MAX_ATTEMPTS;

        // Si se excedió el máximo de intentos, bloqueamos el nombre de usuario y/o IP
        if (shouldBlockUsername) {
            usernameBlockCache.put(username, true);
        }

        if (shouldBlockIp) {
            ipBlockCache.put(ipAddress, true);
        }

        return Mono.just(shouldBlockUsername || shouldBlockIp);
    }

    /**
     * Verifica si el nombre de usuario o IP está bloqueado
     * @param username El nombre de usuario a verificar
     * @param ipAddress La dirección IP a verificar
     * @return Mono<Boolean> - true si está bloqueado
     */
    public Mono<Boolean> isBlocked(String username, String ipAddress) {
        Boolean isUsernameBlocked = usernameBlockCache.getIfPresent(username);
        Boolean isIpBlocked = ipBlockCache.getIfPresent(ipAddress);

        return Mono.just((isUsernameBlocked != null && isUsernameBlocked) ||
                         (isIpBlocked != null && isIpBlocked));
    }

    /**
     * Restablece los contadores después de un inicio de sesión exitoso
     * @param username El nombre de usuario a restablecer
     * @param ipAddress La dirección IP a restablecer
     * @return Mono<Void>
     */
    public Mono<Void> resetCounters(String username, String ipAddress) {
        usernameAttemptCache.invalidate(username);
        ipAttemptCache.invalidate(ipAddress);
        return Mono.empty();
    }

    private AtomicInteger getUsernameAttempts(String username) {
        return usernameAttemptCache.get(username, key -> new AtomicInteger(0));
    }

    private AtomicInteger getIpAttempts(String ipAddress) {
        return ipAttemptCache.get(ipAddress, key -> new AtomicInteger(0));
    }
}
