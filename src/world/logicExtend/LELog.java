package world.logicExtend;

import arc.util.Log;

/**
 * 日志工具类，提供中文日志记录功能
 */
public class LELog {
    
    /** 日志记录开关，1表示开启日志，0表示关闭日志，默认关闭 */
    public static int debugLog = 0;
    
    /**
     * 记录调试日志
     * @param message 日志消息
     */
    public static void debug(String message) {
        if (debugLog == 1) {
            Log.infoTag("嵌套逻辑", message);
        }
    }
    
    /**
     * 记录信息日志
     * @param message 日志消息
     */
    public static void info(String message) {
        Log.infoTag("嵌套逻辑", message);
    }
    
    /**
     * 记录错误日志
     * @param message 日志消息
     * @param t 异常对象
     */
    public static void error(String message, Throwable t) {
        Log.errTag("嵌套逻辑", message + ": " + t.getMessage());
    }
    
    /**
     * 记录错误日志
     * @param message 日志消息
     */
    public static void error(String message) {
        Log.errTag("嵌套逻辑", message);
    }
}