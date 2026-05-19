/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.entity;

import app.morphe.extension.crimera.PikoUtils;

public class DeveloperOptionsItem {

    private long mobileConfigSpecifier;
    private String universalName;
    private String paramName;
    private String universalId;
    private String paramId;

    public DeveloperOptionsItem(long mobileConfigSpecifier, String universalName, String paramName) {
        this.mobileConfigSpecifier = mobileConfigSpecifier;
        this.universalName = universalName;
        this.paramName = paramName;

        this.universalId = this.getUniversalId(mobileConfigSpecifier);
        this.paramId = this.getParamId(mobileConfigSpecifier);
    }

    public DeveloperOptionsItem(long mobileConfigSpecifier) {
        this.mobileConfigSpecifier = mobileConfigSpecifier;
        this.universalName = "";
        this.paramName = "";

        this.universalId = this.getUniversalId(mobileConfigSpecifier);
        this.paramId = this.getParamId(mobileConfigSpecifier);
    }

    private Class<?> getUniversalIdHelperClass() throws Exception {
        return Class.forName("X.0B3D");
    }

    public String getUniversalId(long mobileConfigSpecifier) {
        try {
            Class<?> universalIdHelperClass = this.getUniversalIdHelperClass();
            int universalId = (int) new Entity().getMethod(universalIdHelperClass, "A00", new Class[]{long.class}, mobileConfigSpecifier);
            return String.valueOf(universalId);
        } catch (Exception e){
            PikoUtils.logger(e);
        }
        return "0";
    }

    public String getParamId(long mobileConfigSpecifier) {
        try{
            long shifted = mobileConfigSpecifier >>> 16;
            boolean flag = ((mobileConfigSpecifier >>> 62) & 1L) == 1L;
            Object paramId = flag ? (shifted & 0xffff) : (shifted & 0xfff);
            return String.valueOf(paramId);
        } catch (Exception e){
            PikoUtils.logger(e);
        }
        return "0";
    }

    public long getMobileConfigSpecifier() {
        return mobileConfigSpecifier;
    }

    public void setMobileConfigSpecifier(long mobileConfigSpecifier) {
        this.mobileConfigSpecifier = mobileConfigSpecifier;
    }

    public String getUniversalName() {
        return universalName;
    }

    public void setUniversalName(String universalName) {
        this.universalName = universalName;
    }

    public String getParamName() {
        return paramName;
    }

    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    public String getUniversalId() {
        return universalId;
    }

    public void setUniversalId(String universalId) {
        this.universalId = universalId;
    }

    public String getParamId() {
        return paramId;
    }

    public void setParamId(String paramId) {
        this.paramId = paramId;
    }

    public String getConfigId(){
        return this.getUniversalId() + "::" + this.getParamId();
    }

    @java.lang.Override
    public java.lang.String toString() {
        return "DeveloperOptionsItem{" +
                "mobileConfigSpecifier=" + mobileConfigSpecifier +
                ", universalName='" + universalName + '\'' +
                ", paramName='" + paramName + '\'' +
                ", universalId='" + universalId + '\'' +
                ", paramId='" + paramId + '\'' +
                '}';
    }
}
