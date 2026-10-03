package io.github.k7t3.fxgraph.mcp.agent.inspector;

import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyInputRobotTest {
    @Test
    void robotHoldsModifiersAndReleasesKeysInReverseOrder() {
        var keyboard = new RecordingKeyboard();
        var stroke = KeyInput.parse(Map.of("key", "Q", "modifiers", List.of("META", "SHIFT", "CMD"), "mode", "robot"));

        KeyInput.robot(keyboard, stroke);

        assertThat(keyboard.events).containsExactly("press SHIFT", "press " + nativeMeta(), "press Q",
                "release Q", "release " + nativeMeta(), "release SHIFT");
    }

    @Test
    void robotReleasesModifiersWhenKeyPressFails() {
        var keyboard = new RecordingKeyboard();
        keyboard.failPress = KeyCode.Q;
        var stroke = KeyInput.parse(Map.of("key", "Q", "modifiers", List.of("META"), "mode", "robot"));

        assertThatThrownBy(() -> KeyInput.robot(keyboard, stroke)).hasMessage("press failed");

        assertThat(keyboard.events).containsExactly("press " + nativeMeta(), "press Q", "release Q", "release " + nativeMeta());
    }

    @Test
    void robotContinuesReleasingModifiersWhenReleaseFails() {
        var keyboard = new RecordingKeyboard();
        keyboard.failRelease = KeyCode.Q;
        var stroke = KeyInput.parse(Map.of("key", "Q", "modifiers", List.of("META", "SHIFT"), "mode", "robot"));

        assertThatThrownBy(() -> KeyInput.robot(keyboard, stroke)).isSameAs(keyboard.releaseError);

        assertThat(keyboard.events).containsExactly("press SHIFT", "press " + nativeMeta(), "press Q",
                "release Q", "release " + nativeMeta(), "release SHIFT");
    }

    private static String nativeMeta() {
        return System.getProperty("os.name").startsWith("Mac") ? "COMMAND" : "META";
    }

    private static final class RecordingKeyboard implements KeyInput.Keyboard {
        private final List<String> events = new ArrayList<>();
        private final Error releaseError = new AssertionError("release failed");
        private KeyCode failPress;
        private KeyCode failRelease;

        @Override
        public void press(KeyCode code) {
            events.add("press " + code.name());
            if (code == failPress) throw new IllegalStateException("press failed");
        }

        @Override
        public void release(KeyCode code) {
            events.add("release " + code.name());
            if (code == failRelease) throw releaseError;
        }
    }
}
