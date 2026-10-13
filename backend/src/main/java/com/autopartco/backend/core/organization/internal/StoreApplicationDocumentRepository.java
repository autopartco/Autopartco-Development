package com.autopartco.backend.core.organization.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface StoreApplicationDocumentRepository
        extends JpaRepository<StoreApplicationDocument, UUID> {
    @Query("""
            select document from StoreApplicationDocument document
            where document.application.id = :applicationId
            order by document.documentType asc
            """)
    List<StoreApplicationDocument> findAllOfApplication(@Param("applicationId") UUID applicationId);

    @Query("""
            select document from StoreApplicationDocument document
            join fetch document.application application
            join fetch application.store
            where document.id = :documentId
            """)
    Optional<StoreApplicationDocument> findWithApplicationById(@Param("documentId") UUID documentId);
}
