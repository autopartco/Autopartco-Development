package com.autopartco.backend.core.billing.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlanRepository extends JpaRepository<Plan, UUID> {
    @Query("select plan from Plan plan where plan.enabled = true order by plan.monthlyPriceCop asc")
    List<Plan> findEnabledByPrice();

    Optional<Plan> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}
