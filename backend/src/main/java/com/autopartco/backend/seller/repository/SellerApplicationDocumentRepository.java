package com.autopartco.backend.seller.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.autopartco.backend.seller.entity.SellerApplicationDocument;

public interface SellerApplicationDocumentRepository
        extends JpaRepository<SellerApplicationDocument, UUID> {
    List<SellerApplicationDocument> findByApplication_IdOrderByDocumentType(UUID applicationId);

    @Query("""
            select document from SellerApplicationDocument document
            join fetch document.application application
            join fetch application.store
            where document.id = :documentId
            """)
    Optional<SellerApplicationDocument> findWithApplicationById(@Param("documentId") UUID documentId);
}
