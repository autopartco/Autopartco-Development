package com.autopartco.backend.storesuite.automation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.autopartco.backend.core.analytics.AlertRules;
import com.autopartco.backend.core.analytics.StoreSnapshots;
import com.autopartco.backend.core.notification.StoreAlertMailer;
import com.autopartco.backend.core.organization.StaffDirectory;
import com.autopartco.backend.core.organization.StorePermissionCodes;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Evalúa las alertas de un almacén: toma su foto, aplica {@link AlertRules} con los umbrales de sus preferencias,
 * inserta las candidatas que no existan ({@code ON CONFLICT (store_id, dedupe_key) DO NOTHING}) y avisa por correo
 * solo de las filas realmente insertadas cuyo tipo tenga {@code email=true}. Los tipos con {@code in_app=false} no
 * se insertan (y por tanto tampoco se envían). Después resuelve las alertas de {@code STOCKOUT} y
 * {@code STOCKOUT_SOON} que ya no aplican (el producto volvió a tener stock o la alerta es de una semana anterior).
 *
 * <p>Destinatarios del correo: colaboradores activos con {@code ALERT_MANAGE}; si no hay ninguno, el correo del
 * negocio. No se registran correos ni datos de clientes en logs.</p>
 */
@Slf4j
@Service
@AllArgsConstructor
public class StoreAlertEvaluation {
    private static final List<AlertRules.Type> AUTO_RESOLVED =
            List.of(AlertRules.Type.STOCKOUT, AlertRules.Type.STOCKOUT_SOON);

    private final StoreSnapshots snapshots;
    private final StoreAlertQueries queries;
    private final StaffDirectory staffDirectory;
    private final StoreAlertMailer mailer;

    /** Evalúa un almacén y devuelve las alertas nuevas (insertadas). */
    public List<AlertRules.Candidate> evaluate(UUID storeId, String storeName, String businessEmail) {
        Map<AlertRules.Type, AlertPreferenceResponse> preferences = queries.preferences(storeId);
        List<AlertRules.Candidate> candidates = AlertRules.evaluate(snapshots.capture(storeId),
                AlertThresholds.effective(preferences));

        List<AlertRules.Candidate> inserted = new ArrayList<>();
        List<AlertRules.Candidate> toEmail = new ArrayList<>();
        for (AlertRules.Candidate candidate : candidates) {
            AlertPreferenceResponse preference = preferences.getOrDefault(candidate.type(),
                    AlertPreferenceResponse.defaults(candidate.type()));
            if (!preference.inApp()) {
                continue;
            }
            if (queries.insertIfAbsent(storeId, candidate)) {
                inserted.add(candidate);
                if (preference.email()) {
                    toEmail.add(candidate);
                }
            }
        }
        for (AlertRules.Type type : AUTO_RESOLVED) {
            Set<String> current = new LinkedHashSet<>();
            candidates.stream().filter(candidate -> candidate.type() == type)
                    .forEach(candidate -> current.add(candidate.dedupeKey()));
            queries.resolveOthers(storeId, type, current);
        }
        if (!toEmail.isEmpty()) {
            sendEmails(storeId, storeName, businessEmail, toEmail);
        }
        return inserted;
    }

    private void sendEmails(UUID storeId, String storeName, String businessEmail,
                            List<AlertRules.Candidate> alerts) {
        List<String> recipients = staffDirectory.emailsWithPermission(storeId, StorePermissionCodes.ALERT_MANAGE);
        if (recipients.isEmpty() && businessEmail != null && !businessEmail.isBlank()) {
            recipients = List.of(businessEmail);
        }
        List<String> lines = alerts.stream().map(alert -> alert.title() + ". " + alert.message()).toList();
        for (String recipient : recipients) {
            try {
                mailer.send(recipient, storeId, storeName, lines);
            } catch (RuntimeException failure) {
                log.warn("No se pudo enviar el aviso de alertas del almacén {}: {}", storeId,
                        failure.getClass().getSimpleName());
            }
        }
    }
}
