package org.eap.util.microprofile.expansion.pack.installer.util;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class InstallerUtilMain {
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
}
