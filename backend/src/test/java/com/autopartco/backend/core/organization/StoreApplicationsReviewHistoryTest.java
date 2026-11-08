package com.autopartco.backend.core.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.organization.internal.Store;
import com.autopartco.backend.core.organization.internal.StoreApplication;
import com.autopartco.backend.core.organization.internal.StoreApplicationDocumentRepository;
import com.autopartco.backend.core.organization.internal.StoreApplicationRepository;
import com.autopartco.backend.core.organization.internal.StoreFileStorage;
import com.autopartco.backend.core.organization.internal.StoreRepository;
import com.autopartco.backend.shared.persistence.StateCodes;
import com.autopartco.backend.shared.persistence.StateRepository;
import com.autopartco.backend.shared.web.PageResponse;

@ExtendWith(MockitoExtension.class)
class StoreApplicationsReviewHistoryTest {
    private static final String ROOT_EMAIL = "root@example.com";

    @Mock
    private StoreApplicationRepository applicationRepository;
    @Mock
    private StoreApplicationDocumentRepository documentRepository;
    @Mock
    private StoreRepository storeRepository;
    @Mock
    private StoreService storeService;
    @Mock
    private StoreFileStorage fileService;
    @Mock
    private AccountLookup accountLookup;
    @Mock
    private StateRepository stateRepository;

    @InjectMocks
    private StoreApplications storeApplications;

    private final UUID applicantId = UUID.randomUUID();
    private final UUID rootId = UUID.randomUUID();
    private final Store store = Store.builder().id(UUID.randomUUID()).businessName("Repuestos Ana").build();
    private final AccountRef applicant = new AccountRef(
            applicantId, UUID.randomUUID(), "ana@example.com", "Ana Pérez", StateCodes.ACTIVE, "CUSTOMER", "Ana");
    private final AccountRef root = new AccountRef(
            rootId, UUID.randomUUID(), ROOT_EMAIL, "Root Admin", StateCodes.ACTIVE, "ROOT", "Root");
    private StoreApplication application;

    @BeforeEach
    void setUp() {
        application = StoreApplication.builder()
                .id(UUID.randomUUID())
                .applicantAccountId(applicantId)
                .store(store)
                .status(StoreApplicationStatus.PENDING_REVIEW)
                .build();
        lenient().when(applicationRepository.lockById(application.getId())).thenReturn(Optional.of(application));
        lenient().when(accountLookup.findById(applicantId)).thenReturn(Optional.of(applicant));
        lenient().when(accountLookup.findByEmail(ROOT_EMAIL)).thenReturn(Optional.of(root));
        lenient().when(documentRepository.findAllOfApplication(any())).thenReturn(List.of());
    }

    @Test
    void reviewSetsReviewerAndReviewedAt() {
        LocalDateTime before = LocalDateTime.now();

        StoreApplications.Reviewed reviewed = storeApplications.review(
                application.getId(), ROOT_EMAIL, new StoreApplicationReviewRequest(false, "  Falta RUT  "));

        assertEquals(rootId, application.getReviewedByAccountId());
        assertNotNull(application.getReviewedAt());
        assertTrue(!application.getReviewedAt().isBefore(before));
        assertEquals(application.getReviewedAt(), reviewed.application().reviewedAt());
        assertEquals(new StoreApplicationView.Reviewer(rootId, "Root Admin", ROOT_EMAIL),
                reviewed.application().reviewer());
        assertEquals(StoreApplicationStatus.REJECTED, reviewed.application().status());
    }

    @Test
    void historyViewResolvesTheReviewer() {
        LocalDateTime reviewedAt = LocalDateTime.of(2026, 11, 1, 10, 30);
        application.setStatus(StoreApplicationStatus.CONFIGURATION_PENDING);
        application.setReviewedByAccountId(rootId);
        application.setReviewedAt(reviewedAt);
        when(accountLookup.findById(rootId)).thenReturn(Optional.of(root));
        when(applicationRepository.findHistory(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(application), PageRequest.of(0, 20), 1));

        PageResponse<StoreApplicationView> page = storeApplications.history(null, 1, 20);

        StoreApplicationView view = page.items().get(0);
        assertEquals(reviewedAt, view.reviewedAt());
        assertEquals(new StoreApplicationView.Reviewer(rootId, "Root Admin", ROOT_EMAIL), view.reviewer());
        assertEquals("ana@example.com", view.applicantEmail());
    }

    @Test
    void historyViewWithoutReviewerOrWithDeletedReviewerHasNullReviewer() {
        StoreApplication reviewedByDeleted = StoreApplication.builder()
                .id(UUID.randomUUID())
                .applicantAccountId(applicantId)
                .store(store)
                .status(StoreApplicationStatus.REJECTED)
                .reviewedByAccountId(rootId)
                .reviewedAt(LocalDateTime.of(2026, 10, 1, 9, 0))
                .build();
        when(accountLookup.findById(rootId)).thenReturn(Optional.empty());
        when(applicationRepository.findHistory(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(application, reviewedByDeleted), PageRequest.of(0, 20), 2));

        PageResponse<StoreApplicationView> page = storeApplications.history(null, 1, 20);

        assertNull(page.items().get(0).reviewer());
        assertNull(page.items().get(0).reviewedAt());
        assertNull(page.items().get(1).reviewer());
        assertNotNull(page.items().get(1).reviewedAt());
    }

    @Test
    void historyPassesStatusAndClampsPaging() {
        when(applicationRepository.findHistory(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(application), PageRequest.of(0, 100), 1));

        PageResponse<StoreApplicationView> page =
                storeApplications.history(StoreApplicationStatus.PENDING_REVIEW, 0, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(applicationRepository).findHistory(
                eq(StoreApplicationStatus.PENDING_REVIEW),
                eq(StoreApplicationStatus.PENDING_REVIEW),
                pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(StoreApplications.MAX_HISTORY_PAGE_SIZE, pageable.getValue().getPageSize());
        assertEquals(1, page.page());
        assertEquals(1, page.total());
        assertEquals(1, page.totalPages());
    }
}
