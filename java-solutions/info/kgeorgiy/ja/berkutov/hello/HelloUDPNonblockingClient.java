package info.kgeorgiy.ja.berkutov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

public class HelloUDPNonblockingClient implements HelloClient {
    @Override
    public void run(String host, int port, String prefix, int threads, int requests) {
        final Selector selector = Util.getSelector();
        final SocketAddress address = Util.getAddress(host, port);

        if (address == null || selector == null) return;

        for (int index = 1; index <= threads; index++) try {
            ThreadContext context = new ThreadContext(index);
            Util.createChannel(address, true).register(selector, SelectionKey.OP_WRITE, context);
        } catch (final IOException e) {
            System.err.println("Can not create channel");
            return;
        }

        sendLoop(selector, address, prefix, requests);
    }

    private void sendLoop(Selector selector, SocketAddress address, String prefix, int requests) {
        while (!selector.keys().isEmpty()) {
            try {
                selector.select(Util.getSelectTimeout());
            } catch (IOException e) {
                return;
            }

            if (selector.selectedKeys().isEmpty())
                selector.keys().forEach(key -> {
                    if (key.isWritable()) send(prefix, key, address);
                });
            else for (var iter = selector.selectedKeys().iterator(); iter.hasNext(); ) {
                SelectionKey key = iter.next();
                if (key.isWritable()) send(prefix, key, address);
                else if (key.isReadable()) receive(key, requests);
                iter.remove();
            }
        }
    }

    private void receive(SelectionKey key, int requests) {
        ThreadContext context = (ThreadContext) key.attachment();

        String response = Util.receiveInBuffer(key, context.buffer);
        if (response == null) return;

        if (Util.validResponse(context.index, context.request, response)) {
            Util.log("Valid: " + response);
            context.inc();
        } else Util.log("Wrong: " + response);

        if (context.request > requests) Util.closeChannel(key);
        else key.interestOps(SelectionKey.OP_WRITE);
    }

    private void send(String prefix, SelectionKey key, SocketAddress address) {
        ThreadContext context = (ThreadContext) key.attachment();
        String requestBody = prefix + context.index + "_" + context.request;
        DatagramChannel channel = (DatagramChannel) key.channel();

        ByteBuffer buffer = context.buffer;
        buffer.clear();
        try {
            Util.log("Send: " + requestBody);
            channel.send(ByteBuffer.wrap(requestBody.getBytes(Util.getCharset())), address);
        } catch (IOException e) {
            System.err.println("Can not send request");
            return;
        }
        key.interestOps(SelectionKey.OP_READ);
        buffer.flip();
    }

    private static class ThreadContext {
        int index;
        int request;

        ByteBuffer buffer;

        public ThreadContext(int index) {
            this.buffer = ByteBuffer.allocate(Util.getBufferSize());
            this.index = index;
            this.request = 1;
        }

        public void inc() {
            request++;
        }
    }

    public static void main(String... args) {
        Util.runClient(HelloUDPNonblockingClient::new, args);
    }
}
