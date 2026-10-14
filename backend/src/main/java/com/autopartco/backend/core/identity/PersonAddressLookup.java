package com.autopartco.backend.core.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.identity.internal.PersonAddress;
import com.autopartco.backend.core.identity.internal.PersonAddressRepository;
import com.autopartco.backend.shared.location.Location;

import lombok.AllArgsConstructor;

/**
 * Consulta de direcciones guardadas para otros módulos. Devuelve {@link AddressSnapshot}, nunca entidades. No lanza
 * excepciones: el llamador decide la respuesta cuando no hay dirección.
 */
@Service
@AllArgsConstructor
public class PersonAddressLookup {
    private final PersonAddressRepository personAddressRepository;

    /** Dirección vigente de la persona; vacío si no existe, está borrada o es de otra persona. */
    @Transactional(readOnly = true)
    public Optional<AddressSnapshot> findOwned(UUID personId, UUID addressId) {
        if (personId == null || addressId == null) {
            return Optional.empty();
        }
        return personAddressRepository.findByIdAndPerson_Id(addressId, personId)
                .map(PersonAddress::getLocation)
                .map(PersonAddressLookup::toSnapshot);
    }

    private static AddressSnapshot toSnapshot(Location location) {
        return new AddressSnapshot(
                location.getAddressLine(),
                location.getAddressComplement(),
                location.getCity(),
                location.getDepartment(),
                location.getCountry(),
                location.getPostalCode(),
                location.getLatitude(),
                location.getLongitude()
        );
    }
}
