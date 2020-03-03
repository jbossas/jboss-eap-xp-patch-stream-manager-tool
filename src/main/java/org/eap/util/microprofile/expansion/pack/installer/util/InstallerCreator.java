package org.eap.util.microprofile.expansion.pack.installer.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.jboss.as.patching.generator.PatchGenerator;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
class InstallerCreator {
    static final String LAYERS_ROOT = "modules/system/layers";
    private static final String LAYERS_MANIFEST_KEY = "server-target-layers";

    private final List<String> patchGenArgs;
    private final Path patchConfig;
    private final Path oldServerHome;
    private final Path newServerHome;
    private final Path combineWith;
    private final String expansionPackVersion;
    private final List<String> addedConfigs;
    private final Path installerCore;
    private final Path outputInstaller;
    private Path patchFile;
    private Path tmpDir;

    InstallerCreator(List<String> patchGenArgs, Path patchConfig, Path oldServerHome, Path newServerHome, Path combineWith, String expansionPackVersion, List<String> addedConfigs, Path installerCore) {
        this.patchGenArgs = patchGenArgs;
        this.patchConfig = patchConfig;
        this.oldServerHome = oldServerHome;
        this.newServerHome = newServerHome;
        this.combineWith = combineWith;
        this.expansionPackVersion = expansionPackVersion;
        this.addedConfigs = addedConfigs;
        this.installerCore = installerCore;
        this.outputInstaller = Paths.get("microprofile-expansion-pack-installer.jar");
    }

    void createInstaller() throws Exception {
        try {
            setupLayersInOldServer();
            createPatch();
            this.tmpDir = unzipInstallerCore();
            addManifestLayers();
            copyConfigsFromNewServerToInstaller();
            // Copy the installer to the patch.zip
            Files.copy(patchFile, tmpDir.resolve("patch.zip"));
            zipInstaller();
        } finally {
            cleanup();
        }
    }

    private void cleanup() {
        if (patchFile != null) {
            if (Files.exists(patchFile)) {
                try {
                    Files.delete(patchFile);
                } catch (IOException e) {
                    System.err.println("Could not delete " + patchFile.toString());
                }
            }
        }
        if (tmpDir != null) {
            try {
                Files.walkFileTree(tmpDir, new SimpleFileVisitor<Path>(){
                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                });
            } catch (IOException e) {
                System.err.println("Problems deleting" + tmpDir + " recursively: " + e.getLocalizedMessage());
            }
        }
    }

    private void setupLayersInOldServer() throws Exception {
        Path layersRoot = oldServerHome.resolve(LAYERS_ROOT);
        String layerName = "microprofile";
        Path layerDir = layersRoot.resolve(layerName);
        if (!Files.exists(layerDir)) {
            Files.createDirectories(layerDir);
        }
        Path layersConf = oldServerHome.resolve("modules/layers.conf");
        Files.write(layersConf, ("layers=" + layerName).getBytes(StandardCharsets.UTF_8));
    }

    private void createPatch() throws Exception {
        patchFile = Files.createTempFile("patch", ".zip");
        if (!Files.exists(patchFile.getParent())) {
            Files.createDirectories(patchFile.getParent());
        }
        List<String> args = new ArrayList<>(patchGenArgs);
        patchGenArgs.add("--output-file=" + patchFile.toAbsolutePath().toString());
        //Create patch
        PatchGenerator.main(patchGenArgs.toArray(new String[0]));
        if (!Files.exists(patchFile)) {
            System.err.println("No generated patch file found at " + patchFile);
            System.exit(1);
        }
    }

    private Path unzipInstallerCore() throws IOException {
        Path tmpDir = Files.createTempDirectory("mp-installer");
        byte[] buffer = new byte[1024];
        boolean foundPatch = false;
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(installerCore.toFile())))) {
            ZipEntry entry = zin.getNextEntry();
            while (entry != null) {
                try {
                    if (!entry.isDirectory()) {
                        Path path = tmpDir.resolve(entry.getName());
                        if (!Files.exists(path.getParent())) {
                            Files.createDirectories(path.getParent());
                        }
                        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(path.toFile()))) {
                            int len;
                            while ((len = zin.read(buffer)) > 0) {
                                out.write(buffer, 0, len);
                            }
                        }
                    }
                } finally {
                    zin.closeEntry();
                    entry = zin.getNextEntry();
                }
            }
        }

        return tmpDir;
    }

    private void addManifestLayers() throws Exception {
        Path manifestPath = tmpDir.resolve("META-INF/MANIFEST.MF");
        Manifest manifest = null;
        if (!Files.exists(manifestPath)) {
            Files.createDirectories(manifestPath.getParent());
            manifest = new Manifest();
        } else {
            try (InputStream in = new BufferedInputStream(manifestPath.toUri().toURL().openStream())) {
                manifest = new Manifest(in);
            }
            Files.delete(manifestPath);
        }
        if (manifest.getMainAttributes().getValue(LAYERS_MANIFEST_KEY) == null) {
            manifest.getMainAttributes().putValue(LAYERS_MANIFEST_KEY, "microprofile");
        }

        Files.createFile(manifestPath);
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(manifestPath.toFile()))) {
            manifest.write(out);
        }
    }


    private void copyConfigsFromNewServerToInstaller() throws Exception {
        Path configsDirectory = tmpDir.resolve("added-configs");
        if (!Files.exists(configsDirectory)) {
            Files.createDirectories(configsDirectory);
        }

        for (String config : addedConfigs) {
            Path dest = configsDirectory.resolve(config.trim());
            if (!Files.exists(dest.getParent())) {
                Files.createDirectories(dest.getParent());
            }

            Path source = newServerHome.resolve(config.trim());
            if (!Files.exists(source)) {
                throw new IllegalStateException("The config file " + source + " could not be found");
            }
            Files.copy(source, dest);
        }
    }

    private void zipInstaller() throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outputInstaller.toFile())))) {
            Files.walkFileTree(tmpDir, new SimpleFileVisitor<Path>(){
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path targetFile = tmpDir.relativize(file);
                    zos.putNextEntry(new ZipEntry(targetFile.toString()));
                    byte[] bytes = Files.readAllBytes(file);
                    zos.write(bytes, 0, bytes.length);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
