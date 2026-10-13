package com.autopartco.backend.core.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.organization.internal.StaffInvitation;
import com.autopartco.backend.core.organization.internal.StaffInvitationRepository;
import com.autopartco.backend.core.organization.internal.Store;
import com.autopartco.backend.core.organization.internal.StorePermission;
import com.autopartco.backend.core.organization.internal.StorePermissionRepository;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.core.organization.internal.StoreRole;
import com.autopartco.backend.core.organization.internal.StoreRoleRepository;
import com.autopartco.backend.shared.error.BadRequestException;

@ExtendWith(MockitoExtension.class)
class StaffInvitationsTest {
    @Mock
    private StaffInvitationRepository invitationRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private StoreRoleRepository roleRepository;
    @Mock
    private StorePermissionRepository permissionRepository;

    @InjectMocks
    private StaffInvitations staffInvitations;

    private final Store store = Store.builder().id(UUID.randomUUID()).businessName("Repuestos Ana").build();

    @Test
    void createStoresOnlyTheSha256OfTheReturnedTokenAndExpiresInSevenDays() throws Exception {
        StorePermission read = StorePermission.builder().code(StorePermissionCodes.STORE_READ).build();
        when(storeRepository.getReferenceById(store.getId())).thenReturn(store);
        when(permissionRepository.findByCodeIn(Set.of(StorePermissionCodes.STORE_READ))).thenReturn(List.of(read));
        when(roleRepository.save(any(StoreRole.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(invitationRepository.save(any(StaffInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime before = LocalDateTime.now();
        StaffInvitations.Issued issued = staffInvitations.create(
                store.getId(),
                UUID.randomUUID(),
                "nuevo@example.com",
                " Vendedor ",
                Set.of(StorePermissionCodes.STORE_READ)
        );

        var captor = org.mockito.ArgumentCaptor.forClass(StaffInvitation.class);
        verify(invitationRepository).save(captor.capture());
        StaffInvitation saved = captor.getValue();
        assertEquals(43, issued.rawToken().length());
        assertEquals(sha256(issued.rawToken()), saved.getTokenHash());
        assertNotEquals(issued.rawToken(), saved.getTokenHash());
        assertEquals("Vendedor", issued.invitation().roleName());
        assertEquals(List.of(StorePermissionCodes.STORE_READ), issued.invitation().permissionCodes());
        assertEquals(before.plusDays(7).toLocalDate(), saved.getExpiresAt().toLocalDate());
    }

    @Test
    void renewReplacesTheTokenHashAndExtendsFromTheGivenInstant() throws Exception {
        StaffInvitation invitation = invitation(LocalDateTime.now().minusDays(1));
        invitation.setTokenHash("previous-hash");
        when(invitationRepository.findByIdAndStore_Id(invitation.getId(), store.getId()))
                .thenReturn(Optional.of(invitation));
        when(invitationRepository.save(invitation)).thenReturn(invitation);
        LocalDateTime now = LocalDateTime.now();

        StaffInvitations.Issued issued = staffInvitations.renew(store.getId(), invitation.getId(), now);

        assertEquals(sha256(issued.rawToken()), invitation.getTokenHash());
        assertEquals(now.plusDays(7), invitation.getExpiresAt());
    }

    @Test
    void lockPendingByTokenRejectsAnAlreadyAcceptedInvitationAfterLockingTheStore() throws Exception {
        String token = "accepted-token";
        StaffInvitation invitation = invitation(LocalDateTime.now().plusDays(1));
        invitation.setAcceptedAt(LocalDateTime.now().minusMinutes(5));
        when(invitationRepository.findByTokenHash(sha256(token))).thenReturn(Optional.of(invitation));
        when(storeRepository.findByIdForUpdate(store.getId())).thenReturn(Optional.of(store));
        when(invitationRepository.findByTokenHashForUpdate(sha256(token))).thenReturn(Optional.of(invitation));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> staffInvitations.lockPendingByToken(token)
        );

        assertEquals("SELLER_COLLABORATOR_INVITATION_CLOSED", exception.getCode());
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void markAcceptedClearsTheTokenSoItCannotBeReused() {
        StaffInvitation invitation = invitation(LocalDateTime.now().plusDays(1));
        invitation.setTokenHash("hash");
        when(invitationRepository.findById(invitation.getId())).thenReturn(Optional.of(invitation));

        staffInvitations.markAccepted(invitation.getId());

        assertNull(invitation.getTokenHash());
        assertEquals(true, invitation.getAcceptedAt() != null);
        verify(invitationRepository).save(invitation);
    }

    private StaffInvitation invitation(LocalDateTime expiresAt) {
        StoreRole role = StoreRole.builder()
                .id(UUID.randomUUID())
                .store(store)
                .name("Vendedor")
                .permissions(Set.of())
                .build();
        return StaffInvitation.builder()
                .id(UUID.randomUUID())
                .store(store)
                .role(role)
                .email("nuevo@example.com")
                .expiresAt(expiresAt)
                .build();
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
