package info.kgeorgiy.ja.berkutov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;
import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class Util {
    private static final boolean LOG_ENABLED = false;
    private static final int TIMEOUT = 100;
    private static final int SIZE_OF_BUFFER = 1024;
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    public static String stringFromPacket(DatagramPacket packet) {
        return new String(packet.getData(), packet.getOffset(), packet.getLength(), CHARSET);
    }

    public static Charset getCharset() {
        return CHARSET;
    }

    public static int getSelectTimeout() {
        return TIMEOUT;
    }

    public static int getBufferSize() {
        return SIZE_OF_BUFFER;
    }

    public static void setMessage(DatagramPacket packet, String message) {
        packet.setData(message.getBytes(CHARSET));
        packet.setLength(packet.getData().length);
    }

    private static boolean checkSubstrings(int thread, int request, final String response,
                                           int firstStart, int firstEnd, int secondStart, int secondEnd) {
        return equals(thread, response.substring(firstStart, firstEnd))
                && equals(request, response.substring(secondStart, secondEnd));
    }

    public static DatagramPacket createEmptyPacket(DatagramSocket socket) throws SocketException {
        int size = socket.getReceiveBufferSize();
        return new DatagramPacket(new byte[size], size);
    }

    public static void awaitPool(ExecutorService threadPool, int items) {
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(items * 3L, TimeUnit.SECONDS))
                System.err.println("Can not close executor service.");
        } catch (InterruptedException ignored) {
        }
    }

    public static ThreadPoolExecutor getThreadPool(int threads) {
        final ThreadPoolExecutor.AbortPolicy POLICY = new ThreadPoolExecutor.AbortPolicy();
        final BlockingQueue<Runnable> QUEUE = new ArrayBlockingQueue<>(777);

        return  new ThreadPoolExecutor(threads, threads,
                1, TimeUnit.SECONDS, QUEUE, POLICY);
    }

    public static void send(DatagramSocket socket, String message, SocketAddress address) throws IOException {
        DatagramPacket request = new DatagramPacket(new byte[0], 0);
        setMessage(request, message);
        request.setSocketAddress(address);
        log("Send: " + stringFromPacket(request));
        socket.send(request);
    }

    public static void send(DatagramChannel channel, DatagramPacket packet) {
        try {
            log("Send: " + Util.stringFromPacket(packet));
            channel.send(ByteBuffer.wrap(packet.getData()), packet.getSocketAddress());
        } catch (IOException e) {
            System.err.println("Can not send response");
        }
    }

    public static String sendAndReceive(String string, DatagramSocket socket, SocketAddress address) throws IOException {
        send(socket, string, address);
        return stringFromPacket(receive(socket));
    }

    public static DatagramChannel createChannel(SocketAddress address, boolean connect) throws IOException {
        DatagramChannel datagramChannel = DatagramChannel.open();
        datagramChannel.configureBlocking(false);
        if (connect) datagramChannel.connect(address);
        else datagramChannel.bind(address);
        return datagramChannel;
    }

    public static void closeChannel(SelectionKey key) {
        try {
            key.channel().close();
        } catch (IOException ignored) {
        }
    }

    public static SocketAddress getAddress(String host, int port) {
        try {
            return new InetSocketAddress(InetAddress.getByName(host), port);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public static DatagramPacket receive(DatagramSocket socket) throws IOException {
        DatagramPacket request = createEmptyPacket(socket);
        socket.receive(request);
        Util.log("Receive: " + stringFromPacket(request));
        return request;
    }

    public static String receiveInBuffer(SelectionKey key, ByteBuffer buffer) {
        buffer.clear();
        DatagramChannel dc = (DatagramChannel) key.channel();
        try {
            dc.receive(buffer);
        } catch (IOException e) {
            return null;
        }
        buffer.flip();
        return stringFromBuffer(buffer);
    }

    public static void log(String message) {
        if (LOG_ENABLED) System.out.println(message);
    }

    public static String stringFromBuffer(ByteBuffer buffer) {
        return Util.getCharset().decode(buffer).toString();
    }

    public static boolean validResponse(int thread, int request, final String response) {
        int firstStart = findChar(0, response, true);
        int firstEnd = findChar(firstStart, response, false);
        int secondStart = findChar(firstEnd, response, true);
        int secondEnd = findChar(secondStart, response, false);
        return checkSubstrings(thread, request, response, firstStart, firstEnd, secondStart, secondEnd);
    }

    public static Selector getSelector() {
        try {
            return Selector.open();
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean equals(int n, String number) {
        if (number.isEmpty()) return false;
        int responseInt = Integer.parseInt(number);
        return n == responseInt;
    }

    private static int findChar(int index, String str, boolean isDigit) {
        while (index < str.length() && Character.isDigit(str.charAt(index)) != isDigit) ++index;
        return index;
    }

    public static String responseBody(String request) {
        return String.format("Hello, %s", request);
    }

    private static boolean invalid(String... args) {
        if (args == null || args.length == 0 || Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.err.println("Non null arguments are required");
            return true;
        }
        return false;
    }

    private static Integer getArg(int index, String... args) {
        try {
            return Integer.parseInt(args[index]);
        } catch (NumberFormatException e) {
            System.err.println("Expected integer argument at index " + index + 1);
            return null;
        }
    }

    public static void runClient(Supplier<HelloClient> clientSupplier, String... args) {
        if (invalid(args)) return;

        Integer port = getArg(1, args) ;
        Integer threads = getArg(3, args);
        Integer requests = getArg(4, args);
        if (port != null && threads != null && requests != null)
            clientSupplier.get().run(args[0], port, args[2], threads, requests);
    }

    public static void startServer(Supplier<HelloServer> serverSupplier, String... args) {
        if (invalid(args)) return;

        Integer port = getArg(0, args);
        Integer threads = getArg(1, args);
        if (port != null && threads != null)
            try (HelloServer server = serverSupplier.get()) {
                server.start(port, threads);
            }
    }
}
