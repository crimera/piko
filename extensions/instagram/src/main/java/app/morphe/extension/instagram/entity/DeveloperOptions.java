/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */


package app.morphe.extension.instagram.entity;

import java.util.List;
import app.morphe.extension.crimera.PikoUtils;

public class DeveloperOptions extends Entity {

    public DeveloperOptions() {
        super();
    }

    private Class<?> getQuickExperimentHelperClass() throws Exception {
        return Class.forName("X.Sb1");
    }

    private Class<?> getExperimentItemHelperClass() throws Exception {
        return Class.forName("X.BSw");
    }

    private String getMobileConfigSpecifierFieldName() throws Exception {
        return "A00";
    }

    private String getParamNameFieldName() throws Exception {
        return "A01";
    }

    private String getUniversalNameFieldName() throws Exception {
        return "A02";
    }

    private List getAllExperiments() throws Exception {
        Class<?> quickExperimentHelper = this.getQuickExperimentHelperClass();
        Object result = super.getMethod(quickExperimentHelper, "A06");
        if (result != null) {
            return (List) result;
        }
        return null;
    }

    @java.lang.Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("All Experiments\n");
            sb.append("=====================\n\n");
            Class<?> quickExperimentHelper = this.getExperimentItemHelperClass();

            List allExperiments = this.getAllExperiments();
            for (Object item : allExperiments) {
                long mobileConfigSpecifier = (long) super.getField(quickExperimentHelper, item, this.getMobileConfigSpecifierFieldName());
                String paramName = (String) super.getField(quickExperimentHelper, item, this.getParamNameFieldName());
                String universalName = (String) super.getField(quickExperimentHelper, item, this.getUniversalNameFieldName());

                sb.append("ConfigId: ").append(Long.toHexString(mobileConfigSpecifier)).append("\n");
                sb.append("Universal name: ").append(universalName).append("\n");
                sb.append("Param name: ").append(paramName).append("\n");
                sb.append("---------------------\n");
            }
        } catch (Exception e) {
            PikoUtils.logger(e);
            PikoUtils.toast("Developer options extraction failed");
            sb.append(e.toString() + "\n");
        }
        return sb.toString();
    }

}
