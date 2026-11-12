package com.autopartco.backend.storesuite.questions;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.questions.PortalQuestion;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.PageResponse;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Preguntas sobre los productos del almacén en el portal: consulta, pendientes y respuesta. */
@RestController
@RequestMapping(ApiPaths.STORES + "/{storeId}/questions")
@AllArgsConstructor
public class StoreQuestionsController {
    private final StoreQuestionsPortal portal;

    @GetMapping
    public PageResponse<PortalQuestion> list(
            @PathVariable UUID storeId,
            @RequestParam(required = false) Boolean answered,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication
    ) {
        return portal.list(storeId, authentication.getName(), answered, branchId, page, size);
    }

    @GetMapping("/summary")
    public QuestionSummary summary(@PathVariable UUID storeId, Authentication authentication) {
        return portal.summary(storeId, authentication.getName());
    }

    @PutMapping("/{questionId}/answer")
    public PortalQuestion answer(
            @PathVariable UUID storeId,
            @PathVariable UUID questionId,
            @Valid @RequestBody QuestionAnswerRequest request,
            Authentication authentication
    ) {
        return portal.answer(storeId, authentication.getName(), questionId, request.text());
    }

    @DeleteMapping("/{questionId}/answer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAnswer(@PathVariable UUID storeId, @PathVariable UUID questionId,
            Authentication authentication) {
        portal.removeAnswer(storeId, authentication.getName(), questionId);
    }
}
