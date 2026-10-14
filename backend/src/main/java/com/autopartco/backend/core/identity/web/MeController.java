package com.autopartco.backend.core.identity.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.identity.internal.AccountSessionService;
import com.autopartco.backend.core.identity.internal.PersonAddressService;
import com.autopartco.backend.core.identity.internal.ProfileService;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/**
 * Recursos de la cuenta autenticada: perfil, direcciones y sesiones (antes bajo {@code /auth}).
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/me")
@AllArgsConstructor
public class MeController {
    private final AccountSessionService accountSessionService;
    private final ProfileService profileService;
    private final PersonAddressService personAddressService;

    @GetMapping("/sessions")
    public List<AccountSessionResponse> getActiveSessions(
            Authentication authentication,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String currentSessionId = jwt.getClaimAsString("sid");
        return accountSessionService.findActiveByAccountEmail(authentication.getName())
                .stream()
                .map(session -> new AccountSessionResponse(
                        session.getId(),
                        session.getIpAddress(),
                        session.getUserAgent(),
                        session.getCreatedAt(),
                        session.getExpiredAt(),
                        session.getId().toString().equals(currentSessionId)
                ))
                .toList();
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(
            @PathVariable UUID sessionId,
            Authentication authentication
    ) {
        accountSessionService.revokeOwnedSession(sessionId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/profile")
    public ProfileResponse getOwnProfile(Authentication authentication) {
        return profileService.getOwnProfile(authentication.getName());
    }

    @PatchMapping("/profile")
    public ProfileResponse updateOwnProfile(
            @Valid @RequestBody ProfileUpdateRequest request,
            Authentication authentication
    ) {
        return profileService.updateOwnProfile(authentication.getName(), request);
    }

    @GetMapping("/addresses")
    public List<PersonAddressResponse> getOwnAddresses(Authentication authentication) {
        return personAddressService.list(authentication.getName());
    }

    @PostMapping("/addresses")
    public ResponseEntity<PersonAddressResponse> createOwnAddress(
            @Valid @RequestBody PersonAddressRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(personAddressService.create(authentication.getName(), request));
    }

    @PutMapping("/addresses/{addressId}")
    public PersonAddressResponse updateOwnAddress(
            @PathVariable UUID addressId,
            @Valid @RequestBody PersonAddressRequest request,
            Authentication authentication
    ) {
        return personAddressService.update(authentication.getName(), addressId, request);
    }

    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<Void> deleteOwnAddress(
            @PathVariable UUID addressId,
            Authentication authentication
    ) {
        personAddressService.delete(authentication.getName(), addressId);
        return ResponseEntity.noContent().build();
    }
}
