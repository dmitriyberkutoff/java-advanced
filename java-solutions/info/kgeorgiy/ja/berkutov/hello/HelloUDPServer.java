package info.kgeorgiy.ja.berkutov.hello;

import info.kgeorgiy.java.advanced.hello.HelloServer;

import java.net.*;
import java.io.*;
import java.util.concurrent.*;

public class HelloUDPServer implements HelloServer {

    DatagramSocket socket;
    ExecutorService threadPool;
    final ExecutorService singleListener = Executors.newSingleThreadExecutor();

    @Override
    public void start(int port, int threads) {
        try {
            threadPool = Util.getThreadPool(threads);
            socket = new DatagramSocket(port);
            singleListener.submit(this::receiveLoop);
        } catch (SocketException e) {
            System.err.println("Can not create socket on this port: " + port);
        }
    }

    private void receiveLoop() {
        while (!socket.isClosed()) try {
            DatagramPacket request = Util.receive(socket);
            threadPool.submit(() -> {
                try {
                    Util.send(socket, Util.responseBody(Util.stringFromPacket(request)), request.getSocketAddress());
                } catch (IOException e) {
                    System.err.println("Can not send response.");
                }
            });
        } catch (IOException e) {
            if (!socket.isClosed()) System.err.println("Error during the receiving.");
        }
    }

    @Override
    public void close() {
        socket.close();
        Util.awaitPool(threadPool, 3);
        Util.awaitPool(singleListener, 3);
    }

    public static void main(String... args) {
        Util.startServer(HelloUDPServer::new, args);
    }
}

