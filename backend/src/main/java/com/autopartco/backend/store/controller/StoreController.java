package com.autopartco.backend.store.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.shared.web.ApiPaths;
import com.autopartco.backend.store.dto.StoreResponse;
import com.autopartco.backend.store.dto.StoreStatusResponse;
import com.autopartco.backend.store.dto.StoreUpdateRequest;
import com.autopartco.backend.store.dto.BranchCreateRequest;
import com.autopartco.backend.store.dto.BranchResponse;
import com.autopartco.backend.store.dto.BranchUpdateRequest;
import com.autopartco.backend.store.service.BranchService;
import com.autopartco.backend.store.service.StoreService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping(ApiPaths.BASE + "/stores")
@AllArgsConstructor
public class StoreController {

	private final StoreService storeService;
	private final BranchService branchService;

	@PatchMapping("/{id}")
	public StoreResponse update(
			@PathVariable UUID id,
			@Valid @RequestBody StoreUpdateRequest request,
			Authentication authentication
	) {
		return storeService.update(id, authentication.getName(), request);
	}

	@GetMapping("/{id}/status")
	public StoreStatusResponse getStatus(@PathVariable UUID id, Authentication authentication) {
		return storeService.getStatus(id, authentication.getName());
	}

	@GetMapping("/{storeId}/permissions")
	public List<String> getStorePermissions(
			@PathVariable UUID storeId,
			Authentication authentication
	) {
		return storeService.permissions(storeId, authentication.getName());
	}

	@GetMapping("/published")
	public List<StoreResponse> getPublishedStores() {
		return storeService.getPublishedStores();
	}

	@GetMapping("/{storeId}/branches")
	public List<BranchResponse> getBranches(@PathVariable UUID storeId, Authentication authentication) {
		return branchService.list(storeId, authentication.getName());
	}

	@PostMapping("/{storeId}/branches")
	public ResponseEntity<BranchResponse> createBranch(
			@PathVariable UUID storeId,
			@Valid @RequestBody BranchCreateRequest request,
			Authentication authentication
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(branchService.create(storeId, authentication.getName(), request));
	}

	@PatchMapping("/{storeId}/branches/{branchId}")
	public BranchResponse updateBranch(
			@PathVariable UUID storeId,
			@PathVariable UUID branchId,
			@Valid @RequestBody BranchUpdateRequest request,
			Authentication authentication
	) {
		return branchService.update(storeId, branchId, authentication.getName(), request);
	}

	@DeleteMapping("/{storeId}/branches/{branchId}")
	public ResponseEntity<Void> deactivateBranch(
			@PathVariable UUID storeId,
			@PathVariable UUID branchId,
			Authentication authentication
	) {
		branchService.deactivate(storeId, branchId, authentication.getName());
		return ResponseEntity.noContent().build();
	}
}
