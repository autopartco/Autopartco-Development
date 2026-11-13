package com.autopartco.backend.core.audit.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.Test;

class AuditLogTest {

    /** audit_logs es append-only: un UPDATE de Hibernate (p. ej. por metadata con números) rompe la auditoría. */
    @Test
    void auditLogIsImmutableForHibernate() {
        assertThat(AuditLog.class.isAnnotationPresent(Immutable.class)).isTrue();
    }
}
