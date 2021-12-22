package org.libdohj.cate.util;

import java.nio.file.Path;
import java.util.logging.Logger;

// Convenience functions that interface with Windows' Shell32 Lib
public class Win32Utils {

    private static Logger logger  = Logger.getLogger(Win32Utils.class.getName());

    /**
     * Determines the location of the AppData/Roaming folder
     */
    public static Path determineAppDataDir()
    {
        return Path.of(System.getenv("APPDATA"));
    }
}
