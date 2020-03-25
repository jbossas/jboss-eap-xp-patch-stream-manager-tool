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

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class InstallerCreatorMain {

    static final String CREATE_CONFIG = "--create-config";
    private static final String INSTALLER_CORE = "--installer-core";
    private static final String ADDED_CONFIGS_ROOT = "--added-configs-root";
    private static final String OUTPUT_DIR = "--output-dir";

    public static void main(String[] args) throws Exception {
        InstallerCreator creator = InstallerCreatorMain.parse(args);
        if (creator != null) {
            creator.createInstaller();
        }
    }

    private static InstallerCreator parse(String[] args) throws Exception {

        Path addedConfigsRoot = null;
        Path installerCore = null;
        Path outputDir = null;

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
                } else if (arg.startsWith(ADDED_CONFIGS_ROOT)) {
                    String val = arg.substring(ADDED_CONFIGS_ROOT.length() + 1);
                    addedConfigsRoot = Paths.get(val);
                    if (!Files.exists(addedConfigsRoot)) {
                        ToolLogger.fileDoesNotExist(arg);
                        return null;
                    } else if (!Files.isDirectory(addedConfigsRoot)) {
                        ToolLogger.fileIsNotADirectory(arg);
                        return null;
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


        return new InstallerCreator(addedConfigsRoot, installerCore, outputDir);
    }


    private static void usage() {

        Usage usage = new Usage();

        usage.addArguments(ADDED_CONFIGS_ROOT + "=<directory>");
        usage.addInstruction("File system path to directory containing configuration files. The files will be added to the same relative location of the target server. Optional");

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
