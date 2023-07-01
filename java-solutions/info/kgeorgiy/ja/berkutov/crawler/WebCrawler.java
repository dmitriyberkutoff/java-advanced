package info.kgeorgiy.ja.berkutov.crawler;

import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

public class WebCrawler implements Crawler {
    private final Map<String, HostTasks> hosts = new ConcurrentHashMap<>();
    private final Downloader downloader;
    private final ExecutorService downloaders;
    private final ExecutorService extractors;
    private final int perHost;

    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        this.downloader = downloader;
        this.perHost = perHost;
        this.extractors = Executors.newFixedThreadPool(extractors);
        this.downloaders = Executors.newFixedThreadPool(downloaders);
    }

    private class DownloadTask {
        private final Phaser phaser = new Phaser(1);
        private final Set<String> done = ConcurrentHashMap.newKeySet();
        private final Map<String, IOException> errors = new ConcurrentHashMap<>();

        private final Queue<String> nextStep = new ConcurrentLinkedQueue<>();
        private final int depth;

        public DownloadTask(String url, int depth) {
            nextStep.add(url);
            this.depth = depth;
        }

        public Result run() {
            IntStream.range(0, depth).forEach(i ->  {
                final int curDepth = depth - i;
                final List<String> currentStep = List.copyOf(nextStep);
                nextStep.clear();
                currentStep.stream().filter(done::add)
                        .forEach(u -> downloadUrl(u, curDepth));
                phaser.arriveAndAwaitAdvance();
            });
            done.removeAll(errors.keySet());
            return new Result(List.copyOf(done), errors);
        }

        private void downloadUrl(String url, int depth) {
            HostTasks host;
            try {
                host = hosts.computeIfAbsent(URLUtils.getHost(url), u -> new HostTasks());
            } catch (IOException e) {
                errors.put(url, e);
                return;
            }
            phaser.register();
            host.push(() -> {
                try {
                    Document document = downloader.download(url);
                    if (depth >= 2) {
                        phaser.register();
                        extractors.submit(() -> {
                            try {
                                nextStep.addAll(document.extractLinks());
                            } catch (IOException e) {
                                System.err.println("Can not extract links from " + url);
                            } finally {
                                phaser.arriveAndDeregister();
                            }
                        });
                    }
                } catch (IOException e) {
                    errors.put(url, e);
                } finally {
                    phaser.arriveAndDeregister();
                }
            });
        }
    }

    @Override
    public Result download(String url, int depth) {
        return new DownloadTask(url, depth).run();
    }

    private class HostTasks {
        private final Queue<Runnable> tasks = new LinkedList<>();
        private int processing = 0;

        public synchronized void push(Runnable hostTask) {
            tasks.add(hostTask);
            pollAndStart();
        }

        public synchronized void pollAndStart() {
            if (processing < perHost) {
                Runnable task = tasks.poll();
                if (task == null) return;
                ++processing;
                downloaders.submit(() -> {
                    task.run();
                    --processing;
                    pollAndStart();
                });
            }
        }
    }

    @Override
    public void close() {
        downloaders.shutdown();
        extractors.shutdown();
    }

    private static int get(int ind, String[] args) throws NumberFormatException {
        if (ind >= args.length) return 1;
        return Integer.parseInt(args[ind]);
    }


    public static void main(String[] args) {
        if (args == null) {
            System.err.println("Expected non null arguments");
            return;
        }
        if (args.length == 0) {
            System.err.println("At least one argument is required.");
            return;
        }
        try {
            int depth = get(1, args);
            int downloaders = get(2, args);
            int extractors = get(3, args);
            int perHost = get(4, args);
            try (WebCrawler crawler = new WebCrawler(new CachingDownloader(10), downloaders, extractors, perHost)) {
                crawler.download(args[0], depth);
            } catch (IOException e) {
                System.err.println("Error during the creating Downloader");
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid format of number arguments");
        }
    }
}
