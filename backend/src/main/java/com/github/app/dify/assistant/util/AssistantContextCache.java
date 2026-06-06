package com.github.app.dify.assistant.util;

import com.github.app.dify.assistant.req.AssistantChatReq;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AssistantContextCache {

    private static final int MAX_ENTRIES = 512;
    private static final long TTL_MILLIS = Duration.ofMinutes(30).toMillis();

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public Optional<AssistantChatReq.AssistantPageContext> get(Long userId, String contextHash) {
        String key = buildKey(userId, contextHash);
        if (key == null) {
            return Optional.empty();
        }

        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (isExpired(entry)) {
            cache.remove(key);
            return Optional.empty();
        }

        entry.touch();
        return Optional.of(entry.context());
    }

    public void put(Long userId, String contextHash, AssistantChatReq.AssistantPageContext context) {
        String key = buildKey(userId, contextHash);
        if (key == null || context == null) {
            return;
        }

        cache.put(key, new CacheEntry(context));
        evictExpired();
        evictOverflow();
    }

    private String buildKey(Long userId, String contextHash) {
        if (contextHash == null || contextHash.isBlank()) {
            return null;
        }
        String owner = userId == null ? "anonymous" : String.valueOf(userId);
        return owner + ":" + contextHash.trim();
    }

    private boolean isExpired(CacheEntry entry) {
        return System.currentTimeMillis() - entry.lastAccessedAt() > TTL_MILLIS;
    }

    private void evictExpired() {
        cache.entrySet().removeIf(entry -> isExpired(entry.getValue()));
    }

    private void evictOverflow() {
        int overflow = cache.size() - MAX_ENTRIES;
        if (overflow <= 0) {
            return;
        }
        cache.entrySet().stream()
                .sorted(Comparator.comparingLong(entry -> entry.getValue().lastAccessedAt()))
                .limit(overflow)
                .map(Map.Entry::getKey)
                .forEach(cache::remove);
    }

    private static class CacheEntry {
        private final AssistantChatReq.AssistantPageContext context;
        private volatile long lastAccessedAt;

        CacheEntry(AssistantChatReq.AssistantPageContext context) {
            this.context = context;
            this.lastAccessedAt = System.currentTimeMillis();
        }

        AssistantChatReq.AssistantPageContext context() {
            return context;
        }

        long lastAccessedAt() {
            return lastAccessedAt;
        }

        void touch() {
            lastAccessedAt = System.currentTimeMillis();
        }
    }
}
