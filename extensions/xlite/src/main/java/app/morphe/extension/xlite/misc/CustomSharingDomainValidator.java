package app.morphe.extension.xlite.misc;

import androidx.annotation.Nullable;

import app.morphe.extension.shared.StringRef;
import app.morphe.extension.xlite.settings.SettingsValueValidator;

public final class CustomSharingDomainValidator implements SettingsValueValidator {
    @Override
    @Nullable
    public String errorMessage(String value) {
        if (ShareUrlResolver.isValidCustomDomain(value)) return null;
        return StringRef.str("piko_xlite_custom_sharing_domain_invalid");
    }
}
