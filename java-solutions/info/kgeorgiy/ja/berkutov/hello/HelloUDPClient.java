package info.kgeorgiy.ja.berkutov.hello;

import info.kgeorgiy.java.advanced.hello.HelloClient;

import java.net.*;
import java.io.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class HelloUDPClient implements HelloClient {
    private void sendRequest(SocketAddress address, String prefix, int thread, int requests) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(50);
            for (int i = 1; i <= requests; i++) {
                String requestBody = prefix + thread + "_" + i;
                while (!socket.isClosed()) try {
                    String response = Util.sendAndReceive(requestBody, socket, address);
                    if (Util.validResponse(thread, i, response)) {
                        Util.log("Valid: " + response);
                        break;
                    } else Util.log("Wrong: " + response);
                } catch (IOException e) {
                    System.err.println("Error during the receiving response");
                }

            }
        } catch (SocketException e) {
            System.err.println("Can not create socket");
        }
    }

    @Override
    public void run(String host, int port, String prefix, int threads, int requests) {
        final SocketAddress address = Util.getAddress(host, port);
        if (address == null) return;

        final ExecutorService threadPool = Executors.newFixedThreadPool(threads);
        // :NOTE: можно IntStream | OK
        IntStream.range(1, threads + 1).forEach(i -> threadPool.submit(() -> sendRequest(address, prefix, i, requests)));
        Util.awaitPool(threadPool, threads * requests);
    }

    public static void main(String... args) {
        Util.runClient(HelloUDPClient::new, args);
    }
}

