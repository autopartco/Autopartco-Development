package com.autopartco.backend.operations.staff;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.operations.AdminActor;
import com.autopartco.backend.shared.security.PlatformAccess;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;

/** Equipo de la plataforma (solo {@code ROOT}): miembros, roles y permisos. */
@RestController
@RequestMapping(ApiPaths.ADMIN + "/staff")
@Validated
@AllArgsConstructor
public class StaffAdminController {
    private final PlatformStaff platformStaff;
    private final AdminActor adminActor;

    public record NewMemberRequest(
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 100) String surname,
            @NotBlank @Size(min = 5, max = 20) @Pattern(regexp = "^[0-9A-Za-z-]+$") String identification,
            @NotNull @Past LocalDate dateBirth,
            @NotBlank @Size(max = 20) @Pattern(regexp = "^\\+?[0-9 ().-]{7,20}$") String numberPhone,
            @NotNull UUID roleId
    ) {
    }

    public record UpdateMemberRequest(UUID roleId, Boolean active) {
    }

    public record RoleRequest(@NotBlank @Size(max = 100) String name, @Size(max = 255) String description,
            List<String> permissions) {
    }

    @GetMapping
    @PreAuthorize(PlatformAccess.ROOT)
    public List<PlatformStaff.Member> members() {
        return platformStaff.members();
    }

    @PostMapping
    @PreAuthorize(PlatformAccess.ROOT)
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformStaff.Member add(@Valid @RequestBody NewMemberRequest request, Authentication authentication) {
        return platformStaff.addMember(new PlatformStaff.NewMember(request.email(), request.name(), request.surname(),
                request.identification(), request.dateBirth(), request.numberPhone(), request.roleId()),
                adminActor.of(authentication).accountId());
    }

    @PatchMapping("/{accountId}")
    @PreAuthorize(PlatformAccess.ROOT)
    public PlatformStaff.Member update(@PathVariable UUID accountId, @RequestBody UpdateMemberRequest request,
            Authentication authentication) {
        return platformStaff.updateMember(accountId, request.roleId(), request.active(),
                adminActor.of(authentication).accountId());
    }

    @DeleteMapping("/{accountId}")
    @PreAuthorize(PlatformAccess.ROOT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID accountId, Authentication authentication) {
        platformStaff.removeMember(accountId, adminActor.of(authentication).accountId());
    }

    @GetMapping("/roles")
    @PreAuthorize(PlatformAccess.ROOT)
    public List<PlatformStaff.Role> roles() {
        return platformStaff.roles();
    }

    @PostMapping("/roles")
    @PreAuthorize(PlatformAccess.ROOT)
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformStaff.Role createRole(@Valid @RequestBody RoleRequest request, Authentication authentication) {
        return platformStaff.createRole(new PlatformStaff.RoleInput(request.name(), request.description(),
                request.permissions()), adminActor.of(authentication).accountId());
    }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize(PlatformAccess.ROOT)
    public PlatformStaff.Role updateRole(@PathVariable UUID roleId, @Valid @RequestBody RoleRequest request,
            Authentication authentication) {
        return platformStaff.updateRole(roleId, new PlatformStaff.RoleInput(request.name(), request.description(),
                request.permissions()), adminActor.of(authentication).accountId());
    }

    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize(PlatformAccess.ROOT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRole(@PathVariable UUID roleId, Authentication authentication) {
        platformStaff.deleteRole(roleId, adminActor.of(authentication).accountId());
    }

    @GetMapping("/permissions")
    @PreAuthorize(PlatformAccess.ROOT)
    public List<PlatformStaff.Permission> permissions() {
        return platformStaff.permissions();
    }
}
