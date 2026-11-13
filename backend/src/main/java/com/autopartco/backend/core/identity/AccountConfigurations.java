package com.autopartco.backend.core.identity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.internal.Account;
import com.autopartco.backend.core.identity.internal.AccountRepository;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/**
 * Acceso a {@code accounts.configuration} (jsonb) para otros módulos ({@code core.settings}) y lectura de la parte de
 * seguridad que usa identity ({@code security.twoFactor.enabled}, {@code security.loginAlerts}). El esquema completo y
 * su validación viven en {@code core.settings}; aquí solo se leen y escriben mapas.
 */
@Service
@AllArgsConstructor
public class AccountConfigurations {
    public static final String SECURITY = "security";
    public static final String TWO_FACTOR = "twoFactor";
    public static final String ENABLED = "enabled";
    public static final String METHOD = "method";
    public static final String LOGIN_ALERTS = "loginAlerts";
    public static final String EMAIL_METHOD = "EMAIL";
    /**
     * Clave de primer nivel, fuera del esquema de {@code core.settings} (que conserva lo que no conoce): {@code false} en
     * cuentas creadas con Google, cuya contraseña es aleatoria y nadie conoce. Ausente = la persona definió su contraseña.
     */
    public static final String PASSWORD_SET = "passwordSet";

    private final AccountRepository accountRepository;

    /** Seguridad de una cuenta para decisiones de acceso (p. ej. 2FA obligatorio en un almacén). */
    public record SecurityStatus(UUID accountId, UUID personId, boolean twoFactorEnabled) {
    }

    @Transactional(readOnly = true)
    public Map<String, Object> read(UUID accountId) {
        return copy(find(accountId).getConfiguration());
    }

    /** Lee la configuración bloqueando la fila de la cuenta hasta el final de la transacción del llamador. */
    @Transactional
    public Map<String, Object> lockForUpdate(UUID accountId) {
        Account account = accountRepository.lockById(accountId).orElseThrow(AccountConfigurations::notFound);
        return copy(account.getConfiguration());
    }

    /** Reemplaza la configuración completa (el llamador partió de {@link #lockForUpdate}). */
    @Transactional
    public void replace(UUID accountId, Map<String, Object> configuration) {
        Account account = find(accountId);
        account.setConfiguration(new HashMap<>(configuration));
        accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Optional<SecurityStatus> securityStatus(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return accountRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .map(account -> new SecurityStatus(
                        account.getId(),
                        account.getPerson().getId(),
                        twoFactorEnabled(account.getConfiguration())
                ));
    }

    /** {@code security.twoFactor.enabled}; por defecto {@code false}. */
    public static boolean twoFactorEnabled(Map<String, Object> configuration) {
        return section(section(configuration, SECURITY), TWO_FACTOR).get(ENABLED) instanceof Boolean enabled && enabled;
    }

    /** ¿La persona conoce su contraseña? Solo es {@code false} si {@link #PASSWORD_SET} lo dice explícitamente. */
    public static boolean passwordSet(Map<String, Object> configuration) {
        return configuration == null || !Boolean.FALSE.equals(configuration.get(PASSWORD_SET));
    }

    /** Copia de {@code configuration} sin la marca de contraseña desconocida. */
    public static Map<String, Object> withPasswordSet(Map<String, Object> configuration) {
        Map<String, Object> result = copy(configuration);
        result.remove(PASSWORD_SET);
        return result;
    }

    /** {@code security.loginAlerts}; por defecto {@code true}. */
    public static boolean loginAlertsEnabled(Map<String, Object> configuration) {
        return !(section(configuration, SECURITY).get(LOGIN_ALERTS) instanceof Boolean enabled) || enabled;
    }

    /** Copia de {@code configuration} con {@code security.twoFactor = {enabled, method: EMAIL}}; conserva el resto. */
    public static Map<String, Object> withTwoFactor(Map<String, Object> configuration, boolean enabled) {
        Map<String, Object> result = copy(configuration);
        Map<String, Object> security = new HashMap<>(section(result, SECURITY));
        security.put(TWO_FACTOR, Map.of(ENABLED, enabled, METHOD, EMAIL_METHOD));
        result.put(SECURITY, security);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> map, String key) {
        if (map == null) {
            return Map.of();
        }
        return map.get(key) instanceof Map<?, ?> nested ? (Map<String, Object>) nested : Map.of();
    }

    private static Map<String, Object> copy(Map<String, Object> configuration) {
        return configuration == null ? new HashMap<>() : new HashMap<>(configuration);
    }

    private Account find(UUID accountId) {
        return accountRepository.findById(accountId).orElseThrow(AccountConfigurations::notFound);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("ACCOUNT_NOT_FOUND", "No se encontró la cuenta.");
    }
}
