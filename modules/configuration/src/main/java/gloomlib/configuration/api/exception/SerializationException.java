package gloomlib.configuration.api.exception;

import gloomlib.diagnostic.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configuration serialization/deserialization exception integrating with
 * the unified {@link DiagnosticException} system.
 *
 * <p>Produces structured error output including YAML key path, value context
 * and type hint:
 *
 * <pre>
 * [Type]     at classes.warrior.health — Type mismatch: expected Integer
 *   health: 'abc'
 *   ↑ expected Integer, got String
 * </pre>
 *
 * <h3>工厂方法选择指南</h3>
 * <table>
 *   <tr><th>场景</th><th>方法</th><th>类别</th></tr>
 *   <tr><td>YAML 值类型与 Java 类型不匹配</td><td>{@link #typeMismatch}</td><td>TYPE</td></tr>
 *   <tr><td>必填字段缺失</td><td>{@link #missing}</td><td>SEMANTIC</td></tr>
 *   <tr><td>带 cause 的通用反序列化失败</td><td>{@link #wrap}</td><td>CONFIG</td></tr>
 *   <tr><td>@Check 注解校验失败</td><td>{@link #validation}</td><td>SEMANTIC</td></tr>
 * </table>
 */
public class SerializationException extends DiagnosticException {

    private final List<String> nodePath;
    private final Class<?> expectedType;
    private final Object actualValue;

    private SerializationException(
            @NotNull Diagnostic diagnostic,
            @Nullable List<String> nodePath,
            @Nullable Class<?> expectedType,
            @Nullable Object actualValue,
            @Nullable Throwable cause) {
        super(diagnostic, cause);
        this.nodePath = nodePath != null ? Collections.unmodifiableList(new ArrayList<>(nodePath)) : List.of();
        this.expectedType = expectedType;
        this.actualValue = actualValue;
    }


    /**
     * YAML 值类型与 Java 类型不匹配（例如字符串写入了 int 字段）。
     * 类别：{@link DiagnosticCategory#TYPE}
     *
     * @param path     YAML 键路径
     * @param expected 期望的 Java 类型
     * @param actual   实际读取到的值
     */
    public static SerializationException typeMismatch(
            @NotNull List<String> path,
            @Nullable Class<?> expected,
            @Nullable Object actual) {
        String msg = "Type mismatch: expected " + (expected != null ? expected.getSimpleName() : "?");
        String snippet = SourceView.yamlValueSnippet(path, actual, expected);
        Diagnostic d = new Diagnostic(pathLocation(path), DiagnosticCategory.TYPE, msg, snippet);
        return new SerializationException(d, path, expected, actual, null);
    }

    /**
     * YAML 中缺少必填字段或 section。
     * 类别：{@link DiagnosticCategory#SEMANTIC}
     *
     * @param path      父 YAML 键路径
     * @param fieldName 缺失的字段名
     */
    public static SerializationException missing(
            @NotNull List<String> path,
            @NotNull String fieldName) {
        List<String> fullPath = appendedPath(path, fieldName);
        String msg = "Missing required field: " + fieldName;
        Diagnostic d = new Diagnostic(pathLocation(fullPath), DiagnosticCategory.SEMANTIC, msg, null);
        return new SerializationException(d, fullPath, null, null, null);
    }

    /**
     * 带 cause 的通用反序列化失败，保留完整异常链。
     * 类别：{@link DiagnosticCategory#CONFIG}
     *
     * @param path     YAML 键路径
     * @param expected 期望的 Java 类型（可为 null）
     * @param actual   实际读取到的值（可为 null）
     * @param cause    原始异常
     */
    public static SerializationException wrap(
            @NotNull List<String> path,
            @Nullable Class<?> expected,
            @Nullable Object actual,
            @Nullable Throwable cause) {
        String msg = (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank())
                ? cause.getMessage()
                : "Deserialization failed";
        String snippet = SourceView.yamlValueSnippet(path, actual, expected);
        Diagnostic d = new Diagnostic(pathLocation(path), DiagnosticCategory.CONFIG, msg, snippet);
        return new SerializationException(d, path, expected, actual, cause);
    }

    /**
     * {@code @Check} 注解校验失败。
     * 类别：{@link DiagnosticCategory#SEMANTIC}
     *
     * @param path    YAML 键路径
     * @param message 校验失败原因
     */
    public static SerializationException validation(
            @NotNull List<String> path,
            @NotNull String message) {
        Diagnostic d = new Diagnostic(pathLocation(path), DiagnosticCategory.SEMANTIC, message, null);
        return new SerializationException(d, path, null, null, null);
    }


    private static SourceLocation pathLocation(List<String> path) {
        if (path == null || path.isEmpty()) return SourceLocation.UNKNOWN;

        String filename = LoadContext.filename();
        String dotPath = String.join(".", path);

        if (filename != null) {
            int line = LoadContext.lineFor(path);
            // Encode both file:line and dotpath into the source string so that
            // Diagnostic.format() prints: "at config.yml:15 (classes.warrior.health)"
            String source = line > 0
                    ? filename + ":" + line + " (" + dotPath + ")"
                    : filename + " (" + dotPath + ")";
            return new SourceLocation(source, 0, 0);
        }

        return SourceLocation.ofYamlPath(path);
    }

    private static List<String> appendedPath(List<String> path, String key) {
        List<String> full = new ArrayList<>(path != null ? path : List.of());
        full.add(key);
        return full;
    }


    /**
     * YAML 键路径，例如 {@code ["classes", "warrior", "health"]}。
     */
    @NotNull
    public List<String> getNodePath() {
        return nodePath;
    }

    /**
     * 期望的 Java 类型，可为 null。
     */
    @Nullable
    public Class<?> getExpectedType() {
        return expectedType;
    }

    /**
     * YAML 中读取到的实际值，可为 null。
     */
    @Nullable
    public Object getActualValue() {
        return actualValue;
    }

    /**
     * 路径的点分隔形式，根路径返回 {@code "<root>"}。
     */
    @NotNull
    public String getPathString() {
        return nodePath.isEmpty() ? "<root>" : String.join(".", nodePath);
    }
}
