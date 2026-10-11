package com.autopartco.backend.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;

import com.autopartco.backend.auth.dto.RegisterRequest;
import com.autopartco.backend.auth.dto.RegisterResponse;
import com.autopartco.backend.auth.dto.SellerRegistrationRequest;
import com.autopartco.backend.auth.dto.SellerRegistrationResponse;
import com.autopartco.backend.auth.dto.LoginRequest;
import com.autopartco.backend.auth.dto.LoginResponse;
import com.autopartco.backend.auth.dto.AccountSessionResponse;
import com.autopartco.backend.auth.dto.RefreshTokenRequest;
import com.autopartco.backend.auth.dto.VerifyEmailResponse;
import com.autopartco.backend.auth.dto.UnlockAccountRequest;
import com.autopartco.backend.auth.dto.UnlockAccountResponse;
import com.autopartco.backend.auth.dto.LogoutRequest;
import com.autopartco.backend.auth.dto.ProfileResponse;
import com.autopartco.backend.auth.dto.ProfileUpdateRequest;
import com.autopartco.backend.auth.dto.PersonAddressRequest;
import com.autopartco.backend.auth.dto.PersonAddressResponse;
import com.autopartco.backend.auth.dto.PasswordRecoveryRequest;
import com.autopartco.backend.auth.dto.PasswordRecoveryResponse;
import com.autopartco.backend.auth.dto.PasswordResetRequest;
import com.autopartco.backend.auth.service.AccountUnlockService;
import com.autopartco.backend.auth.service.AccountSessionService;
import com.autopartco.backend.auth.service.SessionRefreshService;
import com.autopartco.backend.auth.service.EmailVerificationService;
import com.autopartco.backend.auth.service.LoginService;
import com.autopartco.backend.auth.service.ProfileService;
import com.autopartco.backend.auth.service.PersonAddressService;
import com.autopartco.backend.auth.service.PasswordRecoveryService;
import com.autopartco.backend.auth.service.RegistrationService;
import com.autopartco.backend.auth.service.SellerRegistrationService;
import com.autopartco.backend.seller.entity.SellerDocumentType;
import com.autopartco.backend.config.ApiConfig;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(ApiConfig.API_BASE_PATH + "/auth")
@AllArgsConstructor
public class AuthController {
    private RegistrationService registrationService;
    private LoginService loginService;
    private EmailVerificationService emailVerificationService;
    private AccountUnlockService accountUnlockService;
    private AccountSessionService accountSessionService;
    private SessionRefreshService sessionRefreshService;
    private ProfileService profileService;
    private PersonAddressService personAddressService;
    private PasswordRecoveryService passwordRecoveryService;
    private SellerRegistrationService sellerRegistrationService;

    @PostMapping("/register")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return registrationService.register(request);
    }

    @PostMapping(value = "/register/seller", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SellerRegistrationResponse> registerSeller(
            @Valid @RequestPart("request") SellerRegistrationRequest request,
            @RequestPart(name = "identityDocument", required = false) MultipartFile identityDocument,
            @RequestPart(name = "rut", required = false) MultipartFile rut,
            @RequestPart(name = "legalRepresentativeId", required = false) MultipartFile legalRepresentativeId,
            @RequestPart(name = "certificateOfExistence", required = false) MultipartFile certificateOfExistence
    ) {
        java.util.Map<SellerDocumentType, MultipartFile> documents = new java.util.EnumMap<>(SellerDocumentType.class);
        if (identityDocument != null) {
            documents.put(SellerDocumentType.IDENTITY_DOCUMENT, identityDocument);
        }
        if (rut != null) {
            documents.put(SellerDocumentType.RUT, rut);
        }
        if (legalRepresentativeId != null) {
            documents.put(SellerDocumentType.LEGAL_REPRESENTATIVE_ID, legalRepresentativeId);
        }
        if (certificateOfExistence != null) {
            documents.put(SellerDocumentType.CERTIFICATE_OF_EXISTENCE, certificateOfExistence);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sellerRegistrationService.register(request, documents));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return loginService.login(request, httpRequest.getRemoteAddr(), httpRequest.getHeader("User-Agent"));
    }

    @PostMapping("/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return sessionRefreshService.refresh(request.refreshToken());
    }

    @GetMapping("/verify-email")
    public VerifyEmailResponse verifyEmail(@RequestParam String token) {
        return emailVerificationService.verify(token);
    }

    @PostMapping("/unlock/request")
    public UnlockAccountResponse requestUnlock(@Valid @RequestBody UnlockAccountRequest request) {
        return accountUnlockService.requestUnlock(request);
    }

    @GetMapping("/unlock")
    public UnlockAccountResponse unlock(@RequestParam String token) {
        return accountUnlockService.unlock(token);
    }

    @PostMapping("/password-recovery/request")
    public PasswordRecoveryResponse requestPasswordRecovery(@Valid @RequestBody PasswordRecoveryRequest request) {
        return passwordRecoveryService.requestRecovery(request);
    }

    @PostMapping("/password-recovery/reset")
    public PasswordRecoveryResponse resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        return passwordRecoveryService.resetPassword(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody LogoutRequest request,
            Authentication authentication
    ) {
        accountSessionService.revokeByRefreshToken(
                request.refreshToken(),
                authentication.getName()
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout/others")
    public ResponseEntity<Void> logoutOtherSessions(
            @Valid @RequestBody LogoutRequest request,
            Authentication authentication
    ) {
        accountSessionService.revokeOthersByRefreshToken(
                request.refreshToken(),
                authentication.getName()
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public java.util.List<AccountSessionResponse> getActiveSessions(
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

    @org.springframework.web.bind.annotation.DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID sessionId,
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
    public java.util.List<PersonAddressResponse> getOwnAddresses(Authentication authentication) {
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
            @PathVariable java.util.UUID addressId,
            @Valid @RequestBody PersonAddressRequest request,
            Authentication authentication
    ) {
        return personAddressService.update(authentication.getName(), addressId, request);
    }

    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<Void> deleteOwnAddress(
            @PathVariable java.util.UUID addressId,
            Authentication authentication
    ) {
        personAddressService.delete(authentication.getName(), addressId);
        return ResponseEntity.noContent().build();
    }
}
