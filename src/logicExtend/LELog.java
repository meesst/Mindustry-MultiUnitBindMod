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
    
    /** 日志文件 */
    private static PrintWriter logWriter = null;
    
    /**
     * 初始化日志文件
     */
    public static void initLogFile() {
        if (debugLog == 1) {
            try {
                File logDir = new File(LOG_DIR);
                if (!logDir.exists()) {
                    logDir.mkdirs();
                }
                
                String fileName = "nestedlogic.log";
                File logFile = new File(logDir, fileName);
                
                logWriter = new PrintWriter(new FileWriter(logFile, true));
                info("日志文件已初始化: " + logFile.getAbsolutePath());
            } catch (IOException e) {
                Log.errTag("嵌套逻辑", "无法创建日志文件: " + e.getMessage());
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
        if (debugLog == 1 && logWriter != null) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            logWriter.println("[" + timestamp + "] [嵌套逻辑] " + message);
            logWriter.flush();
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
        Log.infoTag("嵌套逻辑", message);
        writeToFile("INFO: " + message);
    }
    
    /**
     * 记录错误日志
     * @param message 日志消息
     * @param t 异常对象
     */
    public static void error(String message, Throwable t) {
        Log.errTag("嵌套逻辑", message + ": " + t.getMessage());
        writeToFile("ERROR: " + message + ": " + t.getMessage());
    }
    
    /**
     * 记录错误日志
     * @param message 日志消息
     */
    public static void error(String message) {
        Log.errTag("嵌套逻辑", message);
        writeToFile("ERROR: " + message);
    }
}