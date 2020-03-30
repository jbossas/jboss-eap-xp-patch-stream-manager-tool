/*
 * JBoss, Home of Professional Open Source
 * Copyright 2020, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.eap.util.xp.patch.stream.tool;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class InstallerCreatorMain {

    static final String CREATE_CONFIG = "--create-config";
    private static final String INSTALLER_CORE = "--installer-core";
    private static final String ADDED_CONFIGS = "--added-configs";
    private static final String OUTPUT_DIR = "--output-dir";

    private static final String HTTP = "http://";
    private static final String HTTPS = "https://";


    public static void main(String[] args) throws Exception {
        InstallerCreator creator = InstallerCreatorMain.parse(args);
        if (creator != null) {
            creator.createInstaller();
        }
    }

    private static InstallerCreator parse(String[] args) throws Exception {

        List<Path> addedConfigFiles = new ArrayList<>();
        Path installerCore = null;
        boolean installerCoreIsTemp = false;
        Path outputDir = null;

        boolean error = true;
        try {
            Set<String> required = new HashSet<>(Arrays.asList(INSTALLER_CORE));
            final int argsLength = args.length;
            for (int i = 0; i < argsLength; i++) {
                final String arg = args[i];
                try {
                    if ("--help".equals(arg) || "-h".equals(arg) || "-H".equals(arg)) {
                        usage();
                        return null;
                    } else if (arg.equals(CREATE_CONFIG)) {
                        ConfigCreator.generate(args);
                        return null;
                    } else if (arg.startsWith(INSTALLER_CORE)) {
                        required.remove(INSTALLER_CORE);
                        String val = arg.substring(INSTALLER_CORE.length() + 1);

                        installerCore = downloadIfNeeded(val);
                        if (installerCore != null) {
                            installerCoreIsTemp = true;
                        } else {
                            installerCore = Paths.get(val);

                            if (!Files.exists(installerCore)) {
                                ToolLogger.fileDoesNotExist(arg);
                                return null;
                            } else if (Files.isDirectory(installerCore)) {
                                ToolLogger.fileIsADirectory(arg);
                                return null;
                            } else if (!installerCore.getFileName().toString().endsWith(".jar")) {
                                System.err.println(installerCore + " does not appear to be a jar file");
                                usage();
                                return null;
                            }
                        }
                    } else if (arg.startsWith(ADDED_CONFIGS)) {
                        String val = arg.substring(ADDED_CONFIGS.length() + 1);
                        String[] parts = val.split(",");
                        for (String part : parts) {
                            Path path = Paths.get(part);
                            if (!Files.exists(path)) {
                                ToolLogger.fileInListArgDoesNotExist(path.toString(), arg);
                                return null;
                            }
                            if (!Files.exists(path)) {
                                ToolLogger.fileInListArgIsNotAFile(path.toString(), arg);
                                return null;
                            }
                            addedConfigFiles.add(path);
                        }
                    } else if (arg.startsWith(OUTPUT_DIR)) {
                        String val = arg.substring(OUTPUT_DIR.length() + 1);
                        outputDir = Paths.get(val);
                        if (Files.exists(outputDir) && !Files.isDirectory(outputDir)) {
                            System.err.println(arg + " already exists, but it is not a directory");
                            usage();
                            return null;
                        }
                    } else {
                        System.err.println("Unknown argument: " + arg);
                        usage();
                        return null;
                    }
                } catch (IndexOutOfBoundsException e) {
                    ToolLogger.argumentExpected(arg);
                    usage();
                    return null;
                }
            }

            if (required.size() != 0) {
                ToolLogger.missingRequiredArgs(required);
                usage();
                return null;
            }
            error = false;
        } finally {
            if (error && installerCoreIsTemp) {
                Files.delete(installerCore);
            }
        }

        return new InstallerCreator(addedConfigFiles, installerCore, installerCoreIsTemp, outputDir);
    }

    private static Path downloadIfNeeded(String location) throws IOException {
        if (!location.startsWith(HTTP) && !location.startsWith(HTTPS)) {
            return null;
        }

        URL url = new URL(location);
        URLConnection connection = url.openConnection();

        Path tmp = Files.createTempFile("jboss-eap-xp-installer", ".jar");
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp.toFile()))) {
            byte[] buffer = new byte[1024];
            try (InputStream in = new BufferedInputStream(connection.getInputStream())) {
                int len = in.read(buffer, 0, buffer.length);
                while (len != -1) {
                    out.write(buffer, 0, len);
                    len = in.read(buffer, 0, buffer.length);
                }
            }
        }

        return tmp;
    }


    private static void usage() {

        Usage usage = new Usage();

        usage.addArguments(ADDED_CONFIGS + "=<directory>");
        usage.addInstruction("Comma-separated list of file system paths to server configuration files that should be included in the installer");

        usage.addArguments("-h", "--help");
        usage.addInstruction("Display this message and exit");

        usage.addArguments(INSTALLER_CORE + "=<file>");
        usage.addInstruction("Filesystem path of the mp-expansion-pack-core jar");

        usage.addArguments(OUTPUT_DIR + "=<file>");
        usage.addInstruction("Filesystem path of a directory to output the created installer. The resulting jar will be called jboss-eap-xp-installer.jar");

        usage.addArguments(CREATE_CONFIG);
        usage.addInstruction("If passed in the other parameters will be ignored, and a patch config xml will be created.");

        String headline = usage.getDefaultUsageHeadline(getJavaCommand());
        System.out.print(usage.usage(headline));
    }

    private static String getJavaCommand() {
        return getJavaCommand(InstallerCreatorMain.class);
    }

    static String getJavaCommand(Class clazz) {
        final URL url = clazz.getProtectionDomain().getCodeSource().getLocation();
        if (url.toString().endsWith(".jar")) {
            String name;
            try {
                name = Paths.get(url.toURI()).getFileName().toString();
            } catch (URISyntaxException e) {
                // Just return the name without versions
                name = "eap-mp-xp-installer-tool.jar";
            }
            return "java -jar " + name;
        } else {
            return "java " + InstallerCreatorMain.class.getName();
        }

    }
}
