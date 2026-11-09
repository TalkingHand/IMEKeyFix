package talkinghand.imekeyfix;

import me.zed_0xff.zombie_buddy.Patch;

public class Patches {
    /** Runs on the window thread every frame, around glfwPollEvents (the window messages). */
    @Patch(className = "org.lwjglx.opengl.Display", methodName = "processMessages")
    public static class ProcessMessages {
        @Patch.OnEnter
        public static void enter() {
            Ime.update();
            Ime.fakeEnterDuringComposition();
        }

        @Patch.OnExit
        public static void exit() {
            Ime.flush();
        }
    }

    /**
     * putCharacter only changes internalText; getText() (what onCommandEntered reads) follows in the
     * next update(). A character typed in the same frame as Enter, such as the syllable the IME
     * commits on Enter, would be left out of the submitted text, so sync it first.
     */
    @Patch(className = "zombie.ui.UITextBox2", methodName = "onKeyEnter")
    public static class OnKeyEnter {
        @Patch.OnEnter
        public static void enter(@Patch.This Object self) {
            ((zombie.ui.UITextBox2) self).updateText();
        }
    }

    /** GLFW key callback: holds Enter pressed during IME composition until the poll ends. */
    @Patch(className = "org.lwjglx.input.Keyboard", methodName = "addKeyEvent")
    public static class AddKeyEvent {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter(@Patch.Argument(0) int key, @Patch.Argument(1) int status) {
            return Ime.deferKey(key, status);
        }
    }
}
