package com.autopartco.backend.storesuite.inventory;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.inventory.StockLedger;

import lombok.AllArgsConstructor;

/**
 * Resuelve la cuenta autenticada y la liga a la transacción en curso para que los movimientos de inventario (y el
 * historial de precios de los triggers puente) registren quién hizo el cambio. Llamar después de comprobar permisos.
 */
@Service
@AllArgsConstructor
public class InventoryActor {
    private final AccountLookup accountLookup;
    private final StockLedger stockLedger;

    @Transactional
    public UUID bind(String email) {
        UUID accountId = accountLookup.requireByEmail(email).accountId();
        stockLedger.bindActor(accountId);
        return accountId;
    }
}
