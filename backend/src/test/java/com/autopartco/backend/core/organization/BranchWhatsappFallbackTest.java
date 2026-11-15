package com.autopartco.backend.core.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.autopartco.backend.core.organization.PublicBranches.PublicBranch;

/** Sin WhatsApp de la sede ni del almacén, el celular de la sede sirve para el botón de WhatsApp. */
class BranchWhatsappFallbackTest {

    @Test
    void onlyColombianMobilesAreUsed() {
        assertThat(PublicBranches.mobileWhatsapp("+573001234567")).isEqualTo("+573001234567");
        assertThat(PublicBranches.mobileWhatsapp("300 123 4567")).isEqualTo("+573001234567");
        assertThat(PublicBranches.mobileWhatsapp("6017654321")).isNull();
        assertThat(PublicBranches.mobileWhatsapp("0000000")).isNull();
        assertThat(PublicBranches.mobileWhatsapp(null)).isNull();
    }

    @Test
    void ownWhatsappWinsOverTheMobile() {
        assertThat(branch("+573001234567", "+573119998877").withMobileFallback().whatsapp())
                .isEqualTo("+573119998877");
        assertThat(branch("+573001234567", null).withMobileFallback().whatsapp()).isEqualTo("+573001234567");
        assertThat(branch("0000000", null).withMobileFallback().whatsapp()).isNull();
    }

    private static PublicBranch branch(String phone, String whatsapp) {
        return new PublicBranch(UUID.randomUUID(), "Centro", "Calle 1", "Bogotá", "Cundinamarca", null, null, false,
                phone, whatsapp, null, null, List.of());
    }
}
