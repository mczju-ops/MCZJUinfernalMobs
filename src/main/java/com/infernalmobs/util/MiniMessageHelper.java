package com.infernalmobs.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * MiniMessage 解析与 Legacy(&) 转 MiniMessage，统一用 MiniMessage 输出。
 */
public final class MiniMessageHelper {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private MiniMessageHelper() {}

    /** 将 & 颜色/格式码转为 MiniMessage 标签，再解析为 Component（用于仍带 & 的配置如技能 display）。 */
    public static Component fromLegacy(String legacy) {
        if (legacy == null || legacy.isEmpty()) return Component.empty();
        return MM.deserialize(legacyToMiniMessage(legacy));
    }

    /** & 码转 MiniMessage 标签串。 */
    public static String legacyToMiniMessage(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '&' && i + 1 < s.length()) {
                String tag = legacyCodeToTag(s.charAt(i + 1));
                if (tag != null) out.append(tag);
                i++;
            } else {
                out.append(s.charAt(i));
            }
        }
        return out.toString();
    }

    private static String legacyCodeToTag(char c) {
        return switch (Character.toLowerCase(c)) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'l' -> "<bold>";
            case 'k' -> "<obfuscated>";
            case 'm' -> "<strikethrough>";
            case 'n' -> "<underline>";
            case 'o' -> "<italic>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }

    public static MiniMessage miniMessage() {
        return MM;
    }

    /** 解析 MiniMessage 模板，并注入占位符。 */
    public static Component deserialize(String template, TagResolver... resolvers) {
        return MM.deserialize(template, resolvers);
    }
}
