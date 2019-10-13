import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.io.Opener;
import ij.plugin.BrowserLauncher;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

public class Blind_AnalyseDecide implements PlugIn {

    private String topLevelDir;
    // for opening images
    int noOfFiles=0;
    static ArrayList<String> listOfImageFilesAbsolute;
    int[] imageUseCount;
    public static String PREFS_PREFIX = "BlindAnalyseDecide.";

    public static String recordCommandsKey = "_recordCommands";
    boolean recordCommands = Prefs.get(PREFS_PREFIX+recordCommandsKey, true);
    ArrayList<Integer> indexOpened = new ArrayList<>();

    // GUI window
    JFrame iamMainWindow;
    JLabel jLabelStatus;
    JButton jButtonSetting;

    JTextArea jTextFieldMacro;

    String timeStart, timeEnd, timeFileName;

    SecureRandom randomImageToOpen, randomImageName; int randomNameRange = Integer.MAX_VALUE;

    boolean firstImage = true; boolean saveLogs = false;

    String macroCommandsDefault = "// write initial macro commands here\n"+
            "// these commands will be applied to each image before displaying it to the user.";

    public static String macroCommandsKey = "_macroCommands";
    String macroCommands = Prefs.get(PREFS_PREFIX+macroCommandsKey, macroCommandsDefault);

    String logs = "", imageNamePrefix = "      -";
    Recorder recorder;

    String nameOfFilesPrefix = "BlindAnalyse&Decide";
    String nameOfLogFile = nameOfFilesPrefix+"_Log";
    String extensionOfLogFile =  ".txt";

    DecisionTable dt = DecisionTable.getInstance();

    String helpPage = "https://imagej.net/Blind_Analysis_Tools";

    int noOfImagesUsedUp = 0; private static final int maxRepeatImage = 1;

    String defaultDecisionChoices = "None, Weak, Strong";
    public static String decisionChoicesKey = "_decisionChoices";
    String[] decisionChoices ;

    JComboBox<String> decisionComboBox;
    String nameOfDecisionFile = nameOfFilesPrefix+"_Decisions";

    String currentImageName="";
    int imageCounter = -1;

    String iamWindowTitle = "Analyse & Decide";

    JButton jButtonNextImg;

    JLabel jLabelMacroCommands;

    private JTextArea decisionJTextArea;
    JFrame settingWindow;

    public void run(String arg) {
        setup();
        if (WindowManager.getIDList() == null) { //no image is open
            boolean success = chooseMainFolder();

            if(success) {

                noOfFiles = listOfImageFilesAbsolute.size();

                imageUseCount = new int[noOfFiles];
                Arrays.fill(imageUseCount, 0);

                randomImageToOpen = new SecureRandom();

                randomImageName = new SecureRandom();

                IJ.register(Blind_AnalyseDecide.class);

                iamWindow();
            }

        } else { // images are open, request to close them
            waitForUserImagesOpen();
        }
    }

    private boolean chooseMainFolder(){
        topLevelDir = IJ.getDir("Choose main directory");
        if(topLevelDir == null || topLevelDir.isEmpty()){
            return false;
        }

        listOfImageFilesAbsolute = listAllImageFilesNoSkip(topLevelDir);
        if(listOfImageFilesAbsolute.size() == 0){
            return false;
        }

        return filterFilesAsPerExtensions();
    }

    private void setup(){
        LocalDateTime ldt = LocalDateTime.now();
        String[] splitAtT = ldt.toString().split("T");
        String dateNow = splitAtT[0];
        String[] splitAtColon = splitAtT[1].split(":");
        String timeNow = splitAtColon[0]+":"+splitAtColon[1];
        timeStart = "On "+dateNow+" at "+ timeNow;

        decisionChoices  = stringToArray( Prefs.get(PREFS_PREFIX+decisionChoicesKey, defaultDecisionChoices) );
    }

    /************************************************ File extension related **********************************************************/

    private boolean filterFilesAsPerExtensions(){
        HashSet<String> extensionListTemp = new HashSet<String>();
        for ( String fileNameUnderExamination : listOfImageFilesAbsolute ) {
            extensionListTemp.add(  extractFileExtension(fileNameUnderExamination) );
        }

        int noOfExtensions = extensionListTemp.size();
        boolean[] defaultValues = new boolean[noOfExtensions];
        String[] allExtensions = extensionListTemp.toArray(new String[noOfExtensions]);
        Arrays.fill(defaultValues,true);

        GenericDialog gd = new GenericDialog("Choose...");
        gd.addMessage("Choose file extensions to open?");
        gd.addCheckboxGroup(noOfExtensions, 1, allExtensions, defaultValues);

        gd.showDialog();

        if (gd.wasCanceled()) {
            return false;
        }
        for (int row = 0; row < noOfExtensions; row++) {
            defaultValues[row] = gd.getNextBoolean();
        }

        LinkedList<String> extensionListFinal = new LinkedList<>();
        for(int i=0; i< noOfExtensions; i++){
            if(defaultValues[i]){
                extensionListFinal.add(allExtensions[i]);
            }
        }

        // remove unwanted files from the list
        for(int i = listOfImageFilesAbsolute.size()-1; i>=0; i--){
            if(!wantedExtension( extractFileExtension(listOfImageFilesAbsolute.get(i)), extensionListFinal )){
                listOfImageFilesAbsolute.remove(i);
            }
        }

        if(listOfImageFilesAbsolute.size() == 0){
            return false;
        }
        return true;
    }

    private boolean wantedExtension(String fileExtension, LinkedList<String>  wantedExtensions ){
        for (String ext : wantedExtensions) {
            if(ext.matches(fileExtension)){
                return true;
            }
        }
        return false;
    }

    private String extractFileExtension(String fileNameUnderExamination){
        String theExtension = fileNameUnderExamination.substring( fileNameUnderExamination.lastIndexOf('.')+1 );
        return theExtension;
    }

    /************************************* image file related **********************************/
    public  ArrayList<String> listAllImageFilesNoSkip( String path ){
        Stack<String> listOfFolder = new Stack<>();

        ArrayList<String> listOfImageFilesAbsolute = new ArrayList<String>();

        listOfFolder.push(path);

        while (!listOfFolder.empty()){
            String folderToScan = listOfFolder.pop();
            String[] listOfAllFiles = getListAllFiles( folderToScan);
            int noOfAllFiles = listOfAllFiles.length;

            for(int i = 0; i < noOfAllFiles; i++){
                String fileName = listOfAllFiles[i];
                File f = new File(folderToScan+fileName);
                if(f.isDirectory()){
                    listOfFolder.push(folderToScan+fileName+File.separator);
                }
                else if( !fileName.startsWith(".") && !f.isHidden()){
                    listOfImageFilesAbsolute.add(folderToScan+fileName);
                }
            }
        }

        return listOfImageFilesAbsolute;
    }

    private String[] getListAllFiles( String path){
        File f_sub = new File(path);
        String[] listOfAllFiles = f_sub.list();
        return listOfAllFiles;
    }

    /******************************************* window *************************************************/

    private void decisionStringSettingWindow(){
        iamMainWindow.setEnabled(false);
        settingWindow = new JFrame("Decision choices");

        JPanel settingPanel = new JPanel(new GridBagLayout());

        JLabel jLabel1 = new JLabel();
        jLabel1.setText("Comma separated decision choices: ");
        jLabel1.setForeground(Color.BLACK);
        addConstrainedComponent(settingPanel, jLabel1, 0, 0, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);


        decisionJTextArea = new JTextArea(5,30);

        decisionJTextArea.setToolTipText("Enter comma separated decision choices here...");
        decisionJTextArea.setText("");
        addConstrainedComponent(settingPanel, decisionJTextArea, 0, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

        JScrollPane scrollbar1 = new JScrollPane(decisionJTextArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        addConstrainedComponent(settingPanel, scrollbar1, 0, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

        JButton jButtonOk = new JButton();
        jButtonOk.setText("OK");
        jButtonOk.setToolTipText("Saves settings...");
        jButtonOk.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setDecisionChoices();
            }
        });
        addConstrainedComponent(settingPanel, jButtonOk, 0, 2, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

        settingWindow.setContentPane(settingPanel);
        settingWindow.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        settingWindow.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                noDecisionChoicesMade();
            }
        });

        settingWindow.setResizable(false);
        settingWindow.pack();

        settingWindow.setLocationRelativeTo(null);
        settingWindow.setVisible(true);

    }

    private void setDecisionChoices(){
        decisionChoices = stringToArray(decisionJTextArea.getText());
        Prefs.set(PREFS_PREFIX+decisionChoicesKey, decisionJTextArea.getText());

        settingWindow.dispose();
        iamMainWindow.setEnabled(true);

        DefaultComboBoxModel model = new DefaultComboBoxModel( decisionChoices );
        decisionComboBox.setModel( model );

        iamMainWindow.pack();
        iamMainWindow.revalidate();
        iamMainWindow.repaint();

    }

    private void noDecisionChoicesMade(){
        settingWindow.dispose();
        iamMainWindow.setEnabled(true);
    }


    private void iamWindow(){
        iamMainWindow = new JFrame(iamWindowTitle);

        GridBagLayout gb = new GridBagLayout();
        JPanel panel = new JPanel(gb);

        jButtonNextImg = new JButton();
        jButtonNextImg.setText("Open First Image");
        jButtonNextImg.setToolTipText("Opens next image for analysis");
        jButtonNextImg.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openNextImage();
            }
        });

        addConstrainedComponent(panel, jButtonNextImg, 0, 0, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

        decisionComboBox = new JComboBox<String>(decisionChoices);
        decisionComboBox.setToolTipText("Choose a decision for current image.");
        decisionComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateDecisionTable();
            }
        });

        addConstrainedComponent(panel, decisionComboBox, 1, 0, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

        jButtonSetting = new JButton();
        jButtonSetting.setText("Set");
        jButtonSetting.setToolTipText("Set decision choices...");
        jButtonSetting.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                decisionStringSettingWindow();
            }
        });

        addConstrainedComponent(panel, jButtonSetting, 2, 0, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);


        jLabelStatus = new JLabel();
        jLabelStatus.setText("No. of images opened: "+(imageCounter+1) + " out of "+noOfFiles);
        jLabelStatus.setForeground(Color.BLACK);

        addConstrainedComponent(panel, jLabelStatus, 0, 1, 3, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

        jLabelMacroCommands = new JLabel();
        jLabelMacroCommands.setText("Macro commands to apply to each image: ");
        jLabelMacroCommands.setForeground(Color.BLACK);

        addConstrainedComponent(panel, jLabelMacroCommands, 0, 2, 3, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);


        jTextFieldMacro = new JTextArea(5,35);
        jTextFieldMacro.setToolTipText("Macro commands to apply to each image before displaying.");
        jTextFieldMacro.setText(macroCommands);

        addConstrainedComponent(panel, jTextFieldMacro, 0, 3, 3, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

        JScrollPane scrollbar1 = new JScrollPane(jTextFieldMacro, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        addConstrainedComponent(panel, scrollbar1, 0, 3, 3, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

        JButton jButtonHelp = new JButton();
        jButtonHelp.setText("Help");
        jButtonHelp.setToolTipText("Opens help page.");
        jButtonHelp.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                BrowserLauncher br = new BrowserLauncher();
                br.run(helpPage);
            }
        });


        addConstrainedComponent(panel, jButtonHelp, 0, 4, 1, 1, GridBagConstraints.EAST, GridBagConstraints.NONE);

        JButton jButtonDone = new JButton();
        jButtonDone.setText("Done");
        jButtonDone.setToolTipText("Saves results and ends the program");
        jButtonDone.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doneAction();
            }
        });

        addConstrainedComponent(panel, jButtonDone, 1, 4, 1, 1, GridBagConstraints.EAST, GridBagConstraints.NONE);

        iamMainWindow.setContentPane(panel);
        iamMainWindow.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // Exit_On_Close closes Fiji window also!
        iamMainWindow.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                doneAction();
            }
        });

        iamMainWindow.pack();
        iamMainWindow.setLocationRelativeTo(null);
        iamMainWindow.setVisible(true);

    }


    private void iamWindowUpdateStatus(String status, Color color){
        jLabelStatus.setText(status);
        jLabelStatus.setForeground(color);
        iamMainWindow.revalidate();
        iamMainWindow.repaint();
    }

    private void updateNextImage(){
        jButtonNextImg.setText("Open Next Image");
        iamMainWindow.revalidate();
        iamMainWindow.repaint();
    }

    /****************************************************************************************************************/
    private boolean openAnImage(String imageToOpen, int index){
        boolean success = false;

        ImagePlus impTemp = open(imageToOpen );

        if(impTemp != null){
            impTemp.show();
            IJ.runMacro(macroCommands);
            String currName = "Image_"+index;

            impTemp.setTitle(currName);
            int noOfSlices = impTemp.getStack().getSize();
            for (int i =1; i<= noOfSlices; i++)
                impTemp.getStack().setSliceLabel(currName, i);

            currentImageName = imageToOpen;
            imageCounter++;
            success = true;
        }

        return success;
    }


    public ImagePlus open(String imageAbsoluteName) {
        Opener opener = new Opener();

        return opener.openImage(imageAbsoluteName);

    }

    private void openNextImage() {
        if(firstImage) {
            macroCommands = jTextFieldMacro.getText();

            jLabelMacroCommands.setEnabled(false);
            jTextFieldMacro.setEnabled(false);
            jButtonSetting.setEnabled(false);
            if(recordCommands) {
                recorder = Recorder.getInstance();
                if(recorder != null) {
                    recorder.close();
                }
                recorder = new Recorder(false);
            }

            saveLogs = true;
        }

        if(!firstImage){
            updateDecisionTable();
        }
        removeAllImageInstances();
        firstImage = false;

        boolean success;
        while(noOfImagesUsedUp < noOfFiles) {
            int imageIndexToOpen = randomImageToOpen.nextInt(noOfFiles);

            if(imageUseCount[imageIndexToOpen] < maxRepeatImage) {
                success = openAnImage(listOfImageFilesAbsolute.get(imageIndexToOpen), randomImageName.nextInt(randomNameRange));
                if(success){
                    indexOpened.add(imageIndexToOpen);

                    imageUseCount[imageIndexToOpen]++;
                    if(imageUseCount[imageIndexToOpen] >= maxRepeatImage){
                        noOfImagesUsedUp++;
                    }

                    iamWindowUpdateStatus("No. of images opened: "+(imageCounter+1) + " out of "+noOfFiles, Color.BLACK);

                    if(imageCounter == 0){
                        updateNextImage();
                    }

                    updateDecisionTable();

                    return;
                }
                else {

                    imageUseCount[imageIndexToOpen]++;
                    if(imageUseCount[imageIndexToOpen] == maxRepeatImage){
                        noOfImagesUsedUp++;
                    }

                    if(imageCounter == 0){
                        updateNextImage();
                    }

                    return;

                }
            }
        }

        iamWindowUpdateStatus("All images have been processed", Color.RED);
    }

    private void updateDecisionTable(){
        if (imageCounter >= 0) {
            dt.updateDecisionTable(currentImageName, imageCounter ,decisionComboBox.getSelectedItem().toString());
        }

    }

    private void doneAction(){

        Prefs.set(PREFS_PREFIX+macroCommandsKey, macroCommands);
        if(saveLogs) {
            LocalDateTime ldt = LocalDateTime.now();
            String[] splitAtT = ldt.toString().split("T");
            String dateNow = splitAtT[0];
            String[] splitAtColon = splitAtT[1].split(":");
            String timeNow = splitAtColon[0] + ":" + splitAtColon[1];
            timeEnd = "On " + dateNow + " at " + timeNow;

            timeFileName = ldt.toString().replace(":", "_").replace(".", "_").replace("-", "_").replace("T", "_T_");

            logs = logs + "Analysis started: " + timeStart + "\n";
            logs = logs + "Top level directory: " + topLevelDir + "\n";
            logs = logs + "Number of analysed images: " + indexOpened.size() + "\n";
            logs = logs + "Number of available images: " + noOfFiles + "\n";

            logs = logs + "\n";
            logs = logs + "Decision choices: " + Prefs.get(PREFS_PREFIX+decisionChoicesKey, defaultDecisionChoices) + "\n";

            logs = logs + "\n";
            logs = logs + "\n";
            logs = logs + "Following images have been analysed:" + "\n";

            for (int i : indexOpened) {
                String imgAnalyzed = listOfImageFilesAbsolute.get(i);
                logs = logs + imageNamePrefix + imgAnalyzed + "\n";
            }

            logs = logs + "\n";
            logs = logs + "\n";
            logs = logs + "Following images were provided:" + "\n";

            for (String name : listOfImageFilesAbsolute) {
                logs = logs + imageNamePrefix + name + "\n";
            }

            logs = logs + "\n";
            logs = logs + "\n";
            logs = logs + "Initial Macro Commands:" + "\n";
            logs = logs + macroCommands + "\n";
            logs = logs + "\n";
            logs = logs + "Analysis finished: " + timeEnd + "\n";

            if (recordCommands && recorder != null) {
                String postRecordingText = recorder.getText();

                logs = logs + "\n";
                logs = logs + "\n";
                logs = logs + "Recorded macro commands:" + "\n";
                logs = logs + postRecordingText + "\n";

                recorder.setVisible(true);
                recorder.close();
            }


            LogsIAM.append(logs);

            LogsIAM.saveLogs(topLevelDir, timeFileName, nameOfLogFile, extensionOfLogFile);
        }

        // save decisions
        dt.save(topLevelDir, nameOfDecisionFile+timeFileName);
        dt.close();

        removeAllImageInstances();
        listOfImageFilesAbsolute = null;

        iamMainWindow.dispose();
    }

    public static void removeAllImageInstances(){
        IJ.run("Close All", "");
    }

    /************************************************** Utility ***********************************************************/
    private void addConstrainedComponent(JPanel basePanel, JComponent componentToAdd, int xPos, int yPos, int widthCols, int heightRows, int place, int stretch){
        GridBagConstraints gridConstraints = new GridBagConstraints();
        gridConstraints.gridx = xPos;
        gridConstraints.gridy = yPos;
        gridConstraints.gridwidth = widthCols;
        gridConstraints.gridheight = heightRows;
        gridConstraints.weightx = 100;
        gridConstraints.weighty = 100;
        gridConstraints.insets = new Insets(5,10,5,10);
        gridConstraints.anchor = place;
        gridConstraints.fill = stretch;
        basePanel.add(componentToAdd, gridConstraints);
    }


    public static void waitForUserImagesOpen(){
        String macroCode = "waitForUser("+"\"Close...\""+", " + "\"Close all images and run again!\")";
        IJ.runMacro(macroCode);
    }

    private String[] stringToArray(String commaSeparatedList){
        String[] strLiterals = commaSeparatedList.split("\\s*,\\s*");
        return strLiterals;
    }

}

