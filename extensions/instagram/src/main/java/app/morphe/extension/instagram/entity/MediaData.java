package app.morphe.extension.instagram.entity;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class MediaData extends Entity {
    private final Object obj;

    public MediaData(Object obj) {
        super(obj);
        this.obj = obj;
    }

    private Class<?> getHelperClass() throws Exception {
        return Class.forName("className");
    }

    public HashSet getMentionSet() throws Exception {
        String methodName = "methodName";
        Class<?> helperClass = this.getHelperClass();
        Object result = super.getMethod(helperClass,methodName,this.obj);
        if (result!=null){
            return new HashSet<>((List)result);
        }
        return null;

    }
}