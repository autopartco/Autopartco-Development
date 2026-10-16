package com.autopartco.backend.storesuite.onboarding;

/**
 * Se emitió un token de configuración nuevo y hay que enviarlo por correo. Se publica dentro de la transacción y se
 * atiende tras el commit, fuera del hilo de la petición. El token en claro no aparece en {@code toString}.
 */
record SetupLinkIssued(String applicantEmail, String applicantGivenName, String onboardingToken) {
    @Override
    public String toString() {
        return "SetupLinkIssued[applicantEmail=***, applicantGivenName=***, onboardingToken=***]";
    }
}
