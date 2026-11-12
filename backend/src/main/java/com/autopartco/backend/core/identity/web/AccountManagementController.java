package com.autopartco.backend.core.identity.web;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.core.identity.internal.AccountManagementService;
import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;

/**
 * Administración de la cuenta, igual para cualquier rol: contraseña, correo, métodos de ingreso, copia de los datos y
 * solicitud de eliminación. Perfil, direcciones y sesiones siguen en {@code /me}; preferencias en
 * {@code /account/settings}.
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/me")
@AllArgsConstructor
public class AccountManagementController {
    private final AccountManagementService service;

    public record PasswordChangeRequest(
            @NotBlank(message = "Escribe tu contraseña actual.") @Size(max = 72) String currentPassword,
            @NotBlank(message = "La contraseña es obligatoria.")
            @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
            @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                    message = "La contraseña debe incluir mayúscula, minúscula, número y carácter especial.")
            String newPassword) {
    }

    public record EmailChangeRequest(
            @NotBlank(message = "Escribe el correo nuevo.") @Email(message = "El correo no es válido.") @Size(max = 100)
            String newEmail,
            @Size(max = 72) String currentPassword) {
    }

    public record EmailChangeConfirmRequest(
            @NotBlank(message = "Escribe el correo nuevo.") @Email(message = "El correo no es válido.") @Size(max = 100)
            String newEmail,
            @NotBlank(message = "Escribe el código.") @Size(max = 12) String code) {
    }

    public record PasswordConfirmation(@Size(max = 72) String currentPassword) {
    }

    public record DeletionRequestBody(@Size(max = 72) String currentPassword,
            @Size(max = 500, message = "El motivo no puede superar 500 caracteres.") String reason) {
    }

    /** Cambia la contraseña y cierra las demás sesiones (la actual sigue abierta). */
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody PasswordChangeRequest request, Authentication authentication,
            @CookieValue(name = RefreshCookie.NAME, required = false) String refreshToken) {
        service.changePassword(authentication.getName(), request.currentPassword(), request.newPassword(), refreshToken);
    }

    /** Envía un código al correo nuevo. 202 con la espera para reenviar. */
    @PostMapping("/email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AccountManagementService.EmailChangeRequested requestEmailChange(@Valid @RequestBody EmailChangeRequest request,
            Authentication authentication) {
        return service.requestEmailChange(authentication.getName(), request.newEmail(), request.currentPassword());
    }

    /** Confirma el código y cambia el correo; cierra todas las sesiones (hay que volver a ingresar). */
    @PostMapping("/email/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmEmailChange(@Valid @RequestBody EmailChangeConfirmRequest request, Authentication authentication) {
        service.confirmEmailChange(authentication.getName(), request.newEmail(), request.code());
    }

    @GetMapping("/identities")
    public List<AccountManagementService.LinkedIdentity> identities(Authentication authentication) {
        return service.identities(authentication.getName());
    }

    @PostMapping("/identities/{provider}/unlink")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable String provider, @Valid @RequestBody PasswordConfirmation request,
            Authentication authentication) {
        service.unlink(authentication.getName(), provider, request.currentPassword());
    }

    /** Descarga en JSON de los datos de la cuenta. */
    @GetMapping("/data-export")
    public ResponseEntity<Map<String, Object>> export(Authentication authentication) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("autopartco-mis-datos-" + LocalDate.now() + ".json", StandardCharsets.UTF_8).build().toString())
                .body(service.export(authentication.getName()));
    }

    /** Última solicitud de eliminación (204 si nunca hubo). */
    @GetMapping("/deletion-request")
    public ResponseEntity<AccountManagementService.DeletionRequest> deletionRequest(Authentication authentication) {
        AccountManagementService.DeletionRequest current = service.deletionRequest(authentication.getName());
        return current == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(current);
    }

    @PostMapping("/deletion-request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AccountManagementService.DeletionRequest requestDeletion(@Valid @RequestBody DeletionRequestBody request,
            Authentication authentication) {
        return service.requestDeletion(authentication.getName(), request.currentPassword(), request.reason());
    }

    @DeleteMapping("/deletion-request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelDeletion(Authentication authentication) {
        service.cancelDeletion(authentication.getName());
    }
}
