package org.eap.util.microprofile.expansion.pack.installer.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class InstallerUtilMain {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalStateException("Usage: java InstallerUtilMain <path to server> [<added layers>]");
        }
        // The unzipped server home will be the only directory inside this one. Rename it to be something known
        // for when the maven plugins pick up again
        Path serverHome = Paths.get(args[0]);
        if (args.length == 2) {
            String allLayers = args[1];

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
        }
    }
}
