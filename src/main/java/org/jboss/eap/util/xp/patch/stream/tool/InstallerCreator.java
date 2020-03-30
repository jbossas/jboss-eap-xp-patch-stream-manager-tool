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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
class InstallerCreator {
    private static final String LAYERS_MANIFEST_KEY = "server-target-layers";

    private final List<Path> addedConfigFiles;
    private final Path installerCore;
    private final Path outputDir;
    private final Path outputInstaller;
    private Path tmpDir;

    InstallerCreator(List<Path> addedConfigFiles, Path installerCore, Path outputDir) throws Exception {
        this.addedConfigFiles = addedConfigFiles;
        this.installerCore = installerCore;
        this.outputDir = outputDir;

        Path tmp = Paths.get("jboss-eap-xp-installer.jar");
        if (outputDir != null) {
            Files.createDirectories(outputDir);
            tmp = outputDir.resolve(tmp);
        }
        this.outputInstaller = tmp;
    }

    void createInstaller() throws Exception {
        try {
            this.tmpDir = unzipInstallerCore();
            addManifestLayers();
            copyConfigsToInstaller();
            zipInstaller();
        } finally {
            cleanup();
        }
    }

    private void cleanup() {
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


    private void copyConfigsToInstaller() throws Exception {
        if (addedConfigFiles == null) {
            return;
        }
        Path configsDirectory = tmpDir.resolve("added-configs");
        if (!Files.exists(configsDirectory)) {
            Files.createDirectories(configsDirectory);
        }

        for (Path path : addedConfigFiles) {
            Path target = configsDirectory.resolve(path.getFileName());
            Files.copy(path, target);
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
