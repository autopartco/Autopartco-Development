package com.autopartco.backend.storesuite.onboarding;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StoreApplicationControllerRoutesTest {
    private final CompleteStoreSetup completeStoreSetup = mock(CompleteStoreSetup.class);
    private final RequestSetupLink requestSetupLink = mock(RequestSetupLink.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new StoreApplicationController(
            mock(SubmitStoreApplication.class), completeStoreSetup, requestSetupLink,
            mock(ListMyApplications.class), mock(DownloadMyApplicationDocument.class))).build();

    @Test
    void theTokenSetupAndResendEndpointsNoLongerExistAndExecuteNothing() throws Exception {
        mvc.perform(multipart("/store-applications/setup").param("token", "abc")).andExpect(status().isNotFound());
        mvc.perform(post("/store-applications/setup/resend").contentType("application/json")
                .content("{\"token\":\"abc\"}")).andExpect(status().isNotFound());

        verifyNoInteractions(completeStoreSetup, requestSetupLink);
    }
}
