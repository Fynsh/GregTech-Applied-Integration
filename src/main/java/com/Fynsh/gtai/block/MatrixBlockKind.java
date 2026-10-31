package com.Fynsh.gtai.block;

import org.jetbrains.annotations.Nullable;

/**
 * 一个矩阵方块在注册名中携带的完整结构语义。
 *
 * <p>199 个方块的名字本身是系统化的，因此这里集中解析一次，供成型判定与后续逻辑使用。
 * 解析失败会在 {@code MatrixBlocks} 类初始化时直接抛异常，避免"少了一个方块却悄悄运行"。</p>
 *
 * @param role     结构角色
 * @param family   外壳/核心类型；玻璃、空核心、变压器等没有类型时为 {@code null}
 * @param level    外壳等级；处理核心、变压器等没有等级时为 {@code null}
 * @param process  处理核心的处理类型；其它角色为 {@code null}
 * @param voltage  电压等级；只对处理核心、速度核心、变压器有意义
 * @param extended 是否为扩展变体
 */
public record MatrixBlockKind(MatrixBlockRole role, @Nullable MatrixFamily family,
        @Nullable MatrixShellLevel level, @Nullable MatrixProcess process, @Nullable MatrixVoltage voltage,
        boolean extended) {

    public MatrixBlockKind {
        if (role == null) {
            throw new IllegalArgumentException("Matrix block role must not be null");
        }
        switch (role) {
            case FRAME -> {
                require(family != null, role, "family");
                require(level != null, role, "level");
            }
            // 当前方块表里样板核心/速度核心没有等级变体，注册名只带类型（速度核心另带电压）；
            // 等级字段保留给后续等级变体，存在时才参与校验。
            case PATTERN_CORE, SPEED_CORE -> require(family != null, role, "family");
            case GLASS -> require(level != null, role, "level");
            case PROCESSING_CORE -> require(process != null, role, "process");
            case MONITOR -> require(family != null, role, "family");
            case TRANSFORMER -> require(voltage != null, role, "voltage");
            default -> {
                // 其它角色不需要额外字段
            }
        }
    }

    private static void require(boolean condition, MatrixBlockRole role, String field) {
        if (!condition) {
            throw new IllegalArgumentException("Matrix block role " + role + " requires " + field);
        }
    }

    /** 是否属于外壳方块（不含 GT 能源仓，能源仓由结构扫描单独识别）。 */
    public boolean isShell() {
        return role == MatrixBlockRole.FRAME || role == MatrixBlockRole.GLASS
                || role == MatrixBlockRole.FILTER || role == MatrixBlockRole.STERILE_FILTER;
    }

    /** 是否属于内部方块。 */
    public boolean isInterior() {
        return !isShell();
    }

    /**
     * 按注册名解析结构语义。
     *
     * <p>命名规则来自 {@code 方块注册映射.txt}：变量在前、类型词在后；扩展变体的
     * {@code _extend} 放在最后。解析顺序从最长/最具体的后缀开始，避免
     * {@code _matrix_cleanroom_frame} 被 {@code _matrix_frame} 抢先匹配。</p>
     */
    public static MatrixBlockKind parse(String id) {
        if (id == null) {
            throw new IllegalArgumentException("Matrix block id must not be null");
        }

        // 五个没有维度变量的独立方块
        switch (id) {
            case "matrix_cleanroom":
                return simple(MatrixBlockRole.CLEANROOM);
            case "matrix_filter_casing":
                return simple(MatrixBlockRole.FILTER);
            case "matrix_sterile_filter_casing":
                return simple(MatrixBlockRole.STERILE_FILTER);
            case "matrix_active_transformer":
                return simple(MatrixBlockRole.ACTIVE_TRANSFORMER);
            case "empty_matrix_core":
                return simple(MatrixBlockRole.EMPTY_CORE);
            default:
                break;
        }

        boolean extended = id.endsWith("_extend");
        String base = extended ? id.substring(0, id.length() - "_extend".length()) : id;

        if (base.endsWith("_matrix_pattern_provider_core")) {
            String family = strip(base, "_matrix_pattern_provider_core");
            return new MatrixBlockKind(MatrixBlockRole.PATTERN_CORE, MatrixFamily.byId(family), null, null, null,
                    extended);
        }
        if (base.endsWith("_matrix_monitor_core")) {
            String family = strip(base, "_matrix_monitor_core");
            return new MatrixBlockKind(MatrixBlockRole.MONITOR, MatrixFamily.byId(family), null, null, null, extended);
        }
        if (base.endsWith("_matrix_speed_core")) {
            String[] parts = splitOnce(strip(base, "_matrix_speed_core"), id);
            return new MatrixBlockKind(MatrixBlockRole.SPEED_CORE, MatrixFamily.byId(parts[1]), null, null,
                    MatrixVoltage.byId(parts[0]), extended);
        }
        if (base.endsWith("_matrix_processing_core")) {
            String[] parts = splitOnce(strip(base, "_matrix_processing_core"), id);
            return new MatrixBlockKind(MatrixBlockRole.PROCESSING_CORE, null, null,
                    MatrixProcess.byId(parts[1]), MatrixVoltage.byId(parts[0]), extended);
        }
        if (base.endsWith("_matrix_cleanroom_frame")) {
            return frame(strip(base, "_matrix_cleanroom_frame"), id);
        }
        if (base.endsWith("_matrix_frame")) {
            return frame(strip(base, "_matrix_frame"), id);
        }
        if (base.endsWith("_matrix_cleanroom_glass")) {
            return glass(strip(base, "_matrix_cleanroom_glass"), id);
        }
        if (base.endsWith("_matrix_glass")) {
            return glass(strip(base, "_matrix_glass"), id);
        }
        if (base.endsWith("_matrix_transformer")) {
            String voltage = strip(base, "_matrix_transformer");
            return new MatrixBlockKind(MatrixBlockRole.TRANSFORMER, null, null, null,
                    MatrixVoltage.byId(voltage), extended);
        }

        throw new IllegalArgumentException("Unknown matrix block id: " + id);
    }

    private static MatrixBlockKind simple(MatrixBlockRole role) {
        return new MatrixBlockKind(role, null, null, null, null, false);
    }

    private static MatrixBlockKind frame(String rest, String originalId) {
        String[] parts = splitOnce(rest, originalId);
        return new MatrixBlockKind(MatrixBlockRole.FRAME, MatrixFamily.byId(parts[1]),
                MatrixShellLevel.byId(parts[0]), null, null, false);
    }

    private static MatrixBlockKind glass(String rest, String originalId) {
        if (rest.isEmpty() || rest.indexOf('_') >= 0) {
            throw new IllegalArgumentException("Invalid matrix glass id: " + originalId);
        }
        return new MatrixBlockKind(MatrixBlockRole.GLASS, null, MatrixShellLevel.byId(rest), null, null, false);
    }

    private static String strip(String value, String suffix) {
        return value.substring(0, value.length() - suffix.length());
    }

    private static String[] splitOnce(String value, String originalId) {
        int split = value.indexOf('_');
        if (split <= 0 || split == value.length() - 1) {
            throw new IllegalArgumentException("Invalid matrix block id: " + originalId);
        }
        return new String[] { value.substring(0, split), value.substring(split + 1) };
    }
}
