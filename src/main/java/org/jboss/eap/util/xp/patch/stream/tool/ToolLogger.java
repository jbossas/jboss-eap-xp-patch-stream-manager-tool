package org.jboss.eap.util.xp.patch.stream.tool;

import java.util.Set;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ToolLogger {
    static void fileDoesNotExist(String arg) {
        System.err.println("File at path specified by argument " + arg +" does not exist");
    }

    static void fileIsNotADirectory(String arg) {
        System.err.println("File at path specified by argument " + arg + " is not a directory");
    }

    static void fileIsADirectory(String arg) {
        System.err.println("File at path specified by argument " + arg + " is a directory");
    }

    static void fileInListArgDoesNotExist(String path, String arg) {
        System.err.println("File at " + path + " specified by argument " + arg + " does not exist");
    }

    static void fileInListArgIsNotAFile(String path, String arg) {
        System.err.println("File at " + path + " specified by argument " + arg + " is not a file");
    }

    static void argumentExpected(String arg) {
        System.err.println("Argument expected for option " + arg);
    }

    static void missingRequiredArgs(Set<String> set) {
        System.err.println("Missing required argument(s): " + set);
    }
}
