package info.kgeorgiy.ja.berkutov.arrayset;

import java.util.*;

public class ArraySet<E> extends AbstractSet<E> implements NavigableSet<E> {
    private final Comparator<? super E> comparator;
    private final OrderedList<E> list;

    private ArraySet(OrderedList<E> list, Comparator<? super E> comp) {
        this.list = list;
        comparator = comp;
    }

    public ArraySet() {
        this(new ArrayList<>());
    }

    public ArraySet(Collection<E> collection) {
        this(new ArrayList<>(collection), null);
    }

    public ArraySet(Comparator<? super E> comparator) {
        this(new ArrayList<>(), comparator);
    }

    public ArraySet(Collection<E> collection, Comparator<? super E> comparator) {
        this.comparator = comparator;
        Set<E> set = new TreeSet<>(this.comparator);
        set.addAll(collection);
        this.list = new OrderedList<>(List.copyOf(set), false);
    }

    private ArraySet<E> emptySet() {
        return new ArraySet<>(comparator);
    }

    private boolean invalidIndex(int ind) {
        return ind < 0 || ind >= size();
    }

    private int shiftedSearch(E el, boolean inc, int shift1, int shift2) {
        int ind = Collections.binarySearch(list, Objects.requireNonNull(el), comparator);
        if (ind < 0) return -ind - shift2 - 1;
        if (inc && comparator.compare(el, list.get(ind)) == 0) return ind;
        return ind + shift1;
    }

    private int less_or_equals(E el, boolean inc) {
        return shiftedSearch(el, inc, -1, 1);
    }

    private int more_or_equals(E el, boolean inc) {
        return shiftedSearch(el, inc, 1, 0);
    }

    private E safeGet(int ind) {
        if (invalidIndex(ind)) return null;
        return list.get(ind);
    }

    @Override
    public E lower(E o) {
        return safeGet(less_or_equals(o, false));
    }

    @Override
    public E floor(E o) {
        return safeGet(less_or_equals(o, true));
    }

    @Override
    public NavigableSet<E> descendingSet() {
        return new ArraySet<>(list.getReverse(), Collections.reverseOrder(comparator));
    }

    @Override
    public E ceiling(E o) {
        return safeGet(more_or_equals(o, true));
    }

    @Override
    public E higher(E o) {
        return safeGet(more_or_equals(o, false));
    }

    @Override
    public Iterator<E> descendingIterator() {
        return descendingSet().iterator();
    }

    private ArraySet<E> mySubSet(int from, int to) {
        return new ArraySet<>(list.subList(from, to + 1), comparator);
    }

    @Override
    public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
        if (bigger(fromElement, toElement)) throw new IllegalArgumentException();

        int l = more_or_equals(fromElement, fromInclusive);
        int r = less_or_equals(toElement, toInclusive);

        if (r < l) return emptySet();
        return mySubSet(l, r);
    }

    @Override
    public NavigableSet<E> headSet(E toElement, boolean inclusive) {
        if (isEmpty() || bigger(first(), toElement)) return emptySet();

        int r = less_or_equals(toElement, inclusive);
        return mySubSet(0, r);
    }

    @Override
    public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
        if (isEmpty() || bigger(fromElement, last())) return emptySet();

        int l = more_or_equals(fromElement, inclusive);
        return mySubSet(l, size() - 1);
    }

    @Override
    public Comparator<? super E> comparator() {
        return comparator;
    }

    @SuppressWarnings("unchecked")
    private boolean bigger(E lhs, E rhs) {
        return comparator != null
                ? comparator.compare(lhs, rhs) > 0
                : ((Comparable<? super E>) lhs).compareTo(rhs) > 0;
    }

    @Override
    public SortedSet<E> subSet(E fromElement, E toElement) {
        return subSet(fromElement, true,
                toElement, false);
    }

    @Override
    public SortedSet<E> tailSet(E fromElement) {
        return tailSet(fromElement, true);
    }

    @Override
    public E last() {
        if (isEmpty()) throw new NoSuchElementException();
        return list.get(size() - 1);
    }

    @Override
    public SortedSet<E> headSet(E toElement) {
        return headSet(toElement, false);
    }

    @Override
    public int size() {
        return list.size();
    }

    @Override
    public Iterator<E> iterator() {
        return Collections.unmodifiableList(list).iterator();
    }

    @Override
    public E first() {
        if (isEmpty()) throw new NoSuchElementException();
        return list.get(0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean contains(Object o) {
        return Collections.binarySearch(list, (E) Objects.requireNonNull(o), comparator) > -1;
    }

    @Override
    public E pollFirst() {
        throw new UnsupportedOperationException();
    }

    @Override
    public E pollLast() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(Object o) {
        throw new UnsupportedOperationException();
    }

    private static class OrderedList<E> extends AbstractList<E> implements RandomAccess {
        private final Boolean reverse;
        private final ArrayList<E> list;

        public OrderedList(List<E> list, boolean reverse) {
            this.list = new ArrayList<>(list);
            this.reverse = reverse;
        }

        public OrderedList<E> getReverse() {
            return new OrderedList<>(list, !reverse);
        }

        public E get(int ind) {
            return list.get(!reverse ? ind : list.size() - ind - 1);
        }

        @Override
        public int size() {
            return list.size();
        }
    }
}