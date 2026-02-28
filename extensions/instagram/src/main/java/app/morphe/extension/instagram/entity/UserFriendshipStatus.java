package app.morphe.extension.instagram.entity;

public class UserFriendshipStatus extends Entity {
    private final Object obj;

    public UserFriendshipStatus(Object obj) {
        super(obj);
        this.obj = obj;
    }

    private Class<?> getHelperClass() throws Exception {
        return Class.forName("className");
    }

    public Boolean getFollowBackStatus() throws Exception {
        String methodName = "methodName";
        Class<?> helperClass = this.getHelperClass();
        return (Boolean) super.getMethod(helperClass,methodName,this.obj);
    }
}