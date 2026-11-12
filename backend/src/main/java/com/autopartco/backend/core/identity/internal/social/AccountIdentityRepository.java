package com.autopartco.backend.core.identity.internal.social;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountIdentityRepository extends JpaRepository<AccountIdentity, UUID> {
    @EntityGraph(attributePaths = {"account", "account.state"})
    Optional<AccountIdentity> findByProviderAndSubject(String provider, String subject);

    long countByProvider(String provider);

    boolean existsByAccount_Id(UUID accountId);

    java.util.List<AccountIdentity> findByAccount_Id(UUID accountId);
}
