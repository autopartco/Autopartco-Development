package com.autopartco.backend.operations.storeverification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.billing.PlanService;
import com.autopartco.backend.core.notification.SellerSetupMailer;
import com.autopartco.backend.core.organization.StoreApplicationReviewRequest;
import com.autopartco.backend.core.organization.StoreApplicationView;
import com.autopartco.backend.core.organization.StoreApplications;

@ExtendWith(MockitoExtension.class)
class ReviewStoreApplicationTest {
    @Mock
    private StoreApplications storeApplications;
    @Mock
    private PlanService planService;
    @Mock
    private SellerSetupMailer sellerSetupMailer;

    @InjectMocks
    private ReviewStoreApplication reviewStoreApplication;

    private final UUID applicationId = UUID.randomUUID();

    @Test
    void approvalSendsTheSetupMailWithoutAnyToken() {
        var request = new StoreApplicationReviewRequest(true, null);
        when(storeApplications.review(applicationId, "root@example.com", request))
                .thenReturn(new StoreApplications.Reviewed(view(), "ana@example.com", "Ana", true));

        reviewStoreApplication.execute(applicationId, "root@example.com", request);

        verify(sellerSetupMailer).send("ana@example.com", "Ana");
    }

    @Test
    void rejectionSendsNoMail() {
        var request = new StoreApplicationReviewRequest(false, "no");
        when(storeApplications.review(applicationId, "root@example.com", request))
                .thenReturn(new StoreApplications.Reviewed(view(), "ana@example.com", "Ana", false));

        reviewStoreApplication.execute(applicationId, "root@example.com", request);

        verifyNoInteractions(sellerSetupMailer);
    }

    private static StoreApplicationView view() {
        return new StoreApplicationView(null, null, null, null, null, null, null, null, null, null, java.util.List.of(), null, null);
    }
}
