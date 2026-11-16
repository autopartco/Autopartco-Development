package com.autopartco.backend.operations.staff;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlatformStaffTest {
    @Test
    void roleCodesAreUppercaseAsciiWithUnderscores() {
        assertThat(PlatformStaff.codeOf("Soporte técnico nivel 2")).isEqualTo("SOPORTE_TECNICO_NIVEL_2");
        assertThat(PlatformStaff.codeOf("  Riesgo & pagos ")).isEqualTo("RIESGO_PAGOS");
        assertThat(PlatformStaff.codeOf("2do turno")).isEqualTo("ROL_2DO_TURNO");
        assertThat(PlatformStaff.codeOf("x".repeat(80))).hasSize(60);
    }
}
