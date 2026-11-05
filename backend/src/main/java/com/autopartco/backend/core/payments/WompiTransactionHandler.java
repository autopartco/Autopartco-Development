package com.autopartco.backend.core.payments;

/**
 * SPI: aplica una transacción de Wompi sobre el pago de un producto. Cada implementación es un bean de Spring y
 * reclama un prefijo de referencia ({@code APC-} suscripciones, {@code APM-} marketplace).
 * <p>
 * Contrato:
 * <ul>
 *   <li>{@link #apply} corre dentro de la transacción de {@link WompiEventRouter} (propagación por defecto): no abre
 *       otra ni hace llamadas de red.</li>
 *   <li>Bloquea la fila del pago ({@code FOR UPDATE}) antes de leer su estado y es idempotente por
 *       {@code provider_transaction_id}: aplicar dos veces la misma transacción da {@link WompiEventOutcome#IGNORED}.</li>
 *   <li>Valida referencia, monto en centavos, moneda y entorno del pago. Un dato que no cuadra devuelve
 *       {@link WompiEventOutcome#REJECTED_MISMATCH}; una referencia sin pago, {@link WompiEventOutcome#UNKNOWN_REFERENCE}.
 *       No lanza excepciones de negocio (el webhook respondería con error y Wompi reintentaría).</li>
 *   <li>Solo lanza ante fallos internos (BD, invariantes rotas): la transacción se revierte, no se registra el evento
 *       y el webhook responde 500 para que Wompi reintente.</li>
 *   <li>Nunca devuelve {@link WompiEventOutcome#DUPLICATE}: lo decide el router.</li>
 * </ul>
 */
public interface WompiTransactionHandler {

    /** {@code true} si la referencia pertenece a este producto (por prefijo). Sin efectos ni acceso a BD. */
    boolean supports(String reference);

    /** Aplica la transacción y devuelve el resultado que se registra en {@code payment_provider_events}. */
    WompiEventOutcome apply(WompiTransaction transaction, WompiEventSource source);
}
