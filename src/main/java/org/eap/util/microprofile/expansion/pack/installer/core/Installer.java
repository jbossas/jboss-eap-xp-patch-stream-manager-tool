package org.eap.util.microprofile.expansion.pack.installer.core;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.dmr.ModelNode;
import org.wildfly.core.embedded.Configuration;
import org.wildfly.core.embedded.EmbeddedProcessFactory;
import org.wildfly.core.embedded.StandaloneServer;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class Installer {
    private final Path jbossHome;
    private final Path modulesDir;
    private final Path patchZip;
    private final Path addedConfigs;
    private final List<String> layersToCreate;
    private final Path layersConf;
    private final List<Path> createdLayers = new ArrayList<>();

    private ModelControllerClient client;

    public Installer(Path jbossHome, Path modulesDir, Path patchZip, Path addedConfigs) throws Exception {
        this.jbossHome = jbossHome;
        this.modulesDir = modulesDir;
        this.patchZip = patchZip;
        this.addedConfigs = addedConfigs;
        this.layersToCreate = getLayersToAdd();
        layersConf = modulesDir.resolve("layers.conf");
    }

    public void install() throws Exception {
        Configuration configuration = Configuration.Builder.of(jbossHome.toFile())
                .setModulePath(modulesDir.toString())
                .addSystemPackages("org.jboss.logging", "org.jboss.logmanager")
                .build();
        StandaloneServer server = EmbeddedProcessFactory.createStandaloneServer(configuration);
        checkAndSetupLayers();
        copyAddedConfigs();
        try {
            server.start();
            try (ModelControllerClient client = server.getModelControllerClient()) {
                this.client = client;
                checkVersion();
                checkCumulativePatchId();
                applyPatch();
            }
        } catch (Exception e) {
            // Clean up the stuff we created in checkAndSetupLayers()
            if (Files.exists(layersConf)) {
                Files.delete(layersConf);
            }
            for (Path layerDir : createdLayers) {
                if (Files.exists(layerDir)) {
                    Files.delete(layerDir);
                }
            }
            throw e;
        } finally {
            this.client = null;
            server.stop();
        }
    }

    private void copyAddedConfigs() throws Exception {
        if (!Files.exists(addedConfigs)) {
            return;
        }
        Files.walkFileTree(addedConfigs, new FileVisitors.CopyAddedConfigs(addedConfigs, jbossHome));
    }

    private void applyPatch() throws Exception {
        ModelNode operation = new OperationBuilder("patch")
                .addr("core-service", "patching")
                .param("override-modules", "false")
                .param("override-all", "false")
                .param("input-stream-index", "0")
                .build();

        final org.jboss.as.controller.client.OperationBuilder operationBuilder = org.jboss.as.controller.client.OperationBuilder.create(operation);
        operationBuilder.addFileAsAttachment(patchZip.toFile());
        executeOperation(operationBuilder.build());
    }

    private void checkVersion() throws Exception {
        ModelNode versionNode = executeOperation(
                new OperationBuilder("read-attribute")
                        .param("name", "product-version")
                        .build());
        String version = versionNode.asString();
        // TODO check version
    }

    private void checkCumulativePatchId() throws Exception {
        ModelNode versionNode = executeOperation(
                new OperationBuilder("read-attribute")
                        .addr("core-service", "patching")
                        .param("name", "cumulative-patch-id")
                        .build());
        String id = versionNode.asString();
        if (!id.equals("base")) {
            throw new IllegalStateException("The cumulative-patch-id in /core-service=patching:read-resource(recursive=true, include-runtime=true) was " +
                    "'" + id + "'. A value of 'base' is expected in an untouched Red Hat JBoss EAP installation");
        }
    }

    private void checkAndSetupLayers() throws Exception {
        StringBuilder layersConfContents = new StringBuilder("layers=");
        boolean first = false;

        for (String layerToAdd : layersToCreate) {
            Path layerDir = modulesDir.resolve("system/layers/" + layerToAdd);

            if (Files.exists(layerDir)) {
                throw new IllegalStateException(layerDir + " already exists. This does not appear to be an untouched Red Hat JBoss EAP installation");
            }

            Files.createDirectories(layerDir);
            createdLayers.add(layerDir);

            if (!first) {
                layersConfContents.append(",");
            } else {
                first = false;
            }
            layersConfContents.append(layerToAdd);
        }

        if (Files.exists(layersConf)) {
            throw new IllegalStateException(modulesDir + "/layers.conf already exists. This does not appear to be an untouched Red Hat JBoss EAP installation");
        }

        Files.write(layersConf, layersConfContents.toString().getBytes(StandardCharsets.UTF_8));
    }

    private ModelNode executeOperation(ModelNode operation) throws Exception {
        ModelNode result = client.execute(operation);
        return getResult(result);
    }

    private ModelNode executeOperation(Operation operation) throws Exception {
        ModelNode result = client.execute(operation);
        return getResult(result);
    }

    private ModelNode getResult(ModelNode result) {
        if (result.get("outcome").asString().equals("failed")) {
            throw new RuntimeException(result.get("failure-description").asString());
        }
        return result.get("result");
    }

    private List<String> getLayersToAdd() throws Exception {
        String rawLayers;
        rawLayers = readValueFromManifest("server-target-layers");
        List<String> layers = new ArrayList<>();
        String[] tokens = rawLayers.split(",");
        for (String layer : tokens) {
            layer = layer.trim();
            if (layer.length() > 0) {
                layers.add(layer);
            }
        }
        return layers;
    }

    private String readValueFromManifest(String name) throws Exception {
        // Doing a simple Installer.class.getClassLoader().getResource("META-INF/MANIFEST.MF") doesn't always work
        // since it tries to load from jar:file:/System/Library/Java/Extensions/MRJToolkit.jar!/META-INF/MANIFEST.MF
        for (Enumeration<URL> e = Installer.class.getClassLoader().getResources("META-INF/MANIFEST.MF"); e.hasMoreElements() ; ) {
            URL url = e.nextElement();
            try (InputStream stream = url.openStream()) {
                Manifest manifest = null;
                if (stream != null) {
                    manifest = new Manifest(stream);
                    String value = manifest.getMainAttributes().getValue(name);
                    if (value != null) {
                        return value;
                    }
                }
            }
        }

        throw new IllegalStateException("No '" + name + "' entry found in any MANIFEST.MF");
    }

    private static class OperationBuilder {
        private final String name;
        private Map<String, String> address = new LinkedHashMap<>();
        private Map<String, String> parameters = new HashMap<>();

        OperationBuilder(String name) {
            this.name = name;
        }

        OperationBuilder addr(String key, String value) {
            address.put(key, value);
            return this;
        }

        OperationBuilder param(String key, String value) {
            parameters.put(key, value);
            return this;
        }

        ModelNode build() {
            ModelNode op = new ModelNode();
            op.get("operation").set(name);

            ModelNode addr = new ModelNode().setEmptyList();
            for (Map.Entry<String, String> entry : address.entrySet()) {
                addr.add(entry.getKey(), entry.getValue());
            }
            op.get("address").set(addr);

            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                op.get(entry.getKey()).set(entry.getValue());
            }

            return op;
        }
    }
}
