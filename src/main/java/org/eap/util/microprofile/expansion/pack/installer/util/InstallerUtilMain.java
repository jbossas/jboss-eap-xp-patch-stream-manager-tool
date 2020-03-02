package org.eap.util.microprofile.expansion.pack.installer.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class InstallerUtilMain {

    public static final String APPLIES_TO_DIST = "--applies-to-dist";
    public static final String CREATE_TEMPLATE = "--create-template";
    public static final String COMBINE_WITH = "--combine-with";
    public static final String OUTPUT_FILE = "--output-file";
    public static final String PATCH_CONFIG = "--patch-config";
    public static final String UPDATED_DIST = "--updated-dist";
    public static final String EXPANSION_PACK_VERSION = "--expansion-pack-version";


    private static final String USAGE = "Usage: java InstallerUtilMain <id> <path to server> [--layers=<added layers>] [--added-configs=<added configs>]";



    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalStateException(USAGE);
        }

        String id = args[0];
        Path serverHome = Paths.get(args[1]).toAbsolutePath();
        if (!Files.exists(serverHome)) {
            throw new IllegalStateException("Could not find server at " + serverHome);
        }
        Path layersRoot = serverHome.resolve("modules/system/layers");
        if (!Files.exists(serverHome)) {
            throw new IllegalStateException("Could not find layers root at " + layersRoot);
        }

        if (args.length > 2) {
            for (int i = 2; i < args.length; i++) {
                if (args[i].startsWith("--layers=")) {
                    setupLayers(serverHome, layersRoot, args[i].substring("--layers=".length()));
                } else if (args[i].startsWith("--added-configs=")) {
                    setupConfigs(serverHome, args[i].substring("--added-configs=".length()));
                } else {
                    throw new IllegalStateException("Unknown argument at index " + i + " '" + args[i] + "'\n" + USAGE);
                }
            }
        }

        recordScmRevision(id, layersRoot);
    }

    private static void setupLayers(Path serverHome, Path layersRoot, String allLayers) throws Exception {
        String[] tokens = allLayers.split(",");
        StringBuilder layersConfContents = new StringBuilder("layers=");
        boolean first = true;
        for (String token : tokens) {
            if (!first) {
                layersConfContents.append(",");
            } else {
                first = false;
            }
            layersConfContents.append(token);

            Path layerDir = layersRoot.resolve(token);
            if (!Files.exists(layerDir)) {
                Files.createDirectories(layerDir);
            }
        }
        Path layersConf = serverHome.resolve("modules/layers.conf");
        Files.write(layersConf, layersConfContents.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void setupConfigs(Path serverHome, String addedConfigs) throws Exception {
        Path configsDirectory = Paths.get(InstallerUtilMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        configsDirectory = configsDirectory.resolve("added-configs");
        if (!Files.exists(configsDirectory)) {
            Files.createDirectories(configsDirectory);
        }

        String[] tokens = addedConfigs.split(",");
        for (String token : tokens) {
            Path dest = configsDirectory.resolve(token.trim());
            if (!Files.exists(dest.getParent())) {
                Files.createDirectories(dest.getParent());
            }

            Path source = serverHome.resolve(token.trim());
            if (!Files.exists(source)) {
                throw new IllegalStateException("The config file " + source + " could not be found");
            }
            Files.copy(source, dest);
        }

    }

    private static void recordScmRevision(String id, Path layersRoot) throws Exception {
        // Look for the naming jar
        Path jarDirectory = layersRoot.resolve("base/org/jboss/as/naming/main/").toAbsolutePath();
        if (!Files.exists(jarDirectory)) {
            throw new IllegalStateException("Could not find " + jarDirectory);
        }
        List<Path> jars = Files.list(jarDirectory).filter(path -> path.getFileName().toString().endsWith(".jar")).collect(Collectors.toList());
        if (jars.size() == 0) {
            throw new IllegalStateException("No jars found in " + jarDirectory);
        }


        Path jarWithScmInManifest = jars.get(0);
        try (JarInputStream stream = new JarInputStream(jarWithScmInManifest.toUri().toURL().openStream())) {
            Manifest manifest = stream.getManifest();
            if (manifest != null) {
                String value = manifest.getMainAttributes().getValue("Scm-Revision");
                if (value != null) {
                    // Found the manifest entry - store it in a file
                    Path path = Paths.get(InstallerUtilMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                    path = path.resolve("META-INF");
                    if (!Files.exists(path)) {
                        Files.createDirectories(path);
                    }
                    path = path.resolve(id + "-scm-revision.txt");
                    Files.write(path, value.getBytes(StandardCharsets.UTF_8));
                    return;
                }
            }
        }

        throw new IllegalStateException("Was not able to read Scm-Revision from manifest");

    }

    private static InstallerUtilMain parse(String[] args) throws Exception {

        Path patchConfig = null;
        Path oldFile = null;
        Path newFile = null;
        Path patchFile = null;
        Path combineWith = null;
        String expansionPackVersion = null;

        Set<String> required = new HashSet<>(Arrays.asList(PATCH_CONFIG, EXPANSION_PACK_VERSION));
        List<String> patchGenArgs = new ArrayList<>();
        boolean createTemplate = false;
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
                    oldFile = Paths.get(val);
                    if (!Files.exists(oldFile)) {
                        fileDoesNotExist(arg);
                        usage();
                        return null;
                    } else if (!Files.isDirectory(oldFile)) {
                        fileIsNotADirectory(arg);
                        usage();
                        return null;
                    }
                } else if (arg.startsWith(UPDATED_DIST)) {
                    patchGenArgs.add(arg);
                    String val = arg.substring(UPDATED_DIST.length() + 1);
                    newFile = Paths.get(val);
                    if (!Files.exists(newFile)) {
                        fileDoesNotExist(arg);
                        usage();
                        return null;
                    } else if (!Files.isDirectory(newFile)) {
                        fileIsNotADirectory(arg);
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
                } else if (arg.startsWith(OUTPUT_FILE)) {
                    String val = arg.substring(OUTPUT_FILE.length() + 1);
                    patchFile = Paths.get(val);
                    if (Files.exists(patchFile) && Files.isDirectory(patchFile)) {
                        fileIsADirectory(arg);
                        usage();
                        return null;
                    }
                } else if (arg.equals(CREATE_TEMPLATE)) {
                    createTemplate = true;
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

        return null; //new PatchGenerator(patchConfig, oldFile, newFile, patchFile, includeVersion, combineWith);
    }

    private static void missingRequiredArgs(Set<String> set) {
        System.err.println("Missing required argument(s): " + set);
    }

    private static void fileIsNotADirectory(String arg) {
        System.err.println("File at path specified by argument " + arg + " is not a directory");
    }

    private static void fileIsADirectory(String arg) {
        System.err.println("File at path specified by argument " + arg + " is a directory");
    }

    private static void fileDoesNotExist(String arg) {
        System.err.println("File at path specified by argument " + arg +" does not exist");
    }

    public static void argumentExpected(String arg) {
        System.err.println("Argument expected for option " + arg);
    }



    private static void usage() {

        Usage usage = new Usage();

        usage.addArguments(APPLIES_TO_DIST + "=<file>");
        usage.addInstruction("Filesystem path of a pristine unzip of the distribution of the version of the software to which the generated patch applies");

        usage.addArguments("-h", "--help");
        usage.addInstruction("Display this message and exit");

        usage.addArguments(OUTPUT_FILE + "=<file>");
        usage.addInstruction("Filesystem location to which the generated patch file should be written");

        usage.addArguments(PATCH_CONFIG + "=<file>");
        usage.addInstruction("Filesystem path of the patch generation configuration file to use");

        usage.addArguments(UPDATED_DIST + "=<file>");
        usage.addInstruction("Filesystem path of a pristine unzip of a distribution of software which contains the changes that should be incorporated in the patch");

        usage.addArguments("-v", "--version");
        usage.addInstruction("Print version and exit");

        usage.addArguments(COMBINE_WITH + "=<file>");
        usage.addInstruction("Filesystem path of the previous CP to be included into the same package with the newly generated one");

        String headline = usage.getDefaultUsageHeadline("eap-mp-expansion-pack-installer-gen");
        System.out.print(usage.usage(headline));
    }
}
