package com.autopartco.backend.core.notification;

/** Tipo de notificación in-app ({@code notifications.kind}); la UI elige icono y agrupación con él. */
public enum NotificationKind {
    ORDER_STATUS,
    QUESTION,
    ANSWER,
    REVIEW_REQUEST,
    REVIEW_REPLY,
    STORE_ALERT,
    ACCOUNT_SECURITY,
    SYSTEM
}
