import ij.IJ;
import ij.gui.GenericDialog;
import ij.measure.ResultsTable;
import ij.plugin.BrowserLauncher;
import ij.plugin.PlugIn;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;

import java.time.LocalDateTime;
import java.util.*;

public class Blind_RandomFilesCopyGroupModes implements PlugIn {

    String tableName = "Mappings", suffixTable = ".csv", tableColOrig = "OriginalName", tableColAssigned = "AssignedName", tableColParams = "Parameters", GUIWindowTitle = "File Name Encrypter";

    String topLevelDir = null, outputFolderName = "BlindRandomFiles", outputFolder = null, splitSequence = " ", timeStamp, helpPage = "https://imagej.net/Blind_Analysis_Tools";

    ArrayList<String> listOfImageFilesAbsolute, listOfImageFileNames;
    LinkedList<String> groupsStartString;
    LinkedList<LinkedList<String>> listOfImageFilesAbsoluteGrouped = null, listOfImageFilesNamesGrouped = null;

    JFrame initParamsWindow;
    JLabel jLabelStatus, jLabelSplitString, jLabelMin, jLabelMax, jLabelShowings;
    JTextField fileNameSplitField;
    JFormattedTextField noOfFilesBasicField, minTimesField, maxTimesField, noOfShowingsField;

    JComboBox groupingChoiceComboBox;
    Box panelGroup, panelAdvanceOptions;

    ResultsTable mappingTable;

    SecureRandom randomImageToOpen;

    int noOfAvailableFiles = 0, noOfBasicfileToCopy, maxTimes = 1, minTimes = 1, noOfShowings, noOfFoundGroups;

    boolean groupFiles = false, basicMode = true,  advanceMode = false;

    int[] groupUseCount;

    String[] groupFileDecisionChoices = {"No", "Yes"};

    @Override
    public void run(String arg) {
        if(chooseMainFolder()) {
            if (filterFilesAsPerExtensions()) {
                noOfAvailableFiles = listOfImageFilesAbsolute.size();
                if (noOfAvailableFiles > 0) {
                    timeStamp = getTimeString();
                    noOfShowings = noOfAvailableFiles;
                    createInitParamsWindow();
                }
            }
        }
    }

    /*********************************************** GUI design ********************************************************/
    private void createInitParamsWindow() {
        initParamsWindow = new JFrame(GUIWindowTitle);
        initParamsWindow.setResizable(false);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Basic", createBasicPanel());
        tabbedPane.addTab("Advance", createAdvancePanel());

        initParamsWindow.setContentPane(tabbedPane);
        initParamsWindow.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        initParamsWindow.pack();
        initParamsWindow.setPreferredSize(new Dimension(350, 300));
        initParamsWindow.setLocationRelativeTo(null);
        initParamsWindow.setVisible(true);
    }

    private JPanel createBasicPanel(){
        JPanel basicPanel = new JPanel(new GridBagLayout());
        basicPanel.setBorder(BorderFactory.createEmptyBorder(10,5,10,5));
        addConstrainedComponent(basicPanel, new JLabel("Number of available files: "+ noOfAvailableFiles), 0, 0, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

        JLabel jLabelNoOfFilesBasic = new JLabel("No. of files to copy: ");

        noOfFilesBasicField = new JFormattedTextField();
        noOfFilesBasicField.setValue(new Integer(noOfAvailableFiles));
        noOfFilesBasicField.setColumns(10);
        noOfFilesBasicField.setToolTipText("Number of files to copy.");

        addConstrainedComponent(basicPanel, jLabelNoOfFilesBasic, 0, 1, 1, 1, GridBagConstraints.EAST, GridBagConstraints.NONE);
        addConstrainedComponent(basicPanel, noOfFilesBasicField , 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE);

        JLabel jLabelStatusBasic = new JLabel();
        jLabelStatusBasic.setText("<html><p></p>"
                + "<p></p>"
                + "<p></p>"
                + "<p></p>"
                +"<p>Enter number of files to be copied and press ok. </p>"
                +"<p>Note that values less than 1 and greater than "+ noOfAvailableFiles +" are ignored.</p>"
                + "<p>For more options see the advance tab! </p>"
                + "<p></p>"
                + "<p></p>"
                + "<p></p>"
                +"<p></p></html>");

        jLabelStatusBasic.setForeground(Color.darkGray);
        addConstrainedComponent(basicPanel, jLabelStatusBasic, 0, 3, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

        JButton jButtonHelpBasic = new JButton();
        jButtonHelpBasic.setText("Help");
        jButtonHelpBasic.setToolTipText("Opens help page.");
        jButtonHelpBasic.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                BrowserLauncher br = new BrowserLauncher();
                br.run(helpPage);
            }
        });

        addConstrainedComponent(basicPanel, jButtonHelpBasic, 0, 4, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

        JButton jButtonOkBasic = new JButton("OK");
        jButtonOkBasic.setToolTipText("Checks the parameters and performs the copy.");
        jButtonOkBasic.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                okParamsActionBasic();
            }
        });
        addConstrainedComponent(basicPanel, jButtonOkBasic, 1, 4, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

        return basicPanel;
    }


    private JPanel createAdvancePanel(){
        JPanel advancePanel = new JPanel(new GridBagLayout());

        advancePanel.setBorder(BorderFactory.createEmptyBorder(10,5,10,5));

        addConstrainedComponent(advancePanel, new JLabel("Number of available files: "+ noOfAvailableFiles), 0, 0, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

        panelGroup = Box.createVerticalBox();

        JPanel statePanel1 = new JPanel();
        statePanel1.setLayout( new GridBagLayout() );

        groupingChoiceComboBox = new JComboBox<String>(groupFileDecisionChoices);
        groupingChoiceComboBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                groupingDecisionMade();
            }
        });
        addConstrainedComponent(statePanel1, groupingChoiceComboBox, 0, 0, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE);

        jLabelSplitString = new JLabel("Substring: ");
        jLabelSplitString.setEnabled(false);
        addConstrainedComponent(statePanel1, jLabelSplitString, 0, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE);
        fileNameSplitField = new JTextField("", 15);
        fileNameSplitField.setToolTipText("String to split file names to group files.");
        fileNameSplitField.setEnabled(false);
        addConstrainedComponent(statePanel1, fileNameSplitField, 0, 2, 2, 1, GridBagConstraints.WEST, GridBagConstraints.NONE);

        panelGroup.add(statePanel1);
        panelGroup.setBorder(BorderFactory.createTitledBorder("Grouping"));

        addConstrainedComponent(advancePanel, panelGroup, 0, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE);

        panelAdvanceOptions = Box.createVerticalBox();

        JPanel statePanel = new JPanel();
        statePanel.setLayout( new GridBagLayout());

        jLabelMin = new JLabel("Minimum: ");
        addConstrainedComponent(statePanel, jLabelMin, 0, 0, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE);
        minTimesField = new JFormattedTextField();
        minTimesField.setValue(new Integer(1));
        minTimesField.setColumns(8);
        minTimesField.setToolTipText("Min. number of times each image repeats.");
        addConstrainedComponent(statePanel, minTimesField, 1, 0, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE);

        jLabelMax = new JLabel("Maximum: ");
        addConstrainedComponent(statePanel, jLabelMax, 0, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE);
        maxTimesField = new JFormattedTextField();
        maxTimesField.setValue(new Integer(1));
        maxTimesField.setColumns(8);
        maxTimesField.setToolTipText("Max. number of times each image repeats.");
        addConstrainedComponent(statePanel, maxTimesField, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE);

        jLabelShowings = new JLabel("Total: ");
        addConstrainedComponent(statePanel, jLabelShowings, 0, 2, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE);
        noOfShowingsField = new JFormattedTextField();
        noOfShowingsField.setValue(new Integer(noOfShowings));
        noOfShowingsField.setColumns(8);
        noOfShowingsField.setToolTipText("No. of entities (files / groups) to copy.");
        addConstrainedComponent(statePanel, noOfShowingsField, 1, 2, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE);
        panelAdvanceOptions.add(statePanel);

        panelAdvanceOptions.setBorder(BorderFactory.createTitledBorder("Replicates"));

        addConstrainedComponent(advancePanel, panelAdvanceOptions, 1, 1, 1, 1, GridBagConstraints.WEST, GridBagConstraints.NONE);

        jLabelStatus = new JLabel();
        jLabelStatus.setText("<html><p> Status here...</p>"
                +"<p>\n</p>"
                +"<p>\n</p>"
                +"<p>\n </p></html>");

        jLabelStatus.setForeground(Color.LIGHT_GRAY);
        addConstrainedComponent(advancePanel, jLabelStatus, 0, 3, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

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

        addConstrainedComponent(advancePanel, jButtonHelp, 0, 4, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

        JButton jButtonOk = new JButton("OK");
        jButtonOk.setToolTipText("Checks the parameters and performs the copy.");
        jButtonOk.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                okParamsAction();
            }
        });
        addConstrainedComponent(advancePanel, jButtonOk, 1, 4, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.NONE);

        return advancePanel;
    }

    private void initParamsWindowUpdateStatus(String msg) {
        jLabelStatus.setText(msg);
        jLabelStatus.setForeground(Color.RED);
        initParamsWindow.revalidate();
        initParamsWindow.repaint();
    }

    /*********************************************** GUI Action functions ********************************************************/

    private void groupingDecisionMade(){
        if(groupingChoiceComboBox.getSelectedIndex() <= 0 ){
            fileNameSplitField.setText("");
            fileNameSplitField.setEnabled(false);
            jLabelSplitString.setEnabled(false);
            groupFiles = false;
        }
        else if(groupingChoiceComboBox.getSelectedIndex() == 1 ){
            fileNameSplitField.setEnabled(true);
            jLabelSplitString.setEnabled(true);
            groupFiles = true;
        }
    }

    private void okParamsAction(){

        basicMode = false;
        advanceMode = true;

        minTimes = Integer.parseInt(minTimesField.getText());
        maxTimes = Integer.parseInt(maxTimesField.getText());
        noOfShowings = Integer.parseInt(noOfShowingsField.getText());

        noOfFoundGroups = noOfAvailableFiles;
        if(groupFiles) {
            splitSequence  = fileNameSplitField.getText();
            if(!splitSequence.isEmpty()) {
                groupsStartString = generateStartStrings();
                noOfFoundGroups = groupsStartString.size();
            }
            else {
                groupFiles = false;
            }
        }

        if(areValuesFeasible()) {
            createOutPutFolder();
            createTableWindow();
            randomImageToOpen = new SecureRandom();

            createGroupStructure();

            if (listOfImageFilesAbsoluteGrouped != null) {
                groupUseCount = new int[noOfFoundGroups];
                Arrays.fill(groupUseCount, 0);
                copyRequestedImageGroup(noOfFoundGroups);

                saveTableWindow();

                initParamsWindow.dispose();
            }
            else {
                initParamsWindowUpdateStatus("Incorrect \"No of files per group\" or \"split string\"");
            }
        }
        else {
            initParamsWindowUpdateStatus("<html><p> 1. All values must be positive.</p>"
                    +"<p> 2. Maximum must be greater than minimum.</p>"
                    +"<p> 3. Number of showings must be between "+(minTimes * noOfFoundGroups)+" and "+ (maxTimes * noOfFoundGroups)+"</p></html>");
        }

        listOfImageFilesAbsoluteGrouped = null;
        listOfImageFilesNamesGrouped = null;
    }

    private void okParamsActionBasic(){

        basicMode = true;
        advanceMode = false;

        noOfBasicfileToCopy = Integer.parseInt(noOfFilesBasicField.getText());

        noOfFoundGroups = noOfAvailableFiles;

        if(noOfBasicfileToCopy < 1 || noOfBasicfileToCopy > noOfAvailableFiles)
            noOfBasicfileToCopy = noOfAvailableFiles;

        createOutPutFolder();
        createTableWindow();
        randomImageToOpen = new SecureRandom();

        createGroupStructure();

        if (listOfImageFilesAbsoluteGrouped != null) {
            groupUseCount = new int[noOfFoundGroups];
            Arrays.fill(groupUseCount, 0);
            copyRequestedImageGroupBasic(noOfBasicfileToCopy);

            saveTableWindow();

            initParamsWindow.dispose();
        }
        else {
            initParamsWindowUpdateStatus("An error occurred while copying");
        }

        listOfImageFilesAbsoluteGrouped = null;
        listOfImageFilesNamesGrouped = null;
    }

    private boolean areValuesFeasible(){
        boolean feasible = true;
        if( minTimes < 0 || maxTimes < 0 ){
            feasible  = false;
        }

        if( minTimes > maxTimes ){
            feasible  = false;
        }

        if( noOfShowings < (minTimes*noOfFoundGroups) || noOfShowings > (maxTimes*noOfFoundGroups) ){
            feasible  = false;
        }
        return  feasible;
    }

    /*********************************************************************************************************/

    private void copyRequestedImageGroup(int noOfGroups) {
        int copiedGroups = 0;
        // to guarantee the minimum
        for (int j = 0; j < minTimes; j++) {
            for (int i = 0; i < noOfGroups; i++) {
                int imageIndexToOpen = i;
                LinkedList<String> currentGroup = listOfImageFilesAbsoluteGrouped.get(i);
                LinkedList<String> currentNamesGroup = listOfImageFilesNamesGrouped.get(i);
                if (groupUseCount[imageIndexToOpen] < maxTimes) {
                    try {
                        copyGroup(currentGroup, currentNamesGroup);
                        copiedGroups++;
                        groupUseCount[imageIndexToOpen]++;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // copy remaining number of files
        while (copiedGroups < noOfShowings) {
            int imageIndexToOpen = randomImageToOpen.nextInt(noOfFoundGroups);
            if (groupUseCount[imageIndexToOpen] < maxTimes) {
                try {
                    copyGroup(listOfImageFilesAbsoluteGrouped.get(imageIndexToOpen), listOfImageFilesNamesGrouped.get(imageIndexToOpen));
                    copiedGroups++;
                    groupUseCount[imageIndexToOpen]++;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void copyRequestedImageGroupBasic(int noOfFilesToCopyBasic) {
        int copiedGroups = 0;
        // copy remaining number of files
        while (copiedGroups < noOfFilesToCopyBasic) {
            int imageIndexToOpen = randomImageToOpen.nextInt(noOfAvailableFiles);
            if (groupUseCount[imageIndexToOpen] < 1) {
                try {
                    copyGroup(listOfImageFilesAbsoluteGrouped.get(imageIndexToOpen), listOfImageFilesNamesGrouped.get(imageIndexToOpen));
                    copiedGroups++;
                    groupUseCount[imageIndexToOpen]++;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void copyGroup(LinkedList<String> group, LinkedList<String> nameGroup) throws IOException {

        String groupID = UUID.randomUUID().toString().replace("-", "_");

        for (int k = 0; k < group.size(); k++) {
            try {
                String sourceImage = group.get(k);
                String sourceImageName = nameGroup.get(k);

                if (!groupFiles) {
                    String fileExtension = sourceImageName.substring(sourceImageName.lastIndexOf("."));
                    String fileNameRandom = groupID + fileExtension;
                    copyImage(sourceImage, fileNameRandom);
                } else {
                    int indexOfSplitString = sourceImageName.indexOf(splitSequence);

                    String subString = "";
                    if(indexOfSplitString >= 0){
                        subString = sourceImageName.substring(sourceImageName.indexOf(splitSequence));
                    }
                    else{
                        subString = sourceImageName.substring(sourceImageName.lastIndexOf("."));
                    }

                    String fileNameRandom = groupID + subString;
                    copyImage(sourceImage, fileNameRandom);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    /************************************************ Grouping related **********************************************************/

    private LinkedList<String> generateStartStrings() {
        LinkedList<String> groupsStartString = new LinkedList<String>();

        for (String name : listOfImageFileNames) {
            String startString = name.split(splitSequence)[0];
            if (!groupsStartString.contains(startString)) {
                groupsStartString.add(startString);
            }
        }

        return groupsStartString;
    }

    private void createGroupStructure(){
        listOfImageFilesAbsoluteGrouped = new LinkedList<LinkedList<String>>();
        listOfImageFilesNamesGrouped = new LinkedList<LinkedList<String>>();

        for(int i = 0; i<noOfFoundGroups; i++){
            listOfImageFilesAbsoluteGrouped.add( new LinkedList<String>() );
            listOfImageFilesNamesGrouped.add( new LinkedList<String>() );
        }

        // create group
        if(!groupFiles){
            for (int i = 0; i < listOfImageFileNames.size(); i++) {
                listOfImageFilesAbsoluteGrouped.get(i).add(listOfImageFilesAbsolute.get(i));
                listOfImageFilesNamesGrouped.get(i).add(listOfImageFileNames.get(i));
            }
        }
        else {
            for (int i = 0; i < listOfImageFileNames.size(); i++) {
                String name = listOfImageFileNames.get(i);

                int indexOfSplitString = name.indexOf(splitSequence);

                String startString = "";
                if(indexOfSplitString < 0){
                    startString = name;
                }
                else {
                    startString = name.substring(0, indexOfSplitString);
                }
                int index = groupsStartString.indexOf(startString);
                listOfImageFilesAbsoluteGrouped.get(index).add(listOfImageFilesAbsolute.get(i));
                listOfImageFilesNamesGrouped.get(index).add(listOfImageFileNames.get(i));
            }
        }
    }

    /**************************************** Output functions *******************************************/
    private void createTableWindow() {
        mappingTable = new ResultsTable();
    }

    public void appendToTable(String origName, String assignedName) {
        int rowIndex = mappingTable.getCounter();
        mappingTable.setValue(tableColOrig, rowIndex, origName);
        mappingTable.setValue(tableColAssigned, rowIndex, assignedName);

        if (rowIndex == 0) {
            String paramsString = "";
            if(basicMode == true) {
                paramsString = "No. of available files: " + noOfAvailableFiles + ", No. of files to copy: " + noOfBasicfileToCopy;
            }
            else{
                paramsString = "No. of available files: " + noOfAvailableFiles + ", No. of group: " + noOfFoundGroups + ", File names split string: " + splitSequence + ", Min repeat: " + minTimes + ", Max repeat: " + maxTimes + ", No of replicates: " + noOfShowings;
            }
            mappingTable.setValue(tableColParams, rowIndex, paramsString);

        }
    }

    private void saveTableWindow() {
        String path = topLevelDir + File.separator + tableName + timeStamp + suffixTable;
        mappingTable.save(path);
    }

    private String getTimeString() {
        LocalDateTime ldt = LocalDateTime.now();
        String timeString = ldt.toString().replace(":", "_").replace(".", "_").replace("-", "_").replace("T", "_T_");
        return timeString;
    }

    private void copyImage(String sourceImage, String randomName) throws IOException {
        // https://docs.oracle.com/javase/tutorial/essential/io/copy.html
        // https://docs.oracle.com/javase/8/docs/api/java/nio/file/Files.html
        Path srcPath = Paths.get(sourceImage);
        Path fileName = Paths.get("File_" + randomName);
        Path dstPath = Paths.get(outputFolder).resolve(fileName);
        Files.copy(srcPath, dstPath);
        appendToTable(sourceImage, dstPath.toString());
    }

    private void createOutPutFolder() {
        outputFolder = topLevelDir + outputFolderName + timeStamp;
        new File(outputFolder).mkdir();
    }



    /**************************************** Initial folder scan related unctions *******************************************/
    private boolean chooseMainFolder() {

        topLevelDir = IJ.getDir("Choose top level directory");
        if (topLevelDir == null || topLevelDir.isEmpty()) {
            return false;
        }

        listImageFilesSkipFormat(topLevelDir, outputFolderName); // outputFolderName is to skip those folders
        return true;
    }

    public void listImageFilesSkipFormat(String path, String skipString) {
        listOfImageFileNames = new ArrayList<String>();
        listOfImageFilesAbsolute = new ArrayList<String>();

        Stack<String> listOfFolder = new Stack<>();

        listOfFolder.push(path);

        while (!listOfFolder.empty()) {
            String folderToScan = listOfFolder.pop();

            String[] listOfAllFiles = getListAllFiles(folderToScan);

            int noOfAllFiles = listOfAllFiles.length;

            for (int i = 0; i < noOfAllFiles; i++) {
                String fileName = listOfAllFiles[i];
                File f = new File(folderToScan + fileName);

                if (f.isDirectory() && !fileName.startsWith(skipString)) {
                    listOfFolder.push(folderToScan + fileName + File.separator);
                } else if (f.isFile() && !f.isHidden() && !fileName.startsWith(".")) {
                    listOfImageFileNames.add(fileName);
                    listOfImageFilesAbsolute.add(folderToScan + fileName);
                }
            }
        }

    }

    private String[] getListAllFiles(String path) {
        File f_sub = new File(path);
        String[] listOfAllFiles = f_sub.list();
        return listOfAllFiles;
    }

    /************************************************ File extension related **********************************************************/
    private boolean filterFilesAsPerExtensions() {
        HashSet<String> extensionListTemp = new HashSet<String>();
        for (String fileNameUnderExamination : listOfImageFilesAbsolute) {
            extensionListTemp.add(extractFileExtension(fileNameUnderExamination));
        }

        int noOfExtensions = extensionListTemp.size();
        boolean[] defaultValues = new boolean[noOfExtensions];
        String[] allExtensions = extensionListTemp.toArray(new String[noOfExtensions]);
        Arrays.fill(defaultValues, true);

        GenericDialog gd = new GenericDialog("Choose...");
        gd.addMessage("Which file extensions to copy?");
        gd.addCheckboxGroup(noOfExtensions, 1, allExtensions, defaultValues);
        gd.showDialog();

        if (gd.wasCanceled()) {
            return false;
        }
        for (int row = 0; row < noOfExtensions; row++) {
            defaultValues[row] = gd.getNextBoolean();
        }

        LinkedList<String> extensionListFinal = new LinkedList<>();
        for (int i = 0; i < noOfExtensions; i++) {
            if (defaultValues[i]) {
                extensionListFinal.add(allExtensions[i]);
            }
        }

        // remove unwanted files from the list
        for (int i = listOfImageFilesAbsolute.size() - 1; i >= 0; i--) {
            if (!wantedExtension(extractFileExtension(listOfImageFilesAbsolute.get(i)), extensionListFinal)) {
                listOfImageFilesAbsolute.remove(i);
                listOfImageFileNames.remove(i);
            }
        }
        return true;
    }

    private boolean wantedExtension(String fileExtension, LinkedList<String> wantedExtensions) {
        for (String ext : wantedExtensions) {
            if (ext.matches(fileExtension)) {
                return true;
            }
        }
        return false;
    }

    private String extractFileExtension(String fileNameUnderExamination) {
        String theExtension = fileNameUnderExamination.substring(fileNameUnderExamination.lastIndexOf('.') + 1);
        return theExtension;
    }

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

}
