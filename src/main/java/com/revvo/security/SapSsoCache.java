package com.revvo.security;

import com.revvo.domain.UserPermissions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache simples em memória para permissões de usuários SSO.
 *
 * Em produção, considere usar Redis ou outro cache distribuído
 * se tiver múltiplas instâncias da aplicação.
 */
@Slf4j
@Component
public class SapSsoCache {

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    // Tempo de vida do cache em minutos (ajustar conforme necessário)
    private static final long TTL_MINUTES = 15;

    public UserPermissions get(String username) {
        CacheEntry entry = cache.get(username);

        if (entry == null) {
            return null;
        }

        // Verificar expiração
        if (entry.isExpired()) {
            log.debug("Cache expirado para usuário: {}", username);
            cache.remove(username);
            return null;
        }

        return entry.userPermissions;
    }

    public void put(String username, UserPermissions userPermissions) {
        cache.put(username, new CacheEntry(userPermissions));
        log.debug("Permissões cacheadas para usuário: {} (TTL: {} min)", username, TTL_MINUTES);
    }

    public void invalidate(String username) {
        cache.remove(username);
        log.debug("Cache invalidado para usuário: {}", username);
    }

    public void clear() {
        cache.clear();
        log.info("Cache de SSO limpo completamente");
    }

    private static class CacheEntry {
        private final UserPermissions userPermissions;
        private final LocalDateTime expiresAt;

        CacheEntry(UserPermissions userPermissions) {
            this.userPermissions = userPermissions;
            this.expiresAt = LocalDateTime.now().plusMinutes(TTL_MINUTES);
        }

        boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }
    }
}

