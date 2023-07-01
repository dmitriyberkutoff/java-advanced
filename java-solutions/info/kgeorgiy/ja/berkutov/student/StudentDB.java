package info.kgeorgiy.ja.berkutov.student;

import info.kgeorgiy.java.advanced.student.Group;
import info.kgeorgiy.java.advanced.student.GroupName;
import info.kgeorgiy.java.advanced.student.GroupQuery;
import info.kgeorgiy.java.advanced.student.Student;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class StudentDB implements GroupQuery {

    private static final Comparator<Map.Entry<GroupName, Integer>> GROUP_HIGH_COMPARATOR =
            Map.Entry.<GroupName, Integer>comparingByValue().
                    thenComparing(Map.Entry.comparingByKey());

    private static final Comparator<Student> NAME_COMPARATOR =
            Comparator.comparing(Student::getLastName)
                    .thenComparing(Student::getFirstName)
                    .reversed()
                    .thenComparing(Student::compareTo);

    private static final Comparator<Map.Entry<GroupName, Integer>> GROUP_LOW_COMPARATOR =
            Map.Entry.<GroupName, Integer>comparingByValue().reversed()
                    .thenComparing(Map.Entry.comparingByKey()).reversed();

    private static Stream<Map.Entry<GroupName, List<Student>>> studentsToGroup(Collection<Student> students) {
        return students.stream()
                .collect(Collectors.groupingBy(
                        Student::getGroup,
                        TreeMap::new,
                        Collectors.toList())).entrySet().stream();
    }

    private static <R> Stream<Map.Entry<GroupName, R>> getMapGroupTo(Collection<Student> students,
                                                              Function<List<Student>, R> func) {
        return studentsToGroup(students).map(o -> Map.entry(o.getKey(), func.apply(o.getValue())));
    }

    private static List<Group> getGroup(Collection<Student> students, Function<List<Student>, List<Student>> func) {
        return getMapGroupTo(students, func)
                .map(e -> new Group(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Group> getGroupsByName(Collection<Student> students) {
        return getGroup(students, this::sortStudentsByName);
    }

    @Override
    public List<Group> getGroupsById(Collection<Student> students) {
        return getGroup(students, this::sortStudentsById);
    }

    private GroupName getLargest(Stream<Map.Entry<GroupName, Integer>> groups,
                                 Comparator<Map.Entry<GroupName, Integer>> cmp) {
        return getElem(groups, cmp, Map.Entry::getKey, null);
    }

    // :NOTE: выглядит очень похоже на getGroup | OK
    @Override
    public GroupName getLargestGroup(Collection<Student> students) {
        return getLargest(getMapGroupTo(students, List::size), GROUP_HIGH_COMPARATOR);
    }

    // :NOTE: выглядит очень похоже на getGroup | OK
    @Override
    public GroupName getLargestGroupFirstName(Collection<Student> students) {
        return getLargest(getMapGroupTo(students, this::getDistinctNamesSize), GROUP_LOW_COMPARATOR);
    }

    private int getDistinctNamesSize(List<Student> students) {
        return getDistinctFirstNames(students).size();
    }

    private <T> List<T> get(List<Student> students, Function<Student, T> func) {
        return students.stream().map(func)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getFirstNames(List<Student> students) {
        return get(students, Student::getFirstName);
    }

    @Override
    public List<GroupName> getGroups(List<Student> students) {
        return get(students, Student::getGroup);
    }

    @Override
    public List<String> getFullNames(List<Student> students) {
        return get(students, s -> String.format("%s %s", s.getFirstName(), s.getLastName()));
    }

    @Override
    public List<String> getLastNames(List<Student> students) {
        return get(students, Student::getLastName);
    }


    // :NOTE: зачем LinkedHashSet? | OK
    // :NOTE: нужна упорядоченная коллекций
    @Override
    public Set<String> getDistinctFirstNames(List<Student> students) {
        return students.stream()
                .map(Student::getFirstName)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private <T, R> R getElem(Stream<T> str, Comparator<T> cmp, Function<T, R> func, R defaultValue) {
        return str.max(cmp).map(func).orElse(defaultValue);
    }

    // :NOTE: очень похоже на getLargest | OK
    @Override
    public String getMaxStudentFirstName(List<Student> students) {
        return getElem(students.stream(), Student::compareTo, Student::getFirstName, "");
    }

    private List<Student> sortStudent(Collection<Student> students, Comparator<Student> comparator) {
        return students.stream().sorted(comparator).collect(Collectors.toList());
    }

    @Override
    public List<Student> sortStudentsById(Collection<Student> students) {
        return sortStudent(students, Student::compareTo);
    }

    @Override
    public List<Student> sortStudentsByName(Collection<Student> students) {
        return sortStudent(students, NAME_COMPARATOR);
    }

    @Override
    public List<Student> findStudentsByFirstName(Collection<Student> students, String name) {
        return findStudentsBy(name, students, Student::getFirstName);
    }

    // :NOTE: все предикаты имеют общий вид | OK
    private List<Student> findStudentsBy(Object name, Collection<Student> students, Function<Student, Object> func) {
        return students.stream()
                .filter(s -> func.apply(s).equals(name))
                .sorted(NAME_COMPARATOR)
                .collect(Collectors.toList());
    }

    @Override
    public List<Student> findStudentsByLastName(Collection<Student> students, String name) {
        return findStudentsBy(name, students, Student::getLastName);
    }

    @Override
    public Map<String, String> findStudentNamesByGroup(Collection<Student> students, GroupName group) {
        return findStudentsByGroup(students, group).stream().collect(Collectors.toMap(
                        Student::getLastName, Student::getFirstName,
                        BinaryOperator.minBy(String::compareTo)));
    }

    @Override
    public List<Student> findStudentsByGroup(Collection<Student> students, GroupName group) {
        return findStudentsBy(group, students, Student::getGroup);
    }
}
