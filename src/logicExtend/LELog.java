package logicExtend;

import arc.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 日志工具类，提供中文日志记录功能
 */
public class LELog {
    
    /** 日志记录开关，1表示开启日志，0表示关闭日志，默认关闭 */
    public static int debugLog = 0;
    
    /** 日志文件路径 */
    private static final String LOG_DIR = "E:\\SteamLibrary\\steamapps\\common\\Mindustry";
    /** 日志文件完整路径 */
    private static String logFilePath = "";
    
    /** 日志文件 */
    private static PrintWriter logWriter = null;
    
    /**
     * 初始化日志文件
     */
    public static void initLogFile() {
        // 只在日志开关开启时初始化日志文件
        if (debugLog == 1) {
            Log.infoTag("嵌套逻辑", "开始初始化日志文件...");
            Log.infoTag("嵌套逻辑", "LOG_DIR: " + LOG_DIR);
            
            try {
                // 测试文件路径是否正确
                File testFile = new File(LOG_DIR);
                String absolutePath = testFile.getAbsolutePath();
                Log.infoTag("嵌套逻辑", "绝对路径: " + absolutePath);
                logFilePath = absolutePath + "\\nestedlogic.log";
                Log.infoTag("嵌套逻辑", "完整日志文件路径: " + logFilePath);
                
                // 检查目录是否存在
                File logDir = new File(absolutePath);
                Log.infoTag("嵌套逻辑", "目录存在: " + logDir.exists());
                
                if (!logDir.exists()) {
                    Log.infoTag("嵌套逻辑", "目录不存在，尝试创建...");
                    boolean created = logDir.mkdirs();
                    Log.infoTag("嵌套逻辑", "目录创建结果: " + created);
                    Log.infoTag("嵌套逻辑", "创建后目录存在: " + logDir.exists());
                }
                
                if (logDir.exists()) {
                    Log.infoTag("嵌套逻辑", "目录存在，检查写入权限...");
                    boolean canWrite = logDir.canWrite();
                    Log.infoTag("嵌套逻辑", "目录写入权限: " + canWrite);
                    
                    // 直接使用完整路径创建文件
                    File logFile = new File(logFilePath);
                    Log.infoTag("嵌套逻辑", "日志文件存在: " + logFile.exists());
                    Log.infoTag("嵌套逻辑", "日志文件可写: " + logFile.canWrite());
                    
                    try {
                        // 尝试创建文件
                        if (!logFile.exists()) {
                            boolean fileCreated = logFile.createNewFile();
                            Log.infoTag("嵌套逻辑", "文件创建结果: " + fileCreated);
                            Log.infoTag("嵌套逻辑", "创建后文件存在: " + logFile.exists());
                        }
                        
                        // 尝试打开文件写入
                        logWriter = new PrintWriter(new FileWriter(logFile, true));
                        Log.infoTag("嵌套逻辑", "日志文件已成功初始化: " + logFile.getAbsolutePath());
                        
                        // 写入测试日志
                        Log.infoTag("嵌套逻辑", "尝试写入测试日志...");
                        String testMessage = "日志文件初始化成功 - " + new Date().toString();
                        logWriter.println("[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "] [嵌套逻辑] " + testMessage);
                        logWriter.flush();
                        Log.infoTag("嵌套逻辑", "测试日志写入成功");
                        
                    } catch (IOException e) {
                        Log.errTag("嵌套逻辑", "无法创建或写入日志文件: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    Log.errTag("嵌套逻辑", "无法创建目录: " + absolutePath);
                }
            } catch (Exception e) {
                Log.errTag("嵌套逻辑", "初始化日志文件时发生错误: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * 关闭日志文件
     */
    public static void closeLogFile() {
        if (logWriter != null) {
            logWriter.close();
            logWriter = null;
        }
    }
    
    /**
     * 记录日志到文件
     * @param message 日志消息
     */
    private static void writeToFile(String message) {
        // 只在日志开关开启时写入文件
        if (debugLog == 1 && logWriter != null) {
            try {
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                logWriter.println("[" + timestamp + "] [嵌套逻辑] " + message);
                logWriter.flush();
                Log.infoTag("嵌套逻辑", "日志已写入文件: " + message);
            } catch (Exception e) {
                Log.errTag("嵌套逻辑", "写入日志文件时发生错误: " + e.getMessage());
                e.printStackTrace();
            }
        } else if (logWriter == null && debugLog == 1) {
            Log.infoTag("嵌套逻辑", "日志写入失败: logWriter 为 null");
        }
    }
    
    /**
     * 记录调试日志
     * @param message 日志消息
     */
    public static void debug(String message) {
        if (debugLog == 1) {
            Log.infoTag("嵌套逻辑", message);
            writeToFile("DEBUG: " + message);
        }
    }
    
    /**
     * 记录信息日志
     * @param message 日志消息
     */
    public static void info(String message) {
        if (debugLog == 1) {
            Log.infoTag("嵌套逻辑", message);
            writeToFile("INFO: " + message);
        }
    }
    
    /**
     * 记录错误日志
     * @param message 日志消息
     * @param t 异常对象
     */
    public static void error(String message, Throwable t) {
        if (debugLog == 1) {
            Log.errTag("嵌套逻辑", message + ": " + t.getMessage());
            writeToFile("ERROR: " + message + ": " + t.getMessage());
        }
    }
    
    /**
     * 记录错误日志
     * @param message 日志消息
     */
    public static void error(String message) {
        if (debugLog == 1) {
            Log.errTag("嵌套逻辑", message);
            writeToFile("ERROR: " + message);
        }
    }
}