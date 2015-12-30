/*
 * Copyright 2015 Ross Nicoll.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.libdohj.cate;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Map;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeMapped;
import com.sun.jna.PointerType;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.win32.W32APITypeMapper;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.libdohj.cate.controller.MainController;

/**
 *
 * @author Ross Nicoll
 */
public class CATE extends Application {
    private static final String APPLICATION_NAME_FOLDER = "CATE";
    private static Logger logger  = Logger.getLogger(CATE.class.getName());
    private static File dataDir;

    private static File buildDataDir() {
        if (com.sun.jna.Platform.isWindows()) {
            // Modified from http://stackoverflow.com/questions/5953149/detect-the-location-of-appdata-locallow-with-jna
            final PointerByReference ppszPath = new PointerByReference();
            int hResult = Shell32.INSTANCE.SHGetKnownFolderPath(Shell32.FOLDERID_RoamingAppData,
                Shell32.KF_FLAG_CREATE, (HANDLE) null, ppszPath);
            if (Shell32.S_OK == hResult) {
                try {
                    String path = nullTerminatedToString(ppszPath);

                    return new File(path + "\\" + APPLICATION_NAME_FOLDER);
                } finally {
                    Ole32.INSTANCE.CoTaskMemFree(ppszPath.getValue());
                }
            } else {
                logger.log(Level.SEVERE, null, "Could not determine local application data directory: " + hResult);
            }
        } else if (com.sun.jna.Platform.isMac()) {
            // As per https://developer.apple.com/library/mac/qa/qa1170/_index.html
            return new File(System.getProperty("user.home") + "/Library/" + APPLICATION_NAME_FOLDER);
        }
        return new File(System.getProperty("user.home") + "/." + APPLICATION_NAME_FOLDER.toLowerCase());
    }

    /**
     * @return the dataDir
     */
    public static File getDataDir() {
        return dataDir;
    }

    /**
     * Convert a Win32 string into a Java string.
     *
     * @param ppszPath pointer to the string to convert.
     */
    private static String nullTerminatedToString(PointerByReference ppszPath) {
        char delim = '\0';
        char[] chars = ppszPath.getValue().getCharArray(0, Shell32.MAX_PATH);
        int charIdx;

        for (charIdx = 0; charIdx < chars.length; charIdx++) {
            if (chars[charIdx] == delim) {
                break;
            }
        }

        return new String(chars, 0, charIdx);
    }

    private MainController controller;

    @Override
    public void start(Stage primaryStage) throws Exception{
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/main.fxml"));
        Parent root = loader.load();

        this.controller = (MainController) loader.getController();

        primaryStage.setTitle("CATE");
        primaryStage.setScene(new Scene(root, 800, 500));
        primaryStage.show();
    }

    @Override
    public void stop() {
        this.controller.stop();
    }

    public static void main(String[] args) {
        dataDir = buildDataDir();

        if (!dataDir.exists()) {
            dataDir.mkdir();
        }

        launch(args);
    }

    private static final Map<String, Object> LOAD_LIBRARY_OPTIONS = new HashMap<>();
    static {
        LOAD_LIBRARY_OPTIONS.put(Library.OPTION_TYPE_MAPPER, W32APITypeMapper.UNICODE);
        LOAD_LIBRARY_OPTIONS.put(Library.OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.UNICODE);
    }

    static class HANDLE extends PointerType implements NativeMapped {
    }

    /**
     * Wrapper around the Windows Shell32 library, used to get the path of the
     * application data folder.
     */
    static interface Shell32 extends StdCallLibrary {

        public static final int MAX_PATH = 260;
        public static final Guid.GUID FOLDERID_RoamingAppData = new Guid.GUID("3EB685DB-65F9-4CF6-A03A-E3EF65729F3D");
        public static final int KF_FLAG_CREATE = 0x00008000;
        public static final int S_OK = 0;

        static Shell32 INSTANCE = (Shell32) Native.loadLibrary("shell32", Shell32.class, LOAD_LIBRARY_OPTIONS);

        /**
         * See https://msdn.microsoft.com/en-us/library/bb762188(v=vs.85).aspx
         */
        public int SHGetKnownFolderPath(Guid.GUID hwndOwner, int dwFlags, HANDLE hToken,
                PointerByReference ppszPath);

    }
}