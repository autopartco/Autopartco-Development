package com.autopartco.backend.core.organization.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface StoreApplicationDocumentRepository
        extends JpaRepository<StoreApplicationDocument, UUID> {
    List<StoreApplicationDocument> findByApplication_IdOrderByDocumentType(UUID applicationId);

    @Query("""
            select document from StoreApplicationDocument document
            join fetch document.application application
            join fetch application.store
            where document.id = :documentId
            """)
    Optional<StoreApplicationDocument> findWithApplicationById(@Param("documentId") UUID documentId);
}
