package com.autopartco.backend.seller.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.autopartco.backend.seller.entity.SellerPlan;

public interface SellerPlanRepository extends JpaRepository<SellerPlan, UUID> {
    List<SellerPlan> findByEnabledTrueOrderByMonthlyPriceCopAsc();

    Optional<SellerPlan> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}
