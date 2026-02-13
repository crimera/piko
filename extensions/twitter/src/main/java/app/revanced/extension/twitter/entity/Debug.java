package app.revanced.extension.twitter.entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import app.revanced.extension.twitter.Utils;

public class Debug {
    protected final Object obj;

    public Debug(Object obj) {
        this.obj = obj;
    }

    public Class<?> getObjClass() throws ClassNotFoundException {
        return this.obj.getClass();
    }

    public Object getField(Class cls, Object clsObj, String fieldName) throws Exception {
        Field field = cls.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Object) field.get(clsObj);
    }

    public Object getField(Object clsObj, String fieldName) throws Exception {
        return getField(clsObj.getClass(), clsObj, fieldName);
    }

    public Object getField(String fieldName) throws Exception {
        return getField(this.obj, fieldName);
    }

    public Object getMethod(Object clsObj, String methodName) throws Exception {
        return clsObj.getClass().getDeclaredMethod(methodName).invoke(clsObj);
    }

    public Object getMethod(String methodName) throws Exception {
        return this.getMethod(this.obj, methodName);
    }

    /*** THE BELOW FUNCTIONS SHOULD BE USED ONLY WHILE DEVELOPMENT ***/
    public String describeFields() throws Exception {
        String line = "----------------------------";
        StringBuilder sb = new StringBuilder();
        Class<?> cls = this.getObjClass();

        Field[] fields = cls.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            String name = field.getName();
            String tyName = field.getType().getName();
            Object value;
            sb.append(name)
                    .append(" - ")
                    .append(tyName)
                    .append("\n ");

            try {
                value = field.get(this.obj);
            } catch (IllegalAccessException e) {
                value = "<access denied>\n";
            }
            sb.append(value)
            .append("\n"+line+"\n");
        }
        return sb.toString();
    }

    public String describeMethods() throws Exception {
        String line = "----------------------------";
        StringBuilder sb = new StringBuilder();
        Class<?> cls = this.getObjClass();
        Method[] methods = cls.getDeclaredMethods();

        for (Method method : methods) {
            method.setAccessible(true);
            sb.append(method.getName())
                    .append(" - ")
                    .append(method.getReturnType().getSimpleName())
                    .append("\n ");

            Parameter[] params = method.getParameters();
            if (params.length == 0) {
                try {
                    Object result = method.invoke(this.obj);
                    sb.append(result);
                } catch (Exception e) {
                    sb.append("<error invoking>");
                }
            } else {
                for (Parameter param : params) {
                    sb.append(param.getType().getSimpleName())
                            .append(" ")
                            .append(param.getName())
                            .append(", ");
                }
            }
            sb.append("\n"+line+"\n");
        }
        return sb.toString();
    }

    public void describeClass() throws Exception {
        String className = this.getObjClass().getName();
        String line = "----------------------------";

        String fields = this.describeFields();
        String methods = this.describeMethods();

        StringBuilder sb = new StringBuilder();
        sb.append(line)
                .append("\n" + className)                
                .append("\n" + line + "\n" + line)
                .append("\nFIELDS\n" + line +"\n"+ fields)
                .append("\n" + line + "\n" + line)
                .append("\nMETHODS\n" + line + "\n"+methods)
                .append("\n" + line + "\n" + line);
        
        String fileName = className+".txt";
        Utils.pikoWriteFile(fileName,sb.toString(),false);
        Utils.toast("DONE: "+className);

    }
}
