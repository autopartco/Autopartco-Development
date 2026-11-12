package com.autopartco.backend.operations.questions;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.questions.AdminQuestion;
import com.autopartco.backend.core.questions.QuestionModeration;
import com.autopartco.backend.operations.auditlog.AdminAudit;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.PageResponse;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Moderación de preguntas de producto (solo root): lista, oculta y muestra, con auditoría. */
@RestController
@RequestMapping(ApiPaths.ADMIN + "/questions")
@AllArgsConstructor
public class QuestionAdminController {
    static final String HIDDEN_ACTION = "QUESTION_HIDDEN";
    static final String UNHIDDEN_ACTION = "QUESTION_UNHIDDEN";
    static final String ENTITY_TYPE = "PRODUCT_QUESTION";

    private final QuestionModeration moderation;
    private final AdminAudit adminAudit;

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public PageResponse<AdminQuestion> list(
            @RequestParam(required = false) Boolean hidden,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return moderation.list(hidden, page, size);
    }

    @PostMapping("/{questionId}/hide")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public AdminQuestion hide(
            @PathVariable UUID questionId,
            @Valid @RequestBody HideQuestionRequest request,
            Authentication authentication
    ) {
        AdminQuestion question = moderation.hide(questionId, request.reason());
        adminAudit.record(authentication, HIDDEN_ACTION, ENTITY_TYPE, questionId,
                Map.of("reason", question.hiddenReason(), "storeId", question.storeId()));
        return question;
    }

    @PostMapping("/{questionId}/unhide")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public AdminQuestion unhide(@PathVariable UUID questionId, Authentication authentication) {
        AdminQuestion question = moderation.unhide(questionId);
        adminAudit.record(authentication, UNHIDDEN_ACTION, ENTITY_TYPE, questionId,
                Map.of("storeId", question.storeId()));
        return question;
    }
}
