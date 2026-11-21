package com.surv.platform.auth;

import com.surv.platform.config.JwtProperties;
import com.surv.platform.domain.AppAccount;
import com.surv.platform.domain.AuthAuditLog;
import com.surv.platform.domain.RefreshToken;
import com.surv.platform.domain.User;
import com.surv.platform.repository.AppAccountRepository;
import com.surv.platform.repository.AuthAuditLogRepository;
import com.surv.platform.repository.RefreshTokenRepository;
import com.surv.platform.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MINUTES = 30;

    private final AppAccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final AuthAuditLogRepository authAuditLogRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String ip = extractIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        AppAccount account = accountRepository.findByUsername(request.username())
                .orElseThrow(() -> {
                    auditLog(null, request.username(), "LOGIN", "FAILED_USER_NOT_FOUND", ip, userAgent, null);
                    return new BadCredentialsException("Invalid credentials");
                });

        // Check lockout
        if (account.isLocked()) {
            if (account.getLockedUntil() != null && Instant.now().isBefore(account.getLockedUntil())) {
                auditLog(account, request.username(), "LOGIN", "FAILED_LOCKED", ip, userAgent, null);
                throw new LockedException("Account is locked. Try again later.");
            } else {
                // Lockout expired — unlock
                account.setLocked(false);
                account.setFailedAttempts(0);
                account.setLockedUntil(null);
            }
        }

        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            int attempts = account.getFailedAttempts() + 1;
            account.setFailedAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                account.setLocked(true);
                account.setLockedUntil(Instant.now().plusSeconds(LOCKOUT_DURATION_MINUTES * 60));
                log.warn("Account locked due to too many failed attempts: {}", request.username());
            }
            accountRepository.save(account);
            auditLog(account, request.username(), "LOGIN", "FAILED_BAD_CREDENTIALS", ip, userAgent, Map.of("attempts", attempts));
            throw new BadCredentialsException("Invalid credentials");
        }

        // Successful login
        account.setFailedAttempts(0);
        account.setLastLoginAt(Instant.now());
        account.setLastLoginIp(ip);
        accountRepository.save(account);

        String accessToken = jwtService.generateAccessToken(account);
        String rawRefreshToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setAccount(account);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setIssuedAt(Instant.now());
        refreshToken.setExpiresAt(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpirationMs()));
        refreshToken.setIssuerIp(ip);
        refreshToken.setUserAgent(userAgent);
        refreshTokenRepository.save(refreshToken);

        auditLog(account, request.username(), "LOGIN", "SUCCESS", ip, userAgent, null);
        return new AuthResponse(accessToken, rawRefreshToken,
                jwtProperties.getAccessTokenExpirationMs() / 1000,
                account.getRole().name());
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken, HttpServletRequest httpRequest) {
        String ip = extractIp(httpRequest);
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (!token.isValid()) {
            throw new BadCredentialsException("Refresh token expired or revoked");
        }

        AppAccount account = token.getAccount();
        // Rotate: revoke old, issue new
        token.setRevokedAt(Instant.now());
        token.setRevokedReason("ROTATED");
        refreshTokenRepository.save(token);

        String newAccessToken = jwtService.generateAccessToken(account);
        String newRaw = UUID.randomUUID().toString();
        RefreshToken newRefresh = new RefreshToken();
        newRefresh.setAccount(account);
        newRefresh.setTokenHash(hashToken(newRaw));
        newRefresh.setIssuedAt(Instant.now());
        newRefresh.setExpiresAt(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpirationMs()));
        newRefresh.setIssuerIp(ip);
        newRefresh.setUserAgent(httpRequest.getHeader("User-Agent"));
        refreshTokenRepository.save(newRefresh);

        auditLog(account, account.getUsername(), "TOKEN_REFRESH", "SUCCESS", ip, httpRequest.getHeader("User-Agent"), null);
        return new AuthResponse(newAccessToken, newRaw,
                jwtProperties.getAccessTokenExpirationMs() / 1000,
                account.getRole().name());
    }

    @Transactional
    public void logout(String rawRefreshToken, AppAccount account, HttpServletRequest httpRequest) {
        String tokenHash = hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(t -> {
            t.setRevokedAt(Instant.now());
            t.setRevokedReason("LOGOUT");
            refreshTokenRepository.save(t);
        });
        auditLog(account, account.getUsername(), "LOGOUT", "SUCCESS",
                extractIp(httpRequest), httpRequest.getHeader("User-Agent"), null);
    }

    @Transactional
    public AppAccount registerAccount(RegisterRequest request) {
        if (accountRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username already taken");
        }
        User user = userRepository.findByEmail(request.email())
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(request.email());
                    newUser.setDisplayName(request.displayName());
                    newUser.setStatus(User.UserStatus.ACTIVE);
                    return userRepository.save(newUser);
                });

        AppAccount account = new AppAccount();
        account.setUser(user);
        account.setUsername(request.username());
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setRole(request.role() != null ? request.role() : AppAccount.AppRole.EMPLOYEE);
        return accountRepository.save(account);
    }

    private void auditLog(AppAccount account, String username, String action, String outcome,
                           String ip, String userAgent, Map<String, Object> details) {
        try {
            AuthAuditLog logEntry = new AuthAuditLog();
            logEntry.setAccount(account);
            logEntry.setUsername(username);
            logEntry.setAction(action);
            logEntry.setOutcome(outcome);
            logEntry.setIpAddress(ip);
            logEntry.setUserAgent(userAgent);
            logEntry.setDetails(details);
            logEntry.setOccurredAt(Instant.now());
            authAuditLogRepository.save(logEntry);
        } catch (Exception e) {
            // Never let audit failure break auth flow
        }
    }

    private String hashToken(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
