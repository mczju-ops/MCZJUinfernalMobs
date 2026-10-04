package com.infernalmobs.config;

/**
 * 配置加载期间产生的诊断信息。路径使用“文件名:YAML 路径”的形式，便于管理员定位。
 */
public record ConfigDiagnostic(Severity severity, String path, String message) {

    public enum Severity {
        WARNING,
        ERROR
    }

    public static ConfigDiagnostic warning(String path, String message) {
        return new ConfigDiagnostic(Severity.WARNING, path, message);
    }

    public static ConfigDiagnostic error(String path, String message) {
        return new ConfigDiagnostic(Severity.ERROR, path, message);
    }
}
