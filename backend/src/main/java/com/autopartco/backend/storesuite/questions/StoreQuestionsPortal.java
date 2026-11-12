package com.autopartco.backend.storesuite.questions;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.organization.StoreAccess;
import com.autopartco.backend.core.organization.StorePermissionCodes;
import com.autopartco.backend.core.questions.PortalQuestion;
import com.autopartco.backend.core.questions.StoreQuestionDesk;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/**
 * Preguntas sobre los productos del almacén en el portal. Con {@code STORE_READ} (leer) o {@code STORE_UPDATE}
 * (responder) se trabaja sobre todo el almacén; sin ese permiso, un colaborador asignado a sucursales trabaja solo con
 * los productos que tienen inventario en ellas. Sin permiso ni sucursales: 403. Una pregunta de otro almacén o fuera
 * del alcance: 404 {@code QUESTION_NOT_FOUND}.
 */
@Service
@AllArgsConstructor
public class StoreQuestionsPortal {
    private final StoreAccess storeAccess;
    private final StoreQuestionDesk desk;

    /** @param branchId {@code null} = todo el alcance; si no, solo esa sucursal (403 si queda fuera del alcance) */
    @Transactional(readOnly = true)
    public PageResponse<PortalQuestion> list(UUID storeId, String email, Boolean answered, UUID branchId,
            Integer page, Integer size) {
        List<UUID> scope = scope(storeId, email, StorePermissionCodes.STORE_READ);
        List<UUID> branches;
        if (branchId == null) {
            branches = scope;
        } else if (scope == null || scope.contains(branchId)) {
            branches = List.of(branchId);
        } else {
            throw new AccessDeniedException("Branch outside collaborator scope.");
        }
        return desk.list(storeId, answered, branches, page, size);
    }

    @Transactional(readOnly = true)
    public QuestionSummary summary(UUID storeId, String email) {
        return new QuestionSummary(desk.pending(storeId, scope(storeId, email, StorePermissionCodes.STORE_READ)));
    }

    @Transactional
    public PortalQuestion answer(UUID storeId, String email, UUID questionId, String text) {
        return desk.answer(storeId, questionId, scope(storeId, email, StorePermissionCodes.STORE_UPDATE), email, text);
    }

    @Transactional
    public void removeAnswer(UUID storeId, String email, UUID questionId) {
        desk.removeAnswer(storeId, questionId, scope(storeId, email, StorePermissionCodes.STORE_UPDATE));
    }

    /** {@code null} = todo el almacén (tiene el permiso); si no, sus sucursales asignadas (403 si no tiene ninguna). */
    private List<UUID> scope(UUID storeId, String email, String permission) {
        if (storeAccess.hasPermission(email, storeId, permission)) {
            return null;
        }
        List<UUID> branches = storeAccess.assignedBranches(email, storeId);
        if (branches.isEmpty()) {
            throw new AccessDeniedException("Store permission denied: " + permission);
        }
        return List.copyOf(branches);
    }
}
