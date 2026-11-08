package com.autopartco.backend.operations.auditlog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;

class AdminAuditTest {
    @Test
    void recordsRootActorAndDropsNullValues() {
        AuditService auditService = mock(AuditService.class);
        AccountLookup accountLookup = mock(AccountLookup.class);
        UUID rootId = UUID.randomUUID();
        AccountRef ref = mock(AccountRef.class);
        when(ref.accountId()).thenReturn(rootId);
        when(accountLookup.findByEmail("root@autopartco.co")).thenReturn(Optional.of(ref));
        UUID planId = UUID.randomUUID();
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("code", "PRO");
        metadata.put("description", null);

        new AdminAudit(auditService, accountLookup)
                .record(new TestingAuthenticationToken("root@autopartco.co", null), "PLAN_UPDATED", "PLAN", planId, metadata);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(eq(rootId), eq("PLAN_UPDATED"), eq("PLAN"), eq(planId), data.capture());
        assertEquals("PRO", data.getValue().get("code"));
        assertFalse(data.getValue().containsKey("description"));
    }
}
