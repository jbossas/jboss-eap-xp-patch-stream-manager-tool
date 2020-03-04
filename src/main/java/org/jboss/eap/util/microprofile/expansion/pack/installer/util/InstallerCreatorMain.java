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

package org.jboss.eap.util.microprofile.expansion.pack.installer.util;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.as.patching.generator.PatchGenLogger;
import org.jboss.as.patching.generator.Usage;
import org.jboss.eap.util.microprofile.expansion.pack.installer.core.InstallerLogger;
import org.jboss.eap.util.microprofile.expansion.pack.installer.core.InstallerMain;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class InstallerCreatorMain {

    public static final String APPLIES_TO_DIST = "--applies-to-dist";
    public static final String CREATE_TEMPLATE = "--create-template";
    public static final String COMBINE_WITH = "--combine-with";
    public static final String PATCH_CONFIG = "--patch-config";
    public static final String UPDATED_DIST = "--updated-dist";
    public static final String EXPANSION_PACK_VERSION = "--expansion-pack-version";
    public static final String INSTALLER_CORE = "--installer-core";
    public static final String ADDED_CONFIGS = "--added-configs";
    public static final String OUTPUT_DIR = "--output-dir";

    public static void main(String[] args) throws Exception {
        InstallerCreator creator = InstallerCreatorMain.parse(args);
        if (creator != null) {
            creator.createInstaller();
        }
    }

    private static InstallerCreator parse(String[] args) throws Exception {

        Path patchConfig = null;
        Path oldServerHome = null;
        Path newServerHome = null;
        Path combineWith = null;
        String expansionPackVersion = null;
        List<String> addedConfigs = new ArrayList<>();
        Path installerCore = null;
        Path outputDir = null;

        Set<String> required = new HashSet<>(Arrays.asList(PATCH_CONFIG, EXPANSION_PACK_VERSION, INSTALLER_CORE));

        // Arguments we received which are recognised by patch-gen for doing the work
        List<String> patchGenArgs = new ArrayList<>();

        final int argsLength = args.length;
        for (int i = 0; i < argsLength; i++) {
            final String arg = args[i];
            try {
                if ("--help".equals(arg) || "-h".equals(arg) || "-H".equals(arg)) {
                    usage();
                    return null;
                } else if (arg.startsWith(APPLIES_TO_DIST)) {
                    patchGenArgs.add(arg);
                    String val = arg.substring(APPLIES_TO_DIST.length() + 1);
                    oldServerHome = Paths.get(val);
                    if (!Files.exists(oldServerHome)) {
                        fileDoesNotExist(arg);
                        usage();
                        return null;
                    } else if (!Files.isDirectory(oldServerHome)) {
                        fileIsNotADirectory(arg);
                        usage();
                        return null;
                    } else if (!checkFindLayersRoot(oldServerHome)) {
                        usage();
                        return null;
                    }
                } else if (arg.startsWith(UPDATED_DIST)) {
                    patchGenArgs.add(arg);
                    String val = arg.substring(UPDATED_DIST.length() + 1);
                    newServerHome = Paths.get(val);
                    if (!Files.exists(newServerHome)) {
                        fileDoesNotExist(arg);
                        usage();
                        return null;
                    } else if (!Files.isDirectory(newServerHome)) {
                        fileIsNotADirectory(arg);
                        usage();
                        return null;
                    } else if (!checkFindLayersRoot(newServerHome)) {
                        usage();
                        return null;
                    }
                } else if (arg.startsWith(PATCH_CONFIG)) {
                    patchGenArgs.add(arg);
                    String val = arg.substring(PATCH_CONFIG.length() + 1);
                    patchConfig = Paths.get(val);
                    required.remove(PATCH_CONFIG);
                    if (!Files.exists(patchConfig)) {
                        fileDoesNotExist(arg);
                        usage();
                        return null;
                    } else if (Files.isDirectory(patchConfig)) {
                        fileIsADirectory(arg);
                        usage();
                        return null;
                    }
                } else if (arg.equals(CREATE_TEMPLATE)) {
                    TemplateGenerator.generate(args);
                    return null;
                } else if (arg.startsWith(COMBINE_WITH)) {
                    String val = arg.substring(COMBINE_WITH.length() + 1);
                    combineWith = Paths.get(val);
                    if (!Files.exists(combineWith)) {
                        fileDoesNotExist(arg);
                        usage();
                        return null;
                    }
                } else if (arg.startsWith(EXPANSION_PACK_VERSION)) {
                    expansionPackVersion = arg.substring(EXPANSION_PACK_VERSION.length() + 1);
                    required.remove(EXPANSION_PACK_VERSION);
                } else if (arg.startsWith(INSTALLER_CORE)) {
                    required.remove(INSTALLER_CORE);
                    String val = arg.substring(INSTALLER_CORE.length() + 1);
                    installerCore = Paths.get(val);
                    if (!Files.exists(installerCore)) {
                        fileDoesNotExist(arg);
                        return null;
                    } else if (Files.isDirectory(installerCore)) {
                        fileIsADirectory(arg);
                        return null;
                    } else if (!installerCore.getFileName().toString().endsWith(".jar")) {
                        System.err.println(installerCore + " does not appear to be a jar file");
                        usage();
                        return null;
                    }
                } else if (arg.startsWith(ADDED_CONFIGS)) {
                    addedConfigs = Arrays.asList(arg.substring(ADDED_CONFIGS.length() + 1).split(","));
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
                argumentExpected(arg);
                usage();
                return null;
            }
        }

        if (required.size() != 0) {
            missingRequiredArgs(required);
            usage();
            return null;
        }


        return new InstallerCreator(
                patchGenArgs, patchConfig, oldServerHome, newServerHome, combineWith,
                expansionPackVersion, addedConfigs, installerCore, outputDir);
    }

    private static boolean checkFindLayersRoot(Path serverHome) {
        Path path = serverHome.resolve(InstallerCreator.LAYERS_ROOT);
        if (!Files.exists(path)) {
            System.err.println("Could not find a " + path + " directory. " + serverHome + " does not appear to be a valid server location");
            return false;
        }
        return true;
    }

    private static void fileDoesNotExist(String arg) {
        System.err.println("File at path specified by argument " + arg +" does not exist");
    }

    private static void fileIsNotADirectory(String arg) {
        System.err.println(PatchGenLogger.fileIsNotADirectory(arg));
    }

    private static void fileIsADirectory(String arg) {
        System.err.println(PatchGenLogger.fileIsADirectory(arg));
    }

    private static void argumentExpected(String arg) {
        System.err.println(PatchGenLogger.argumentExpected(arg));
    }

    private static void missingRequiredArgs(Set<String> set) {
        System.err.println(PatchGenLogger.missingRequiredArgs(set));
    }

    private static void usage() {

        Usage usage = new Usage();

        usage.addArguments(APPLIES_TO_DIST + "=<file>");
        usage.addInstruction("Filesystem path of a pristine unzip of the distribution of the version of the software to which the generated patch applies");

        usage.addArguments("-h", "--help");
        usage.addInstruction("Display this message and exit");

        usage.addArguments(PATCH_CONFIG + "=<file>");
        usage.addInstruction("Filesystem path of the patch generation configuration file to use");

        usage.addArguments(UPDATED_DIST + "=<file>");
        usage.addInstruction("Filesystem path of a pristine unzip of a distribution of software which contains the changes that should be incorporated in the patch");

        usage.addArguments("-v", "--version");
        usage.addInstruction("Print version and exit");

        usage.addArguments(COMBINE_WITH + "=<file>");
        usage.addInstruction("Filesystem path of the previous CP to be included into the same package with the newly generated one");

        usage.addArguments(EXPANSION_PACK_VERSION + "=<version>");
        usage.addInstruction("The version of the expansion pack. It will be used both for the resulting jars, and the overlays in the patch");

        usage.addArguments(ADDED_CONFIGS + "=<files>");
        usage.addInstruction("Comma separated list of configuration files, relative to the server root, to add to the patched server. Optional");

        usage.addArguments(INSTALLER_CORE + "=<file>");
        usage.addInstruction("Filesystem path of the mp-expansion-pack-core jar");

        usage.addArguments(OUTPUT_DIR + "=<file>");
        usage.addInstruction("Filesystem path of a directory to output the created installer. This is optional, and if used the " +
                "patch.zip that is part of the installer will also be output to that directory for easier verification");

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
