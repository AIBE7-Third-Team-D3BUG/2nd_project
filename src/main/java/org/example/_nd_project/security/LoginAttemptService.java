package org.example._nd_project.security;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String email, String remoteAddress) {
        String key = key(email, remoteAddress);
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return false;
        }
        if (attempt.expiresAt().isBefore(Instant.now())) {
            attempts.remove(key, attempt);
            return false;
        }
        return attempt.failures() >= MAX_FAILURES;
    }

    public void recordFailure(String email, String remoteAddress) {
        String key = key(email, remoteAddress);
        Instant now = Instant.now();
        attempts.compute(key, (ignored, previous) -> {
            if (previous == null || previous.expiresAt().isBefore(now)) {
                return new Attempt(1, now.plus(WINDOW));
            }
            return new Attempt(previous.failures() + 1, previous.expiresAt());
        });
    }

    public void clear(String email, String remoteAddress) {
        attempts.remove(key(email, remoteAddress));
    }

    private static String key(String email, String remoteAddress) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return normalizedEmail + '|' + remoteAddress;
    }

    private record Attempt(int failures, Instant expiresAt) {
    }
}
