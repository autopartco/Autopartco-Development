package com.autopartco.backend.seller.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.seller.dto.SellerDashboardResponse;
import com.autopartco.backend.seller.dto.SellerCollaboratorAcceptRequest;
import com.autopartco.backend.seller.dto.SellerCollaboratorInviteRequest;
import com.autopartco.backend.seller.dto.SellerCollaboratorInvitationResponse;
import com.autopartco.backend.seller.dto.SellerCollaboratorResponse;
import com.autopartco.backend.seller.dto.SellerOrderResponse;
import com.autopartco.backend.seller.dto.SellerOrderStatusRequest;
import com.autopartco.backend.seller.dto.SellerOrderSummaryResponse;
import com.autopartco.backend.seller.dto.SellerPermissionOption;
import com.autopartco.backend.seller.dto.SellerShipmentCreateRequest;
import com.autopartco.backend.seller.dto.SellerShipmentStatusRequest;
import com.autopartco.backend.seller.service.SellerCollaboratorService;
import com.autopartco.backend.seller.service.SellerDashboardService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(ApiPaths.BASE + "/seller-dashboard")
@AllArgsConstructor
public class SellerDashboardController {
    private final SellerDashboardService dashboardService;
    private final SellerCollaboratorService collaboratorService;

    @GetMapping("/{storeId}")
    public SellerDashboardResponse dashboard(
            @PathVariable UUID storeId,
            Authentication authentication
    ) {
        return dashboardService.dashboard(storeId, authentication.getName());
    }

    @GetMapping("/{storeId}/orders")
    public List<SellerOrderSummaryResponse> orders(
            @PathVariable UUID storeId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return dashboardService.orders(storeId, authentication.getName(), status, page, size);
    }

    @GetMapping("/{storeId}/orders/{saleId}")
    public SellerOrderResponse order(
            @PathVariable UUID storeId,
            @PathVariable UUID saleId,
            Authentication authentication
    ) {
        return dashboardService.order(storeId, saleId, authentication.getName());
    }

    @PatchMapping("/{storeId}/orders/{saleId}/status")
    public SellerOrderResponse updateOrderStatus(
            @PathVariable UUID storeId,
            @PathVariable UUID saleId,
            @Valid @RequestBody SellerOrderStatusRequest request,
            Authentication authentication
    ) {
        return dashboardService.updateOrderStatus(storeId, saleId, authentication.getName(), request);
    }

    @PostMapping("/{storeId}/orders/{saleId}/shipment")
    public SellerOrderResponse createShipment(
            @PathVariable UUID storeId,
            @PathVariable UUID saleId,
            @Valid @RequestBody SellerShipmentCreateRequest request,
            Authentication authentication
    ) {
        return dashboardService.createShipment(storeId, saleId, authentication.getName(), request);
    }

    @PatchMapping("/{storeId}/orders/{saleId}/shipment")
    public SellerOrderResponse updateShipmentStatus(
            @PathVariable UUID storeId,
            @PathVariable UUID saleId,
            @Valid @RequestBody SellerShipmentStatusRequest request,
            Authentication authentication
    ) {
        return dashboardService.updateShipmentStatus(storeId, saleId, authentication.getName(), request);
    }

    @GetMapping("/{storeId}/collaborators")
    public SellerCollaboratorResponse collaborators(
            @PathVariable UUID storeId,
            Authentication authentication
    ) {
        return collaboratorService.list(storeId, authentication.getName());
    }

    @GetMapping("/{storeId}/collaborators/permissions")
    public List<SellerPermissionOption> collaboratorPermissions(
            @PathVariable UUID storeId,
            Authentication authentication
    ) {
        return collaboratorService.permissionOptions(storeId, authentication.getName());
    }

    @PostMapping("/{storeId}/collaborators/invitations")
    public SellerCollaboratorInvitationResponse inviteCollaborator(
            @PathVariable UUID storeId,
            @Valid @RequestBody SellerCollaboratorInviteRequest request,
            Authentication authentication
    ) {
        return collaboratorService.invite(storeId, authentication.getName(), request);
    }

    @PostMapping("/{storeId}/collaborators/invitations/{invitationId}/resend")
    public SellerCollaboratorInvitationResponse resendCollaboratorInvitation(
            @PathVariable UUID storeId,
            @PathVariable UUID invitationId,
            Authentication authentication
    ) {
        return collaboratorService.resend(storeId, invitationId, authentication.getName());
    }

    @PatchMapping("/{storeId}/collaborators/invitations/{invitationId}/revoke")
    public void revokeCollaboratorInvitation(
            @PathVariable UUID storeId,
            @PathVariable UUID invitationId,
            Authentication authentication
    ) {
        collaboratorService.revoke(storeId, invitationId, authentication.getName());
    }

    @GetMapping("/invitations/preview")
    public SellerCollaboratorResponse.InvitationPreview previewCollaboratorInvitation(
            @RequestParam String token
    ) {
        return collaboratorService.preview(token);
    }

    @PostMapping("/invitations/accept")
    public SellerCollaboratorResponse.Accepted acceptCollaboratorInvitation(
            @Valid @RequestBody SellerCollaboratorAcceptRequest request
    ) {
        return collaboratorService.accept(request);
    }
}
