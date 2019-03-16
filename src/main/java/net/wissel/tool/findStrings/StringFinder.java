/** ========================================================================= *
 * Copyright (C)  2017, 2018 Salesforce Inc ( http://www.salesforce.com/      *
 *                            All rights reserved.                            *
 *                                                                            *
 *  @author     Stephan H. Wissel (stw) <swissel@salesforce.com>              *
 *                                       @notessensei                         *
 * @version     1.0                                                           *
 * ========================================================================== *
 *                                                                            *
 * Licensed under the  Apache License, Version 2.0  (the "License").  You may *
 * not use this file except in compliance with the License.  You may obtain a *
 * copy of the License at <http://www.apache.org/licenses/LICENSE-2.0>.       *
 *                                                                            *
 * Unless  required  by applicable  law or  agreed  to  in writing,  software *
 * distributed under the License is distributed on an  "AS IS" BASIS, WITHOUT *
 * WARRANTIES OR  CONDITIONS OF ANY KIND, either express or implied.  See the *
 * License for the  specific language  governing permissions  and limitations *
 * under the License.                                                         *
 *                                                                            *
 * ========================================================================== *
 */
package net.wissel.tool.findStrings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

/**
 * @author swissel
 *
 */
public class StringFinder {

    public static final String DIRNAME             = "d";
    public static final String DIRNAME_LONGNAME    = "dir";
    public static final String STRINGFILE          = "s";
    public static final String STRINGFILE_LONGNAME = "stringfile";
    public static final String OUTPUT              = "o";
    public static final String OUTPUT_LONGNAME     = "output";
    public static final String NOUNZIP             = "nz";
    public static final String NOUNZIP_LONGNAME    = "nounzip";

    /**
     * @param Command
     *            line provides input/output/search
     * @throws Exception
     */
    public static void main(final String[] args) throws Exception {
        final StringFinder sf = new StringFinder();
        if (sf.parseCommandLine(args)) {
            sf.run();
        }
        System.out.println("Done");

    }

    private final Options options = new Options();
    private String        dirName;
    private String        stringFileName;

    private PrintStream out;

    private final Map<String, String>      keys         = new HashMap<>();
    private final Map<String, Set<String>> results      = new HashMap<>();
    private boolean                        extractFiles = true;

    public StringFinder() {
        this.setupOptions();
    }

    public boolean parseCommandLine(final String[] args) throws FileNotFoundException {
        boolean canProceed = false;
        final CommandLineParser parser = new DefaultParser();
        CommandLine line = null;
        try {
            // parse the command line arguments
            line = parser.parse(this.options, args);
        } catch (final ParseException exp) {
            // oops, something went wrong
            System.err.println("Command line parsing failed.  Reason: " + exp.getMessage());
            System.exit(-1);
        }

        if (line != null) {
            canProceed = (line.hasOption(StringFinder.DIRNAME) && line.hasOption(StringFinder.STRINGFILE));
            if (line.hasOption(StringFinder.OUTPUT)) {
                this.out = new PrintStream(line.getOptionValue(StringFinder.OUTPUT));
            } else {
                this.out = System.out;
            }
            if (line.hasOption(StringFinder.NOUNZIP)) {
                this.extractFiles = false;
            }
        }

        if (!canProceed) {
            this.printHelp();

        } else {
            this.dirName = line.getOptionValue(StringFinder.DIRNAME);
            this.stringFileName = line.getOptionValue(StringFinder.STRINGFILE);
        }

        return canProceed;
    }

    public void run() throws Exception {
        this.populateKeys();

        final File sourceDir = new File(this.dirName);

        if (!sourceDir.isDirectory()) {
            throw new Exception("Input is not a directory");
        }

        if (this.extractFiles) {
            this.expandSources(sourceDir);
        }

        final File[] dirs = sourceDir.listFiles();
        for (final File d : dirs) {
            if (d.isDirectory()) {
                this.scanForKeys(d);
            }
        }

        if (!this.results.isEmpty()) {
            this.printResults();
        }
    }

    /**
     * Expands a ZIP file, but only if the target directory doesn't exist
     * already
     *
     * @param the
     *            ZIP file
     * @param targetDir
     * @return
     * @throws IOException
     */
    private boolean expandFile(final File f, final File targetDir) throws IOException {
        if (targetDir.exists()) {
            return false;
        }

        final ZipInputStream zis = new ZipInputStream(new FileInputStream(f));
        ZipEntry zipEntry = zis.getNextEntry();
        while (zipEntry != null) {
            final File outFile = this.newFile(targetDir, zipEntry);

            final FileOutputStream out = new FileOutputStream(outFile);
            ByteStreams.copy(zis, out);
            out.close();
            zipEntry = zis.getNextEntry();
        }

        zis.close();
        // Unpacking worked!
        return true;
    }

    /**
     * Scans the source directory for ZIP files and expands them into the target
     *
     * @param sourceDir
     * @param targetDir
     * @throws IOException
     */
    private boolean expandSources(final File sourceDir) throws IOException {
        boolean result = false;
        final File[] allFiles = sourceDir.listFiles();

        for (final File f : allFiles) {
            if (f.isDirectory()) {
                result = result || this.expandSources(f);

            } else if (f.getName().endsWith(".zip")) {
                final String newDirName = f.getAbsolutePath().replace(".zip", "");
                final File newTarget = new File(newDirName);

                // Need to scan the new directory too
                if (this.expandFile(f, newTarget)) {
                    result = result || this.expandSources(newTarget);
                }
            }
        }
        return result;

    }

    private void findKeyInFile(final File targetDirOrFile) throws IOException {
        final String source = Files.asCharSource(targetDirOrFile, Charsets.UTF_8).read().toLowerCase();
        this.keys.keySet().forEach(k -> {
            if (source.indexOf(k) > -1) {
                final Set<String> thisResult = this.results.containsKey(k) ? this.results.get(k)
                        : new HashSet<>();
                thisResult.add(targetDirOrFile.getAbsolutePath());
                this.results.put(k, thisResult);
            }
        });

    }

    private File newFile(final File destinationDir, final ZipEntry zipEntry) throws IOException {
        final File destFile = new File(destinationDir, zipEntry.getName());
        Files.createParentDirs(destFile);
        final String destDirPath = destinationDir.getCanonicalPath();
        final String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    private void populateKeys() throws FileNotFoundException {
        final File keyFile = new File(this.stringFileName);
        final Scanner c = new Scanner(keyFile);
        while (c.hasNextLine()) {
            final String curLine = c.nextLine().trim();
            if (!curLine.equals("") && !curLine.startsWith("#")) {
                this.keys.put(curLine.toLowerCase(), curLine);
            }
        }
        c.close();
    }

    private void printHelp() {
        final HelpFormatter formatter = new HelpFormatter();
        formatter.setOptionComparator(null);

        formatter.printHelp(
                "java -jar findString.jar ",
                this.options);
    }

    private void printResults() {
        this.out.println();
        this.out.println("# Scan Results");
        this.out.println();
        this.out.println("## Strings found in files");
        this.out.println();
        this.keys.forEach((key, printval) -> {

            if (this.results.containsKey(key)) {
                this.out.println("### " + printval);
                this.out.println();
                this.results.get(key).forEach(f -> {
                    this.out.println("- " + f);
                });
                this.out.println();
            }

        });

        this.out.println("## Strings not found");
        this.out.println();
        this.keys.forEach((k, v) -> {
            if (!this.results.containsKey(k)) {
                this.out.println("- " + v);
            }
        });
        this.out.flush();
        this.out.close();

    }

    private void scanForKeys(final File targetDirOrFile) throws IOException {
        if (!targetDirOrFile.exists()) {
            return;
        }
        if (targetDirOrFile.isDirectory()) {
            final File[] children = targetDirOrFile.listFiles();
            for (final File f : children) {
                this.scanForKeys(f);
            }
        } else {
            if (!targetDirOrFile.getName().endsWith(".zip")) {
                this.findKeyInFile(targetDirOrFile);
            }
        }

    }

    private void setupOptions() {
        this.options.addOption(Option.builder(StringFinder.DIRNAME).longOpt(StringFinder.DIRNAME_LONGNAME)
                .desc("directory with all zip files")
                .hasArg()
                .build());
        this.options.addOption(Option.builder(StringFinder.STRINGFILE).longOpt(StringFinder.STRINGFILE_LONGNAME)
                .desc("Filename with Strings to search, one per line")
                .hasArg()
                .build());

        this.options.addOption(Option.builder(StringFinder.OUTPUT).longOpt(StringFinder.OUTPUT_LONGNAME)
                .desc("Output file name for report in MD format")
                .hasArg()
                .build());

        this.options.addOption(Option.builder(StringFinder.NOUNZIP).longOpt(StringFinder.NOUNZIP)
                .desc("Rerun find operation on a ready unzipped structure - good for alternate finds")
                .build());
    }

}
