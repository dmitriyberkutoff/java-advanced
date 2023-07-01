package info.kgeorgiy.ja.berkutov.concurrent;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

public class ParallelMapperImpl implements ParallelMapper {
    private final List<Thread> threadList = new ArrayList<>();

    private final Queue<Runnable> taskQueue = new ArrayDeque<>();
    private boolean closed = false;

    public ParallelMapperImpl(int threads) {
        if (threads < 1) throw new IllegalArgumentException("Number of threads should be at least 1");
        Runnable workForThread = () -> {
            try {
                while (!Thread.interrupted()) getTaskAndRun();
            } catch (InterruptedException ignored) {
            }
        };
        IntStream.range(0, threads).forEach(i -> {
            Thread t = new Thread(workForThread);
            threadList.add(t);
            t.start();
        });
    }

    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> f, List<? extends T> args) throws InterruptedException {
        final List<RuntimeException> exs = new ArrayList<>();
        Results<R> results = new Results<>(args.size());

        if (!closed) {
            IntStream.range(0, args.size()).forEach(index -> {
                synchronized (taskQueue) {
                    taskQueue.add(() -> {
                        try {
                            results.set(index, f.apply(args.get(index)));
                        } catch (RuntimeException e) {
                            exs.add(e);
                        }
                    });
                    taskQueue.notifyAll();
                }
            });
        }
        if (!exs.isEmpty()) throw getCommon(exs);

        return results.get();
    }

    private RuntimeException getCommon(List<RuntimeException> exs) {
        RuntimeException common = exs.get(0);
        IntStream.range(1, exs.size()).forEach(i -> common.addSuppressed(exs.get(i)));
        return common;
    }

    @Override
    public void close() {
        for (Thread thread : threadList) {
            thread.interrupt();
            try {
                thread.join();
            } catch (InterruptedException ignored) {
            }
        }
        closed = true;
    }

    private static class Results<R> {
        private final List<R> res;
        private int done = 0;

        public Results(int size) {
            res = new ArrayList<>(Collections.nCopies(size, null));
        }

        public synchronized List<R> get() throws InterruptedException {
            while (done < res.size()) wait(0L, 0);
            return res;
        }

        public synchronized void set(int ind, R result) {
            res.set(ind, result);
            if (done++ == res.size()-1) notify();
        }
    }

    private void getTaskAndRun() throws InterruptedException {
        Runnable task;
        synchronized (taskQueue) {
            while (taskQueue.isEmpty()) taskQueue.wait();
            task = taskQueue.poll();
        }
        task.run();
    }
}
