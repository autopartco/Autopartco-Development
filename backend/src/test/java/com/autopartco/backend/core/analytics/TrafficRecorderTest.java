package com.autopartco.backend.core.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TrafficRecorderTest {
    @Test
    void groupsIdsAndDropsQueryString() {
        assertEquals("/producto/:id", TrafficRecorder.group("/producto/3f2b1c4e-1a2b-4c3d-9e8f-001122334455?ref=x"));
        assertEquals("/tiendas/:id", TrafficRecorder.group("/tiendas/12345"));
        assertEquals("/buscar", TrafficRecorder.group("/Buscar#arriba"));
        assertEquals("/", TrafficRecorder.group("/"));
    }

    @Test
    void ignoresAdminPortalAuthAndInvalidPaths() {
        assertNull(TrafficRecorder.group("/admin/resumen"));
        assertNull(TrafficRecorder.group("/portal/abc/inicio"));
        assertNull(TrafficRecorder.group("/ingresar"));
        assertNull(TrafficRecorder.group("https://evil.example/x"));
        assertNull(TrafficRecorder.group(null));
    }

    @Test
    void truncatesLongPaths() {
        String path = "/" + "segmento/".repeat(20);
        assertEquals(TrafficRecorder.MAX_PATH, TrafficRecorder.group(path).length());
    }

    @Test
    void keepsOnlyReferrerHostWithoutWww() {
        assertEquals("google.com", TrafficRecorder.host("https://www.google.com/search?q=frenos"));
        assertNull(TrafficRecorder.host("no es url"));
        assertNull(TrafficRecorder.host(""));
    }

    @Test
    void detectsMobileDevices() {
        assertEquals("mobile", TrafficRecorder.device("Mozilla/5.0 (Linux; Android 14) Mobile Safari"));
        assertEquals("desktop", TrafficRecorder.device("Mozilla/5.0 (Windows NT 10.0; Win64; x64)"));
        assertEquals("desktop", TrafficRecorder.device(null));
    }
}
