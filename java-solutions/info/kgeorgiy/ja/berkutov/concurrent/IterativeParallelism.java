package info.kgeorgiy.ja.berkutov.concurrent;

import info.kgeorgiy.java.advanced.concurrent.ListIP;
import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Class for parallel processing of {@link List}.
 */
public class IterativeParallelism implements ListIP {
    private final ParallelMapper mapper;

    public IterativeParallelism() {
        mapper = null;
    }

    public IterativeParallelism(ParallelMapper mapper) {
        this.mapper = mapper;
    }
    @Override
    public String join(int threads, List<?> values) throws InterruptedException {
        return startTask(threads, values,
                vals -> vals.map(Object::toString).collect(Collectors.joining()),
                vals -> vals.collect(Collectors.joining()));
    }

    @Override
    public <T> List<T> filter(int threads, List<? extends T> values, Predicate<? super T> predicate)
            throws InterruptedException {
        return startTask(threads, values,
                vals -> vals.filter(predicate).collect(Collectors.toList()),
                vals -> vals.flatMap(List::stream).collect(Collectors.toList()));
    }

    @Override
    public <T> T maximum(int threads, List<? extends T> values, Comparator<? super T> comparator)
            throws InterruptedException {
        return minimum(threads, values, comparator.reversed());
    }

    @Override
    public <T, U> List<U> map(int threads, List<? extends T> values, Function<? super T, ? extends U> f)
            throws InterruptedException {
        return startTask(threads, values,
                vals -> vals.map(f).collect(Collectors.toList()),
                vals -> vals.flatMap(List::stream).collect(Collectors.toList()));
    }

    @Override
    public <T> boolean any(int threads, List<? extends T> values, Predicate<? super T> predicate)
            throws InterruptedException {
        return startTask(threads, values,
                vals -> vals.anyMatch(predicate),
                vals -> vals.anyMatch(b -> b));
    }

    @Override
    public <T> T minimum(int threads, List<? extends T> values, Comparator<? super T> comparator)
            throws InterruptedException {
        Function<Stream<? extends T>, T> min =
                vals -> vals.min(comparator).orElse(null);
        return startTask(threads, values, min, min::apply);
    }

    @Override
    public <T> boolean all(int threads, List<? extends T> values, Predicate<? super T> predicate)
            throws InterruptedException {
        return !any(threads, values, Predicate.not(predicate));
    }

    @Override
    public <T> int count(int threads, List<? extends T> values, Predicate<? super T> predicate)
            throws InterruptedException {
        return filter(threads, values, predicate).size();
    }

    private <T> List<Stream<? extends T>> splitData(int threads, List<? extends T> values) {
        int valuesSize = values.size();
        int blockSize = valuesSize / threads;
        int dif = valuesSize % threads;

        List<Stream<? extends T>> blocksOfData = new ArrayList<>();

        int l = 0;
        for (int i = 0; i < Math.min(valuesSize, threads); i++) {
            int r = l + blockSize + (dif > 0 ? 1 : 0);
            dif -= 1;
            blocksOfData.add(values.subList(l, r).stream());
            l = r;
        }

        return blocksOfData;
    }

    private void joinAllThreads(List<Thread> threads) throws InterruptedException {
        for (int i = 0; i < threads.size(); i++) try {
            threads.get(i).join();
        } catch (InterruptedException e) {
            for (int j = i; j < threads.size(); j++) {
                Thread curThread = threads.get(j);
                curThread.interrupt();
                try {
                    curThread.join();
                } catch (InterruptedException sup) {
                    e.addSuppressed(sup);
                }
            }
            throw e;
        }
        // :NOTE: интеррапт всех потоков и джоин
    }

    private <T, P, R> R startTask(int numberThreads, List<? extends T> values,
                                  Function<Stream<? extends T>, P> func,
                                  Function<Stream<P>, R> collectFunc) throws InterruptedException {
        if (numberThreads < 1) throw new IllegalArgumentException("Number of threads should be at least 1.");

        final List<Stream<? extends T>> splitted = splitData(numberThreads, values);
        final List<Thread> threads = new ArrayList<>();
        final List<P> results;

        if (mapper == null) {
            results = new ArrayList<>(Collections.nCopies(splitted.size(), null));
            IntStream.range(0, splitted.size()).forEach(ind -> {
                Thread thread = new Thread(() -> results.set(ind, func.apply(splitted.get(ind))));
                threads.add(thread);
                thread.start();
            });
        } else results = mapper.map(func, splitted);

        joinAllThreads(threads);
        return collectFunc.apply(results.stream());
    }
}
