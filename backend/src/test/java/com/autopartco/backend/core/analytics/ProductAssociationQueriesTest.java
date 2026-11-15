package com.autopartco.backend.core.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductAssociationQueriesTest {
    @Test
    void relatedFiltersAssociationsFirstAndChecksPublishedWithCorrelatedExists() {
        String sql = ProductAssociationQueries.RELATED_SQL;

        assertThat(sql).doesNotContain("published AS").doesNotContain("WITH ");
        assertThat(sql).contains("WHERE a.antecedent_product_id = :productId")
                .contains("EXISTS (")
                .contains("p.id = a.consequent_product_id")
                .contains("i.stock > 0")
                .contains("ps.code = 'ACTIVE' AND bs.code = 'ACTIVE' AND ss.code = 'ACTIVE'")
                .contains("p.deleted_at IS NULL").contains("m.deleted_at IS NULL")
                .contains("c.deleted_at IS NULL").contains("i.deleted_at IS NULL")
                .contains("b.deleted_at IS NULL").contains("s.deleted_at IS NULL")
                .contains("ps.deleted_at IS NULL").contains("bs.deleted_at IS NULL").contains("ss.deleted_at IS NULL")
                .contains("a.store_id IS NULL OR a.store_id = CAST(:storeId AS uuid)")
                .contains("LIMIT :limit");
        assertThat(sql.indexOf("a.antecedent_product_id")).isLessThan(sql.indexOf("EXISTS ("));
    }
}
