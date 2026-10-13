package com.autopartco.backend.seller.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.autopartco.backend.auth.repository.AccountRepository;
import com.autopartco.backend.auth.repository.PersonRepository;
import com.autopartco.backend.auth.repository.StateRepository;
import com.autopartco.backend.auth.repository.TypeAccountRepository;
import com.autopartco.backend.core.notification.EmailService;
import com.autopartco.backend.shared.config.AppProperties;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.seller.entity.SellerCollaboratorInvitation;
import com.autopartco.backend.seller.repository.SellerCollaboratorInvitationRepository;
import com.autopartco.backend.store.repository.CollaboratorRepository;
import com.autopartco.backend.store.repository.StorePermissionRepository;
import com.autopartco.backend.store.repository.StoreRepository;
import com.autopartco.backend.store.repository.StoreRoleRepository;
import com.autopartco.backend.store.service.StoreAccessService;
import com.autopartco.backend.store.service.StorePermissionCodes;

@ExtendWith(MockitoExtension.class)
class SellerCollaboratorServiceTest {
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private PersonRepository personRepository;
    @Mock
    private StateRepository stateRepository;
    @Mock
    private TypeAccountRepository accountTypeRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private StoreRoleRepository roleRepository;
    @Mock
    private StorePermissionRepository permissionRepository;
    @Mock
    private CollaboratorRepository collaboratorRepository;
    @Mock
    private SellerCollaboratorInvitationRepository invitationRepository;
    @Mock
    private SellerPlanEntitlementService entitlementService;
    @Mock
    private StoreAccessService storeAccessService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private SellerCollaboratorService collaboratorService;

    @Test
    void requiresManagementPermissionBeforeCreatingAnInvitation() {
        UUID storeId = UUID.randomUUID();
        doThrow(new AccessDeniedException("forbidden"))
                .when(storeAccessService)
                .requirePermission("owner@example.com", storeId, StorePermissionCodes.COLLABORATOR_MANAGE);

        assertThrows(
                AccessDeniedException.class,
                () -> collaboratorService.invite(storeId, "owner@example.com", null)
        );
        verify(storeAccessService).requirePermission(
                "owner@example.com",
                storeId,
                StorePermissionCodes.COLLABORATOR_MANAGE
        );
        verifyNoInteractions(
                jdbcTemplate,
                accountRepository,
                personRepository,
                stateRepository,
                accountTypeRepository,
                storeRepository,
                roleRepository,
                permissionRepository,
                collaboratorRepository,
                invitationRepository,
                entitlementService,
                passwordEncoder,
                emailService,
                appProperties
        );
    }

    @Test
    void rejectsExpiredInvitationPreviewWithAnActionableError() throws Exception {
        String token = "expired-invitation-token";
        String tokenHash = hash(token);
        SellerCollaboratorInvitation invitation = new SellerCollaboratorInvitation();
        invitation.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(invitationRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(invitation));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> collaboratorService.preview(token)
        );

        assertEquals("SELLER_COLLABORATOR_INVITATION_EXPIRED", exception.getCode());
        verifyNoInteractions(accountRepository, storeRepository);
    }

    private String hash(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }
}
