package com.autopartco.backend.operations.storeverification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.billing.PlanService;
import com.autopartco.backend.core.organization.StoreApplicationStatus;
import com.autopartco.backend.core.organization.StoreApplicationView;
import com.autopartco.backend.core.organization.StoreApplications;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.web.PageResponse;

@ExtendWith(MockitoExtension.class)
class ListStoreApplicationsTest {
    @Mock
    private StoreApplications storeApplications;
    @Mock
    private PlanService planService;

    @InjectMocks
    private ListStoreApplications listStoreApplications;

    @Test
    void filtersByStatusAndMapsReviewer() {
        UUID rootId = UUID.randomUUID();
        LocalDateTime reviewedAt = LocalDateTime.of(2026, 11, 2, 8, 0);
        StoreApplicationView view = new StoreApplicationView(UUID.randomUUID(), UUID.randomUUID(), "Repuestos", null,
                null, "ana@example.com", null, StoreApplicationStatus.REJECTED, null, null, List.of(), "no", null,
                reviewedAt, new StoreApplicationView.Reviewer(rootId, "Root Admin", "root@example.com"));
        when(storeApplications.history(StoreApplicationStatus.REJECTED, 2, 10))
                .thenReturn(PageResponse.of(List.of(view), 2, 10, 11));

        PageResponse<StoreApplicationResponse> page = listStoreApplications.execute(" rejected ", 2, 10);

        assertEquals(2, page.page());
        assertEquals(10, page.size());
        assertEquals(11, page.total());
        assertEquals(2, page.totalPages());
        StoreApplicationResponse response = page.items().get(0);
        assertEquals(reviewedAt, response.reviewedAt());
        assertEquals(new StoreApplicationResponse.Reviewer(rootId, "Root Admin", "root@example.com"),
                response.reviewer());
    }

    @Test
    void blankStatusListsAll() {
        when(storeApplications.history(null, 1, 20)).thenReturn(PageResponse.of(List.of(), 1, 20, 0));

        PageResponse<StoreApplicationResponse> page = listStoreApplications.execute("", 1, 20);

        verify(storeApplications).history(null, 1, 20);
        assertEquals(0, page.total());
        assertNull(ListStoreApplications.parseStatus(null));
    }

    @Test
    void invalidStatusIsBadRequest() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> listStoreApplications.execute("APPROVED", 1, 20)
        );

        assertEquals("INVALID_APPLICATION_STATUS", exception.getCode());
        verifyNoInteractions(storeApplications);
    }
}
