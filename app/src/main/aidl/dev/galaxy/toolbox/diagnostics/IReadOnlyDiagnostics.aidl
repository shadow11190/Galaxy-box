package dev.galaxy.toolbox.diagnostics;

interface IReadOnlyDiagnostics {
    void destroy() = 16777114;
    String collectBattery() = 1;
    String collectGpu() = 2;
}
