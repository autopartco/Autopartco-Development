package com.autopartco.backend.marketplace.questions;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/**
 * Preguntas de producto: listado público (con sesión, también las pendientes propias); preguntar y borrar con sesión.
 * Errores: 404 {@code PRODUCT_NOT_FOUND}/{@code QUESTION_NOT_FOUND}; 400 {@code QUESTION_INVALID}/
 * {@code QUESTION_CONTACT_NOT_ALLOWED}/{@code QUESTION_QUERY_INVALID}; 409 {@code QUESTION_ALREADY_ANSWERED}; 429
 * {@code QUESTION_RATE_LIMITED}.
 */
@RestController
@RequestMapping(ApiPaths.MARKETPLACE + "/products/{productId}/questions")
@AllArgsConstructor
public class ProductQuestionController {
    private final ProductQuestionService service;

    @GetMapping
    public PageResponse<ProductQuestionResponse> list(
            @PathVariable UUID productId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String q,
            Authentication authentication
    ) {
        return service.list(productId, page, size, q, emailOrNull(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductQuestionResponse ask(
            @PathVariable UUID productId,
            @RequestBody ProductQuestionRequest request,
            Authentication authentication
    ) {
        return service.ask(productId, authentication.getName(), request);
    }

    @DeleteMapping("/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID productId, @PathVariable UUID questionId, Authentication authentication) {
        service.delete(productId, questionId, authentication.getName());
    }

    static String emailOrNull(Authentication authentication) {
        return authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken ? null : authentication.getName();
    }
}
