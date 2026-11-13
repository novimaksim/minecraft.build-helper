package ru.avganbaev.sevenstarsbuilder;

import net.minecraft.core.BlockPos;

import java.util.*;

public final class BuildDefinition {
    public record Op(boolean fill, int x1, int y1, int z1, int x2, int y2, int z2, String block) {}
    public record Cell(int x, int y, int z, String block) {}

    private static final String AIR = "minecraft:air";
    private static final String[] DIRS = {"north", "east", "south", "west"};

    private final String name;
    private final int sx, sz;
    private final List<Op> ops = new ArrayList<>();
    private final Map<Integer, List<Cell>> cache = new HashMap<>();

    public BuildDefinition(String name, int sx, int sz) { this.name = name; this.sx = sx; this.sz = sz; }

    public String name() { return name; }

    public BuildDefinition fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        ops.add(new Op(true, x1, y1, z1, x2, y2, z2, block));
        return this;
    }

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
            out.add(new Op(o.fill(), Math.min(a[0], c[0]), Math.min(o.y1(), o.y2()), Math.min(a[1], c[1]),
                    Math.max(a[0], c[0]), Math.max(o.y1(), o.y2()), Math.max(a[1], c[1]), rotBlock(o.block(), r)));
        }
        return out;
    }

    /** Visible (surface) cells for ghost rendering, relative to origin. */
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

    public static BuildDefinition starterTower() {
        String s = "minecraft:stone_bricks";
        return new BuildDefinition("Башня 7x7", 7, 7)
                .fill(0, 0, 0, 6, 0, 6, s)
                .fill(0, 1, 0, 6, 6, 6, s)
                .fill(1, 1, 1, 5, 6, 5, AIR)
                .fill(3, 1, 0, 3, 2, 0, AIR)
                .fill(0, 7, 0, 6, 7, 6, "minecraft:stone_brick_wall")
                .fill(1, 7, 1, 5, 7, 5, AIR);
    }

    /** 50 x 10, three floors of 5 blocks, timber-framed European house with brick gable roof. */
    public static BuildDefinition europeanHouse() {
        String stone = "minecraft:stone_bricks", plaster = "minecraft:calcite", wood = "minecraft:dark_oak_planks";
        String glass = "minecraft:glass_pane", brick = "minecraft:brick_stairs";
        BuildDefinition b = new BuildDefinition("Европейский дом 50x10", 50, 10);
        b.fill(0, 0, 0, 49, 0, 9, stone);
        for (int f = 0; f < 3; f++) {
            int y = 5 * f;
            if (f > 0) b.fill(0, y, 0, 49, y, 9, wood);
            b.fill(0, y + 1, 0, 49, y + 4, 9, plaster);
            b.fill(1, y + 1, 1, 48, y + 4, 8, AIR);
            b.fill(0, y + 4, 0, 49, y + 4, 0, wood);
            b.fill(0, y + 4, 9, 49, y + 4, 9, wood);
            b.fill(0, y + 4, 0, 0, y + 4, 9, wood);
            b.fill(49, y + 4, 0, 49, y + 4, 9, wood);
            for (int p = 0; p <= 49; p += 7) {
                b.fill(p, y + 1, 0, p, y + 4, 0, wood);
                b.fill(p, y + 1, 9, p, y + 4, 9, wood);
            }
            for (int p = 0; p < 49; p += 7) {
                if (!(f == 0 && p == 21)) b.fill(p + 2, y + 2, 0, p + 5, y + 3, 0, glass);
                b.fill(p + 2, y + 2, 9, p + 5, y + 3, 9, glass);
            }
            b.fill(0, y + 2, 4, 0, y + 3, 5, glass);
            b.fill(49, y + 2, 4, 49, y + 3, 5, glass);
        }
        b.fill(23, 1, 0, 26, 3, 0, AIR);
        b.fill(23, 1, 0, 23, 3, 0, wood);
        b.fill(26, 1, 0, 26, 3, 0, wood);
        b.fill(23, 0, -1, 26, 0, -1, "minecraft:stone_brick_stairs[facing=south]");
        for (int f = 0; f < 2; f++) {
            int y = 5 * f;
            b.fill(2, y + 5, 1, 5, y + 5, 2, AIR);
            for (int i = 0; i < 4; i++) b.fill(2 + i, y + 1 + i, 1, 2 + i, y + 1 + i, 2, "minecraft:dark_oak_stairs[facing=east]");
        }
        b.fill(0, 15, 0, 49, 15, 9, wood);
        for (int k = 1; k <= 4; k++) {
            int yy = 15 + k;
            b.fill(0, yy, k, 0, yy, 9 - k, plaster);
            b.fill(49, yy, k, 49, yy, 9 - k, plaster);
        }
        for (int k = 0; k <= 4; k++) {
            int yy = 15 + k;
            b.fill(-1, yy, -1 + k, 50, yy, -1 + k, brick + "[facing=south]");
            b.fill(-1, yy, 10 - k, 50, yy, 10 - k, brick + "[facing=north]");
        }
        b.fill(-1, 19, 4, 50, 19, 5, "minecraft:bricks");
        return b;
    }
}
