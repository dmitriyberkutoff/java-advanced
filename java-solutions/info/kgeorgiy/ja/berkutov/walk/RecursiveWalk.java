package info.kgeorgiy.ja.berkutov.walk;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public class RecursiveWalk {
    public static void main(final String[] args) {
        if (args == null || args.length != 2 || (args[0] == null || args[1] == null)) {
            System.out.println("Two arguments are required");
            return;
        }

        try {
            final Path inputFile = Path.of(args[0]);
            final Path outputFile = Path.of(args[1]);
            final Path parent = outputFile.getParent();
            try {
                if (parent != null && Files.notExists(parent)) Files.createDirectories(parent);
            } catch (final IOException | SecurityException ignored) {
            }
            try (final BufferedReader reader = Files.newBufferedReader(inputFile)) {
                try (final BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
                    final FileVisitor<Path> visitor = new RecursiveWalker(writer);
                    try {
                        String name;
                        while ((name = reader.readLine()) != null)
                            try {
                                Files.walkFileTree(Path.of(name), visitor);
                            } catch (final InvalidPathException e) {
                                printException("Invalid file name", e);
                                writeZero(writer, name);
                            } catch (final SecurityException e) {
                                printException("Error during the walking", e);
                                writeZero(writer, name);
                            } catch (final IOException e) {
                                printException("Error writing output file", e);
                                return;
                            }
                    } catch (final IOException e) {
                        printException("Error during the reading input file", e);
                    }
                } catch (final IOException | SecurityException e) {
                    printException("Can not open output file", e);
                }
            } catch (final IOException | SecurityException e) {
                printException("Can not open input file", e);
            }
        } catch (final InvalidPathException e) {
            // :NOTE: input or output?
            printException("Invalid file path", e);
        }
    }

    static private void writeZero(final BufferedWriter writer, final String name) throws IOException {
        writer.write("0".repeat(64) + " " + name);
        writer.newLine();
    }

    static private void printException(final String message, final Exception e) {
        System.out.println(message + ": " + e.getMessage());
    }
}
