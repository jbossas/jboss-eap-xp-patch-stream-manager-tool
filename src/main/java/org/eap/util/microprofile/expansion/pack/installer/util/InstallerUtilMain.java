package org.eap.util.microprofile.expansion.pack.installer.util;

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
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            throw new IllegalStateException("Usage: java InstallerUtilMain <id> <path to server> [<added layers>]");
        }
        // The unzipped server home will be the only directory inside this one. Rename it to be something known
        // for when the maven plugins pick up again
        String id = args[0];
        Path serverHome = Paths.get(args[1]);
        if (args.length == 3) {
            String allLayers = args[2];

            Path layersRoot = serverHome.resolve("modules/system/layers");

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
            recordScmRevision(id, layersRoot);
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
