package com.autopartco.backend.core.order;

/** Método de pago activo ({@code payment_methods}): código y nombre visible. */
public record PaymentMethodOption(String code, String name) {
}
