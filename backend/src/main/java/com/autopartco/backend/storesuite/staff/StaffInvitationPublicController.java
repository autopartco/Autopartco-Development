package com.autopartco.backend.storesuite.staff;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.autopartco.backend.shared.web.ApiPaths;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

/** Invitaciones a colaborador sin sesión: vista previa y aceptación con el token del enlace del correo. */
@RestController
@RequestMapping(ApiPaths.BASE + "/staff-invitations")
@AllArgsConstructor
public class StaffInvitationPublicController {
    private final PreviewStaffInvitation previewStaffInvitation;
    private final AcceptStaffInvitation acceptStaffInvitation;

    @GetMapping("/preview")
    public StaffResponse.InvitationPreview preview(@RequestParam String token) {
        return previewStaffInvitation.execute(token);
    }

    @PostMapping("/accept")
    public StaffResponse.Accepted accept(@Valid @RequestBody InvitationAcceptRequest request) {
        return acceptStaffInvitation.execute(request);
    }
}
