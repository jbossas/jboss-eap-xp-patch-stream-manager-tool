package org.eap.util.microprofile.expansion.pack.installer.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A utility to take an input YAML file containing aliases, and outputting
 * a YAML file with the aliases 'exanded'.
 *
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class InstallerMain {

    public static void main(String[] args) throws Exception {
        URL url = InstallerMain.class.getProtectionDomain().getCodeSource().getLocation();
        if (!url.toString().contains(".jar")) {
            throw new IllegalStateException("The MicroProfile Installer must be run from the distributed jar. It should not be unzipped!");
        }

        ArgsParser parser = new ArgsParser(url, args);
        parser.parseArguments();

        Path tempDir = Files.createTempDirectory("wildflY-mp-installer");
        Path patchZip = tempDir.resolve("patch.zip");
        Path addedConfigs = tempDir.resolve("added-configs");
        try {
            unzipSelfToTemp(url, patchZip, addedConfigs);
            Installer installer = new Installer(parser.jbossHome, parser.modulesDir, patchZip, addedConfigs);
            installer.install();
        } finally {
            Files.walkFileTree(tempDir, new FileVisitors.DeleteDirectory());
        }
    }


    private static boolean getDevMode() {
        return AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
            @Override
            public Boolean run() {
                return Boolean.getBoolean("installer.dev");
            }
        });
    }

    private static void unzipSelfToTemp(URL url, Path patchZip, Path addedConfigs) throws Exception {
        byte[] buffer = new byte[1024];
        File file = new File(url.toURI());
        boolean foundPatch = false;
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            ZipEntry entry = zin.getNextEntry();
            while (entry != null) {
                try {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    if (entry.getName().equals("patch.zip")) {
                        writeToFile(patchZip, buffer, zin);
                        foundPatch = true;
                    } else if (entry.getName().startsWith("added-configs") && !entry.isDirectory()) {
                        Path path = addedConfigs.getParent().resolve(entry.getName());
                        if (!Files.exists(path.getParent())) {
                            Files.createDirectories(path.getParent());
                        }
                        writeToFile(path, buffer, zin);
                    }
                } finally {
                    zin.closeEntry();
                    entry = zin.getNextEntry();
                }
            }
        }

        if (!foundPatch) {
            throw new IllegalStateException(file.getAbsolutePath() + " does not contain the patch to install");
        }
    }

    private static void writeToFile(Path path, byte[] buffer, ZipInputStream zin) throws Exception {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(path.toFile()))) {
            int len;
            while ((len = zin.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
        }
    }

    private static class ArgsParser {
        private URL url;
        private final String[] args;
        private Path jbossHome;
        private Path modulesDir;

        private ArgsParser(URL url, String[] args) {
            this.url = url;
            this.args = args;
        }

        private void parseArguments() throws Exception {
            String jbossHome = null;

            for (String arg : args) {
                if (arg.equals("--help")) {
                    displayHelp();
                    System.exit(0);
                } else if (arg.startsWith("--jboss-home=")) {
                    jbossHome = arg.substring(13);
                }
            }

            if (jbossHome == null) {
                jbossHome = System.getenv("JBOSS_HOME");
            }
            if (jbossHome == null) {
                System.out.println("--jboss-home was not specified");
                displayHelp();
                System.exit(1);
            }
            this.jbossHome = Paths.get(jbossHome).normalize().toAbsolutePath();
            if (!Files.exists(this.jbossHome)) {
                throw new IllegalStateException("The server home " + this.jbossHome + " does not exist");
            }

            this.modulesDir = this.jbossHome.resolve("modules");

            if (!Files.exists(this.modulesDir)) {
                throw new IllegalStateException("The determined modules base directory " + this.modulesDir + " does not exist");
            }
            if (!Files.exists(this.modulesDir.resolve("system"))) {
                throw new IllegalStateException("The determined modules base directory " + this.modulesDir + " does not look like a modules directory");
            }
        }

        private void displayHelp() throws Exception {
            System.out.println("Usage:");
            Path path = Paths.get(url.toURI());
            System.out.println("java -jar " + path.getFileName().toString() + "[--help | --jboss-home=<value>]");
            System.out.println("the arguments are:");
            System.out.println();
            System.out.println("--help");
            System.out.println("\tDisplay this help message");
            System.out.println();
            System.out.println("--jboss-home=<value>");
            System.out.println("\tPoints to the root of the server you want to patch. If not present this may also be " +
                    "set in the JBOSS_HOME environment variable.");
        }

    }
}
