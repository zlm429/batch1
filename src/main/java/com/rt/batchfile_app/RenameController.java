package com.rt.batchfile_app;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class RenameController {

    @FXML
    private TextArea logArea;

    @FXML
    private TextField excelPathField;

    @FXML
    private TextField folderPathField;

    @FXML
    private TextArea selectedFoldersArea;

    private List<String> excelNames = new ArrayList<>();
    private List<File> selectedFolderFiles = new ArrayList<>();
    private List<File> selectedFiles = new ArrayList<>(); // 选中的文件列表
    private File parentDirectory = null;

    @FXML
    public void initialize() {
        // 初始化界面
        selectedFoldersArea.setEditable(false);
        
        // 为Excel路径输入框添加拖拽支持
        setupDragAndDrop();
        
        // 为Excel路径输入框添加粘贴支持
        setupPasteHandler();
    }

    /**
     * 设置拖拽功能
     */
    private void setupDragAndDrop() {
        excelPathField.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            event.consume();
        });
        
        excelPathField.setOnDragDropped(event -> {
            javafx.scene.input.Dragboard db = event.getDragboard();
            boolean success = false;
            
            if (db.hasFiles()) {
                File file = db.getFiles().get(0);
                if (file.getName().toLowerCase().endsWith(".xlsx") || 
                    file.getName().toLowerCase().endsWith(".xls")) {
                    excelPathField.setText(file.getAbsolutePath());
                    loadExcelData(file);
                    success = true;
                    appendLog("通过拖拽加载Excel文件: " + file.getName());
                } else {
                    showWarning("请拖拽Excel文件（.xlsx或.xls）");
                }
            }
            
            event.setDropCompleted(success);
            event.consume();
        });
    }

    /**
     * 设置粘贴功能
     */
    private void setupPasteHandler() {
        excelPathField.setOnKeyPressed(event -> {
            // 跨平台支持: Windows/Linux 用 Ctrl+V, macOS 用 Cmd+V
            boolean isPaste = (event.isControlDown() || event.isMetaDown()) && 
                             event.getCode() == javafx.scene.input.KeyCode.V;
            
            if (isPaste) {
                // 延迟执行，让粘贴操作完成后再处理
                javafx.application.Platform.runLater(() -> {
                    String path = excelPathField.getText();
                    if (path != null && !path.trim().isEmpty()) {
                        File file = new File(path.trim());
                        if (file.exists() && file.isFile()) {
                            if (file.getName().toLowerCase().endsWith(".xlsx") || 
                                file.getName().toLowerCase().endsWith(".xls")) {
                                loadExcelData(file);
                                appendLog("通过粘贴加载Excel文件: " + file.getName());
                            } else {
                                showWarning("粘贴的路径不是Excel文件");
                            }
                        }
                    }
                });
            }
        });
    }

    /**
     * 从剪贴板获取选中的文件夹和文件
     */
    @FXML
    private void getFoldersFromClipboard() {
        selectedFolderFiles.clear();
        selectedFiles.clear();
        
        try {
            // 使用 JavaFX 原生剪贴板（跨平台兼容）
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            
            if (clipboard == null || !clipboard.hasFiles()) {
                // 如果 JavaFX 剪贴板没有文件，尝试 AWT 剪贴板（Windows 兼容）
                try {
                    java.awt.Toolkit toolkit = java.awt.Toolkit.getDefaultToolkit();
                    java.awt.datatransfer.Clipboard awtClipboard = toolkit.getSystemClipboard();
                    java.awt.datatransfer.Transferable content = awtClipboard.getContents(null);
                    
                    if (content != null && content.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked")
                        java.util.List<File> files = (java.util.List<File>) content.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                        
                        for (File file : files) {
                            if (file.isDirectory()) {
                                selectedFolderFiles.add(file);
                            } else if (file.isFile()) {
                                selectedFiles.add(file);
                            }
                        }
                    }
                } catch (Exception e) {
                    // AWT 剪贴板失败，忽略
                }
                
                if (selectedFolderFiles.isEmpty() && selectedFiles.isEmpty()) {
                    showWarning("剪贴板为空，请先在资源管理器中选中文件夹或文件并按 Ctrl+C (macOS: Cmd+C) 复制");
                    return;
                }
            } else {
                // JavaFX 剪贴板有文件
                for (File file : clipboard.getFiles()) {
                    if (file.isDirectory()) {
                        selectedFolderFiles.add(file);
                    } else if (file.isFile()) {
                        selectedFiles.add(file);
                    }
                }
            }
            
            // 显示选中的文件夹和文件路径
            StringBuilder sb = new StringBuilder();
            if (!selectedFolderFiles.isEmpty()) {
                sb.append("文件夹:\n");
                for (File folder : selectedFolderFiles) {
                    sb.append("  ").append(folder.getAbsolutePath()).append("\n");
                }
            }
            if (!selectedFiles.isEmpty()) {
                sb.append("\n文件:\n");
                for (File file : selectedFiles) {
                    sb.append("  ").append(file.getAbsolutePath()).append("\n");
                }
            }
            selectedFoldersArea.setText(sb.toString());
            
            // 获取父目录（所有选中项目的共同父目录）
            if (!selectedFolderFiles.isEmpty()) {
                parentDirectory = selectedFolderFiles.get(0).getParentFile();
            } else if (!selectedFiles.isEmpty()) {
                parentDirectory = selectedFiles.get(0).getParentFile();
            }
            if (parentDirectory != null) {
                folderPathField.setText(parentDirectory.getAbsolutePath());
            }
            
            appendLog("从剪贴板获取了 " + selectedFolderFiles.size() + " 个文件夹和 " + selectedFiles.size() + " 个文件");
        } catch (Exception e) {
            showError("读取剪贴板失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 选择Excel文件
     */
    @FXML
    private void selectExcelFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择Excel文件");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls")
        );
        
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            excelPathField.setText(selectedFile.getAbsolutePath());
            loadExcelData(selectedFile);
        }
    }



    /**
     * 加载Excel数据（读取4列：A+B+C+D）
     */
    private void loadExcelData(File excelFile) {
        excelNames.clear();
        try (FileInputStream fis = new FileInputStream(excelFile);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            int rowNum = 0;
            for (Row row : sheet) {
                rowNum++;
                // 跳过空行
                if (row.getRowNum() == 0 && row.getCell(0) == null) {
                    continue;
                }
                
                // 拼接4列数据：A+B+C+D（用短横线连接）
                StringBuilder nameBuilder = new StringBuilder();
                for (int i = 0; i < 4; i++) {
                    org.apache.poi.ss.usermodel.Cell cell = row.getCell(i);
                    if (cell != null) {
                        String value = getCellValue(cell);
                        if (value != null && !value.trim().isEmpty()) {
                            if (nameBuilder.length() > 0) {
                                nameBuilder.append("-"); // 用短横线连接各列
                            }
                            nameBuilder.append(value.trim());
                        }
                    }
                }
                
                String combinedName = nameBuilder.toString();
                if (!combinedName.isEmpty()) {
                    excelNames.add(combinedName);
                }
            }
            appendLog("从 Excel中加载了 " + excelNames.size() + " 个名称（4列组合）");
        } catch (IOException e) {
            showError("读取Excel文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取单元格的值
     */
    private String getCellValue(org.apache.poi.ss.usermodel.Cell cell) {
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf((int) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    /**
     * 执行重命名文件夹操作
     */
    @FXML
    private void executeRename() {
        if (excelNames.isEmpty()) {
            showWarning("请先选择Excel文件并加载数据");
            return;
        }

        if (selectedFolderFiles.isEmpty()) {
            showWarning("请先从剪贴板获取要重命名的文件夹\n\n操作步骤：\n1. 在资源管理器中选中文件夹\n2. 按 Ctrl+C 复制\n3. 点击'从剪贴板获取文件夹'按钮");
            return;
        }
        
        if (selectedFolderFiles.size() > excelNames.size()) {
            showError("选中的文件夹数量(" + selectedFolderFiles.size() + ")多于Excel中的名称数量(" + excelNames.size() + ")");
            return;
        }

        appendLog("开始重命名文件夹...");
        appendLog("选中了 " + selectedFolderFiles.size() + " 个文件夹待重命名");

        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < selectedFolderFiles.size(); i++) {
            File folder = selectedFolderFiles.get(i);
            String newName = excelNames.get(i);

            try {
                renameFolderOnly(folder, newName);
                successCount++;
                appendLog("成功重命名文件夹: " + folder.getName() + " -> " + newName);
            } catch (Exception e) {
                failCount++;
                appendLog("重命名文件夹失败: " + folder.getName() + ", 错误: " + e.getMessage());
                e.printStackTrace();
            }
        }

        appendLog("文件夹重命名完成! 成功: " + successCount + ", 失败: " + failCount);
        showAlert("重命名完成", "成功: " + successCount + ", 失败: " + failCount);
        
        // 清空选择
        selectedFolderFiles.clear();
        selectedFoldersArea.clear();
    }

    /**
     * 执行重命名文件夹内文件操作
     */
    @FXML
    private void executeRenameFiles() {
        if (selectedFolderFiles.isEmpty()) {
            showWarning("请先从剪贴板获取要处理的文件夹\n\n操作步骤：\n1. 在资源管理器中选中文件夹\n2. 按 Ctrl+C 复制\n3. 点击'从剪贴板获取文件夹'按钮");
            return;
        }

        appendLog("开始重命名文件夹内的文件...");
        appendLog("选中了 " + selectedFolderFiles.size() + " 个文件夹待处理");

        int successCount = 0;
        int failCount = 0;
        int skipCount = 0;

        for (File folder : selectedFolderFiles) {
            try {
                // 使用文件夹名称的第一个-前面的部分作为文件新名称（料号）
                String folderName = folder.getName();
                String materialCode = extractMaterialCode(folderName);
                
                // 重命名文件夹内的所有文件
                File[] files = folder.listFiles();
                if (files == null || files.length == 0) {
                    appendLog("跳过空文件夹: " + folder.getName());
                    skipCount++;
                    continue;
                }
                
                renameFilesInFolder(folder, materialCode);
                successCount++;
                appendLog("成功重命名文件: " + folder.getName() + " 内的文件 -> " + materialCode);
            } catch (Exception e) {
                failCount++;
                appendLog("重命名文件失败: " + folder.getName() + ", 错误: " + e.getMessage());
                e.printStackTrace();
            }
        }

        appendLog("文件重命名完成! 成功: " + successCount + ", 失败: " + failCount + ", 跳过空文件夹: " + skipCount);
        showAlert("重命名完成", "成功: " + successCount + "\n失败: " + failCount + "\n跳过空文件夹: " + skipCount);
        
        // 清空选择
        selectedFolderFiles.clear();
        selectedFoldersArea.clear();
    }



    /**
     * 只重命名文件夹（不处理内部文件）
     */
    private void renameFolderOnly(File folder, String newFolderName) throws IOException {
        appendLog("正在处理文件夹: " + folder.getAbsolutePath());
        appendLog("新名称: " + newFolderName);
        
        // 清理文件名中的非法字符
        newFolderName = sanitizeFileName(newFolderName);
        appendLog("清理后的名称: " + newFolderName);
        
        File parentDir = folder.getParentFile();
        File newFolder = new File(parentDir, newFolderName);

        // 如果目标文件夹已存在，添加序号避免冲突
        if (newFolder.exists() && !newFolder.getAbsolutePath().equals(folder.getAbsolutePath())) {
            int counter = 1;
            String baseName = newFolderName;
            while (newFolder.exists()) {
                newFolderName = baseName + "_" + counter;
                newFolder = new File(parentDir, newFolderName);
                counter++;
            }
            appendLog("检测到名称冲突，使用: " + newFolderName);
        }

        // 重命名文件夹本身
        appendLog("重命名文件夹: " + folder.getName() + " -> " + newFolder.getName());
        if (!folder.renameTo(newFolder)) {
            throw new IOException("无法重命名文件夹: " + folder.getName() + 
                "\n源路径: " + folder.getAbsolutePath() + 
                "\n目标路径: " + newFolder.getAbsolutePath() +
                "\n可能原因：文件夹被其他程序占用或权限不足");
        }
        
        appendLog("文件夹重命名成功");
    }

    /**
     * 只重命名文件夹内的文件（不重命名文件夹本身）
     */
    private void renameFilesInFolder(File folder, String newFileName) throws IOException {
        appendLog("正在处理文件夹: " + folder.getAbsolutePath());
        appendLog("文件新名称（料号）: " + newFileName);
        
        // 清理文件名中的非法字符
        newFileName = sanitizeFileName(newFileName);
        appendLog("清理后的名称: " + newFileName);

        // 重命名文件夹内的所有文件
        File[] files = folder.listFiles();
        if (files != null && files.length > 0) {
            appendLog("文件夹内有 " + files.length + " 个文件");
            for (File file : files) {
                if (file.isFile()) {
                    String extension = getFileExtension(file.getName());
                    String newFullFileName = newFileName + (extension.isEmpty() ? "" : "." + extension);
                    File newFile = new File(folder, newFullFileName);
                    
                    appendLog("  重命名文件: " + file.getName() + " -> " + newFullFileName);
                    
                    if (!file.renameTo(newFile)) {
                        throw new IOException("无法重命名文件: " + file.getName() + 
                            "\n源路径: " + file.getAbsolutePath() + 
                            "\n目标路径: " + newFile.getAbsolutePath());
                    }
                }
            }
            appendLog("文件重命名成功");
        } else {
            appendLog("文件夹内没有文件");
        }
    }

    /**
     * 清理文件名中的非法字符（跨平台兼容）
     * Windows: \ / : * ? " < > |
     * macOS:   / (冒号: 会被系统自动转换)
     */
    private String sanitizeFileName(String fileName) {
        // 替换所有平台的非法字符
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        return "";
    }

    /**
     * 清空日志
     */
    @FXML
    private void clearLog() {
        logArea.clear();
    }

    /**
     * 追加日志
     */
    private void appendLog(String message) {
        javafx.application.Platform.runLater(() -> {
            logArea.appendText(message + "\n");
            logArea.setScrollTop(Double.MAX_VALUE); // 滚动到底部
        });
    }

    /**
     * 显示错误信息
     */
    private void showError(String message) {
        javafx.application.Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("错误");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * 显示警告信息
     */
    private void showWarning(String message) {
        javafx.application.Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("警告");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * 显示信息对话框
     */
    private void showAlert(String title, String message) {
        javafx.application.Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * 提取料号（第一个-前面的部分）
     * @param folderName 文件夹名称
     * @return 料号（第一个-前面的部分），如果没有-则返回整个名称
     */
    private String extractMaterialCode(String folderName) {
        if (folderName == null || folderName.isEmpty()) {
            return folderName;
        }
        
        int dashIndex = folderName.indexOf('-');
        if (dashIndex > 0) {
            // 找到第一个-，返回前面的部分
            return folderName.substring(0, dashIndex);
        } else {
            // 没有-，返回整个名称
            return folderName;
        }
    }

    /**
     * 执行文件分类移动到对应文件夹操作
     */
    @FXML
    private void executeSortFilesIntoFolders() {
        if (selectedFolderFiles.isEmpty()) {
            showWarning("请先从剪贴板获取文件夹\n\n操作步骤：\n1. 在资源管理器中选中文件夹和文件\n2. 按 Ctrl+C 复制\n3. 点击'从剪贴板获取文件夹'按钮");
            return;
        }

        if (selectedFiles.isEmpty()) {
            showWarning("请先从剪贴板获取要移动的文件\n\n操作步骤：\n1. 在资源管理器中选中文件夹和文件\n2. 按 Ctrl+C 复制\n3. 点击'从剪贴板获取文件夹'按钮");
            return;
        }

        appendLog("开始将文件分类移动到对应文件夹...");
        appendLog("选中了 " + selectedFolderFiles.size() + " 个文件夹和 " + selectedFiles.size() + " 个文件");

        int successCount = 0;
        int failCount = 0;
        int notMatchedCount = 0;

        // 遍历每个文件，查找匹配的文件夹
        for (File file : selectedFiles) {
            String fileName = file.getName();
            // 获取不带扩展名的文件名
            String fileNameWithoutExt = fileName;
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                fileNameWithoutExt = fileName.substring(0, dotIndex);
            }
            
            boolean matched = false;
            
            // 遍历所有文件夹，查找匹配的文件夹
            for (File folder : selectedFolderFiles) {
                String folderName = folder.getName();
                
                // 检查文件名是否包含文件夹名
                if (fileNameWithoutExt.contains(folderName)) {
                    try {
                        // 构建目标路径
                        File destFile = new File(folder, fileName);
                        
                        // 如果目标文件已存在，添加序号避免冲突
                        if (destFile.exists()) {
                            String baseName = fileNameWithoutExt;
                            String extension = dotIndex > 0 ? fileName.substring(dotIndex) : "";
                            int counter = 1;
                            while (destFile.exists()) {
                                String newName = baseName + "_" + counter + extension;
                                destFile = new File(folder, newName);
                                counter++;
                            }
                            appendLog("检测到文件冲突，使用新名称: " + destFile.getName());
                        }
                        
                        // 移动文件
                        if (file.renameTo(destFile)) {
                            successCount++;
                            appendLog("成功移动文件: " + fileName + " -> " + folder.getName() + "/" + destFile.getName());
                            matched = true;
                            break; // 找到匹配的文件夹后跳出循环
                        } else {
                            failCount++;
                            appendLog("移动文件失败: " + fileName + " -> " + folder.getName());
                        }
                    } catch (Exception e) {
                        failCount++;
                        appendLog("移动文件异常: " + fileName + ", 错误: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
            
            if (!matched && failCount == 0) {
                notMatchedCount++;
                appendLog("未找到匹配的文件夹: " + fileName);
            }
        }

        appendLog("文件分类移动完成! 成功: " + successCount + ", 失败: " + failCount + ", 未匹配: " + notMatchedCount);
        showAlert("分类移动完成", "成功: " + successCount + "\n失败: " + failCount + "\n未匹配: " + notMatchedCount);
        
        // 清空选择
        selectedFolderFiles.clear();
        selectedFiles.clear();
        selectedFoldersArea.clear();
    }
}
