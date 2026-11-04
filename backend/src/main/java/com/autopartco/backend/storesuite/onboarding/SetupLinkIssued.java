package com.autopartco.backend.storesuite.onboarding;

/**
 * Hay que reenviar el correo de configuración. Se publica dentro de la transacción y se atiende tras el commit,
 * fuera del hilo de la petición. Los datos personales no aparecen en {@code toString}.
 */
record SetupLinkIssued(String applicantEmail, String applicantGivenName) {
    @Override
    public String toString() {
        return "SetupLinkIssued[applicantEmail=***, applicantGivenName=***]";
    }
}
