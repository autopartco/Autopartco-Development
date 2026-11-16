package com.autopartco.backend.operations.anomalies;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.operations.AdminActor;
import com.autopartco.backend.operations.AdminPaging;
import com.autopartco.backend.shared.security.PlatformAccess;
import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.shared.web.PageResponse;

import lombok.AllArgsConstructor;

/** Pagos atípicos (Isolation Forest y otras evaluaciones de riesgo) y modelos predictivos. */
@RestController
@RequestMapping(ApiPaths.ADMIN)
@AllArgsConstructor
public class AnomaliesAdminController {
    private final PaymentRiskAdmin paymentRisk;
    private final MlModelsAdmin mlModels;
    private final AdminActor adminActor;

    public record OutcomeRequest(String outcome) {
    }

    @GetMapping("/payment-risk")
    @PreAuthorize(PlatformAccess.PAYMENT_RISK)
    public PageResponse<PaymentRiskAdmin.Assessment> paymentRisk(
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + AdminPaging.DEFAULT_SIZE) int size
    ) {
        return paymentRisk.list(decision, status, q, page, size);
    }

    @GetMapping("/payment-risk/summary")
    @PreAuthorize(PlatformAccess.PAYMENT_RISK)
    public PaymentRiskAdmin.Summary paymentRiskSummary() {
        return paymentRisk.summary();
    }

    @PostMapping("/payment-risk/{id}/outcome")
    @PreAuthorize(PlatformAccess.PAYMENT_RISK)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markOutcome(@PathVariable long id, @RequestBody OutcomeRequest request, Authentication authentication) {
        paymentRisk.markOutcome(id, request.outcome(), adminActor.of(authentication).accountId());
    }

    @GetMapping("/ml/models")
    @PreAuthorize(PlatformAccess.ANALYTICS)
    public List<MlModelsAdmin.ModelView> models() {
        return mlModels.models();
    }
}
