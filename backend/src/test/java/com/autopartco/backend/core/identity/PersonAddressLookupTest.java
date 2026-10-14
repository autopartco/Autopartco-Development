package com.autopartco.backend.core.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.autopartco.backend.core.identity.internal.PersonAddress;
import com.autopartco.backend.core.identity.internal.PersonAddressRepository;
import com.autopartco.backend.shared.location.Location;

@ExtendWith(MockitoExtension.class)
class PersonAddressLookupTest {
    private static final UUID PERSON_ID = UUID.randomUUID();
    private static final UUID OTHER_PERSON_ID = UUID.randomUUID();
    private static final UUID ADDRESS_ID = UUID.randomUUID();

    @Mock
    private PersonAddressRepository personAddressRepository;

    @InjectMocks
    private PersonAddressLookup lookup;

    @Test
    void returnsSnapshotOfOwnAddress() {
        Location location = Location.builder()
                .addressLine("Calle 10 # 20-30")
                .addressComplement("Apto 401")
                .city("Bogotá")
                .department("Cundinamarca")
                .country("Colombia")
                .postalCode("110111")
                .latitude(new BigDecimal("4.7110000"))
                .longitude(new BigDecimal("-74.0721000"))
                .provider("MANUAL")
                .build();
        PersonAddress address = PersonAddress.builder().location(location).label("Casa").build();
        when(personAddressRepository.findByIdAndPerson_Id(ADDRESS_ID, PERSON_ID)).thenReturn(Optional.of(address));

        Optional<AddressSnapshot> result = lookup.findOwned(PERSON_ID, ADDRESS_ID);

        assertThat(result).contains(new AddressSnapshot(
                "Calle 10 # 20-30", "Apto 401", "Bogotá", "Cundinamarca", "Colombia", "110111",
                new BigDecimal("4.7110000"), new BigDecimal("-74.0721000")));
    }

    @Test
    void returnsEmptyForAddressOfAnotherPerson() {
        when(personAddressRepository.findByIdAndPerson_Id(ADDRESS_ID, OTHER_PERSON_ID)).thenReturn(Optional.empty());

        assertThat(lookup.findOwned(OTHER_PERSON_ID, ADDRESS_ID)).isEmpty();
    }

    @Test
    void returnsEmptyWithoutIdentifiers() {
        assertThat(lookup.findOwned(PERSON_ID, null)).isEmpty();
        assertThat(lookup.findOwned(null, ADDRESS_ID)).isEmpty();
    }
}
