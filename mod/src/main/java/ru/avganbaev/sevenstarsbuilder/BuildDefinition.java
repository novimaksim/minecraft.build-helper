package ru.avganbaev.sevenstarsbuilder;

import net.minecraft.core.BlockPos;

import java.util.*;

public final class BuildDefinition {
    public record Op(int x1, int y1, int z1, int x2, int y2, int z2, String block) {}
    public record Cell(int x, int y, int z, String block) {}

    private static final String AIR = "minecraft:air";
    private static final String[] DIRS = {"north", "east", "south", "west"};

    private final String name;
    private final int sx, sz;
    private final List<Op> ops;
    private final Map<Integer, List<Cell>> cache = new HashMap<>();

    public BuildDefinition(String name, List<Op> ops) {
        this.name = name;
        this.ops = List.copyOf(ops);
        int mx = 1, mz = 1;
        for (Op o : ops) {
            mx = Math.max(mx, Math.max(o.x1(), o.x2()) + 1);
            mz = Math.max(mz, Math.max(o.z1(), o.z2()) + 1);
        }
        this.sx = mx;
        this.sz = mz;
    }

    public String name() { return name; }
    public int opCount() { return ops.size(); }

    private int[] rp(int x, int z, int r) {
        return switch (r & 3) {
            case 1 -> new int[]{sz - 1 - z, x};
            case 2 -> new int[]{sx - 1 - x, sz - 1 - z};
            case 3 -> new int[]{z, sx - 1 - x};
            default -> new int[]{x, z};
        };
    }

    private static String rotBlock(String b, int r) {
        r &= 3;
        if (r == 0) return b;
        int i = b.indexOf("facing=");
        if (i < 0) return b;
        for (int d = 0; d < 4; d++) {
            String key = "facing=" + DIRS[d];
            if (b.startsWith(key, i)) return b.substring(0, i) + "facing=" + DIRS[(d + r) & 3] + b.substring(i + key.length());
        }
        return b;
    }

    public List<Op> rotatedOps(int r) {
        List<Op> out = new ArrayList<>();
        for (Op o : ops) {
            int[] a = rp(o.x1(), o.z1(), r), c = rp(o.x2(), o.z2(), r);
            out.add(new Op(Math.min(a[0], c[0]), Math.min(o.y1(), o.y2()), Math.min(a[1], c[1]),
                    Math.max(a[0], c[0]), Math.max(o.y1(), o.y2()), Math.max(a[1], c[1]), rotBlock(o.block(), r)));
        }
        return out;
    }

    public List<Cell> cells(int r) {
        return cache.computeIfAbsent(r & 3, k -> {
            Map<Long, String> m = new LinkedHashMap<>();
            for (Op o : rotatedOps(k))
                for (int x = o.x1(); x <= o.x2(); x++)
                    for (int y = o.y1(); y <= o.y2(); y++)
                        for (int z = o.z1(); z <= o.z2(); z++) {
                            long key = BlockPos.asLong(x, y, z);
                            if (AIR.equals(o.block())) m.remove(key); else m.put(key, o.block());
                        }
            List<Cell> out = new ArrayList<>();
            for (Map.Entry<Long, String> e : m.entrySet()) {
                BlockPos p = BlockPos.of(e.getKey());
                int x = p.getX(), y = p.getY(), z = p.getZ();
                boolean exposed = !m.containsKey(BlockPos.asLong(x + 1, y, z)) || !m.containsKey(BlockPos.asLong(x - 1, y, z))
                        || !m.containsKey(BlockPos.asLong(x, y + 1, z)) || !m.containsKey(BlockPos.asLong(x, y - 1, z))
                        || !m.containsKey(BlockPos.asLong(x, y, z + 1)) || !m.containsKey(BlockPos.asLong(x, y, z - 1));
                if (exposed) out.add(new Cell(x, y, z, e.getValue()));
            }
            return out;
        });
    }

    public List<String> commands(BlockPos o, int r) {
        List<String> out = new ArrayList<>();
        for (Op p : rotatedOps(r)) {
            int x1 = o.getX() + p.x1(), y1 = o.getY() + p.y1(), z1 = o.getZ() + p.z1();
            int x2 = o.getX() + p.x2(), y2 = o.getY() + p.y2(), z2 = o.getZ() + p.z2();
            if (x1 == x2 && y1 == y2 && z1 == z2) out.add("setblock " + x1 + " " + y1 + " " + z1 + " " + p.block());
            else out.add("fill " + x1 + " " + y1 + " " + z1 + " " + x2 + " " + y2 + " " + z2 + " " + p.block());
        }
        return out;
    }
}
