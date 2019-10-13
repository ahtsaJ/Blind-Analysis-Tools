import ij.text.TextPanel;
import ij.text.TextWindow;

import java.io.File;

public class LogsIAM {
    private static String title = "Blind_Logs";
    private static LogsIAM INSTANCE = null;
    private static TextWindow tw;
    private static TextPanel tp;

    private LogsIAM() {
        tw = new TextWindow(title, "", 500, 350);
        tp = tw.getTextPanel();
    }

    private static LogsIAM getINSTANCE(){
        if(INSTANCE == null){
            INSTANCE = new LogsIAM();
        }
        return INSTANCE;
    }

    public static void append(String stringToAppend) {
        LogsIAM logWindow = getINSTANCE();
        logWindow.tp.append(stringToAppend);
    }

    public static void saveLogs(String dir, String time, String nameOfFile, String extensionOfFile){
        if(tp!= null) {
            tw.setVisible(true);
            String path = dir + File.separator + nameOfFile+time+extensionOfFile;
            tp.saveAs(path);
            close();
        }
    }

    public static void close(){
        if(tw!= null) {
            tw.close();
        }
    }

}
