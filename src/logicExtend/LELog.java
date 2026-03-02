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
        // 无论日志开关是否开启，都尝试初始化并输出调试信息
        try {
            File logDir = new File(LOG_DIR);
            Log.infoTag("嵌套逻辑", "尝试初始化日志文件，目录: " + logDir.getAbsolutePath());
            
            if (!logDir.exists()) {
                Log.infoTag("嵌套逻辑", "目录不存在，尝试创建...");
                boolean created = logDir.mkdirs();
                Log.infoTag("嵌套逻辑", "目录创建结果: " + created);
            }
            
            if (logDir.exists()) {
                Log.infoTag("嵌套逻辑", "目录存在，检查写入权限...");
                boolean canWrite = logDir.canWrite();
                Log.infoTag("嵌套逻辑", "目录写入权限: " + canWrite);
                
                String fileName = "nestedlogic.log";
                File logFile = new File(logDir, fileName);
                Log.infoTag("嵌套逻辑", "日志文件路径: " + logFile.getAbsolutePath());
                
                try {
                    logWriter = new PrintWriter(new FileWriter(logFile, true));
                    Log.infoTag("嵌套逻辑", "日志文件已成功初始化: " + logFile.getAbsolutePath());
                    // 写入测试日志
                    writeToFile("日志文件初始化成功");
                } catch (IOException e) {
                    Log.errTag("嵌套逻辑", "无法创建日志文件: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                Log.errTag("嵌套逻辑", "无法创建目录: " + logDir.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.errTag("嵌套逻辑", "初始化日志文件时发生错误: " + e.getMessage());
            e.printStackTrace();
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
        // 无论日志开关是否开启，都尝试写入文件
        if (logWriter != null) {
            try {
                String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                logWriter.println("[" + timestamp + "] [嵌套逻辑] " + message);
                logWriter.flush();
                Log.infoTag("嵌套逻辑", "日志已写入文件: " + message);
            } catch (Exception e) {
                Log.errTag("嵌套逻辑", "写入日志文件时发生错误: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
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