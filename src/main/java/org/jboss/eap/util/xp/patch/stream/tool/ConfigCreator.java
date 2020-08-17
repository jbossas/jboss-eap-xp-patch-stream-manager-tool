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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
class ConfigCreator {
    private static final String H = "-h";
    private static final String HELP = "--help";

    private static final Pattern MP_VERSION_PATTERN = Pattern.compile("\\d+\\.\\d+\\.\\d+\\.GA");
    private static final Pattern MAJOR_VERSION_PATTERN = Pattern.compile("[1-9]+\\.0\\.0\\.GA");

    private static final String PATCH_STREAM_BASE_NAME = "jboss-eap-xp-";

    private static final String APPLIES_TO_VERSON_MARKER = "${applies.to.version}";
    private static final String EXPANSION_PACK_VERSION_MARKER = "${expansion.pack.version}";
    private static final String EXPANSION_PACK_PATCH_STREAM_NAME_MARKER = "${xp.patch.stream.name}";

    private final String appliesToVersion;
    private final String xpVersionRoot;
    private final String patchStreamName;
    private final String outputDir;

    private ConfigCreator(String appliesToVersion, String xpVersionRoot, String patchStreamName, String outputDir) {
        this.appliesToVersion = appliesToVersion;
        this.xpVersionRoot = xpVersionRoot;
        this.patchStreamName = patchStreamName;
        this.outputDir = outputDir;
    }

    static Path generate(final String... args) throws Exception{

        String outputDir = null;
        String xpVersion = null;
        for (int i = 0; i < args.length; i++) {
            final String arg = args[i];
            try {
                if (HELP.equals(arg) || H.equalsIgnoreCase(arg)) {
                    usage();
                    System.exit(1);
                } else if (arg.equals(ManagerCreatorMain.CREATE_CONFIG)) {
                    i++;
                    if (i > args.length) {
                        usage();
                        return null;
                    }
                    xpVersion = args[i];
                    if (!MP_VERSION_PATTERN.matcher(xpVersion).matches()) {
                        System.err.println(xpVersion + " does not look like a valid JBoss EAP XP version. Examples:\n\t1.0.0.GA\n\t1.0.1.GA\n\t1.0.2.GA");
                        usage();
                        return null;
                    }

                    if (++i < args.length) {
                        outputDir = args[i];
                    }
                } else {
                    ToolLogger.argumentExpected(arg);
                    usage();
                    System.exit(1);
                }
            } catch (IndexOutOfBoundsException e) {
                ToolLogger.argumentExpected(arg);
                usage();
                System.exit(1);
            }
        }


        String appliesToVersion = "0.0.0";
        if (!MAJOR_VERSION_PATTERN.matcher(xpVersion).matches()) {
            // Figure out the last version
            String[] parts = xpVersion.split("\\.");
            StringBuilder xpVersionBuilder = new StringBuilder();
            StringBuilder appliesToVersionBuilder = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                if (i > 0) {
                    xpVersionBuilder.append(".");
                    appliesToVersionBuilder.append(".");
                }

                String part = parts[i];
                xpVersionBuilder.append(part);
                if (i == 2) {
                    int micro = Integer.valueOf(part) - 1;
                    part = String.valueOf(micro);
                }
                appliesToVersionBuilder.append(part);
            }
            appliesToVersion = appliesToVersionBuilder.toString();
        }

        int i = xpVersion.indexOf(".GA");
        String xpVersionRoot = xpVersion.substring(0, i);

        String majorVersion = xpVersion.substring(0, xpVersion.indexOf("."));
        String patchStreamName = PATCH_STREAM_BASE_NAME + majorVersion + ".0";
        
        ConfigCreator configCreator = new ConfigCreator(appliesToVersion, xpVersionRoot, patchStreamName, outputDir);
        return configCreator.createPatchConfigXml();
    }

    private static void usage() {
        System.err.println("USAGE:");
        System.err.println(ManagerCreatorMain.getJavaCommand(ConfigCreator.class) + " --create-config <microprofile-expansion-pack-version> [<output-dir>]");
        System.err.println();
        System.err.println("this will create a patch-config-[microprofile-expansion-pack-version].xml adjusted for the EAP CP and MP Expansion Pack versions");
    }

    private Path createPatchConfigXml() throws Exception {
        String xml = readBundledPatchConfigXml();
        Path file = Paths.get("patch-config-" + xpVersionRoot + ".xml");
        if (outputDir != null) {
            Path dir = Paths.get(outputDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            file = dir.resolve(file);
        }
        Files.write(file, xml.getBytes(StandardCharsets.UTF_8));
        System.out.println("Created patch config at " + file.toAbsolutePath());
        return file;
    }

    private String readBundledPatchConfigXml() throws Exception {
        URL url = ConfigCreator.class.getProtectionDomain().getCodeSource().getLocation();
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
                            line = line.replace(APPLIES_TO_VERSON_MARKER, appliesToVersion);
                            line = line.replace(EXPANSION_PACK_VERSION_MARKER, xpVersionRoot);
                            line = line.replace(EXPANSION_PACK_PATCH_STREAM_NAME_MARKER, patchStreamName);
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

    public static void main(String[] args) {
        System.out.println(MAJOR_VERSION_PATTERN.matcher("1.0.0.GA").matches());
    }
}
