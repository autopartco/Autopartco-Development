package com.autopartco.backend.core.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.organization.internal.Collaborator;
import com.autopartco.backend.core.organization.internal.CollaboratorRepository;
import com.autopartco.backend.core.organization.internal.StoreRole;
import com.autopartco.backend.core.organization.internal.StoreRoleRepository;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.persistence.State;
import com.autopartco.backend.shared.persistence.StateCodes;
import com.autopartco.backend.shared.persistence.StateRepository;

@ExtendWith(MockitoExtension.class)
class StaffDirectoryManageTest {
    @Mock
    private CollaboratorRepository collaboratorRepository;
    @Mock
    private StoreRoleRepository roleRepository;
    @Mock
    private StateRepository stateRepository;

    @InjectMocks
    private StaffDirectory staffDirectory;

    private final UUID storeId = UUID.randomUUID();
    private final UUID actorPersonId = UUID.randomUUID();

    @Test
    void changeRoleReplacesAllRolesOfTheMember() {
        Collaborator member = member(UUID.randomUUID(), "SALES", StateCodes.ACTIVE);
        StoreRole viewer = StoreRole.builder().id(UUID.randomUUID()).code("VIEWER").build();
        when(collaboratorRepository.findByIdAndStore_Id(member.getId(), storeId)).thenReturn(Optional.of(member));
        when(roleRepository.getReferenceById(viewer.getId())).thenReturn(viewer);

        staffDirectory.changeRole(storeId, member.getId(), actorPersonId, viewer.getId());

        assertEquals(Set.of(viewer), member.getRoles());
        verify(collaboratorRepository).save(member);
    }

    @Test
    void rejectsAMemberOfAnotherStore() {
        UUID memberId = UUID.randomUUID();
        when(collaboratorRepository.findByIdAndStore_Id(memberId, storeId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> staffDirectory.setActive(storeId, memberId, actorPersonId, false));
        verify(collaboratorRepository, never()).save(any());
    }

    @Test
    void neverTouchesTheOwner() {
        Collaborator owner = member(UUID.randomUUID(), "OWNER", StateCodes.ACTIVE);
        when(collaboratorRepository.findByIdAndStore_Id(owner.getId(), storeId)).thenReturn(Optional.of(owner));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> staffDirectory.setActive(storeId, owner.getId(), actorPersonId, false));
        assertEquals("SELLER_COLLABORATOR_OWNER", exception.getCode());
        verify(collaboratorRepository, never()).save(any());
    }

    @Test
    void neverTouchesTheActor() {
        Collaborator self = member(actorPersonId, "ADMIN", StateCodes.ACTIVE);
        when(collaboratorRepository.findByIdAndStore_Id(self.getId(), storeId)).thenReturn(Optional.of(self));

        ConflictException exception = assertThrows(ConflictException.class,
                () -> staffDirectory.changeRole(storeId, self.getId(), actorPersonId, UUID.randomUUID()));
        assertEquals("SELLER_COLLABORATOR_SELF", exception.getCode());
    }

    @Test
    void deactivatesAndIsIdempotent() {
        Collaborator member = member(UUID.randomUUID(), "SALES", StateCodes.ACTIVE);
        when(collaboratorRepository.findByIdAndStore_Id(member.getId(), storeId)).thenReturn(Optional.of(member));
        when(stateRepository.findByCode(StateCodes.INACTIVE)).thenReturn(Optional.of(state(StateCodes.INACTIVE)));

        assertTrue(staffDirectory.setActive(storeId, member.getId(), actorPersonId, false));
        assertEquals(StateCodes.INACTIVE, member.getState().getCode());
        assertFalse(staffDirectory.setActive(storeId, member.getId(), actorPersonId, false));
        verify(collaboratorRepository).save(member);
    }

    private Collaborator member(UUID personId, String roleCode, String stateCode) {
        return Collaborator.builder()
                .id(UUID.randomUUID())
                .personId(personId)
                .state(state(stateCode))
                .roles(new HashSet<>(Set.of(StoreRole.builder().id(UUID.randomUUID()).code(roleCode).build())))
                .build();
    }

    private State state(String code) {
        return State.builder().id(UUID.randomUUID()).code(code).build();
    }
}
