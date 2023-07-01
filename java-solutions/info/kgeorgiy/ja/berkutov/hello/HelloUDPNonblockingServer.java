package info.kgeorgiy.ja.berkutov.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.*;
import java.util.concurrent.*;

public class HelloUDPNonblockingServer implements HelloServer {
    ExecutorService threadPool;
    Selector selector;
    DatagramChannel channel;
    DatagramSocket socket;
    final ExecutorService singleListener = Executors.newSingleThreadExecutor();
    final Deque<ByteBuffer> buffers = new ConcurrentLinkedDeque<>();
    final Queue<DatagramPacket> writeQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void start(int port, int threads) {
        selector = Util.getSelector();
        if (selector == null) return;

        try {
            channel = Util.createChannel(new InetSocketAddress(port), false);
            socket = channel.socket();
            channel.register(selector, SelectionKey.OP_READ);

            threadPool = Util.getThreadPool(threads);
            int size = socket.getReceiveBufferSize();
            for (int i = 1; i <= threads; i++) buffers.add(ByteBuffer.allocate(size));
            singleListener.submit(this::receiveLoop);
        } catch (final SocketException e) {
            System.err.println("Can not get socket from channel");
            close();
        } catch (final IOException e) {
            System.err.println("Can not create channel");
        }
    }

    private void receiveLoop() {
        while (!socket.isClosed()) try {
            selector.select();
            for (var iter = selector.selectedKeys().iterator(); iter.hasNext(); ) {
                SelectionKey key = iter.next();
                if (key.isWritable()) send(key);
                else if (key.isReadable()) receive(key);
                iter.remove();
            }
        } catch (final IOException e) {
            close();
            return;
        }
    }

    private void send(final SelectionKey key) {
        if (!writeQueue.isEmpty()) {
            DatagramPacket packet = writeQueue.poll();
            Util.send(channel, packet);
        } else key.interestOps(SelectionKey.OP_READ);
    }

    private void receive(final SelectionKey key) {
        if (!buffers.isEmpty()) {
            ByteBuffer buffer = buffers.poll();
            threadPool.submit(() -> addResponse(key, buffer));
        } else key.interestOpsAnd(~SelectionKey.OP_READ);
    }

    private void addResponse(SelectionKey key, ByteBuffer buffer) {
        SocketAddress address;
        try {
            address = channel.receive(buffer);
        } catch (IOException e) {
            System.err.println("Can not get address");
            return;
        }
        buffer.flip();
        String request = Util.stringFromBuffer(buffer);
        Util.log("Receive: " + request);
        byte[] response = Util.responseBody(request).getBytes(Util.getCharset());
        key.interestOps(SelectionKey.OP_WRITE);

        buffer.clear();
        buffers.add(buffer);
        writeQueue.add(new DatagramPacket(response, response.length, address));

        selector.wakeup();
    }

    @Override
    public void close() {
        try {
            selector.close();
            channel.close();
        } catch (IOException ignored) {
        }
        Util.awaitPool(threadPool, 1);
        Util.awaitPool(singleListener, 1);
    }

    public static void main(String... args) {
        Util.startServer(HelloUDPNonblockingServer::new, args);
    }
}
