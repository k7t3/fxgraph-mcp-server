package io.github.k7t3.fxgraph.mcp.agent.inspector;

import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.robot.Robot;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Parses key gestures shared by synthetic and native input. */
final class KeyInput {
    private KeyInput() {}

    record Stroke(KeyCode code, String character, List<KeyCode> modifiers, Mode mode) {
        boolean has(KeyCode modifier) {
            return modifiers.contains(modifier);
        }

        KeyEvent event(javafx.event.EventType<KeyEvent> type) {
            return new KeyEvent(type, type == KeyEvent.KEY_TYPED ? character : KeyEvent.CHAR_UNDEFINED,
                    type == KeyEvent.KEY_TYPED ? "" : code.getName(), code,
                    has(KeyCode.SHIFT), has(KeyCode.CONTROL), has(KeyCode.ALT), has(KeyCode.META));
        }
    }

    enum Mode { SYNTHETIC, ROBOT }

    static Stroke parse(Map<String, Object> params) {
        if (!(params.get("key") instanceof String key) || key.isEmpty()) {
            throw new IllegalArgumentException("key is required");
        }
        var mode = switch (String.valueOf(params.getOrDefault("mode", "synthetic")).toLowerCase(Locale.ROOT)) {
            case "synthetic" -> Mode.SYNTHETIC;
            case "robot" -> Mode.ROBOT;
            default -> throw new IllegalArgumentException("Unsupported key mode: " + params.get("mode") + ". Expected synthetic or robot");
        };
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
        if (mode == Mode.ROBOT && code == KeyCode.UNDEFINED) {
            throw new IllegalArgumentException("Robot mode requires a key code: " + key);
        }
        return new Stroke(code, character, List.copyOf(modifiers), mode);
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

    interface Keyboard {
        void press(KeyCode code);
        void release(KeyCode code);
    }

    static Keyboard robotKeyboard() {
        var robot = new Robot();
        return new Keyboard() {
            @Override
            public void press(KeyCode code) { robot.keyPress(code); }
            @Override
            public void release(KeyCode code) { robot.keyRelease(code); }
        };
    }

    static void robot(Keyboard keyboard, Stroke stroke) {
        var pressed = new ArrayList<KeyCode>();
        Throwable failure = null;
        try {
            var keys = new ArrayList<>(stroke.modifiers());
            keys.add(stroke.code());
            for (var logicalCode : keys) {
                // macOS Glass maps its physical Command key separately from the META event flag.
                var code = logicalCode == KeyCode.META && System.getProperty("os.name").startsWith("Mac")
                        ? KeyCode.COMMAND : logicalCode;
                // A failed native call may have pressed the key before reporting its error.
                pressed.add(code);
                keyboard.press(code);
            }
        } catch (RuntimeException | Error error) {
            failure = error;
            throw error;
        } finally {
            Throwable releaseFailure = null;
            for (var code : pressed.reversed()) {
                try {
                    keyboard.release(code);
                } catch (RuntimeException | Error error) {
                    if (releaseFailure == null) releaseFailure = error;
                    else releaseFailure.addSuppressed(error);
                }
            }
            if (releaseFailure != null) {
                if (failure != null) failure.addSuppressed(releaseFailure);
                else if (releaseFailure instanceof RuntimeException error) throw error;
                else throw (Error) releaseFailure;
            }
        }
    }
}
