package org.libdohj.cate.util;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/**
 * Created by maxke on 15.01.2016.
 * Contains various utility methods that don't deserve their own classes
 */
public class GenericUtils {

    /**
     * Copies the supplied String to the system clipboard
     * @param value String to copy
     */
    public static void copyToClipboard(String value) {
        ClipboardContent content = new ClipboardContent();
        content.putString(value);
        Clipboard.getSystemClipboard().setContent(content);
    }
}
