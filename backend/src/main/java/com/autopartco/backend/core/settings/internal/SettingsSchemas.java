package com.autopartco.backend.core.settings.internal;

import static com.autopartco.backend.core.settings.internal.SettingsSchema.bool;
import static com.autopartco.backend.core.settings.internal.SettingsSchema.emailList;
import static com.autopartco.backend.core.settings.internal.SettingsSchema.oneOf;
import static com.autopartco.backend.core.settings.internal.SettingsSchema.optionalMatching;
import static com.autopartco.backend.core.settings.internal.SettingsSchema.optionalText;
import static com.autopartco.backend.core.settings.internal.SettingsSchema.requiredText;
import static com.autopartco.backend.core.settings.internal.SettingsSchema.uuidList;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.autopartco.backend.core.settings.AccountSettings;
import com.autopartco.backend.core.settings.StoreSettings;

/** Esquemas de cuenta y almacén (contrato del plan «Configuraciones») y conversión a los records públicos. */
public final class SettingsSchemas {
    public static final String DEFAULT_WHATSAPP_MESSAGE =
            "Hola, vi {producto} en Autopartco y quiero cotizarlo: {enlace}";
    public static final String LINK_PLACEHOLDER = "{enlace}";
    public static final int MAX_FEATURED_PRODUCTS = 8;

    private static final Pattern DIVIPOLA_CITY = Pattern.compile("^\\d{5}$");

    public static final SettingsSchema ACCOUNT = SettingsSchema.builder()
            .leaf("security.twoFactor.enabled", false, bool())
            .leaf("security.twoFactor.method", "EMAIL", oneOf("EMAIL"))
            .leaf("security.loginAlerts", true, bool())
            .leaf("notifications.orderUpdates", true, bool())
            .leaf("notifications.priceAlerts", true, bool())
            .leaf("notifications.storeReplies", true, bool())
            .leaf("notifications.marketing", false, bool())
            .leaf("preferences.theme", "system", oneOf("system", "light", "dark"))
            .leaf("preferences.defaultCityCode", null, optionalMatching(DIVIPOLA_CITY,
                    "El código de ciudad debe ser un código DIVIPOLA de 5 dígitos."))
            .build();

    public static final SettingsSchema STORE = SettingsSchema.builder()
            .leaf("contact.whatsappEnabled", true, bool())
            .leaf("contact.whatsappMessage", DEFAULT_WHATSAPP_MESSAGE, whatsappMessage())
            .leaf("profile.showBranchesMap", true, bool())
            .leaf("profile.featuredProductIds", List.of(), uuidList(MAX_FEATURED_PRODUCTS))
            .leaf("profile.tagline", null, optionalText(80))
            .leaf("catalog.showExactStock", false, bool())
            .leaf("notifications.newOrder", true, bool())
            .leaf("notifications.lowStock", true, bool())
            .leaf("notifications.newReview", true, bool())
            .leaf("notifications.weeklyReport", true, bool())
            .leaf("notifications.recipients", List.of(), emailList(5))
            .leaf("security.requireStaffTwoFactor", false, bool())
            .build();

    private SettingsSchemas() {
    }

    private static SettingsSchema.Normalizer whatsappMessage() {
        SettingsSchema.Normalizer text = requiredText(300, true);
        return value -> {
            String message = (String) text.normalize(value);
            if (!message.contains(LINK_PLACEHOLDER)) {
                throw new SettingsSchema.Invalid("El mensaje de WhatsApp debe incluir {enlace}.");
            }
            return message;
        };
    }

    public static AccountSettings toAccount(Map<String, Object> effective) {
        Map<String, Object> security = section(effective, "security");
        Map<String, Object> twoFactor = section(security, "twoFactor");
        Map<String, Object> notifications = section(effective, "notifications");
        Map<String, Object> preferences = section(effective, "preferences");
        return new AccountSettings(
                new AccountSettings.Security(
                        new AccountSettings.TwoFactor(flag(twoFactor, "enabled"), (String) twoFactor.get("method")),
                        flag(security, "loginAlerts")),
                new AccountSettings.Notifications(
                        flag(notifications, "orderUpdates"),
                        flag(notifications, "priceAlerts"),
                        flag(notifications, "storeReplies"),
                        flag(notifications, "marketing")),
                new AccountSettings.Preferences(
                        (String) preferences.get("theme"),
                        (String) preferences.get("defaultCityCode"))
        );
    }

    public static StoreSettings toStore(Map<String, Object> effective) {
        Map<String, Object> contact = section(effective, "contact");
        Map<String, Object> profile = section(effective, "profile");
        Map<String, Object> catalog = section(effective, "catalog");
        Map<String, Object> notifications = section(effective, "notifications");
        Map<String, Object> security = section(effective, "security");
        return new StoreSettings(
                new StoreSettings.Contact(flag(contact, "whatsappEnabled"), (String) contact.get("whatsappMessage")),
                new StoreSettings.Profile(
                        flag(profile, "showBranchesMap"),
                        strings(profile, "featuredProductIds").stream().map(UUID::fromString).toList(),
                        (String) profile.get("tagline")),
                new StoreSettings.Catalog(flag(catalog, "showExactStock")),
                new StoreSettings.Notifications(
                        flag(notifications, "newOrder"),
                        flag(notifications, "lowStock"),
                        flag(notifications, "newReview"),
                        flag(notifications, "weeklyReport"),
                        strings(notifications, "recipients")),
                new StoreSettings.Security(flag(security, "requireStaffTwoFactor"))
        );
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> section(Map<String, Object> map, String key) {
        return map != null && map.get(key) instanceof Map<?, ?> nested ? (Map<String, Object>) nested : Map.of();
    }

    private static boolean flag(Map<String, Object> map, String key) {
        return Boolean.TRUE.equals(map.get(key));
    }

    private static List<String> strings(Map<String, Object> map, String key) {
        return map.get(key) instanceof List<?> items ? items.stream().map(String::valueOf).toList() : List.of();
    }
}
