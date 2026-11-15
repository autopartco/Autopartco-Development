package com.autopartco.backend.core.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.autopartco.backend.core.audit.AuditService;
import com.autopartco.backend.core.booking.internal.BookingCatalog;
import com.autopartco.backend.core.booking.internal.BookingCatalog.BranchInfo;
import com.autopartco.backend.core.booking.internal.BookingCatalog.ServiceInfo;
import com.autopartco.backend.core.booking.internal.BookingChanged;
import com.autopartco.backend.core.booking.internal.ServiceBooking;
import com.autopartco.backend.core.booking.internal.ServiceBookingRepository;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;
import com.autopartco.backend.shared.error.NotFoundException;
import com.autopartco.backend.shared.error.TooManyRequestsException;
import com.autopartco.backend.shared.web.PageResponse;

/**
 * Citas de servicio. El comprador solo ve y cambia las suyas y el almacén solo las de su {@code storeId}: una ajena es
 * 404 {@code BOOKING_NOT_FOUND}. Los permisos del portal los resuelve quien llama ({@code storesuite}). Cada alta o
 * cambio de estado queda en auditoría y publica {@link BookingChanged}; los avisos salen después del commit.
 *
 * <p>Errores: 400 {@code BOOKING_SLOT_INVALID}, {@code BRANCH_NOT_IN_SERVICE}, {@code BOOKING_DISABLED},
 * {@code CONTACT_PHONE_REQUIRED}, {@code CONTACT_PHONE_INVALID}, {@code BOOKING_NOTE_REQUIRED},
 * {@code BOOKING_NOTE_INVALID}; 404 {@code SERVICE_NOT_FOUND}, {@code BOOKING_NOT_FOUND}; 409
 * {@code BOOKING_INVALID_TRANSITION}, {@code BOOKING_VERSION_CONFLICT}; 429 {@code BOOKING_LIMIT_REACHED}.</p>
 */
@Service
public class BookingService {
    static final String ENTITY = "SERVICE_BOOKING";
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 50;
    static final int CODE_ATTEMPTS = 8;

    private final ServiceBookingRepository repository;
    private final BookingCatalog catalog;
    private final AccountLookup accountLookup;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    public BookingService(ServiceBookingRepository repository, BookingCatalog catalog, AccountLookup accountLookup,
            AuditService auditService, ApplicationEventPublisher events) {
        this(repository, catalog, accountLookup, auditService, events, Clock.system(BookingRules.ZONE));
    }

    /** {@code clock} fija «ahora» (pruebas); la hora de las franjas siempre es la de Bogotá. */
    public BookingService(ServiceBookingRepository repository, BookingCatalog catalog, AccountLookup accountLookup,
            AuditService auditService, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.catalog = catalog;
        this.accountLookup = accountLookup;
        this.auditService = auditService;
        this.events = events;
        this.clock = clock;
    }

    // --- Comprador --------------------------------------------------------------------------------------------------

    /** Solicita una cita ({@code REQUESTED}) con el precio «desde» vigente del servicio. */
    @Transactional
    public Booking request(String email, BookingRequest request) {
        AccountRef account = accountLookup.requireByEmail(email);
        ServiceInfo service = catalog.service(request.serviceId()).filter(ServiceInfo::visible)
                .orElseThrow(BookingService::serviceNotFound);
        BranchInfo branch = catalog.serviceBranch(service.id(), request.branchId()).filter(BranchInfo::active)
                .orElseThrow(BookingService::branchNotInService);
        if (!service.bookingEnabled() || !BookingRules.hasWeeklyHours(branch.openingHours())) {
            throw bookingDisabled();
        }
        Instant now = clock.instant();
        Instant startsAt = BookingRules.requireSlot(request.startsAt(), service.slotMinutes(), branch.openingHours(),
                now);
        String phone = contactPhone(account.accountId(), request.contactPhone());
        String note = BookingRules.note(request.note());

        // Serializa las solicitudes del mismo comprador: los límites no se pueden saltar con peticiones simultáneas.
        catalog.lockAccount(account.accountId());
        if (repository.countByAccountAndStore(account.accountId(), service.storeId(), BookingStatus.ACTIVE)
                >= BookingRules.MAX_ACTIVE_PER_STORE) {
            throw new TooManyRequestsException("BOOKING_LIMIT_REACHED", "Ya tienes "
                    + BookingRules.MAX_ACTIVE_PER_STORE + " citas activas en este almacén. Espera a que se atiendan o "
                    + "cancela alguna.");
        }
        Instant startOfDay = LocalDate.ofInstant(now, BookingRules.ZONE).atStartOfDay(BookingRules.ZONE).toInstant();
        if (repository.countCreatedSince(account.accountId(), startOfDay) >= BookingRules.MAX_REQUESTS_PER_DAY) {
            throw new TooManyRequestsException("BOOKING_LIMIT_REACHED", "Llegaste al máximo de "
                    + BookingRules.MAX_REQUESTS_PER_DAY + " solicitudes de cita por día. Inténtalo mañana.");
        }

        ServiceBooking booking = new ServiceBooking();
        booking.setCode(uniqueCode());
        booking.setServiceId(service.id());
        booking.setStoreId(service.storeId());
        booking.setBranchId(branch.id());
        booking.setAccountId(account.accountId());
        booking.setStartsAt(startsAt);
        booking.setEndsAt(startsAt.plus(Duration.ofMinutes(service.slotMinutes())));
        booking.setStatus(BookingStatus.REQUESTED);
        booking.setVehicle(request.vehicle() == null ? null : request.vehicle().toJson());
        booking.setContactPhone(phone);
        booking.setBuyerNote(note);
        // Precio «desde» al pedir: el almacén se compromete a ese valor de referencia. Promoción: cuando exista (T4).
        booking.setQuotedPrice(service.priceFrom());
        booking.setCreatedAt(now);
        ServiceBooking saved = repository.saveAndFlush(booking);
        audit(account.accountId(), "BOOKING_REQUESTED", saved, null);
        events.publishEvent(new BookingChanged(saved.getId(), null, BookingStatus.REQUESTED, null));
        return view(saved, service, branch, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<Booking> listMine(String email, BookingStatus status, Integer page, Integer size) {
        AccountRef account = accountLookup.requireByEmail(email);
        Specification<ServiceBooking> spec = (root, query, cb) -> cb.equal(root.get("accountId"), account.accountId());
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        return page(spec, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")), page, size, false);
    }

    @Transactional(readOnly = true)
    public Booking getMine(String email, UUID bookingId) {
        AccountRef account = accountLookup.requireByEmail(email);
        ServiceBooking booking = repository.findById(bookingId)
                .filter(found -> found.getAccountId().equals(account.accountId()))
                .orElseThrow(BookingService::bookingNotFound);
        return views(List.of(booking), false).getFirst();
    }

    /** El comprador acepta la hora propuesta por el almacén: queda {@code CONFIRMED} en esa hora. */
    @Transactional
    public Booking acceptProposal(String email, UUID bookingId) {
        AccountRef account = accountLookup.requireByEmail(email);
        ServiceBooking booking = lockOwn(account.accountId(), bookingId);
        Instant now = clock.instant();
        if (booking.getStatus() != BookingStatus.PROPOSED) {
            throw invalidTransition("Solo puedes aceptar una cita con una hora propuesta por el almacén.");
        }
        Instant proposed = booking.getProposedStartsAt();
        if (proposed == null || !proposed.isAfter(now)) {
            throw invalidTransition("La hora propuesta ya pasó.");
        }
        Duration length = Duration.between(booking.getStartsAt(), booking.getEndsAt());
        BookingStatus previous = booking.getStatus();
        booking.setStartsAt(proposed);
        booking.setEndsAt(proposed.plus(length));
        booking.setProposedStartsAt(null);
        booking.setStatus(BookingStatus.CONFIRMED);
        ServiceBooking saved = repository.saveAndFlush(booking);
        audit(account.accountId(), "BOOKING_PROPOSAL_ACCEPTED", saved, previous);
        events.publishEvent(new BookingChanged(saved.getId(), previous, saved.getStatus(), null));
        return views(List.of(saved), false).getFirst();
    }

    /** El comprador cancela una cita activa que aún no empieza. {@code reason} solo se envía al almacén. */
    @Transactional
    public Booking cancelByBuyer(String email, UUID bookingId, String reason) {
        AccountRef account = accountLookup.requireByEmail(email);
        String cleanReason = BookingRules.note(reason);
        ServiceBooking booking = lockOwn(account.accountId(), bookingId);
        Instant now = clock.instant();
        BookingRules.requireTransition(booking.getStatus(), BookingStatus.CANCELLED_BY_BUYER, booking.getStartsAt(),
                now);
        if (!booking.getStartsAt().isAfter(now) && booking.getStatus() == BookingStatus.CONFIRMED) {
            throw invalidTransition("La cita ya empezó; comunícate con el almacén.");
        }
        BookingStatus previous = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED_BY_BUYER);
        ServiceBooking saved = repository.saveAndFlush(booking);
        audit(account.accountId(), "BOOKING_CANCELLED_BY_BUYER", saved, previous);
        events.publishEvent(new BookingChanged(saved.getId(), previous, saved.getStatus(), cleanReason));
        return views(List.of(saved), false).getFirst();
    }

    // --- Almacén ----------------------------------------------------------------------------------------------------

    /** Bandeja del almacén. {@code from}/{@code to}: días de Bogotá (inclusive) sobre {@code starts_at}. */
    @Transactional(readOnly = true)
    public PageResponse<Booking> listForStore(UUID storeId, BookingStatus status, UUID branchId, LocalDate from,
            LocalDate to, Integer page, Integer size) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BadRequestException("BOOKING_RANGE_INVALID", "La fecha final debe ser posterior a la inicial.");
        }
        Specification<ServiceBooking> spec = (root, query, cb) -> cb.equal(root.get("storeId"), storeId);
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (branchId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("branchId"), branchId));
        }
        if (from != null) {
            Instant start = from.atStartOfDay(BookingRules.ZONE).toInstant();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startsAt"), start));
        }
        if (to != null) {
            Instant end = to.plusDays(1).atStartOfDay(BookingRules.ZONE).toInstant();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("startsAt"), end));
        }
        return page(spec, Sort.by(Sort.Order.asc("startsAt"), Sort.Order.asc("id")), page, size, true);
    }

    @Transactional(readOnly = true)
    public Booking getForStore(UUID storeId, UUID bookingId) {
        ServiceBooking booking = repository.findById(bookingId)
                .filter(found -> found.getStoreId().equals(storeId))
                .orElseThrow(BookingService::bookingNotFound);
        return views(List.of(booking), true).getFirst();
    }

    /**
     * Acción del almacén: confirmar o proponer otra hora (solo {@code REQUESTED}), rechazar (con nota), cancelar una
     * confirmada (con nota) y cerrar como atendida o no asistió (después de la hora).
     */
    @Transactional
    public Booking decide(UUID storeId, UUID bookingId, String actorEmail, StoreAction action,
            BookingDecision decision) {
        BookingDecision body = decision == null ? BookingDecision.empty() : decision;
        UUID actorId = accountLookup.requireByEmail(actorEmail).accountId();
        String note = BookingRules.note(body.note());
        if (action.requiresNote() && note == null) {
            throw new BadRequestException("BOOKING_NOTE_REQUIRED", "Escribe una nota para el comprador.");
        }
        ServiceBooking booking = repository.lockById(bookingId)
                .filter(found -> found.getStoreId().equals(storeId))
                .orElseThrow(BookingService::bookingNotFound);
        if (body.version() != null && !body.version().equals(booking.getVersion())) {
            throw new ConflictException("BOOKING_VERSION_CONFLICT",
                    "La cita cambió mientras la revisabas. Recárgala e inténtalo de nuevo.");
        }
        Instant now = clock.instant();
        BookingStatus previous = booking.getStatus();
        BookingStatus target = action.target();
        BookingRules.requireTransition(previous, target, booking.getStartsAt(), now);
        switch (action) {
            case CONFIRM -> {
                // Confirmar una contrapropuesta le corresponde al comprador.
                if (previous != BookingStatus.REQUESTED) {
                    throw invalidTransition("Esta cita espera la respuesta del comprador.");
                }
                if (!booking.getStartsAt().isAfter(now)) {
                    throw invalidTransition("La hora pedida ya pasó; propón otra hora o rechaza la solicitud.");
                }
            }
            case PROPOSE -> {
                ServiceInfo service = catalog.service(booking.getServiceId()).orElseThrow(BookingService::serviceNotFound);
                BranchInfo branch = catalog.serviceBranch(booking.getServiceId(), booking.getBranchId())
                        .orElseThrow(BookingService::branchNotInService);
                Instant proposed = BookingRules.requireSlot(body.startsAt(), service.slotMinutes(),
                        branch.openingHours(), now);
                booking.setProposedStartsAt(proposed);
            }
            default -> {
            }
        }
        if (note != null) {
            booking.setStoreNote(note);
        }
        booking.setStatus(target);
        ServiceBooking saved = repository.saveAndFlush(booking);
        audit(actorId, "BOOKING_" + target.name(), saved, previous);
        events.publishEvent(new BookingChanged(saved.getId(), previous, target, null));
        return views(List.of(saved), true).getFirst();
    }

    // --- Sistema ----------------------------------------------------------------------------------------------------

    /**
     * Vence una solicitud sin respuesta cuya hora llegó ({@code REQUESTED}/{@code PROPOSED} → {@code EXPIRED}).
     * Idempotente: {@code false} si ya no aplica.
     */
    @Transactional
    public boolean expire(UUID bookingId) {
        ServiceBooking booking = repository.lockById(bookingId).orElse(null);
        Instant now = clock.instant();
        if (booking == null || !isDue(booking, now)) {
            return false;
        }
        BookingStatus previous = booking.getStatus();
        booking.setStatus(BookingStatus.EXPIRED);
        ServiceBooking saved = repository.saveAndFlush(booking);
        audit(null, "BOOKING_EXPIRED", saved, previous);
        events.publishEvent(new BookingChanged(saved.getId(), previous, BookingStatus.EXPIRED, null));
        return true;
    }

    static boolean isDue(ServiceBooking booking, Instant now) {
        return switch (booking.getStatus()) {
            case REQUESTED -> !booking.getStartsAt().isAfter(now);
            case PROPOSED -> !(booking.getProposedStartsAt() == null ? booking.getStartsAt()
                    : booking.getProposedStartsAt()).isAfter(now);
            default -> false;
        };
    }

    // --- Apoyo ------------------------------------------------------------------------------------------------------

    private String contactPhone(UUID accountId, String requested) {
        if (requested != null && !requested.isBlank()) {
            String phone = BookingRules.normalizePhone(requested);
            if (phone == null) {
                throw new BadRequestException("CONTACT_PHONE_INVALID",
                        "Escribe un teléfono colombiano de 10 dígitos.");
            }
            return phone;
        }
        return catalog.profilePhone(accountId).map(BookingRules::normalizePhone)
                .orElseThrow(() -> new BadRequestException("CONTACT_PHONE_REQUIRED",
                        "Escribe un teléfono de contacto para la cita."));
    }

    private String uniqueCode() {
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            String code = BookingRules.newCode();
            if (!repository.existsByCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique booking code.");
    }

    private ServiceBooking lockOwn(UUID accountId, UUID bookingId) {
        return repository.lockById(bookingId)
                .filter(found -> found.getAccountId().equals(accountId))
                .orElseThrow(BookingService::bookingNotFound);
    }

    private PageResponse<Booking> page(Specification<ServiceBooking> spec, Sort sort, Integer page, Integer size,
            boolean portal) {
        int pageNumber = page == null ? 1 : Math.max(1, page);
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        Page<ServiceBooking> result = repository.findAll(spec, PageRequest.of(pageNumber - 1, pageSize, sort));
        return PageResponse.of(views(result.getContent(), portal), pageNumber, pageSize, result.getTotalElements());
    }

    private List<Booking> views(List<ServiceBooking> bookings, boolean portal) {
        if (bookings.isEmpty()) {
            return List.of();
        }
        Set<UUID> serviceIds = new HashSet<>();
        Set<UUID> branchIds = new HashSet<>();
        Set<UUID> accountIds = new HashSet<>();
        bookings.forEach(booking -> {
            serviceIds.add(booking.getServiceId());
            branchIds.add(booking.getBranchId());
            accountIds.add(booking.getAccountId());
        });
        Map<UUID, ServiceInfo> services = catalog.services(serviceIds);
        Map<UUID, BranchInfo> branches = catalog.branches(branchIds);
        Map<UUID, AccountRef> buyers = portal ? accountLookup.findAllByIds(accountIds) : Map.of();
        List<Booking> views = new ArrayList<>(bookings.size());
        for (ServiceBooking booking : bookings) {
            Booking.Buyer buyer = null;
            if (portal) {
                AccountRef ref = buyers.get(booking.getAccountId());
                buyer = new Booking.Buyer(ref == null ? null : ref.fullName(),
                        booking.getStatus().cancelled() ? null : booking.getContactPhone());
            }
            views.add(view(booking, services.get(booking.getServiceId()), branches.get(booking.getBranchId()),
                    buyer));
        }
        return views;
    }

    /** {@code buyer} no nulo = vista del portal (oculta teléfono y placa en canceladas). */
    static Booking view(ServiceBooking booking, ServiceInfo service, BranchInfo branch, Booking.Buyer buyer) {
        boolean hidePersonal = buyer != null && booking.getStatus().cancelled();
        BookingVehicle vehicle = BookingVehicle.fromJson(booking.getVehicle());
        if (vehicle != null && hidePersonal) {
            vehicle = vehicle.withoutPlate();
        }
        return new Booking(
                booking.getId(),
                booking.getCode(),
                booking.getStatus(),
                local(booking.getStartsAt()),
                local(booking.getEndsAt()),
                local(booking.getProposedStartsAt()),
                service == null ? new Booking.ServiceRef(booking.getServiceId(), null, null, null)
                        : new Booking.ServiceRef(service.id(), service.name(), service.category(), service.imageUrl()),
                service == null ? new Booking.StoreRef(booking.getStoreId(), null, null)
                        : new Booking.StoreRef(service.storeId(), service.storeName(), service.logoUrl()),
                branch == null ? new Booking.BranchRef(booking.getBranchId(), null, null, null, null, null, null, null,
                        null, null)
                        : new Booking.BranchRef(branch.id(), branch.name(), branch.address(), branch.city(),
                                branch.department(), branch.latitude(), branch.longitude(), branch.phone(),
                                branch.whatsapp(), branch.openingHours()),
                booking.getQuotedPrice(),
                booking.getPromotionId() == null ? null : new Booking.PromotionRef(booking.getPromotionId()),
                vehicle,
                hidePersonal ? null : booking.getContactPhone(),
                booking.getBuyerNote(),
                booking.getStoreNote(),
                local(booking.getCreatedAt()),
                booking.getVersion(),
                buyer);
    }

    static OffsetDateTime local(Instant instant) {
        return instant == null ? null : instant.atZone(BookingRules.ZONE).toOffsetDateTime();
    }

    private void audit(UUID accountId, String action, ServiceBooking booking, BookingStatus previous) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("storeId", booking.getStoreId().toString());
        metadata.put("code", booking.getCode());
        if (previous != null) {
            metadata.put("from", previous.name());
        }
        metadata.put("to", booking.getStatus().name());
        auditService.record(accountId, action, ENTITY, booking.getId(), metadata);
    }

    private static NotFoundException serviceNotFound() {
        return new NotFoundException("SERVICE_NOT_FOUND", "No se encontró el servicio.");
    }

    private static NotFoundException bookingNotFound() {
        return new NotFoundException("BOOKING_NOT_FOUND", "No se encontró la cita.");
    }

    private static BadRequestException branchNotInService() {
        return new BadRequestException("BRANCH_NOT_IN_SERVICE", "La sede no presta este servicio.");
    }

    static BadRequestException bookingDisabled() {
        return new BadRequestException("BOOKING_DISABLED",
                "Este servicio no recibe citas en línea en esta sede. Escríbele al almacén por WhatsApp.");
    }

    private static ConflictException invalidTransition(String message) {
        return new ConflictException(BookingRules.INVALID_TRANSITION, message);
    }
}
