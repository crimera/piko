/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.dm;

import java.util.List;
import android.content.Context;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.crimera.ObjectBrowser;

import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.MarkChatAsReadScope;

import com.instagram.model.direct.DirectThreadKey;
import com.instagram.common.session.UserSession;


@SuppressWarnings("unused")
public class MarkChatAsRead {

    private static String getButtonEnumClassName(){
        return "className";
    }

    private static String getThreadSeenDummyParameterClassName(){
        return "className";
    }

    private static String getThreadSeenFunctionClassName(){
        return "className";
    }

    private static String getThreadSeenFunctionMethodName(){
        return "methodName";
    }

    private static String getMessageCursorFieldName(){
        return "fieldName";
    }

    private static Object getButton(String enumTag) throws Exception {
        Class<?> targetClass = Class.forName(MarkChatAsRead.getButtonEnumClassName());
        Class<?>[] paramTypes = new Class<?>[] {String.class};

        Entity entity = new Entity();
        return entity.getMethod(
                targetClass,
                "valueOf",
                paramTypes,
                enumTag
        );

    }

    private static void markAsSeenAPICall(UserSession userSession, String threadId, String messageId, String senderId ) throws Exception{

        Class<?> targetClass = Class.forName(MarkChatAsRead.getThreadSeenFunctionClassName());
        Class<?> userSessionClass = UserSession.class;
        Class<?> lhClass = Class.forName(MarkChatAsRead.getThreadSeenDummyParameterClassName());

        Class<?>[] paramTypes = new Class<?>[] {
                userSessionClass,
                lhClass,
                String.class,
                String.class,
                String.class
        };

        Entity entity = new Entity();
        entity.getMethod(
                targetClass,
                MarkChatAsRead.getThreadSeenFunctionMethodName(),
                paramTypes,
                userSession, null, threadId, messageId, senderId
        );

    }

    private static String getMessageCursorId(Object unknown) throws Exception{
        Entity entity = new Entity(unknown).getFieldAsEntity("A01");
        return (String) entity.getField(MarkChatAsRead.getMessageCursorFieldName());
    }

    private static void markAsRead(UserSession userSession, Object unknown, DirectThreadKey directThreadKey){
        try{
            MarkChatAsReadScope.run(() -> {
                String threadId = directThreadKey.A00;
                String messageId = MarkChatAsRead.getMessageCursorId(unknown);
                String senderId = directThreadKey.A02.get(0).toString();

                markAsSeenAPICall(userSession, threadId, messageId, senderId);
            });
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
    }


    public static List addButton(List buttonList){
        try{
            if(Pref.pikoDebug()){
                buttonList.add(getButton("THREAD_LEVEL_DEBUG"));
            }
            if(Pref.enableMarkChatAsReadOption()){
                buttonList.add(getButton("MARK_AS_READ"));
            }

        } catch (Exception e) {
            PikoUtils.logger(e);
        }
        return buttonList;
    }

    // Return true = skip other button press check.
    // Return false = other button press check.
    public static boolean buttonAction(Context context, UserSession userSession,Object buttonPressed, Object unknown, DirectThreadKey directThreadKey){
        try {
            String buttonEnumTag = buttonPressed.toString();

            if(buttonEnumTag.equals("MARK_AS_READ")){
                MarkChatAsRead.markAsRead(userSession, unknown, directThreadKey);
                return true;
            } else if(buttonEnumTag.equals("THREAD_LEVEL_DEBUG")){
                ObjectBrowser.browseObject(context, unknown);
                return true;
            }
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
        return false;
    }
}
