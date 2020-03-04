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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jboss.as.patching.generator.PatchGenLogger;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
class TemplateGenerator {
    private static final String CREATE_TEMPLATE = "--create-template";
    private static final String H = "-h";
    private static final String HELP = "--help";

    private final String patchID;
    private final String mpXpVersion;
    private final String outputDir;

    private TemplateGenerator(String patchID, String mpXpVersion, String outputDir) {
        this.patchID = patchID;
        this.mpXpVersion = mpXpVersion;
        this.outputDir = outputDir;
    }

    static void generate(final String... args) throws Exception{

        String patchID = "mp-xp-";
        String outputDir = null;
        String mpXpVersion = null;
        for (int i = 0; i < args.length; i++) {
            final String arg = args[i];
            try {
                if (HELP.equals(arg) || H.equalsIgnoreCase(arg)) {
                    usage();
                    return;
                } else if (arg.equals(CREATE_TEMPLATE)) {
                    mpXpVersion = args[++i];
                    patchID += mpXpVersion;
                    if (++i < args.length) {
                        outputDir = args[i];
                    }
                    continue;
                } else {
                    System.err.println(PatchGenLogger.argumentExpected(arg));
                    usage();
                    return;
                }
            } catch (IndexOutOfBoundsException e) {
                System.err.println(PatchGenLogger.argumentExpected(arg));
                usage();
                return;
            }
        }

        TemplateGenerator templateGenerator = new TemplateGenerator(patchID, mpXpVersion, outputDir);
        templateGenerator.createPatchConfigXml();
    }

    private static void usage() {
        System.err.println("USAGE:");
        System.err.println("patch-gen.sh --create-template <microprofile-expansion-pack-version> [<output-dir>]");
        System.err.println();
        System.err.println("this will create a patch-config-[microprofile-expansion-pack-version].xml adjusted for the EAP CP and MP Expansion Pack versions");
    }

    private void createPatchConfigXml() throws Exception {
        String xml = readBundledPatchConfigXml();
        Path file = Paths.get("patch-config-" + patchID + ".xml");
        if (outputDir != null) {
            Path dir = Paths.get(outputDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            file = dir.resolve(file);
        }
        Files.write(file, xml.getBytes(StandardCharsets.UTF_8));
    }

    private String readBundledPatchConfigXml() throws Exception {
        URL url = TemplateGenerator.class.getProtectionDomain().getCodeSource().getLocation();
        if (!url.toString().contains(".jar")) {
            throw new IllegalStateException("The Template Creator must be run from the distributed jar. It should not be unzipped!");
        }

        File file = new File(url.toURI());
        String contents = null;
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            ZipEntry entry = zin.getNextEntry();
            while (entry != null)
                try {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    if (entry.getName().equals("patch-config.xml")) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(zin));
                        StringBuffer sb = new StringBuffer();
                        String line = reader.readLine();
                        while (line != null) {
                            line = line.replace("${expansion.pack.version}", mpXpVersion);
                            sb.append(line);
                            sb.append("\n");
                            line = reader.readLine();
                        }
                        contents = sb.toString();
                    }
                } finally {
                    zin.closeEntry();
                    entry = zin.getNextEntry();
                }
        }
        if (contents == null) {
            throw new IllegalStateException("Could not find patch-config.xml");
        }
        return contents;
    }
}
