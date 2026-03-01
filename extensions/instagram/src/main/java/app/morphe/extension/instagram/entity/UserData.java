package app.morphe.extension.instagram.entity;


public class UserData extends Entity {
    private final Object obj;

    public UserData(Object obj) {
        super(obj);
        this.obj = obj;
    }

    private Object getAdditionalUserInfo() throws Exception {
        return super.getField(this.obj,"fieldName");
    }

    public String getUsername() {
        try{
            Object additionalUserInfo = getAdditionalUserInfo();
            return (String) super.getMethod(additionalUserInfo,"methodName");
        }catch (Exception ex){
            return null;
        }
    }

    public String getFullname() {
        try{
            Object additionalUserInfo = getAdditionalUserInfo();
            return (String) super.getMethod(additionalUserInfo,"methodName");
        }catch (Exception ex){
            return null;
        }
    }

}