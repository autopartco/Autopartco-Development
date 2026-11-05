package com.autopartco.backend.storesuite.automation;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.autopartco.backend.core.organization.StoreResponse;
import com.autopartco.backend.core.organization.StoreService;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tarea nocturna (05:00 America/Bogota) que evalúa las alertas de todos los almacenes activos. Un almacén que falla
 * se registra y no detiene a los demás. {@code @EnableScheduling} lo activa {@code core.payments.internal.PaymentsConfig}.
 * No hay evento de dominio de venta confirmada, así que no se reevalúa al vender: solo corre esta tarea.
 */
@Slf4j
@Component
@AllArgsConstructor
public class StoreAlertJob {
    private final StoreService storeService;
    private final StoreAlertEvaluation evaluation;

    @Scheduled(cron = "0 0 5 * * *", zone = "America/Bogota")
    public void evaluateNightly() {
        try {
            run();
        } catch (RuntimeException failure) {
            log.warn("Falló la evaluación nocturna de alertas: {}", failure.getClass().getSimpleName());
        }
    }

    /** Evalúa cada almacén activo; devuelve cuántos terminaron bien. */
    int run() {
        int evaluated = 0;
        for (StoreResponse store : storeService.published()) {
            try {
                evaluation.evaluate(store.id(), store.businessName(), store.businessEmail());
                evaluated++;
            } catch (RuntimeException failure) {
                log.warn("Falló la evaluación de alertas del almacén {}: {}", store.id(),
                        failure.getClass().getSimpleName());
            }
        }
        log.info("Evaluación de alertas: {} almacenes evaluados.", evaluated);
        return evaluated;
    }
}
