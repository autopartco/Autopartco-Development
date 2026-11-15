package com.autopartco.backend.core.booking;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

import com.autopartco.backend.core.booking.internal.BookingAsyncConfig;
import com.autopartco.backend.core.booking.internal.BookingCatalog;
import com.autopartco.backend.core.booking.internal.BookingCatalog.BranchInfo;
import com.autopartco.backend.core.booking.internal.BookingCatalog.ServiceInfo;
import com.autopartco.backend.core.booking.internal.BookingChanged;
import com.autopartco.backend.core.booking.internal.ServiceBooking;
import com.autopartco.backend.core.booking.internal.ServiceBookingRepository;
import com.autopartco.backend.core.identity.AccountLookup;
import com.autopartco.backend.core.identity.AccountRef;
import com.autopartco.backend.core.notification.EmailService;
import com.autopartco.backend.core.notification.EmailService.BookingMail;
import com.autopartco.backend.core.notification.NotificationKind;
import com.autopartco.backend.core.notification.NotificationService;
import com.autopartco.backend.core.settings.SettingsQueries;
import com.autopartco.backend.core.settings.StoreSettings;
import com.autopartco.backend.shared.config.AppProperties;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Avisos de la agenda, después del commit y en segundo plano (un fallo no revierte la cita):
 * <ul>
 * <li>Solicitud nueva o cancelación del comprador → almacén: notificación in-app a los dueños y, si
 * {@code notifications.newBooking}, correo a {@code notifications.recipients} y a los dueños.</li>
 * <li>Aceptación de la hora propuesta → dueños (in-app).</li>
 * <li>Confirmada, nueva hora propuesta, rechazada, cancelada por el almacén o vencida → comprador (in-app y correo);
 * atendida o no asistió → comprador (in-app).</li>
 * <li>Recordatorio 24 h antes de una cita confirmada → comprador (in-app y correo); lo dispara
 * {@link BookingReminderJob}.</li>
 * </ul>
 * Los correos de citas son transaccionales (no dependen de preferencias de marketing). Los registros no incluyen
 * correos, teléfonos ni notas.
 */
@Component
@AllArgsConstructor
@Slf4j
public class BookingNotifications {
    static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, h:mm a", Locale.forLanguageTag("es-CO"));

    private final ServiceBookingRepository repository;
    private final BookingCatalog catalog;
    private final AccountLookup accountLookup;
    private final NotificationService notifications;
    private final EmailService emailService;
    private final SettingsQueries settingsQueries;
    private final AppProperties appProperties;

    /** Ruta del portal que abre la cita en la bandeja. */
    static String portalPath(UUID storeId, UUID bookingId) {
        return "/portal/" + storeId + "/citas?cita=" + bookingId;
    }

    /** Ruta de la cita en la cuenta del comprador. */
    static String buyerPath(UUID bookingId) {
        return "/mis-citas/" + bookingId;
    }

    @Async(BookingAsyncConfig.BOOKING_NOTIFICATION_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(BookingChanged event) {
        try {
            changed(event);
        } catch (RuntimeException failure) {
            log.error("Could not notify booking change ({})", failure.getClass().getSimpleName());
        }
    }

    /** Envía los avisos de un alta o cambio de estado ya confirmado en la BD. */
    void changed(BookingChanged event) {
        Optional<Context> found = context(event.bookingId());
        if (found.isEmpty()) {
            return;
        }
        Context context = found.get();
        switch (event.status()) {
            case REQUESTED -> notifyStore(context, "Nueva solicitud de cita: " + context.service().name(),
                    "Piden " + context.service().name() + " para el " + when(context.booking().getStartsAt()) + ".",
                    true);
            case CANCELLED_BY_BUYER -> notifyStore(context, "Cita cancelada por el comprador",
                    "Cancelaron la cita " + context.booking().getCode() + " de " + context.service().name()
                            + (event.buyerReason() == null ? "." : ". Motivo: " + event.buyerReason()),
                    false);
            case CONFIRMED -> {
                if (event.previous() == BookingStatus.PROPOSED) {
                    notifyStore(context, "Aceptaron la nueva hora de la cita",
                            "La cita " + context.booking().getCode() + " quedó confirmada para el "
                                    + when(context.booking().getStartsAt()) + ".", false);
                } else {
                    notifyBuyer(context, "Tu cita fue confirmada", context.service().storeName()
                            + " confirmó tu cita. Te esperamos.", context.booking().getStartsAt(), true);
                }
            }
            case PROPOSED -> notifyBuyer(context, "El almacén propone otra hora", context.service().storeName()
                    + " no puede atenderte a la hora que pediste y te propone otra. Acéptala o cancela desde Mis citas.",
                    context.booking().getProposedStartsAt(), true);
            case REJECTED -> notifyBuyer(context, "Tu solicitud de cita no fue aceptada", context.service().storeName()
                    + " no pudo aceptar tu solicitud.", context.booking().getStartsAt(), true);
            case CANCELLED_BY_STORE -> notifyBuyer(context, "El almacén canceló tu cita",
                    context.service().storeName() + " canceló tu cita.", context.booking().getStartsAt(), true);
            case EXPIRED -> notifyBuyer(context, "Tu solicitud de cita venció", context.service().storeName()
                    + " no respondió antes de la hora pedida. Puedes pedir otra cita.",
                    context.booking().getStartsAt(), true);
            case COMPLETED -> notifyBuyer(context, "Cita atendida", "Tu cita en " + context.service().storeName()
                    + " quedó marcada como atendida.", context.booking().getStartsAt(), false);
            case NO_SHOW -> notifyBuyer(context, "Cita marcada como no asistida", context.service().storeName()
                    + " marcó que no asististe a tu cita.", context.booking().getStartsAt(), false);
        }
    }

    /** Recordatorio al comprador de una cita confirmada. {@code false} si la cita ya no está confirmada. */
    boolean remind(UUID bookingId) {
        Optional<Context> found = context(bookingId).filter(c -> c.booking().getStatus() == BookingStatus.CONFIRMED);
        if (found.isEmpty()) {
            return false;
        }
        Context context = found.get();
        notifyBuyer(context, "Recordatorio de tu cita", "Te recordamos tu cita en " + context.service().storeName() + " el "
                + when(context.booking().getStartsAt()) + ". Si no puedes ir, cancélala desde Mis citas.", context.booking().getStartsAt(), true);
        return true;
    }

    private void notifyStore(Context context, String title, String message, boolean newRequest) {
        ServiceBooking booking = context.booking();
        String path = portalPath(booking.getStoreId(), booking.getId());
        List<BookingCatalog.Owner> owners = catalog.owners(booking.getStoreId());
        for (BookingCatalog.Owner owner : owners) {
            try {
                notifications.notify(owner.accountId(), NotificationKind.ORDER_STATUS, title, message, path);
            } catch (RuntimeException failure) {
                log.warn("Could not store booking notification ({})", failure.getClass().getSimpleName());
            }
        }
        StoreSettings.Notifications preferences = settingsQueries.storeSettings(booking.getStoreId()).notifications();
        if (!preferences.newBooking()) {
            return;
        }
        Set<String> emails = new LinkedHashSet<>();
        preferences.recipients().stream().filter(email -> email != null && !email.isBlank())
                .forEach(email -> emails.add(email.strip().toLowerCase(Locale.ROOT)));
        owners.stream().map(BookingCatalog.Owner::email).filter(email -> email != null && !email.isBlank())
                .forEach(email -> emails.add(email.strip().toLowerCase(Locale.ROOT)));
        BookingMail mail = mail(context, booking.getStartsAt(), "Nota del comprador",
                newRequest ? booking.getBuyerNote() : null);
        String link = absolute(path);
        for (String email : emails) {
            try {
                if (newRequest) {
                    emailService.sendBookingRequested(email, mail, link);
                } else {
                    emailService.sendBookingUpdate(email, context.service().storeName(), title, message, mail, link);
                }
            } catch (RuntimeException failure) {
                log.warn("Could not email booking notification ({})", failure.getClass().getSimpleName());
            }
        }
    }

    private void notifyBuyer(Context context, String title, String message, Instant shownTime, boolean email) {
        ServiceBooking booking = context.booking();
        String path = buyerPath(booking.getId());
        notifications.notify(booking.getAccountId(), NotificationKind.ORDER_STATUS, title, message, path);
        if (!email) {
            return;
        }
        Optional<AccountRef> buyer = accountLookup.findById(booking.getAccountId());
        if (buyer.isEmpty() || buyer.get().email() == null) {
            return;
        }
        emailService.sendBookingUpdate(buyer.get().email(), firstName(buyer.get().givenName()), title, message,
                mail(context, shownTime, "Nota del almacén", booking.getStoreNote()), absolute(path));
    }

    private BookingMail mail(Context context, Instant shownTime, String noteLabel, String note) {
        return new BookingMail(context.booking().getCode(), context.service().name(), context.service().storeName(),
                context.branch() == null ? "" : context.branch().name(), when(shownTime), noteLabel, note);
    }

    private Optional<Context> context(UUID bookingId) {
        Optional<ServiceBooking> booking = repository.findById(bookingId);
        if (booking.isEmpty()) {
            return Optional.empty();
        }
        ServiceBooking found = booking.get();
        Optional<ServiceInfo> service = catalog.service(found.getServiceId());
        if (service.isEmpty()) {
            return Optional.empty();
        }
        BranchInfo branch = catalog.branches(List.of(found.getBranchId())).get(found.getBranchId());
        return Optional.of(new Context(found, service.get(), branch));
    }

    static String when(Instant instant) {
        return instant == null ? "" : WHEN.format(instant.atZone(BookingRules.ZONE));
    }

    /** URL absoluta del frontend para la ruta interna (con su consulta). */
    String absolute(String path) {
        return UriComponentsBuilder.fromUriString(appProperties.frontendBaseUrl().replaceAll("/+$", "") + path)
                .build()
                .toUriString();
    }

    private static String firstName(String name) {
        return name == null || name.isBlank() ? "" : name.strip().split("\\s+")[0];
    }

    private record Context(ServiceBooking booking, ServiceInfo service, BranchInfo branch) {
    }
}
