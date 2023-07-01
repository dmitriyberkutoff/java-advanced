package info.kgeorgiy.ja.berkutov.implementor;

import info.kgeorgiy.java.advanced.implementor.Impler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.util.*;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Dmitriy Berkutov
 */

public class Implementor implements Impler, JarImpler {

    /**
     * Main class for implementor.
     * It runs implementor with arguments from command line.
     *
     * @param args arguments for implementor.
     *             It should be "(-jar) class output"
     */
    public static void main(String[] args) {
        final String USAGE = "Usage: (-jar) <class> <output>";
        if (args.length < 2 || args.length > 3) {
            System.out.println(USAGE);
            return;
        }
        for (String arg : args) Objects.requireNonNull(arg, "Argument is null");
        try {
            if (args.length == 2) new Implementor().implement(Class.forName(args[0]), Path.of(args[1]));
            else if (args[0].equals("-jar")) new Implementor().implementJar(Class.forName(args[1]), Path.of(args[2]));
            else System.err.println(USAGE);
        } catch (final ClassNotFoundException e) {
            System.err.println("Can not found class: " + e.getMessage());
        } catch (final ImplerException e) {
            System.err.println("Error during the implementing: " + e.getMessage());
        } catch (final InvalidPathException e) {
            System.err.println("Invalid path: " + e.getMessage());
        }
    }

    @Override
    public void implement(Class<?> token, Path root) throws ImplerException {
        checkArgs(token, root);
        Path source = root.resolve(Path.of(token.getPackageName().replace('.', File.separatorChar),
                getImplName(token, "java")));

        createDirs(source);
        try (BufferedWriter writer = Files.newBufferedWriter(source, StandardCharsets.UTF_8)) {
            writeImpl(token, writer);
        } catch (IOException e) {
            throw new ImplerException("Can not write implementation: " + e.getMessage());
        } catch (SecurityException e) {
            throw new ImplerException("Error with security of output: " + e.getMessage());
        }
    }

    /**
     * Creates parent directory for {@code path}.
     *
     * @param path file whose parent directory creates.
     */
    private void createDirs(Path path) {
        Path parent = path.getParent();
        if (parent != null) try {
            Files.createDirectories(parent);
        } catch (IOException | SecurityException ignored) {
        }
    }

    /**
     * Returns string with information about package for {@code token}.
     *
     * @param token class which package info that should be returned.
     * @return package information.
     */
    private String packageLine(Class<?> token) {
        String name = token.getPackageName();
        return name.isEmpty() ? "" : ("package " + name + ";");
    }

    /**
     * Gives string with declaration of class.
     *
     * @param token class which declaration string should be returned.
     * @return string with declaration of {@code token}
     */
    private String declarationLine(Class<?> token) {
        String name = getImplName(token);
        String extensionType = token.isInterface() ? "implements" : "extends";
        return String.join(" ", "public class", name, extensionType, token.getCanonicalName(), "{");
    }

    /**
     * Generates constructors of {@code token}.
     *
     * @param token class which constructor should be generated or interface.
     * @return {@link String} with generated constructors joined with {@code System.lineSeparator}.
     * If {@code token} is interface this method returns empty string.
     * @throws ImplerException if class has no non-private constructors or security .
     */
    private String constructors(Class<?> token) throws ImplerException {
        if (token.isInterface()) return "";

        List<Constructor<?>> constructors = Arrays.stream(token.getDeclaredConstructors())
                .filter(c -> !Modifier.isPrivate(c.getModifiers())).toList();
        if (constructors.isEmpty()) throw new ImplerException("Class has no non-private constructors");

        return constructors.stream().map(this::constructorString)
                .collect(Collectors.joining(System.lineSeparator(), "", ""));
    }

    /**
     * Generates string from elements of stream.
     *
     * @param stream stream which elements should be joined
     * @param prefix prefix for result string
     * @param suffix suffix for result string
     * @return {@link String} from elements of stream with prefix in the start and suffix in the end.
     */
    private String joinArgs(Stream<String> stream, String prefix, String suffix) {
        return stream.collect(Collectors.joining(", ", prefix, suffix));
    }

    /**
     * Returns constructor parameters to which the function is applied.
     *
     * @param constructor constructor to get parameters from it.
     * @param func        function that will be applied to each parameter.
     * @return {@link Stream<String>} of results applying function to parameters
     */
    private Stream<String> parameters(Constructor<?> constructor, Function<Parameter, String> func) {
        return Arrays.stream(constructor.getParameters()).map(func);
    }

    /**
     * Generates a string with a constructor.
     *
     * @param constructor constructor to convert to a string.
     * @return {@link String} with a constructor.
     */
    private String constructorString(Constructor<?> constructor) {
        String exceptions = Arrays.stream(constructor.getExceptionTypes())
                .map(Class::getCanonicalName).collect(Collectors.joining(", "));
        Stream<String> parametersInDecl = parameters(constructor, p -> p.getType().getCanonicalName() + " " + p.getName());
        Stream<String> parametersInBody = parameters(constructor, Parameter::getName);
        String name = getImplName(constructor.getDeclaringClass());

        return String.join(" ", "public", name, joinArgs(parametersInDecl, "(", ")"),
                (exceptions.isEmpty() ? "" : "throws " + exceptions), "{",
                joinArgs(parametersInBody, "super(", ");") + "}" + System.lineSeparator());
    }

    /**
     * Generates a string with a method.
     *
     * @param method method to convert to a string.
     * @return {@link String} with a method.
     */
    private String methodString(Method method) {
        final Class<?> returnValueType = method.getReturnType();
        final String returnValue;
        String exceptions = Arrays.stream(method.getExceptionTypes())
                .map(Class::getCanonicalName).collect(Collectors.joining(", "));
        Stream<String> parameters = Arrays.stream(method.getParameters())
                .map(p -> p.getType().getCanonicalName() + " " + p.getName());

        if (returnValueType.equals(void.class)) returnValue = "";
        else if (returnValueType.equals(boolean.class)) returnValue = "false";
        else if (returnValueType.isPrimitive()) returnValue = "0";
        else returnValue = "null";

        String mod = Modifier.toString(method.getModifiers() & ~Modifier.ABSTRACT & ~Modifier.TRANSIENT);

        return String.join(" ", mod, returnValueType.getCanonicalName(), method.getName(),
                joinArgs(parameters, "(", ")"),
                (exceptions.isEmpty() ? "" : ("throws " + exceptions)), "{",
                (returnValue.isEmpty() ? "" : ("return " + returnValue + ";")), "}" + System.lineSeparator());
    }

    /**
     * Generate methods of {@code token}.
     *
     * @param token class which methods should be generated or interface.
     * @return {@link String} with methods.
     */
    private String methods(Class<?> token) {
        Set<Method> setOfMethods = new TreeSet<>(
                Comparator.comparing(m -> m.getName() + (Arrays.toString(m.getParameterTypes()))));
        setOfMethods.addAll(Arrays.asList(token.getMethods()));
        setOfMethods.addAll(Arrays.asList(token.getDeclaredMethods()));

        Class<?> parent = token.getSuperclass();
        while (parent != null && parent != Object.class) {
            setOfMethods.addAll(Arrays.asList(parent.getDeclaredMethods()));
            parent = parent.getSuperclass();
        }
        return setOfMethods.stream().filter(m -> Modifier.isAbstract(m.getModifiers()) &&
                        !Modifier.isPrivate(m.getModifiers()) && !Modifier.isFinal(m.getModifiers()))
                .map(this::methodString)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    /**
     * Writes string with generated class with specified writer.
     * <p>
     * It writes package information, declaration, constructors if token is class, methods of implemented class.
     *
     * @param token  class for implementing.
     * @param writer writer that should write class to specified output file.
     * @throws ImplerException if token is a class and don't have non-private constructors or if
     *                         {@link SecurityException} was thrown during the implementation.
     * @throws IOException     if {@code writer} has an error with writing to output.
     */
    private void writeImpl(Class<?> token, BufferedWriter writer) throws ImplerException, IOException {
        String text = String.join(System.lineSeparator(),
                packageLine(token),
                declarationLine(token),
                constructors(token),
                methods(token), "}");
        final StringBuilder sb = new StringBuilder();
        final char[] charArray = text.toCharArray();
        for (final char c : charArray) sb.append((c < 128) ? c : String.format("\\u%04X", (int) c));
        writer.write(sb.toString());
    }


    /**
     * Check if arguments of main method are correct.
     * <p>
     * It checks if all arguments aren't equal {@code null}
     * Also checks if it is possible to implement class or interface.
     *
     * @param token class for extending or interface for implementing.
     * @param path  path where should be code of generated class.
     * @throws ImplerException if one of the {@code args} is {@code null} or {@code token}
     *                         isn't implementable.
     */
    private void checkArgs(Class<?> token, Path path) throws ImplerException {
        if (token == null || path == null) throw new ImplerException("Arguments are null");
        int modifiers = token.getModifiers();
        if (token == Enum.class || token.isPrimitive() || token.isArray()
                || Modifier.isPrivate(modifiers) || Modifier.isFinal(modifiers))
            throw new ImplerException("Error in token");
    }

    /**
     * Creates temp parent directory for {@code file}.
     *
     * @param file file for which the directory will be generated.
     * @return {@link Path} of new directory.
     * @throws ImplerException if it's impossible to create directory.
     */
    private Path newDirectory(Path file) throws ImplerException {
        Path dir;
        try {
            Path parent = file.getParent();
            if (parent == null) dir = Files.createTempDirectory("jar-dir");
            else dir = Files.createTempDirectory(parent, "jar-dir");
            return dir;
        } catch (IOException | SecurityException e) {
            throw new ImplerException("Temporary directory cannot be created", e);
        }
    }

    @Override
    public void implementJar(Class<?> token, Path jarFile) throws ImplerException {
        checkArgs(token, jarFile);
        createDirs(jarFile);

        Path dir = newDirectory(jarFile);
        try {
            implement(token, dir);
            compile(token, dir);
            createJar(token, dir, jarFile);
        } catch (ImplerException e) {
            throw new ImplerException(e.getMessage());
        } finally {
            if (dir != null) try {
                Files.walkFileTree(dir, deletingFileVisitor);
            } catch (final IOException e) {
                System.err.println("Error during the deleting temporary directories: " + e.getMessage());
            }
        }
    }

    /**
     * Compile implemented class in specified {@code path}.
     *
     * @param token class or interface which should be implemented.
     * @param path  path where class should be compiled.
     * @throws ImplerException if class can't be compiled.
     */
    private void compile(Class<?> token, Path path) throws ImplerException {
        CodeSource codeSource;
        String codeSourceString;
        try {
            codeSource = token.getProtectionDomain().getCodeSource();
            if (codeSource == null) codeSourceString = "";
            else codeSourceString = Path.of(codeSource.getLocation().toURI()).toString();
        } catch (final URISyntaxException | SecurityException e) {
            throw new ImplerException("Error during getting code source " + e.getMessage());
        }
        String file = path.resolve(Path.of(token.getPackageName().replace(".", File.separator),
                getImplName(token, "java"))).toString();
        String[] args = new String[]{file, "-cp",
                path + File.pathSeparator + codeSourceString};

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null || compiler.run(null, null, null, args) != 0)
            throw new ImplerException("Сan not compile java class");
    }

    /**
     * Writes .jar file from class implemented {@code token} to a specified path.
     *
     * @param token   class or interface which should be implemented.
     * @param path    path where contains class that will be compiled to jar.
     * @param file path where to write file.
     * @throws ImplerException if IO error happens with {@code writer}.
     */
    private void createJar(Class<?> token, Path path, Path file) throws ImplerException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

        try (JarOutputStream writer = new JarOutputStream(Files.newOutputStream(file), manifest)) {
            String name = token.getPackageName().replace(".", "/")
                    + "/" + getImplName(token, "class");
            JarEntry entry = new JarEntry(name);
            writer.putNextEntry(entry);
            Files.copy(path.resolve(name), writer);
        } catch (IOException | SecurityException e) {
            throw new ImplerException(e.getMessage());
        }
    }

    /**
     * Generate name for class which extends another or implements interface.
     *
     * @param file class for extending or interface for implementing.
     * @param type if it is empty, name will end with "Impl", otherwise with {@code type[0]}
     * @return {@link String} name for class with or without type.
     */
    private String getImplName(Class<?> file, String... type) {
        return file.getSimpleName() + "Impl" + (type.length < 1 ? "" : ("." + type[0]));
    }

    /**
     * {@link FileVisitor} that helps to delete temporary directories.
     */
    private static class DeletingFileVisitor extends SimpleFileVisitor<Path> {

        /**
         * Deletes file.
         *
         * @param file file to delete
         * @return {@code FileVisitResult.CONTINUE} for continuation deleting
         * @throws IOException if it's impossible to delete file
         */
        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attributes)
                throws IOException, SecurityException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        /**
         * Deletes directory.
         *
         * @param dir directory to delete
         * @param exc thrown exception during deleting
         * @return {@code FileVisitResult.CONTINUE} for continuation deleting
         * @throws IOException       if it's impossible to delete file
         * @throws SecurityException if a {@link SecurityException} was thrown during the deleting directory.
         */
        @Override
        public FileVisitResult postVisitDirectory(final Path dir, final IOException exc)
                throws IOException, SecurityException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    }

    /**
     * Instance of {@link DeletingFileVisitor} for deleting directories.
     */
    private static final FileVisitor<Path> deletingFileVisitor = new DeletingFileVisitor();
}
