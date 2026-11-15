package com.autopartco.backend.core.booking;

import static com.autopartco.backend.core.booking.BookingStatus.CANCELLED_BY_BUYER;
import static com.autopartco.backend.core.booking.BookingStatus.CANCELLED_BY_STORE;
import static com.autopartco.backend.core.booking.BookingStatus.COMPLETED;
import static com.autopartco.backend.core.booking.BookingStatus.CONFIRMED;
import static com.autopartco.backend.core.booking.BookingStatus.EXPIRED;
import static com.autopartco.backend.core.booking.BookingStatus.NO_SHOW;
import static com.autopartco.backend.core.booking.BookingStatus.PROPOSED;
import static com.autopartco.backend.core.booking.BookingStatus.REJECTED;
import static com.autopartco.backend.core.booking.BookingStatus.REQUESTED;

import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

import com.autopartco.backend.shared.error.BadRequestException;
import com.autopartco.backend.shared.error.ConflictException;

/**
 * Reglas puras de la agenda (sin BD ni reloj propio):
 * <ul>
 * <li>Franja: hora de Bogotá, {@code [inicio, inicio + slot_minutes)} dentro de una franja de {@code opening_hours} y
 * alineada a {@code slot_minutes} contando desde la apertura de esa franja (08:30 con 60 min → 08:30, 09:30…), entre
 * {@value #MIN_LEAD_HOURS} h y {@value #MAX_DAYS_AHEAD} días desde ahora. Si no: 400 {@code BOOKING_SLOT_INVALID}.</li>
 * <li>Estados: {@code REQUESTED → CONFIRMED | PROPOSED | REJECTED | CANCELLED_BY_BUYER | EXPIRED};
 * {@code PROPOSED → CONFIRMED | CANCELLED_BY_BUYER | EXPIRED};
 * {@code CONFIRMED → COMPLETED | NO_SHOW | CANCELLED_BY_STORE | CANCELLED_BY_BUYER}. Otra: 409
 * {@code BOOKING_INVALID_TRANSITION}. {@code COMPLETED}/{@code NO_SHOW} solo después de {@code starts_at}.</li>
 * <li>Código visible {@code CT-XXXXXX} con un alfabeto sin caracteres ambiguos (sin 0/O, 1/I/L).</li>
 * </ul>
 */
public final class BookingRules {
    public static final ZoneId ZONE = ZoneId.of("America/Bogota");
    public static final int MIN_LEAD_HOURS = 2;
    public static final int MAX_DAYS_AHEAD = 60;
    public static final int MAX_ACTIVE_PER_STORE = 3;
    public static final int MAX_REQUESTS_PER_DAY = 10;
    public static final int NOTE_MAX = 500;
    public static final String SLOT_INVALID = "BOOKING_SLOT_INVALID";
    public static final String INVALID_TRANSITION = "BOOKING_INVALID_TRANSITION";
    static final String CODE_PREFIX = "CT-";
    static final String CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    static final int CODE_LENGTH = 6;
    private static final List<String> DAYS = List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");
    private static final Pattern E164_CO = Pattern.compile("^\\+57\\d{10}$");
    private static final Pattern LOCAL_10 = Pattern.compile("^\\d{10}$");
    private static final Random RANDOM = new SecureRandom();

    private static final Map<BookingStatus, Set<BookingStatus>> TRANSITIONS = new EnumMap<>(BookingStatus.class);

    static {
        TRANSITIONS.put(REQUESTED, EnumSet.of(CONFIRMED, PROPOSED, REJECTED, CANCELLED_BY_BUYER, EXPIRED));
        TRANSITIONS.put(PROPOSED, EnumSet.of(CONFIRMED, CANCELLED_BY_BUYER, EXPIRED));
        TRANSITIONS.put(CONFIRMED, EnumSet.of(COMPLETED, NO_SHOW, CANCELLED_BY_STORE, CANCELLED_BY_BUYER));
    }

    private BookingRules() {
    }

    public static boolean canTransition(BookingStatus from, BookingStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    /**
     * Valida la transición y, para {@code COMPLETED}/{@code NO_SHOW}, que la cita ya haya empezado.
     *
     * @throws ConflictException {@value #INVALID_TRANSITION}
     */
    public static void requireTransition(BookingStatus from, BookingStatus to, Instant startsAt, Instant now) {
        if (!canTransition(from, to)) {
            throw new ConflictException(INVALID_TRANSITION, "La cita no admite ese cambio en su estado actual.");
        }
        if ((to == COMPLETED || to == NO_SHOW) && now.isBefore(startsAt)) {
            throw new ConflictException(INVALID_TRANSITION,
                    "Solo puedes cerrar la cita después de la hora agendada.");
        }
    }

    /** Franjas {@code [apertura, cierre)} del día; vacío si está cerrado o el horario no se puede leer. */
    public static List<LocalTime[]> ranges(Map<String, Object> hours, DayOfWeek day) {
        if (hours == null || !(hours.get(DAYS.get(day.getValue() - 1)) instanceof List<?> slots)) {
            return List.of();
        }
        List<LocalTime[]> ranges = new ArrayList<>();
        try {
            for (Object slot : slots) {
                if (slot instanceof List<?> range && range.size() == 2) {
                    LocalTime open = LocalTime.parse(String.valueOf(range.get(0)));
                    LocalTime close = LocalTime.parse(String.valueOf(range.get(1)));
                    if (open.isBefore(close)) {
                        ranges.add(new LocalTime[] {open, close});
                    }
                }
            }
        } catch (DateTimeParseException unreadable) {
            return List.of();
        }
        return ranges;
    }

    /** {@code true} si el horario tiene al menos un día con franjas: sin eso no hay agenda. */
    public static boolean hasWeeklyHours(Map<String, Object> hours) {
        if (hours == null) {
            return false;
        }
        for (DayOfWeek day : DayOfWeek.values()) {
            if (!ranges(hours, day).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Inicios de franja del día (hora de Bogotá), alineados desde cada apertura y sin pasar del cierre. */
    public static List<ZonedDateTime> slotStarts(Map<String, Object> hours, LocalDate date, int slotMinutes) {
        if (slotMinutes <= 0) {
            return List.of();
        }
        List<ZonedDateTime> starts = new ArrayList<>();
        for (LocalTime[] range : ranges(hours, date.getDayOfWeek())) {
            // En minutos del día: una franja no cruza la medianoche (OpeningHours lo garantiza).
            int open = range[0].toSecondOfDay() / 60;
            int close = range[1].toSecondOfDay() / 60;
            for (int minute = open; minute + slotMinutes <= close; minute += slotMinutes) {
                starts.add(date.atTime(LocalTime.ofSecondOfDay(minute * 60L)).atZone(ZONE));
            }
        }
        return starts;
    }

    /** {@code true} si {@code startsAt} queda en la ventana de reserva: desde ahora + 2 h hasta ahora + 60 días. */
    public static boolean withinWindow(Instant startsAt, Instant now) {
        return !startsAt.isBefore(now.plus(Duration.ofHours(MIN_LEAD_HOURS)))
                && !startsAt.isAfter(now.plus(Duration.ofDays(MAX_DAYS_AHEAD)));
    }

    /**
     * Valida la franja pedida (o propuesta) y devuelve su inicio como {@link Instant}.
     *
     * @throws BadRequestException {@value #SLOT_INVALID}
     */
    public static Instant requireSlot(OffsetDateTime startsAt, int slotMinutes, Map<String, Object> hours,
            Instant now) {
        if (startsAt == null) {
            throw slotInvalid("Elige la fecha y la hora de la cita.");
        }
        Instant instant = startsAt.toInstant();
        if (!withinWindow(instant, now)) {
            throw slotInvalid("La cita debe pedirse con al menos " + MIN_LEAD_HOURS + " horas de anticipación y "
                    + "hasta " + MAX_DAYS_AHEAD + " días.");
        }
        ZonedDateTime local = instant.atZone(ZONE);
        if (!slotStarts(hours, local.toLocalDate(), slotMinutes).contains(local)) {
            throw slotInvalid("Esa hora no está disponible en el horario de la sede.");
        }
        return instant;
    }

    /** Código visible nuevo ({@code CT-} + 6 caracteres sin ambigüedad), con {@link SecureRandom}. */
    public static String newCode() {
        return newCode(RANDOM);
    }

    static String newCode(Random random) {
        StringBuilder code = new StringBuilder(CODE_PREFIX);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    /** Celular o fijo colombiano en E.164 ({@code +57} y 10 dígitos); {@code null} si no se puede normalizar. */
    public static String normalizePhone(String value) {
        if (value == null) {
            return null;
        }
        String compact = value.replaceAll("[\\s\\-().]", "");
        if (E164_CO.matcher(compact).matches()) {
            return compact;
        }
        if (LOCAL_10.matcher(compact).matches()) {
            return "+57" + compact;
        }
        if (compact.startsWith("57") && compact.length() == 12 && compact.chars().allMatch(Character::isDigit)) {
            return "+" + compact;
        }
        return null;
    }

    /** Texto libre recortado; {@code null} si queda vacío. 400 {@code BOOKING_NOTE_INVALID} si es muy largo. */
    public static String note(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() > NOTE_MAX) {
            throw new BadRequestException("BOOKING_NOTE_INVALID",
                    "La nota no puede superar " + NOTE_MAX + " caracteres.");
        }
        return trimmed;
    }

    private static BadRequestException slotInvalid(String message) {
        return new BadRequestException(SLOT_INVALID, message);
    }
}
