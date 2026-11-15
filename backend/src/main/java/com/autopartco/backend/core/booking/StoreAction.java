package com.autopartco.backend.core.booking;

/** Acción del almacén sobre una cita y el estado al que lleva. */
public enum StoreAction {
    CONFIRM(BookingStatus.CONFIRMED),
    PROPOSE(BookingStatus.PROPOSED),
    REJECT(BookingStatus.REJECTED),
    CANCEL(BookingStatus.CANCELLED_BY_STORE),
    COMPLETE(BookingStatus.COMPLETED),
    NO_SHOW(BookingStatus.NO_SHOW);

    private final BookingStatus target;

    StoreAction(BookingStatus target) {
        this.target = target;
    }

    public BookingStatus target() {
        return target;
    }

    /** Rechazar y cancelar exigen una nota del almacén para el comprador. */
    public boolean requiresNote() {
        return this == REJECT || this == CANCEL;
    }
}
