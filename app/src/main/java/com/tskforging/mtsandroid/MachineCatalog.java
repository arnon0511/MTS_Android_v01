package com.tskforging.mtsandroid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Machine/work-center master for the first BT-A2000 rollout. */
public final class MachineCatalog {
    public static final String CUTTING_1 = "Cutting 1";
    public static final String CUTTING_2 = "Cutting 2";
    public static final String CHAMFER_SLUGNUT = "Chamfer Slugnut 3MC";
    public static final String CHAMFER = "Chamfer / Hand Chamfer";
    public static final String BENDING = "Bending";
    public static final String OTHER = "Other";

    public static final class Machine {
        public final String id;
        public final String group;
        public final String planGroup;
        public final boolean bladeEnabled;
        public final boolean machineQrRequired;

        Machine(String id, String group, String planGroup, boolean bladeEnabled,
                boolean machineQrRequired) {
            this.id = id;
            this.group = group;
            this.planGroup = planGroup;
            this.bladeEnabled = bladeEnabled;
            this.machineQrRequired = machineQrRequired;
        }
    }

    private static final List<Machine> ALL;
    static {
        List<Machine> list = new ArrayList<>();
        addCutting(list, CUTTING_1, "SC12", "SC16", "SC25", "SC26", "SC27", "SC28");
        addCutting(list, CUTTING_2, "SC13", "SC15", "SC17", "SC18", "SC19", "SC20",
                "SC21", "SC22", "SC23", "SC24", "CP2", "M069");

        for (String id : Arrays.asList("CH5", "CH6", "CH8"))
            list.add(new Machine(id, CHAMFER_SLUGNUT, "Chamfer Slugnut", false, true));
        for (String id : Arrays.asList("CH10", "CH11", "CH2", "CH4", "CH7"))
            list.add(new Machine(id, CHAMFER, "Chamfer", false, true));
        list.add(new Machine("HAND_CHAMFER", CHAMFER, "Hand Chamfer", false, false));

        list.add(new Machine("BENDING", BENDING, "Bending", false, false));
        list.add(new Machine("SCREENING", OTHER, "Other", false, false));
        list.add(new Machine("CHECK_RUN_OUT", OTHER, "Other", false, false));
        list.add(new Machine("REPAIR", OTHER, "Other", false, false));
        ALL = Collections.unmodifiableList(list);
    }

    private static void addCutting(List<Machine> target, String group, String... ids) {
        for (String id : ids) {
            String planGroup = Arrays.asList("SC21", "SC22", "SC23", "SC24").contains(id)
                    ? "Cut Rack Bar" : "Cut Slug Part";
            target.add(new Machine(id, group, planGroup, true, true));
        }
    }

    public static List<Machine> all() { return ALL; }

    public static List<String> groups() {
        return Arrays.asList(CUTTING_1, CUTTING_2, CHAMFER_SLUGNUT, CHAMFER, BENDING, OTHER);
    }

    public static List<Machine> byGroup(String group) {
        List<Machine> out = new ArrayList<>();
        for (Machine m : ALL) if (m.group.equals(group)) out.add(m);
        return out;
    }

    public static Machine find(String id) {
        if (id == null) return null;
        for (Machine m : ALL) if (m.id.equalsIgnoreCase(id.trim())) return m;
        return null;
    }

    private MachineCatalog() {}
}
