package app.revanced.integrations.twitter.patches.customise;

import android.util.*;
import java.util.*;
import java.lang.reflect.Field;
import app.revanced.integrations.twitter.Pref;

public class Customise {

    private static void logger(Object j){
        Log.d("piko", j.toString());
    }

    public static List navBar(List inp){
        try{
            ArrayList choices = Pref.customNavbar();
            List list2 = new ArrayList<>(inp);
            Iterator itr = list2.iterator();

            while (itr.hasNext()) {
                Object obj = itr.next();
                String itemStr = obj.toString();
                if(choices.contains(itemStr)){
                    inp.remove(obj);
                }
            }

        }catch (Exception e){
            logger(e);
        }
        return inp;
    }

    public static ArrayList profiletabs(ArrayList inp){
        try{
            ArrayList choices = Pref.customProfileTabs();
            Object inpObj = inp.clone();
            ArrayList<?> arr = (ArrayList<?>) inpObj;
            Iterator itr = inp.iterator();

            while (itr.hasNext()) {

                Object obj = itr.next();
                Class<?> clazz = obj.getClass();
                Field field = clazz.getDeclaredField("g");
                String g = (String) field.get(obj);

                if ((g!=null && choices.contains(g)) || (g==null && choices.contains("subs"))){
                    arr.remove(obj);
                }
            }
            return arr;


        }catch (Exception e){
            logger(e);
        }
        return inp;
    }

    public static List inlineBar(List inp){
        try{
            ArrayList choices = Pref.inlineBar();
            List list2 = new ArrayList<>(inp);
            Iterator itr = inp.iterator();

            while (itr.hasNext()) {
                Object obj = itr.next();
                String itemStr = obj.toString();
                if(choices.contains(itemStr)){
                    list2.remove(obj);
                }
            }
            return list2;
        }catch (Exception e){
            logger(e);
        }
        return inp;
    }

    public static List sideBar(List inp){
        try{
            ArrayList choices = Pref.customSidebar();
            List list2 = new ArrayList<>(inp);
            Iterator itr = list2.iterator();

            while (itr.hasNext()) {
                Object obj = itr.next();
                String itemStr = obj.toString();
                if(choices.contains(itemStr)){
                    inp.remove(obj);
                }
            }

        }catch (Exception e){
            logger(e);
        }
        return inp;
    }

//class end
}