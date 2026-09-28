package com.infernalmobs.config;

import java.util.List;

/** 一次配置加载的提交结果。失败时不会替换当前生效快照。 */
public record ConfigLoadResult(boolean committed, boolean degraded, List<ConfigDiagnostic> diagnostics) {

    public ConfigLoadResult {
        diagnostics = List.copyOf(diagnostics);
    }

    public long errorCount() {
        return diagnostics.stream()
                .filter(diagnostic -> diagnostic.severity() == ConfigDiagnostic.Severity.ERROR)
                .count();
    }

    public long warningCount() {
        return diagnostics.stream()
                .filter(diagnostic -> diagnostic.severity() == ConfigDiagnostic.Severity.WARNING)
                .count();
    }
}
