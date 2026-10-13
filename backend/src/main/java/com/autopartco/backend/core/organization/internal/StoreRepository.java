package com.autopartco.backend.core.organization.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import jakarta.persistence.LockModeType;

public interface StoreRepository extends JpaRepository<Store, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select store from Store store where store.id = :id")
    Optional<Store> lockById(@Param("id") UUID id);

    @Query("select case when count(s) > 0 then true else false end from Store s "
        + "where lower(s.businessName) = lower(:name)")
    boolean nameExists(@Param("name") String name);

    @Query("select case when count(s) > 0 then true else false end from Store s where s.nit = :nit")
    boolean nitExists(@Param("nit") String nit);

    @Query("select case when count(s) > 0 then true else false end from Store s "
        + "where lower(s.businessEmail) = lower(:email)")
    boolean emailExists(@Param("email") String email);

    @Query("select case when count(s) > 0 then true else false end from Store s "
        + "where lower(s.businessName) = lower(:name) and s.id <> :id")
    boolean nameUsedByOther(@Param("name") String name, @Param("id") UUID id);

    @Query("select case when count(s) > 0 then true else false end from Store s "
        + "where s.nit = :nit and s.id <> :id")
    boolean nitUsedByOther(@Param("nit") String nit, @Param("id") UUID id);

    @Query("select case when count(s) > 0 then true else false end from Store s "
        + "where lower(s.businessEmail) = lower(:email) and s.id <> :id")
    boolean emailUsedByOther(@Param("email") String email, @Param("id") UUID id);

    @Query("select s from Store s where s.state.code = 'ACTIVE' order by s.businessName")
    List<Store> findPublished();
}