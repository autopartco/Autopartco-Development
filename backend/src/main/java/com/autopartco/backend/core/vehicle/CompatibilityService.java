package com.autopartco.backend.core.vehicle;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.vehicle.internal.Compatibility;
import com.autopartco.backend.core.vehicle.internal.CompatibilityRepository;
import com.autopartco.backend.shared.error.NotFoundException;

import lombok.AllArgsConstructor;

/** Compatibilidades vehiculares (make/model/version). Solo se consideran las no borradas. */
@Service
@AllArgsConstructor
public class CompatibilityService {
    private final CompatibilityRepository compatibilityRepository;

    /** Exige que existan todas las compatibilidades indicadas; si falta alguna, {@code COMPATIBILITY_NOT_FOUND}. */
    @Transactional(readOnly = true)
    public void requireAllExist(Set<UUID> ids) {
        if (compatibilityRepository.findAllById(ids).size() != ids.size()) {
            throw new NotFoundException("COMPATIBILITY_NOT_FOUND", "Una o más compatibilidades no existen.");
        }
    }

    /** Devuelve, de los IDs dados, los que corresponden a compatibilidades existentes (no borradas). */
    @Transactional(readOnly = true)
    public Set<UUID> existingAmong(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        return compatibilityRepository.findAllById(ids).stream()
                .map(Compatibility::getId)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public List<CompatibilityOption> options() {
        return compatibilityRepository.findOptions().stream()
                .map(item -> new CompatibilityOption(item.getId(), item.getMake(), item.getModel(), item.getVersion()))
                .toList();
    }
}
