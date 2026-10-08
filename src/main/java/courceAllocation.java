import java.util.*;

/**
 * VTOP-style slot-wise course registration (console).
 * Checks: duplicate course, faculty seat limit, slot clash, credit limit.
 * Compile: javac CourseAllocation.java   Run: java CourseAllocation
 * NOTE: the slot -> period mapping below is simplified; edit SLOTS to match your timetable.
 */
public class CourseAllocation {

    // ---------- Slot -> timetable periods (DAY-PERIOD). Shared periods = clash ----------
    static final Map<String, Set<String>> SLOTS = new LinkedHashMap<>();
    static {
        slot("A1", "MON-1", "WED-2");   slot("B1", "TUE-1", "THU-2");
        slot("C1", "WED-1", "FRI-2");   slot("D1", "THU-1", "MON-3");
        slot("E1", "FRI-1", "TUE-3");   slot("F1", "MON-2", "WED-3");
        slot("G1", "TUE-2", "THU-3");
        slot("TA1", "FRI-3");           slot("TB1", "MON-4");
        slot("TC1", "WED-4");           slot("TD1", "FRI-4");
        slot("A2", "MON-5", "WED-6");   slot("B2", "TUE-5", "THU-6");
        slot("C2", "WED-5", "FRI-6");   slot("D2", "THU-5", "MON-6");
        // Labs (2 consecutive periods) - clash with theory slots sharing a period
        slot("L1", "MON-1", "MON-2");   slot("L3", "TUE-1", "TUE-2");
        slot("L5", "WED-1", "WED-2");   slot("L7", "THU-1", "THU-2");
        slot("L31", "MON-5", "MON-6");  slot("L33", "TUE-5", "TUE-6");
    }
    static void slot(String name, String... periods) {
        SLOTS.put(name, new LinkedHashSet<>(Arrays.asList(periods)));
    }

    // ---------- Models ----------
    static class Course {
        final String code, title; final int credits;
        Course(String code, String title, int credits) {
            this.code = code; this.title = title; this.credits = credits;
        }
    }

    static class Offering { // one course + faculty + slot + seat limit
        final int id; final Course course; final String faculty, slot; final int capacity;
        final List<String> enrolled = new ArrayList<>();
        Offering(int id, Course c, String faculty, String slot, int capacity) {
            this.id = id; this.course = c; this.faculty = faculty; this.slot = slot; this.capacity = capacity;
        }
        int seatsLeft() { return capacity - enrolled.size(); }
        public String toString() {
            return String.format("[%d] %-8s Faculty: %-14s Slot: %-4s Seats: %d/%d",
                    id, course.code, faculty, slot, seatsLeft(), capacity);
        }
    }

    static class Student {
        final String regNo, name; final int maxCredits;
        final List<Offering> registered = new ArrayList<>();
        Student(String regNo, String name, int maxCredits) {
            this.regNo = regNo; this.name = name; this.maxCredits = maxCredits;
        }
        int credits() { int s = 0; for (Offering o : registered) s += o.course.credits; return s; }
    }

    // ---------- Registry ----------
    static final Map<String, Course> courses = new LinkedHashMap<>();
    static final Map<Integer, Offering> offerings = new LinkedHashMap<>();
    static final Map<String, Student> students = new HashMap<>();
    static int nextId = 1;

    static void addOffering(Course c, String faculty, String slot, int cap) {
        if (!SLOTS.containsKey(slot)) throw new IllegalArgumentException("Unknown slot " + slot);
        offerings.put(nextId, new Offering(nextId, c, faculty, slot, cap));
        nextId++;
    }

    static List<Offering> offeringsFor(String code) {
        List<Offering> r = new ArrayList<>();
        for (Offering o : offerings.values()) if (o.course.code.equalsIgnoreCase(code)) r.add(o);
        return r;
    }

    /** Returns the clashing offering, or null if none. */
    static Offering findClash(Student s, Offering target) {
        Set<String> t = SLOTS.get(target.slot);
        for (Offering o : s.registered)
            for (String p : SLOTS.get(o.slot)) if (t.contains(p)) return o;
        return null;
    }

    static synchronized String register(Student s, int offeringId) {
        Offering o = offerings.get(offeringId);
        if (o == null) return "FAILED: invalid offering id.";
        for (Offering r : s.registered) {
            if (r.id == o.id) return "FAILED: already registered in this offering.";
            if (r.course.code.equals(o.course.code))
                return "FAILED: " + o.course.code + " already registered with " + r.faculty + " (" + r.slot + "). Drop it first to change.";
        }
        if (o.seatsLeft() <= 0) return "FAILED: seats full for " + o.faculty + " (" + o.course.code + ", slot " + o.slot + ").";
        Offering clash = findClash(s, o);
        if (clash != null)
            return "FAILED: slot clash - " + o.slot + " overlaps " + clash.slot + " (" + clash.course.code + ").";
        if (s.credits() + o.course.credits > s.maxCredits)
            return "FAILED: credit limit exceeded (" + s.credits() + "+" + o.course.credits + " > " + s.maxCredits + ").";
        o.enrolled.add(s.regNo);
        s.registered.add(o);
        return "SUCCESS: registered " + o.course.code + " | " + o.faculty + " | " + o.slot;
    }

    static synchronized String drop(Student s, String code) {
        Iterator<Offering> it = s.registered.iterator();
        while (it.hasNext()) {
            Offering o = it.next();
            if (o.course.code.equalsIgnoreCase(code)) {
                o.enrolled.remove(s.regNo);
                it.remove();
                return "Dropped " + o.course.code + " (" + o.faculty + ", " + o.slot + ").";
            }
        }
        return "FAILED: not registered in " + code + ".";
    }

    // ---------- Display ----------
    static void printTimetable(Student s) {
        String[] days = {"MON", "TUE", "WED", "THU", "FRI"};
        Map<String, String> grid = new HashMap<>();
        for (Offering o : s.registered)
            for (String p : SLOTS.get(o.slot)) grid.put(p, o.course.code);
        System.out.printf("%-5s", "");
        for (int p = 1; p <= 6; p++) System.out.printf("%-10s", "P" + p);
        System.out.println();
        for (String d : days) {
            System.out.printf("%-5s", d);
            for (int p = 1; p <= 6; p++) System.out.printf("%-10s", grid.getOrDefault(d + "-" + p, "-"));
            System.out.println();
        }
        System.out.println("Credits: " + s.credits() + "/" + s.maxCredits);
    }

    static void seed() {
        Course cse1 = new Course("CSE2001", "Data Structures", 4);
        Course swe1 = new Course("SWE1001", "Software Engg", 3);
        Course mat1 = new Course("MAT2001", "Statistics", 3);
        Course phy1 = new Course("PHY1001", "Physics", 3);
        Course eng1 = new Course("ENG1001", "Tech English", 2);
        Course lab1 = new Course("CSE2001L", "DS Lab", 1);
        for (Course c : new Course[]{cse1, swe1, mat1, phy1, eng1, lab1}) courses.put(c.code, c);

        addOffering(cse1, "Dr. Ramesh", "A1", 2);
        addOffering(cse1, "Dr. Priya", "B1", 3);
        addOffering(swe1, "Dr. Kumar", "A1", 3);   // clashes with CSE2001/A1
        addOffering(swe1, "Dr. Anitha", "C1", 3);
        addOffering(mat1, "Dr. Suresh", "D1", 2);
        addOffering(mat1, "Dr. Latha", "E1", 2);
        addOffering(phy1, "Dr. Mohan", "F1", 3);
        addOffering(eng1, "Dr. Sheela", "TA1", 3);
        addOffering(lab1, "Dr. Ramesh", "L1", 2);  // clashes with A1/F1
        addOffering(lab1, "Dr. Priya", "L31", 2);
    }

    // ---------- Main ----------
    public static void main(String[] args) {
        seed();
        Scanner sc = new Scanner(System.in);
        System.out.println("=== Course Registration ===");
        System.out.print("Reg No: "); String reg = sc.nextLine().trim().toUpperCase();
        Student st = students.get(reg);
        if (st == null) {
            System.out.print("Name: "); String nm = sc.nextLine().trim();
            st = new Student(reg, nm, 27);
            students.put(reg, st);
        }
        System.out.println("Welcome, " + st.name);

        while (true) {
            System.out.println("\n1.List courses  2.Faculty/slots for a course  3.Register  4.Drop"
                    + "  5.My courses  6.Timetable  7.Slot list  0.Exit");
            System.out.print("> ");
            String ch = sc.nextLine().trim();
            switch (ch) {
                case "1":
                    for (Course c : courses.values())
                        System.out.printf("%-9s %-18s Credits: %d%n", c.code, c.title, c.credits);
                    break;
                case "2": {
                    System.out.print("Course code: ");
                    List<Offering> l = offeringsFor(sc.nextLine().trim());
                    if (l.isEmpty()) System.out.println("No offerings found.");
                    for (Offering o : l) System.out.println(o);
                    break;
                }
                case "3": {
                    System.out.print("Course code: ");
                    List<Offering> l = offeringsFor(sc.nextLine().trim());
                    if (l.isEmpty()) { System.out.println("No offerings found."); break; }
                    for (Offering o : l) System.out.println(o);
                    System.out.print("Choose offering id (slot+faculty): ");
                    try {
                        System.out.println(register(st, Integer.parseInt(sc.nextLine().trim())));
                    } catch (NumberFormatException e) { System.out.println("Invalid id."); }
                    break;
                }
                case "4":
                    System.out.print("Course code to drop: ");
                    System.out.println(drop(st, sc.nextLine().trim()));
                    break;
                case "5":
                    if (st.registered.isEmpty()) System.out.println("Nothing registered.");
                    for (Offering o : st.registered)
                        System.out.printf("%-9s %-18s %-14s %-4s %d cr%n",
                                o.course.code, o.course.title, o.faculty, o.slot, o.course.credits);
                    System.out.println("Total credits: " + st.credits() + "/" + st.maxCredits);
                    break;
                case "6": printTimetable(st); break;
                case "7":
                    for (Map.Entry<String, Set<String>> e : SLOTS.entrySet())
                        System.out.println(e.getKey() + " -> " + e.getValue());
                    break;
                case "0": System.out.println("Bye."); return;
                default: System.out.println("Invalid choice.");
            }
        }
    }
}