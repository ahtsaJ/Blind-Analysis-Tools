import ij.IJ;
import ij.WindowManager;
import ij.measure.ResultsTable;
import ij.text.TextWindow;

import java.awt.*;
import java.io.File;

public class DecisionTable {

    static ResultsTable decisionTable; static int rowIndex;

    private static final String decisionWindowTitle = "Decisions", suffixDecision = ".csv";
    enum ColHeadings {ImageTitle, Decision};

    private static DecisionTable INSTANCE = null;

    private DecisionTable(){
        initDecisionTable();
    }

    public static DecisionTable getInstance(){
        if(INSTANCE == null){
            INSTANCE = new DecisionTable();
        }
        else {
            clear();
        }

        return INSTANCE;
    }

    public void updateDecisionTable(String imageName, int index, String decision){
        rowIndex = index;
        decisionTable.setValue(ColHeadings.ImageTitle.name(),rowIndex, imageName);
        decisionTable.setValue(ColHeadings.Decision.name(),rowIndex, decision);
        //decisionTable.show(decisionWindowTitle);
    }

    public static void clear(){
        decisionTable.deleteRows(0,decisionTable.getCounter());
        rowIndex = 0;
    }

    public void initDecisionTable(){
        decisionTable = new ResultsTable();
        rowIndex = 0;
    }


    public void save(String dir, String nameOfDecisionFile){
        String decisionFileName = nameOfDecisionFile + suffixDecision;
        String path = dir+File.separator+decisionFileName;
        decisionTable.save(path);
    }

    public void close(){
        if(WindowManager.getWindow(decisionWindowTitle)!=null) {
            Frame FR = WindowManager.getFrame(decisionWindowTitle);
            if (FR != null && FR instanceof TextWindow)
                ((TextWindow) FR).close();
        }
        rowIndex = 0;
    }


}
