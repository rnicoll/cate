package org.libdohj.cate.util;

import java.io.File;

/**
 * Determines datadir based on host OS
 */
public class DataDirFactory {

    //TODO might want to make this more generic & extract it
    public class UnableToDetermineDataDirException extends Exception {
        public UnableToDetermineDataDirException(String message) {
            super(message);
        }
    }

    private final String applicationNameFolder;
    private File dataDir = null;

    public DataDirFactory(String applicationNameFolder) {
        this.applicationNameFolder = applicationNameFolder;
    }

    /**
     * Get the dataDir and build it if it is undefined.
     * @return File describing the datadir.
     * @throws UnableToDetermineDataDirException
     */
    public File get() throws UnableToDetermineDataDirException {
        if (dataDir == null) build();
        return dataDir;
    }

    /**
     * Defines and creates a dataDir if it does not exist
     * @return File the dataDir
     * @throws UnableToDetermineDataDirException
     */
    public File build() throws UnableToDetermineDataDirException {
        String path = constructPath();

        //TODO make this a Exception so that we can gracefully shutdown and perhaps inform the user :D
        if (path == null)
            throw new UnableToDetermineDataDirException("Unable to determine appData Directory");

        dataDir = new File(path);

        if (!dataDir.exists())
            dataDir.mkdir();

        return dataDir;
    }

    /**
     * Constructs the path depending on which OS is detected by JNA
     * @return String pointing to the absolute path of our application data folder
     */
    private String constructPath() {
        if (com.sun.jna.Platform.isWindows()) {
            return Win32Utils.determineAppDataDir() + "\\" + applicationNameFolder;
        } else if (com.sun.jna.Platform.isMac()) {
            // As per https://developer.apple.com/library/mac/qa/qa1170/_index.html
            return System.getProperty("user.home") + "/Library/" + applicationNameFolder;
        }
        // assume LINUX/BSD
        return System.getProperty("user.home") + "/." + applicationNameFolder.toLowerCase();
    }
}
