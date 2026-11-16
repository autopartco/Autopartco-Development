package com.autopartco.backend.operations.support;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

/** Herramientas de soporte: almacenes y cuentas de usuarios. */
@RestController
@RequestMapping(ApiPaths.ADMIN)
@AllArgsConstructor
public class SupportAdminController {
    private final StoresAdmin storesAdmin;
    private final AccountsAdmin accountsAdmin;
    private final AdminActor adminActor;

    @GetMapping("/stores")
    @PreAuthorize(PlatformAccess.STORES)
    public PageResponse<StoresAdmin.StoreRow> stores(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + AdminPaging.DEFAULT_SIZE) int size
    ) {
        return storesAdmin.stores(q, state, page, size);
    }

    @GetMapping("/stores/{storeId}")
    @PreAuthorize(PlatformAccess.STORES)
    public StoresAdmin.StoreDetail store(@PathVariable UUID storeId) {
        return storesAdmin.store(storeId);
    }

    @GetMapping("/accounts")
    @PreAuthorize(PlatformAccess.ACCOUNTS_READ)
    public PageResponse<AccountsAdmin.AccountRow> accounts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + AdminPaging.DEFAULT_SIZE) int size
    ) {
        return accountsAdmin.accounts(q, type, state, page, size);
    }

    @GetMapping("/accounts/{accountId}")
    @PreAuthorize(PlatformAccess.ACCOUNTS_READ)
    public AccountsAdmin.AccountDetail account(@PathVariable UUID accountId) {
        return accountsAdmin.account(accountId);
    }

    /** Desactiva la cuenta ({@code INACTIVE}) y cierra sus sesiones. */
    @PostMapping("/accounts/{accountId}/lock")
    @PreAuthorize(PlatformAccess.ACCOUNTS_MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void lock(@PathVariable UUID accountId, Authentication authentication) {
        accountsAdmin.setActive(accountId, false, adminActor.of(authentication));
    }

    /** Activa la cuenta (también la desbloquea si estaba bloqueada por intentos fallidos). */
    @PostMapping("/accounts/{accountId}/unlock")
    @PreAuthorize(PlatformAccess.ACCOUNTS_MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlock(@PathVariable UUID accountId, Authentication authentication) {
        accountsAdmin.setActive(accountId, true, adminActor.of(authentication));
    }

    @PostMapping("/accounts/{accountId}/revoke-sessions")
    @PreAuthorize(PlatformAccess.ACCOUNTS_MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSessions(@PathVariable UUID accountId, Authentication authentication) {
        accountsAdmin.revokeSessions(accountId, adminActor.of(authentication));
    }
}
