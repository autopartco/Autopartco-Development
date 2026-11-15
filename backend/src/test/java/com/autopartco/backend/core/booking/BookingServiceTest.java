package com.autopartco.backend.core.booking;

import static com.autopartco.backend.core.booking.BookingFixtures.BOOKING;
import static com.autopartco.backend.core.booking.BookingFixtures.BRANCH;
import static com.autopartco.backend.core.booking.BookingFixtures.BUYER;
import static com.autopartco.backend.core.booking.BookingFixtures.BUYER_EMAIL;
import static com.autopartco.backend.core.booking.BookingFixtures.CLOCK;
import static com.autopartco.backend.core.booking.BookingFixtures.OTHER_BUYER;
import static com.autopartco.backend.core.booking.BookingFixtures.OTHER_STORE;
import static com.autopartco.backend.core.booking.BookingFixtures.PHONE;
import static com.autopartco.backend.core.booking.BookingFixtures.SERVICE;
import static com.autopartco.backend.core.booking.BookingFixtures.STAFF_EMAIL;
import static com.autopartco.backend.core.booking.BookingFixtures.STORE;
import static com.autopartco.backend.core.booking.BookingFixtures.at;
import static com.autopartco.backend.core.booking.BookingFixtures.booking;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.booking.internal.BookingAsyncConfig;
import com.autopartco.backend.core.booking.internal.BookingCatalog;
import com.autopartco.backend.core.booking.internal.BookingChanged;
import com.autopartco.backend.core.booking.internal.ServiceBooking;
import com.autopartco.backend.core.booking.internal.ServiceBookingRepository;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.notification.EmailService;
import com.autopartco.backend.core.notification.NotificationKind;
import com.autopartco.backend.core.notification.NotificationService;
import com.autopartco.backend.core.settings.SettingsQueries;
import com.autopartco.backend.core.settings.StoreSettings;
import com.autopartco.backend.shared.config.AppProperties;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.error.TooManyRequestsException;

class BookingServiceTest {
    private final ServiceBookingRepository repository = mock(ServiceBookingRepository.class);
    private final BookingCatalog catalog = mock(BookingCatalog.class);
    private final AccountLookup accountLookup = mock(AccountLookup.class);
    private final AuditService auditService = mock(AuditService.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(repository, catalog, accountLookup, auditService, events, CLOCK);
        when(accountLookup.requireByEmail(BUYER_EMAIL)).thenReturn(BookingFixtures.buyer());
        when(accountLookup.requireByEmail(STAFF_EMAIL)).thenReturn(BookingFixtures.staff());
        when(accountLookup.findAllByIds(anyCollection())).thenReturn(Map.of(BUYER, BookingFixtures.buyer()));
        when(catalog.service(SERVICE)).thenReturn(Optional.of(BookingFixtures.service()));
        when(catalog.services(anyCollection())).thenReturn(Map.of(SERVICE, BookingFixtures.service()));
        when(catalog.serviceBranch(SERVICE, BRANCH)).thenReturn(Optional.of(BookingFixtures.branch()));
        when(catalog.branches(anyCollection())).thenReturn(Map.of(BRANCH, BookingFixtures.branch()));
        when(catalog.profilePhone(BUYER)).thenReturn(Optional.of("3001112233"));
        when(repository.saveAndFlush(any(ServiceBooking.class))).thenAnswer(invocation -> {
            ServiceBooking saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
                saved.setVersion(0L);
            }
            return saved;
        });
    }

    private static BookingRequest request(String localStart, String phone) {
        return new BookingRequest(SERVICE, BRANCH, at(localStart), phone, null, "  Ruido al frenar  ");
    }

    // --- Solicitud ----------------------------------------------------------------------------------------------------

    @Test
    void requestKeepsThePromotionalPriceInForceWhenAsking() {
        UUID promotionId = UUID.randomUUID();
        var calculator = new com.autopartco.backend.core.pricing.PriceCalculator(
                mock(com.autopartco.backend.core.pricing.internal.PromotionRepository.class));
        var pricing = mock(com.autopartco.backend.core.pricing.ServicePricing.class);
        when(pricing.current()).thenReturn(new com.autopartco.backend.core.pricing.ServicePricing.Rules(calculator,
                java.util.List.of(new com.autopartco.backend.core.pricing.PromotionRule(promotionId,
                        com.autopartco.backend.core.pricing.PromotionScope.STORE, STORE, "Alineación -15",
                        com.autopartco.backend.core.pricing.PromotionTargetType.SERVICE, SERVICE, null, null, null,
                        com.autopartco.backend.core.pricing.PromotionDiscountType.PERCENTAGE, new BigDecimal("15"),
                        com.autopartco.backend.core.pricing.PromotionAudience.ALL, null, null))));
        BookingService priced = new BookingService(repository, catalog, accountLookup, auditService, events, CLOCK,
                pricing);

        Booking created = priced.request(BUYER_EMAIL, request("2026-11-17T09:00", null));

        assertThat(created.quotedPrice()).isEqualByComparingTo(new BigDecimal("51000"));
        assertThat(created.promotion().id()).isEqualTo(promotionId);
    }

    @Test
    void requestCreatesARequestedBookingWithQuotedPriceAndProfilePhone() {
        Booking created = service.request(BUYER_EMAIL, request("2026-11-17T09:00", null));

        ArgumentCaptor<ServiceBooking> saved = ArgumentCaptor.forClass(ServiceBooking.class);
        verify(repository).saveAndFlush(saved.capture());
        ServiceBooking booking = saved.getValue();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.REQUESTED);
        assertThat(booking.getCode()).matches("^CT-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6}$");
        assertThat(booking.getStoreId()).isEqualTo(STORE);
        assertThat(booking.getAccountId()).isEqualTo(BUYER);
        assertThat(booking.getStartsAt()).isEqualTo(Instant.parse("2026-11-17T14:00:00Z"));
        assertThat(booking.getEndsAt()).isEqualTo(Instant.parse("2026-11-17T15:00:00Z"));
        assertThat(booking.getQuotedPrice()).isEqualByComparingTo(new BigDecimal("60000"));
        assertThat(booking.getPromotionId()).isNull();
        assertThat(booking.getContactPhone()).isEqualTo(PHONE);
        assertThat(booking.getBuyerNote()).isEqualTo("Ruido al frenar");
        // Serializa por comprador antes de contar los límites.
        verify(catalog).lockAccount(BUYER);
        verify(auditService).record(eq(BUYER), eq("BOOKING_REQUESTED"), eq(BookingService.ENTITY), any(), anyMap());
        verify(events).publishEvent(new BookingChanged(booking.getId(), null, BookingStatus.REQUESTED, null));
        assertThat(created.startsAt()).isEqualTo(at("2026-11-17T09:00"));
        assertThat(created.buyer()).isNull();
        assertThat(created.contactPhone()).isEqualTo(PHONE);
    }

    @Test
    void sentPhoneWinsOverTheProfile() {
        service.request(BUYER_EMAIL, request("2026-11-17T09:00", "310 555 0000"));

        ArgumentCaptor<ServiceBooking> saved = ArgumentCaptor.forClass(ServiceBooking.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getContactPhone()).isEqualTo("+573105550000");
    }

    @Test
    void withoutProfilePhoneAndWithoutSentPhoneTheContactIsRequired() {
        when(catalog.profilePhone(BUYER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.request(BUYER_EMAIL, request("2026-11-17T09:00", " ")))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("CONTACT_PHONE_REQUIRED");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void invalidSentPhoneIsRejected() {
        assertThatThrownBy(() -> service.request(BUYER_EMAIL, request("2026-11-17T09:00", "12345")))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("CONTACT_PHONE_INVALID");
    }

    @Test
    void misalignedOrEarlySlotIsRejectedBeforeAnyWrite() {
        for (String start : List.of("2026-11-17T09:30", "2026-11-16T09:00", "2026-11-17T12:00")) {
            assertThatThrownBy(() -> service.request(BUYER_EMAIL, request(start, null)))
                    .isInstanceOf(BadRequestException.class)
                    .extracting("code").isEqualTo(BookingRules.SLOT_INVALID);
        }
        verify(repository, never()).saveAndFlush(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void fourthActiveBookingInTheSameStoreIsLimited() {
        when(repository.countByAccountAndStore(BUYER, STORE, BookingStatus.ACTIVE)).thenReturn(3L);

        assertThatThrownBy(() -> service.request(BUYER_EMAIL, request("2026-11-17T09:00", null)))
                .isInstanceOf(TooManyRequestsException.class)
                .extracting("code").isEqualTo("BOOKING_LIMIT_REACHED");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void eleventhRequestOfTheDayIsLimited() {
        Instant startOfDay = Instant.parse("2026-11-16T05:00:00Z");
        when(repository.countCreatedSince(BUYER, startOfDay)).thenReturn(10L);

        assertThatThrownBy(() -> service.request(BUYER_EMAIL, request("2026-11-17T09:00", null)))
                .isInstanceOf(TooManyRequestsException.class)
                .extracting("code").isEqualTo("BOOKING_LIMIT_REACHED");
    }

    @Test
    void disabledBookingOrBranchWithoutHoursIsRejected() {
        when(catalog.service(SERVICE)).thenReturn(Optional.of(BookingFixtures.service(false, 60)));
        assertThatThrownBy(() -> service.request(BUYER_EMAIL, request("2026-11-17T09:00", null)))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("BOOKING_DISABLED");

        when(catalog.service(SERVICE)).thenReturn(Optional.of(BookingFixtures.service()));
        when(catalog.serviceBranch(SERVICE, BRANCH)).thenReturn(Optional.of(BookingFixtures.branch(null)));
        assertThatThrownBy(() -> service.request(BUYER_EMAIL, request("2026-11-17T09:00", null)))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("BOOKING_DISABLED");
    }

    @Test
    void branchThatDoesNotOfferTheServiceIsRejected() {
        when(catalog.serviceBranch(SERVICE, BRANCH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.request(BUYER_EMAIL, request("2026-11-17T09:00", null)))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("BRANCH_NOT_IN_SERVICE");
    }

    @Test
    void hiddenServiceIsNotFound() {
        BookingCatalog.ServiceInfo hidden = new BookingCatalog.ServiceInfo(SERVICE, STORE, "X",
                com.autopartco.backend.core.organization.ServiceCategory.OTHER, null, null, true, 60, false, true,
                "Taller", null);
        when(catalog.service(SERVICE)).thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> service.request(BUYER_EMAIL, request("2026-11-17T09:00", null)))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("SERVICE_NOT_FOUND");
    }

    // --- Comprador: propias ---------------------------------------------------------------------------------------------

    @Test
    void someoneElsesBookingIsNotFoundForTheBuyer() {
        ServiceBooking foreign = booking(BookingStatus.PROPOSED, "2026-11-17T09:00");
        foreign.setAccountId(OTHER_BUYER);
        when(repository.findById(BOOKING)).thenReturn(Optional.of(foreign));
        when(repository.lockById(BOOKING)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.getMine(BUYER_EMAIL, BOOKING))
                .isInstanceOf(NotFoundException.class).extracting("code").isEqualTo("BOOKING_NOT_FOUND");
        assertThatThrownBy(() -> service.cancelByBuyer(BUYER_EMAIL, BOOKING, null))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.acceptProposal(BUYER_EMAIL, BOOKING))
                .isInstanceOf(NotFoundException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void acceptingAProposalConfirmsAtTheProposedTime() {
        ServiceBooking proposed = booking(BookingStatus.PROPOSED, "2026-11-17T09:00");
        proposed.setProposedStartsAt(at("2026-11-18T15:00").toInstant());
        when(repository.lockById(BOOKING)).thenReturn(Optional.of(proposed));

        Booking result = service.acceptProposal(BUYER_EMAIL, BOOKING);

        assertThat(result.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result.startsAt()).isEqualTo(at("2026-11-18T15:00"));
        assertThat(result.endsAt()).isEqualTo(at("2026-11-18T16:00"));
        assertThat(result.proposedStartsAt()).isNull();
        verify(events).publishEvent(new BookingChanged(BOOKING, BookingStatus.PROPOSED, BookingStatus.CONFIRMED,
                null));
    }

    @Test
    void acceptingWithoutAProposalIsAnInvalidTransition() {
        when(repository.lockById(BOOKING)).thenReturn(Optional.of(booking(BookingStatus.REQUESTED,
                "2026-11-17T09:00")));

        assertThatThrownBy(() -> service.acceptProposal(BUYER_EMAIL, BOOKING))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo(BookingRules.INVALID_TRANSITION);
    }

    @Test
    void buyerCancelsAnActiveBookingAndTheReasonOnlyTravelsInTheEvent() {
        when(repository.lockById(BOOKING)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED,
                "2026-11-17T09:00")));

        Booking result = service.cancelByBuyer(BUYER_EMAIL, BOOKING, " Me salió un viaje ");

        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED_BY_BUYER);
        assertThat(result.storeNote()).isNull();
        verify(events).publishEvent(new BookingChanged(BOOKING, BookingStatus.CONFIRMED,
                BookingStatus.CANCELLED_BY_BUYER, "Me salió un viaje"));
    }

    @Test
    void buyerCannotCancelAClosedBooking() {
        when(repository.lockById(BOOKING)).thenReturn(Optional.of(booking(BookingStatus.COMPLETED,
                "2026-11-13T09:00")));

        assertThatThrownBy(() -> service.cancelByBuyer(BUYER_EMAIL, BOOKING, null))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo(BookingRules.INVALID_TRANSITION);
    }

    // --- Almacén --------------------------------------------------------------------------------------------------------

    @Test
    void bookingOfAnotherStoreIsNotFound() {
        ServiceBooking foreign = booking(BookingStatus.REQUESTED, "2026-11-17T09:00");
        foreign.setStoreId(OTHER_STORE);
        when(repository.findById(BOOKING)).thenReturn(Optional.of(foreign));
        when(repository.lockById(BOOKING)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.getForStore(STORE, BOOKING))
                .isInstanceOf(NotFoundException.class).extracting("code").isEqualTo("BOOKING_NOT_FOUND");
        assertThatThrownBy(() -> service.decide(STORE, BOOKING, STAFF_EMAIL, StoreAction.CONFIRM, null))
                .isInstanceOf(NotFoundException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void storeConfirmsARequestAndItIsAudited() {
        when(repository.lockById(BOOKING)).thenReturn(Optional.of(booking(BookingStatus.REQUESTED,
                "2026-11-17T09:00")));

        Booking result = service.decide(STORE, BOOKING, STAFF_EMAIL, StoreAction.CONFIRM,
                new BookingDecision(null, null, 3L));

        assertThat(result.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result.buyer()).isEqualTo(new Booking.Buyer("Ana Pérez", PHONE));
        verify(auditService).record(eq(BookingFixtures.STAFF), eq("BOOKING_CONFIRMED"), eq(BookingService.ENTITY),
                eq(BOOKING), anyMap());
        verify(events).publishEvent(new BookingChanged(BOOKING, BookingStatus.REQUESTED, BookingStatus.CONFIRMED,
                null));
    }

    @Test
    void storeCannotConfirmItsOwnProposal() {
        ServiceBooking proposed = booking(BookingStatus.PROPOSED, "2026-11-17T09:00");
        proposed.setProposedStartsAt(at("2026-11-18T15:00").toInstant());
        when(repository.lockById(BOOKING)).thenReturn(Optional.of(proposed));

        assertThatThrownBy(() -> service.decide(STORE, BOOKING, STAFF_EMAIL, StoreAction.CONFIRM, null))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo(BookingRules.INVALID_TRANSITION);
    }

    @Test
    void invalidStoreTransitionIsAConflict() {
        when(repository.lockById(BOOKING)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED,
                "2026-11-17T09:00")));

        assertThatThrownBy(() -> service.decide(STORE, BOOKING, STAFF_EMAIL, StoreAction.REJECT,
                new BookingDecision(null, "No hay técnico", null)))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo(BookingRules.INVALID_TRANSITION);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void completingBeforeTheStartIsAConflictAndAfterItWorks() {
        ServiceBooking upcoming = booking(BookingStatus.CONFIRMED, "2026-11-17T09:00");
        when(repository.lockById(BOOKING)).thenReturn(Optional.of(upcoming));

        assertThatThrownBy(() -> service.decide(STORE, BOOKING, STAFF_EMAIL, StoreAction.COMPLETE, null))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo(BookingRules.INVALID_TRANSITION);

        when(repository.lockById(BOOKING)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED,
                "2026-11-13T09:00")));
        assertThat(service.decide(STORE, BOOKING, STAFF_EMAIL, StoreAction.COMPLETE, null).status())
                .isEqualTo(BookingStatus.COMPLETED);
    }

    @Test
    void staleVersionIsAConflict() {
        when(repository.lockById(BOOKING)).thenReturn(Optional.of(booking(BookingStatus.REQUESTED,
                "2026-11-17T09:00")));

        assertThatThrownBy(() -> service.decide(STORE, BOOKING, STAFF_EMAIL, StoreAction.CONFIRM,
                new BookingDecision(null, null, 2L)))
                .isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("BOOKING_VERSION_CONFLICT");
    }

    @Test
    void rejectAndCancelRequireANote() {
        assertThatThrownBy(() -> service.decide(STORE, BOOKING, STAFF_EMAIL, StoreAction.REJECT,
                new BookingDecision(null, "  ", null)))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("BOOKING_NOTE_REQUIRED");
        assertThatThrownBy(() -> service.decide(STORE, BOOKING, STAFF_EMAIL, StoreAction.CANCEL, null))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo("BOOKING_NOTE_REQUIRED");
    }

    @Test
    void rejectingStoresTheNoteAndHidesThePhoneAndPlate() {
        when(repository.lockById(BOOKING)).thenReturn(Optional.of(booking(BookingStatus.REQUESTED,
                "2026-11-17T09:00")));

        Booking result = service.decide(STORE, BOOKING, STAFF_EMAIL, StoreAction.REJECT,
                new BookingDecision(null, "No hay cupo esa semana", null));

        assertThat(result.status()).isEqualTo(BookingStatus.REJECTED);
        assertThat(result.storeNote()).isEqualTo("No hay cupo esa semana");
        assertThat(result.contactPhone()).isNull();
        assertThat(result.buyer()).isEqualTo(new Booking.Buyer("Ana Pérez", null));
        assertThat(result.vehicle().plate()).isNull();
        assertThat(result.vehicle().year()).isEqualTo(2018);
    }

    @Test
    void proposingValidatesTheSlotAndKeepsTheOriginalTime() {
        when(repository.lockById(BOOKING)).thenReturn(Optional.of(booking(BookingStatus.REQUESTED,
                "2026-11-17T09:00")));

        assertThatThrownBy(() -> service.decide(STORE, BOOKING, STAFF_EMAIL, StoreAction.PROPOSE,
                new BookingDecision(at("2026-11-18T15:30"), null, null)))
                .isInstanceOf(BadRequestException.class)
                .extracting("code").isEqualTo(BookingRules.SLOT_INVALID);

        Booking result = service.decide(STORE, BOOKING, STAFF_EMAIL, StoreAction.PROPOSE,
                new BookingDecision(at("2026-11-18T15:00"), "Ese día sí podemos", null));
        assertThat(result.status()).isEqualTo(BookingStatus.PROPOSED);
        assertThat(result.proposedStartsAt()).isEqualTo(at("2026-11-18T15:00"));
        assertThat(result.startsAt()).isEqualTo(at("2026-11-17T09:00"));
    }

    @Test
    void storeListHidesThePhoneOnlyForCancelledBookings() {
        ServiceBooking confirmed = booking(BookingStatus.CONFIRMED, "2026-11-17T09:00");
        ServiceBooking cancelled = booking(BookingStatus.CANCELLED_BY_BUYER, "2026-11-17T10:00");
        cancelled.setId(UUID.randomUUID());
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(confirmed, cancelled)));

        List<Booking> items = service.listForStore(STORE, null, null, null, null, null, null).items();

        assertThat(items.get(0).buyer().phone()).isEqualTo(PHONE);
        assertThat(items.get(0).contactPhone()).isEqualTo(PHONE);
        assertThat(items.get(1).buyer().phone()).isNull();
        assertThat(items.get(1).contactPhone()).isNull();
        assertThat(items.get(1).buyer().name()).isEqualTo("Ana Pérez");
    }

    // --- Avisos tras el commit ------------------------------------------------------------------------------------------

    @Test
    void notificationsListenAfterCommitAndInTheBackground() throws Exception {
        Method listener = BookingNotifications.class.getDeclaredMethod("on", BookingChanged.class);

        TransactionalEventListener annotation = listener.getAnnotation(TransactionalEventListener.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(listener.getAnnotation(Async.class).value())
                .isEqualTo(BookingAsyncConfig.BOOKING_NOTIFICATION_EXECUTOR);
    }

    @Test
    void newRequestNotifiesOwnersAndConfiguredRecipients() {
        Notifier notifier = new Notifier(true);
        when(repository.findById(BOOKING)).thenReturn(Optional.of(booking(BookingStatus.REQUESTED,
                "2026-11-17T09:00")));

        notifier.notifications.changed(new BookingChanged(BOOKING, null, BookingStatus.REQUESTED, null));

        verify(notifier.inApp).notify(eq(BookingFixtures.STAFF), eq(NotificationKind.ORDER_STATUS), anyString(),
                anyString(), eq("/portal/" + STORE + "/citas?cita=" + BOOKING));
        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        verify(notifier.email, org.mockito.Mockito.times(2)).sendBookingRequested(to.capture(), any(),
                eq("https://autopartco.co/portal/" + STORE + "/citas?cita=" + BOOKING));
        assertThat(to.getAllValues()).containsExactly("citas@taller.co", "taller@correo.co");
    }

    @Test
    void newRequestWithoutTheSettingOnlyNotifiesInApp() {
        Notifier notifier = new Notifier(false);
        when(repository.findById(BOOKING)).thenReturn(Optional.of(booking(BookingStatus.REQUESTED,
                "2026-11-17T09:00")));

        notifier.notifications.changed(new BookingChanged(BOOKING, null, BookingStatus.REQUESTED, null));

        verify(notifier.inApp).notify(eq(BookingFixtures.STAFF), any(), anyString(), anyString(), anyString());
        verify(notifier.email, never()).sendBookingRequested(anyString(), any(), anyString());
    }

    @Test
    void statusChangeNotifiesTheBuyerInAppAndByEmail() {
        Notifier notifier = new Notifier(true);
        ServiceBooking rejected = booking(BookingStatus.REJECTED, "2026-11-17T09:00");
        rejected.setStoreNote("Sin repuestos");
        when(repository.findById(BOOKING)).thenReturn(Optional.of(rejected));

        notifier.notifications.changed(new BookingChanged(BOOKING, BookingStatus.REQUESTED, BookingStatus.REJECTED,
                null));

        verify(notifier.inApp).notify(eq(BUYER), eq(NotificationKind.ORDER_STATUS),
                eq("Tu solicitud de cita no fue aceptada"), anyString(), eq("/mis-citas/" + BOOKING));
        ArgumentCaptor<EmailService.BookingMail> mail = ArgumentCaptor.forClass(EmailService.BookingMail.class);
        verify(notifier.email).sendBookingUpdate(eq(BUYER_EMAIL), eq("Ana"), eq("Tu solicitud de cita no fue aceptada"),
                anyString(), mail.capture(), eq("https://autopartco.co/mis-citas/" + BOOKING));
        assertThat(mail.getValue().code()).isEqualTo("CT-7K3P9Q");
        assertThat(mail.getValue().note()).isEqualTo("Sin repuestos");
        assertThat(mail.getValue().when()).contains("17 de noviembre de 2026");
        verify(notifier.email, never()).sendBookingRequested(anyString(), any(), anyString());
    }

    /** {@link BookingNotifications} con sus colaboradores simulados. */
    private final class Notifier {
        final NotificationService inApp = mock(NotificationService.class);
        final EmailService email = mock(EmailService.class);
        final SettingsQueries settings = mock(SettingsQueries.class);
        final AppProperties properties = mock(AppProperties.class);
        final BookingNotifications notifications;

        Notifier(boolean newBooking) {
            when(properties.frontendBaseUrl()).thenReturn("https://autopartco.co/");
            when(settings.storeSettings(STORE)).thenReturn(new StoreSettings(
                    new StoreSettings.Contact(true, "Hola {enlace}"),
                    new StoreSettings.Profile(true, List.of(), null), new StoreSettings.Catalog(false),
                    new StoreSettings.Notifications(true, true, true, true, true, newBooking,
                            List.of("Citas@Taller.co", "taller@correo.co")),
                    new StoreSettings.Security(false)));
            when(catalog.owners(STORE)).thenReturn(List.of(new BookingCatalog.Owner(BookingFixtures.STAFF,
                    "TALLER@correo.co")));
            when(accountLookup.findById(BUYER)).thenReturn(Optional.of(BookingFixtures.buyer()));
            notifications = new BookingNotifications(repository, catalog, accountLookup, inApp, email, settings,
                    properties);
        }
    }

    @Test
    void dueCheckUsesTheProposedTimeForProposals() {
        ServiceBooking proposed = booking(BookingStatus.PROPOSED, "2026-11-16T07:00");
        proposed.setProposedStartsAt(at("2026-11-18T15:00").toInstant());
        assertThat(BookingService.isDue(proposed, BookingFixtures.NOW)).isFalse();
        proposed.setProposedStartsAt(BookingFixtures.NOW.minus(Duration.ofMinutes(1)));
        assertThat(BookingService.isDue(proposed, BookingFixtures.NOW)).isTrue();
        assertThat(BookingService.isDue(booking(BookingStatus.CONFIRMED, "2026-11-13T09:00"), BookingFixtures.NOW))
                .isFalse();
        verify(auditService, never()).record(isNull(), anyString(), anyString(), any(), anyMap());
    }
}
