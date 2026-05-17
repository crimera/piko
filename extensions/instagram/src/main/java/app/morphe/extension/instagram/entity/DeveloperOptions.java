/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;

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

    public DeveloperOptionsItem getDeveloperOptionsItem(Object experimentItem) throws Exception {
        Class<?> experimentItemHelperClass = this.getExperimentItemHelperClass();

        String universalName = (String) super.getField(experimentItemHelperClass, experimentItem, this.getUniversalNameFieldName());
        long mobileConfigSpecifier = (long) super.getField(experimentItemHelperClass, experimentItem, this.getMobileConfigSpecifierFieldName());
        String paramName = (String) super.getField(experimentItemHelperClass, experimentItem, this.getParamNameFieldName());

        return new DeveloperOptionsItem(mobileConfigSpecifier, universalName, paramName);
    }

    public JSONObject toJSONObject() {
        JSONObject mappings = new JSONObject();
        try {
            List allExperiments = this.getAllExperiments();
            for (Object item : allExperiments) {
                DeveloperOptionsItem developerOptionsItem = this.getDeveloperOptionsItem(item);

                String universalId = developerOptionsItem.getUniversalId();
                String paramId = developerOptionsItem.getParamId();

                if (mappings.has(universalId)) {
                    mappings.accumulate(universalId, paramId);
                } else {
                    JSONArray paramList = new JSONArray(Arrays.asList(paramId));
                    mappings.put(universalId, paramList);
                }
            }
        } catch (Exception e) {
            PikoUtils.logger(e);
            PikoUtils.toast("Developer options toJSONObject failed");
        }
        return mappings;
    }

    @java.lang.Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("All Experiments\n");
            sb.append("=====================\n\n");

            List allExperiments = this.getAllExperiments();
            Set<String> seenUniversalNames = new HashSet<>();

            for (Object item : allExperiments) {
                DeveloperOptionsItem developerOptionsItem = this.getDeveloperOptionsItem(item);

                long mobileConfigSpecifier = developerOptionsItem.getMobileConfigSpecifier();
                String universalName = developerOptionsItem.getUniversalName();
                String paramName = developerOptionsItem.getParamName();
                String configId = developerOptionsItem.getConfigId();

                if (!seenUniversalNames.contains(universalName)) {
                    seenUniversalNames.add(universalName);
                    sb.append("---------------------\n").append("Universal name: ").append(universalName).append("\n\n");
                }

                sb.append("Param name: ").append(paramName).append("\n");
                sb.append("Hex id: ").append(Long.toHexString(mobileConfigSpecifier)).append("\n");
                sb.append("Config id: ").append(configId).append("\n\n");
            }
        } catch (Exception e) {
            PikoUtils.logger(e);
            PikoUtils.toast("Developer options extraction failed");
            sb.append(e.toString() + "\n");
        }
        return sb.toString();
    }

}
