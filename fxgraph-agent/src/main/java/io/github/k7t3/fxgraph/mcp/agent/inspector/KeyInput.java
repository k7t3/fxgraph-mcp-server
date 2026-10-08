package io.github.k7t3.fxgraph.mcp.agent.inspector;

import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parses and dispatches JavaFX key gestures. */
final class KeyInput {
    private KeyInput() {}

    record Stroke(KeyCode code, String character, List<KeyCode> modifiers) {
        boolean has(KeyCode modifier) {
            return modifiers.contains(modifier);
        }

        KeyEvent event(javafx.event.EventType<KeyEvent> type) {
            return new KeyEvent(type, type == KeyEvent.KEY_TYPED ? character : KeyEvent.CHAR_UNDEFINED,
                    type == KeyEvent.KEY_TYPED ? "" : code.getName(), code,
                    has(KeyCode.SHIFT), has(KeyCode.CONTROL), has(KeyCode.ALT), has(KeyCode.META));
        }
    }

    static Stroke parse(Map<String, Object> params) {
        if (!(params.get("key") instanceof String key) || key.isEmpty()) {
            throw new IllegalArgumentException("key is required");
        }
        var modifiers = EnumSet.noneOf(KeyCode.class);
        if (params.get("modifiers") != null) {
            if (!(params.get("modifiers") instanceof List<?> values)) {
                throw new IllegalArgumentException("modifiers must be an array");
            }
            for (var value : values) {
                var modifier = switch (String.valueOf(value).trim().toUpperCase(Locale.ROOT)) {
                    case "SHIFT" -> KeyCode.SHIFT;
                    case "CTRL", "CONTROL" -> KeyCode.CONTROL;
                    case "ALT" -> KeyCode.ALT;
                    case "CMD", "META" -> KeyCode.META;
                    default -> throw new IllegalArgumentException("Unsupported modifier: " + value + ". Expected SHIFT, CTRL/CONTROL, ALT, or CMD/META");
                };
                modifiers.add(modifier);
            }
        }
        var code = keyCode(key);
        if (code == KeyCode.UNDEFINED && key.codePointCount(0, key.length()) != 1) {
            throw new IllegalArgumentException("Unsupported key: " + key);
        }
        var character = key.codePointCount(0, key.length()) == 1 ? key : character(code);
        if (modifiers.contains(KeyCode.SHIFT)) {
            character = character.toUpperCase(Locale.ROOT);
        }
        if (modifiers.contains(KeyCode.CONTROL) || modifiers.contains(KeyCode.ALT) || modifiers.contains(KeyCode.META)) {
            character = "";
        }
        return new Stroke(code, character, List.copyOf(modifiers));
    }

    private static KeyCode keyCode(String key) {
        try {
            return KeyCode.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            if (key.codePointCount(0, key.length()) != 1) {
                throw new IllegalArgumentException("Unsupported key: " + key);
            }
            var code = KeyCode.getKeyCode(key);
            return code != null ? code : switch (key) {
                case " " -> KeyCode.SPACE;
                case "," -> KeyCode.COMMA;
                case "." -> KeyCode.PERIOD;
                case "/" -> KeyCode.SLASH;
                case ";" -> KeyCode.SEMICOLON;
                case "=" -> KeyCode.EQUALS;
                case "-" -> KeyCode.MINUS;
                case "[" -> KeyCode.OPEN_BRACKET;
                case "]" -> KeyCode.CLOSE_BRACKET;
                case "\\" -> KeyCode.BACK_SLASH;
                case "'" -> KeyCode.QUOTE;
                case "`" -> KeyCode.BACK_QUOTE;
                default -> KeyCode.UNDEFINED;
            };
        }
    }

    private static String character(KeyCode code) {
        if (code.isLetterKey()) return code.getName().toLowerCase(Locale.ROOT);
        if (code.isDigitKey()) return code.name().substring(code.name().length() - 1);
        return switch (code) {
            case SPACE -> " ";
            case COMMA -> ",";
            case PERIOD -> ".";
            case SLASH -> "/";
            case SEMICOLON -> ";";
            case EQUALS -> "=";
            case MINUS -> "-";
            case OPEN_BRACKET -> "[";
            case CLOSE_BRACKET -> "]";
            case BACK_SLASH -> "\\";
            case QUOTE -> "'";
            case BACK_QUOTE -> "`";
            default -> "";
        };
    }

    static void synthetic(Node target, Stroke stroke) {
        target.fireEvent(stroke.event(KeyEvent.KEY_PRESSED));
        if (!stroke.character().isEmpty()) target.fireEvent(stroke.event(KeyEvent.KEY_TYPED));
        target.fireEvent(stroke.event(KeyEvent.KEY_RELEASED));
    }

}
