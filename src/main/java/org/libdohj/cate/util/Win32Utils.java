package org.libdohj.cate.util;

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

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

// Convenience functions that interface with Windows' Shell32 Lib
public class Win32Utils {

    private static Logger logger  = Logger.getLogger(Win32Utils.class.getName());

    /**
     * Determines the location of the AppData/Roaming folder
     */
    public static String determineAppDataDir()
    {
        String appDataDir = null;

        // Modified from http://stackoverflow.com/questions/5953149/detect-the-location-of-appdata-locallow-with-jna
        final PointerByReference ppszPath = new PointerByReference();

        int hResult = Shell32.INSTANCE.SHGetKnownFolderPath(
                Shell32.FOLDERID_RoamingAppData,
                Shell32.KF_FLAG_CREATE, null, ppszPath);

        if (Shell32.S_OK == hResult) {
            try {
                appDataDir = nullTerminatedToString(ppszPath);
            } finally {
                Ole32.INSTANCE.CoTaskMemFree(ppszPath.getValue());
            }
        } else {
            logger.log(Level.SEVERE, null, "Could not determine local application data directory: " + hResult);
        }

        return appDataDir;
    }
    /**
     * Convert a Win32 string into a Java string.
     *
     * @param ppszPath pointer to the string to convert.
     */
    public static String nullTerminatedToString(PointerByReference ppszPath) {
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

    private static final Map<String, Object> LOAD_LIBRARY_OPTIONS = new HashMap<>();
    static {
        LOAD_LIBRARY_OPTIONS.put(Library.OPTION_TYPE_MAPPER, W32APITypeMapper.UNICODE);
        LOAD_LIBRARY_OPTIONS.put(Library.OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.UNICODE);
    }


    static class HANDLE extends PointerType implements NativeMapped {}

    /**
     * Wrapper around the Windows Shell32 library, used to get the path of the
     * application data folder.
     */
     public interface Shell32 extends StdCallLibrary {

        int MAX_PATH = 260;
        Guid.GUID FOLDERID_RoamingAppData = new Guid.GUID("3EB685DB-65F9-4CF6-A03A-E3EF65729F3D");
        int KF_FLAG_CREATE = 0x00008000;
        int S_OK = 0;

        Shell32 INSTANCE = (Shell32) Native.loadLibrary("shell32", Shell32.class, LOAD_LIBRARY_OPTIONS);

        /**
         * See https://msdn.microsoft.com/en-us/library/bb762188(v=vs.85).aspx
         */
        int SHGetKnownFolderPath(Guid.GUID hwndOwner, int dwFlags, HANDLE hToken,
                                        PointerByReference ppszPath);

    }

}
