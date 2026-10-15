package com.autopartco.backend.core.billing.internal;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEvent, UUID> {
}
