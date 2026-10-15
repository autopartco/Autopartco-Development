package com.autopartco.backend.storesuite.staff;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.autopartco.backend.core.billing.PlanEntitlements;
import com.autopartco.backend.core.billing.PlanEntitlements.Capacity;
import com.autopartco.backend.core.organization.StaffInvitations;

import lombok.AllArgsConstructor;

/**
 * Derechos de plan para el equipo: exige roles personalizados y cupo de usuarios contando los colaboradores
 * activos más las invitaciones vigentes y las que se van a reservar. Se llama con el almacén ya bloqueado.
 */
@Component
@AllArgsConstructor
class StaffSeats {
    private final StaffInvitations staffInvitations;
    private final PlanEntitlements planEntitlements;

    void reserve(UUID storeId, long invitationsToReserve) {
        long pendingInvitations = staffInvitations.countPending(storeId);
        planEntitlements.requireCapability(storeId, "CUSTOM_ROLES");
        planEntitlements.requireCapacity(storeId, Capacity.USERS, pendingInvitations + invitationsToReserve);
    }
}
