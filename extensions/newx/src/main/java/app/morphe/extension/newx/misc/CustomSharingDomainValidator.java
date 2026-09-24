package app.morphe.extension.newx.misc;

import app.morphe.extension.newx.settings.NewXStrings;

import androidx.annotation.Nullable;

import app.morphe.extension.newx.settings.SettingsValueValidator;

public final class CustomSharingDomainValidator implements SettingsValueValidator {
    @Override
    @Nullable
    public String errorMessage(String value) {
        if (ShareUrlResolver.isValidCustomDomain(value)) return null;
        return NewXStrings.str("piko_newx_custom_sharing_domain_invalid");
    }
}
