package com.autopartco.backend.storesuite.branches;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.organization.BranchCreateRequest;
import com.autopartco.backend.core.organization.BranchResponse;
import com.autopartco.backend.core.organization.BranchUpdateRequest;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping(ApiPaths.BASE + "/stores/{storeId}/branches")
@AllArgsConstructor
public class BranchController {
    private final ListBranches listBranches;
    private final CreateBranch createBranch;
    private final UpdateBranch updateBranch;
    private final DeactivateBranch deactivateBranch;

    @GetMapping
    public List<BranchResponse> list(@PathVariable UUID storeId, Authentication authentication) {
        return listBranches.execute(storeId, authentication.getName());
    }

    @PostMapping
    public ResponseEntity<BranchResponse> create(
            @PathVariable UUID storeId,
            @Valid @RequestBody BranchCreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(createBranch.execute(storeId, authentication.getName(), request));
    }

    @PatchMapping("/{branchId}")
    public BranchResponse update(
            @PathVariable UUID storeId,
            @PathVariable UUID branchId,
            @Valid @RequestBody BranchUpdateRequest request,
            Authentication authentication
    ) {
        return updateBranch.execute(storeId, branchId, authentication.getName(), request);
    }

    @DeleteMapping("/{branchId}")
    public ResponseEntity<Void> deactivate(
            @PathVariable UUID storeId,
            @PathVariable UUID branchId,
            Authentication authentication
    ) {
        deactivateBranch.execute(storeId, branchId, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
