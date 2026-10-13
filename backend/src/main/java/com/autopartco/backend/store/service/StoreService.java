package com.autopartco.backend.store.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.auth.repository.AccountRepository;
import com.autopartco.backend.auth.repository.StateRepository;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.store.dto.StoreRegistrationRequest;
import com.autopartco.backend.store.dto.StoreUpdateRequest;
import com.autopartco.backend.store.dto.StoreResponse;
import com.autopartco.backend.store.dto.StoreStatusResponse;
import com.autopartco.backend.store.entity.Collaborator;
import com.autopartco.backend.store.entity.Store;
import com.autopartco.backend.store.entity.StoreRole;
import com.autopartco.backend.store.repository.CollaboratorRepository;
import com.autopartco.backend.store.repository.StoreRepository;
import com.autopartco.backend.store.repository.StorePermissionRepository;
import com.autopartco.backend.store.repository.CollaboratorRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class StoreService {
    private static final String PENDING_STATE = "PENDING";
    private static final String ACTIVE_STATE = "ACTIVE";
    private static final String OWNER_ROLE = "OWNER";

    private final StoreRepository storeRepository;
    private final CollaboratorRepository collaboratorRepository;
    private final AccountRepository accountRepository;
    private final StateRepository stateRepository;
    private final StorePermissionRepository permissionRepository;
    private final StoreAccessService storeAccessService;

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
        var account = accountRepository.findByEmailIgnoreCase(managerEmail)
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
                .permissions(new java.util.HashSet<>(permissionRepository.findAllByOrderByCodeAsc()))
                .build();
        Collaborator owner = Collaborator.builder()
                .person(account.getPerson())
                .store(savedStore)
                .state(activeState)
                .roles(Set.of(ownerRole))
                .build();
        collaboratorRepository.save(owner);

        return toResponse(savedStore);
    }

    @Transactional
    public StoreResponse update(UUID storeId, String managerEmail, StoreUpdateRequest request) {
        storeAccessService.requirePermission(
                managerEmail,
                storeId,
                StorePermissionCodes.STORE_UPDATE
        );
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
    public StoreStatusResponse getStatus(UUID storeId, String email) {
        storeAccessService.requirePermission(email, storeId, StorePermissionCodes.STORE_READ);
        Store store = findStore(storeId);
        return new StoreStatusResponse(store.getId(), store.getState().getCode());
    }

    @Transactional(readOnly = true)
    public List<String> permissions(UUID storeId, String email) {
        return storeAccessService.permissionsFor(email, storeId);
    }

    @Transactional(readOnly = true)
    public List<StoreResponse> getPublishedStores() {
        return storeRepository.findPublished()
                .stream()
                .map(this::toResponse)
                .toList();
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