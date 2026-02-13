package app.revanced.extension.twitter.patches.loggers;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import app.revanced.extension.twitter.Pref;
import app.revanced.extension.twitter.Utils;

public class ResponseLogger {
    private static final boolean LOG_RES;
    static{
        LOG_RES = Pref.serverResponseLogging();
        if(Pref.serverResponseLoggingOverwriteFile()){
            writeFile("",false);
//            Utils.logger("Cleared response log file!!!");
        }
    }

    public static InputStream saveInputStream(InputStream inputStream)  throws Exception {
        if(!LOG_RES) return inputStream;

        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        sb.append("\n");
        inputStream.close();
        String contentBytes = sb.toString();
        if(!(sb.indexOf("session_token") == 2 || sb.indexOf("guest_token") == 2)){
            writeFile(contentBytes,true);
         }

        return new ByteArrayInputStream(contentBytes.getBytes());
    }

    private static boolean writeFile(String data,boolean append){
        String fileName = "Server-Response-Log.txt";
        return Utils.pikoWriteFile(fileName,data,append);
    }

}