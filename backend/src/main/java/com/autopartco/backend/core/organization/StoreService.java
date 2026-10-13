package com.autopartco.backend.core.organization;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.organization.internal.Collaborator;
import com.autopartco.backend.core.organization.internal.CollaboratorRepository;
import com.autopartco.backend.core.organization.internal.Store;
import com.autopartco.backend.core.organization.internal.StorePermissionRepository;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.core.organization.internal.StoreRole;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.persistence.StateRepository;

import lombok.AllArgsConstructor;

/**
 * Alta, actualización y consulta de almacenes. No resuelve permisos de usuario: lo hacen los casos de uso de
 * los productos con {@link StoreAccess}.
 */
@Service
@AllArgsConstructor
public class StoreService {
    private static final String PENDING_STATE = "PENDING";
    private static final String ACTIVE_STATE = "ACTIVE";
    private static final String OWNER_ROLE = "OWNER";

    private final StoreRepository storeRepository;
    private final CollaboratorRepository collaboratorRepository;
    private final AccountLookup accountLookup;
    private final StateRepository stateRepository;
    private final StorePermissionRepository permissionRepository;

    @Transactional
    public StoreResponse register(StoreRegistrationRequest request, String managerEmail) {
        String businessName = request.businessName().trim();
        String nit = request.nit().trim();
        String businessEmail = request.businessEmail().trim().toLowerCase(Locale.ROOT);

        if (storeRepository.nameExists(businessName)) {
            throw new ConflictException("STORE_NAME_ALREADY_EXISTS", "Ya existe un almacén con este nombre.");
        }
        if (storeRepository.nitExists(nit)) {
            throw new ConflictException("STORE_NIT_ALREADY_EXISTS", "Ya existe un almacén con este NIT.");
        }
        if (storeRepository.emailExists(businessEmail)) {
            throw new ConflictException("STORE_EMAIL_ALREADY_EXISTS", "Ya existe un almacén con este correo.");
        }

        State pendingState = stateRepository.findByCode(PENDING_STATE)
                .orElseThrow(() -> new NotFoundException(
                        "STORE_PENDING_STATE_NOT_FOUND",
                        "No está configurado el estado pendiente para los almacenes."
                ));
        AccountRef account = accountLookup.findByEmail(managerEmail)
                .orElseThrow(() -> new NotFoundException(
                        "STORE_OWNER_ACCOUNT_NOT_FOUND",
                        "No se encontró la cuenta autenticada."
                ));
        State activeState = stateRepository.findByCode(ACTIVE_STATE)
                .orElseThrow(() -> new NotFoundException(
                        "STORE_COLLABORATOR_STATE_NOT_FOUND",
                        "No está configurado el estado activo para los colaboradores."
                ));

        Store store = Store.builder()
                .businessName(businessName)
                .nit(nit)
                .businessEmail(businessEmail)
                .state(pendingState)
                .configuration(Map.of())
                .build();

        Store savedStore = storeRepository.save(store);
        StoreRole ownerRole = StoreRole.builder()
                .store(savedStore)
                .code(OWNER_ROLE)
                .name("Propietario")
                .permissions(new HashSet<>(permissionRepository.findAllByOrderByCodeAsc()))
                .build();
        Collaborator owner = Collaborator.builder()
                .personId(account.personId())
                .store(savedStore)
                .state(activeState)
                .roles(Set.of(ownerRole))
                .build();
        collaboratorRepository.save(owner);

        return toResponse(savedStore);
    }

    /** Actualiza los datos del almacén. El llamador ya comprobó el permiso {@code STORE_UPDATE}. */
    @Transactional
    public StoreResponse update(UUID storeId, StoreUpdateRequest request) {
        Store store = findStore(storeId);

        if (request.businessName() != null) {
            String businessName = request.businessName().trim();
            if (storeRepository.nameUsedByOther(businessName, storeId)) {
                throw new ConflictException("STORE_NAME_ALREADY_EXISTS", "Ya existe un almacén con este nombre.");
            }
            store.setBusinessName(businessName);
        }

        if (request.nit() != null) {
            String nit = request.nit().trim();
            if (storeRepository.nitUsedByOther(nit, storeId)) {
                throw new ConflictException("STORE_NIT_ALREADY_EXISTS", "Ya existe un almacén con este NIT.");
            }
            store.setNit(nit);
        }

        if (request.businessEmail() != null) {
            String businessEmail = request.businessEmail().trim().toLowerCase(Locale.ROOT);
            if (storeRepository.emailUsedByOther(businessEmail, storeId)) {
                throw new ConflictException("STORE_EMAIL_ALREADY_EXISTS", "Ya existe un almacén con este correo.");
            }
            store.setBusinessEmail(businessEmail);
        }

        return toResponse(store);
    }

    @Transactional(readOnly = true)
    public StoreStatusResponse status(UUID storeId) {
        Store store = findStore(storeId);
        return new StoreStatusResponse(store.getId(), store.getState().getCode());
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> published() {
        return storeRepository.findPublished()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** {@code true} si el almacén existe (no borrado). */
    @Transactional(readOnly = true)
    public boolean exists(UUID storeId) {
        return storeRepository.existsById(storeId);
    }

    /** Exige que el almacén exista; si no, {@code STORE_NOT_FOUND}. */
    @Transactional(readOnly = true)
    public void requireExists(UUID storeId) {
        findStore(storeId);
    }

    /** Bloquea la fila del almacén ({@code SELECT ... FOR UPDATE}) hasta el final de la transacción del llamador. */
    @Transactional
    public void lockForUpdate(UUID storeId) {
        storeRepository.findByIdForUpdate(storeId)
                .orElseThrow(() -> new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén."));
    }

    private Store findStore(UUID storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new NotFoundException("STORE_NOT_FOUND", "No se encontró el almacén."));
    }

    private StoreResponse toResponse(Store store) {
        return new StoreResponse(
                store.getId(),
                store.getBusinessName(),
                store.getNit(),
                store.getBusinessEmail(),
                store.getState().getCode()
        );
    }
}
