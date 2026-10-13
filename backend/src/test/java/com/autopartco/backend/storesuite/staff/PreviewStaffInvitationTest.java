package com.autopartco.backend.storesuite.staff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.organization.StaffInvitations;
import com.autopartco.backend.core.organization.internal.StaffInvitation;
import com.autopartco.backend.core.organization.internal.StaffInvitationRepository;
import com.autopartco.backend.core.organization.internal.StorePermissionRepository;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.core.organization.internal.StoreRoleRepository;
import com.autopartco.backend.shared.error.BadRequestException;

@ExtendWith(MockitoExtension.class)
class PreviewStaffInvitationTest {
    @Mock
    private StaffInvitationRepository invitationRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private StoreRoleRepository roleRepository;
    @Mock
    private StorePermissionRepository permissionRepository;
    @Mock
    private AccountLookup accountLookup;

    private PreviewStaffInvitation previewStaffInvitation;

    @BeforeEach
    void setUp() {
        StaffInvitations staffInvitations = new StaffInvitations(
                invitationRepository,
                storeRepository,
                roleRepository,
                permissionRepository
        );
        previewStaffInvitation = new PreviewStaffInvitation(staffInvitations, accountLookup);
    }

    @Test
    void rejectsExpiredInvitationPreviewWithAnActionableError() throws Exception {
        String token = "expired-invitation-token";
        StaffInvitation invitation = new StaffInvitation();
        invitation.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(invitationRepository.findByTokenHash(sha256(token))).thenReturn(Optional.of(invitation));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> previewStaffInvitation.execute(token)
        );

        assertEquals("SELLER_COLLABORATOR_INVITATION_EXPIRED", exception.getCode());
        verifyNoInteractions(accountLookup, storeRepository);
    }

    static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
