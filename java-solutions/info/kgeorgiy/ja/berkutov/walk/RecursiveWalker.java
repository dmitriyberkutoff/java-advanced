package info.kgeorgiy.ja.berkutov.walk;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class RecursiveWalker extends SimpleFileVisitor<Path> {
    private final BufferedWriter writer;
    private final MessageDigest md;
    private final byte[] buffer = new byte[2048];

    public RecursiveWalker(final BufferedWriter writer) {
        this.writer = writer;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException e) {
            throw new AssertionError("Digest error: " + e);
        }
    }

    @Override
    public FileVisitResult visitFile(final Path path, final BasicFileAttributes attrs) throws IOException {
        // :NOTE: error handling
        writeHash(hash(path.toString(), md), path);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(final Path path, IOException exc) throws IOException{
        System.out.println("File visiting failed: " + exc.getMessage());
        writeHash("0".repeat(64), path);
        return FileVisitResult.CONTINUE;
    }

    private String hash(final String filePath, final MessageDigest md) throws IOException {
        try (final InputStream bis = Files.newInputStream(Path.of(filePath))) {
            md.reset();
            int count;
            while ((count = bis.read(buffer)) > 0) md.update(buffer, 0, count);
            return HexFormat.of().formatHex(md.digest());
        }
    }

    private void writeHash(final String hash, final Path path) throws IOException {
        writer.write(hash + " " + path.toString());
        writer.newLine();
    }
}
