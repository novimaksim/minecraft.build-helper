package ru.avganbaev.sevenstarsbuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.*;

/**
 * Превращает кусок мира в текст скрипта /sb (fill и set, координаты от минимального угла зоны).
 * Воздух пропускается, одинаковые блоки объединяются в прямоугольники.
 */
final class WorldExporter {
    private static final int MAX_FILL = 32768;
    private static final long MAX_VOLUME = 4_000_000L;

    private WorldExporter() {}

    static List<String> export(Level level, BlockPos a, BlockPos b, String name) {
        int x0 = Math.min(a.getX(), b.getX());
        int y0 = Math.min(a.getY(), b.getY());
        int z0 = Math.min(a.getZ(), b.getZ());
        int sx = Math.abs(a.getX() - b.getX()) + 1;
        int sy = Math.abs(a.getY() - b.getY()) + 1;
        int sz = Math.abs(a.getZ() - b.getZ()) + 1;
        long volume = (long) sx * sy * sz;
        if (volume > MAX_VOLUME) {
            throw new IllegalStateException("зона слишком большая: " + volume + " блоков, лимит " + MAX_VOLUME);
        }

        String[] grid = new String[(int) volume];
        Map<BlockState, String> names = new HashMap<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    BlockState s = level.getBlockState(p.set(x0 + x, y0 + y, z0 + z));
                    if (s.isAir()) continue;
                    grid[idx(x, y, z, sy, sz)] = names.computeIfAbsent(s, WorldExporter::blockString);
                }
            }
        }

        boolean[] done = new boolean[(int) volume];
        List<String> out = new ArrayList<>();
        out.add("name " + name);
        out.add("# Экспорт зоны от " + x0 + " " + y0 + " " + z0 + " до " + (x0 + sx - 1) + " " + (y0 + sy - 1) + " " + (z0 + sz - 1));

        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    int i = idx(x, y, z, sy, sz);
                    if (grid[i] == null || done[i]) continue;
                    String block = grid[i];
                    int ex = x, ey = y, ez = z;
                    while (ex + 1 < sx && vol(x, ex + 1, y, ey, z, ez) <= MAX_FILL
                            && slab(grid, done, sy, sz, ex + 1, ex + 1, y, ey, z, ez, block)) ex++;
                    while (ey + 1 < sy && vol(x, ex, y, ey + 1, z, ez) <= MAX_FILL
                            && slab(grid, done, sy, sz, x, ex, ey + 1, ey + 1, z, ez, block)) ey++;
                    while (ez + 1 < sz && vol(x, ex, y, ey, z, ez + 1) <= MAX_FILL
                            && slab(grid, done, sy, sz, x, ex, y, ey, ez + 1, ez + 1, block)) ez++;

                    for (int xx = x; xx <= ex; xx++)
                        for (int yy = y; yy <= ey; yy++)
                            for (int zz = z; zz <= ez; zz++)
                                done[idx(xx, yy, zz, sy, sz)] = true;

                    if (ex == x && ey == y && ez == z) {
                        out.add("set " + x + " " + y + " " + z + " " + block);
                    } else {
                        out.add("fill " + x + " " + y + " " + z + " " + ex + " " + ey + " " + ez + " " + block);
                    }
                }
            }
        }
        return out;
    }

    private static int idx(int x, int y, int z, int sy, int sz) {
        return (x * sy + y) * sz + z;
    }

    private static long vol(int x1, int x2, int y1, int y2, int z1, int z2) {
        return (long) (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
    }

    /** Проверяет, что все клетки коробки содержат block и ещё не занесены в команду. */
    private static boolean slab(String[] grid, boolean[] done, int sy, int sz,
                                int x1, int x2, int y1, int y2, int z1, int z2, String block) {
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++) {
                    int i = idx(x, y, z, sy, sz);
                    if (done[i] || !block.equals(grid[i])) return false;
                }
        return true;
    }

    static String blockString(BlockState s) {
        String id = Registry.BLOCK.getKey(s.getBlock()).toString();
        if (s.getValues().isEmpty()) return id;
        List<String> parts = new ArrayList<>();
        s.getValues().forEach((prop, value) -> parts.add(prop.getName() + "=" + valueName(prop, value)));
        Collections.sort(parts);
        return id + "[" + String.join(",", parts) + "]";
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueName(Property prop, Object value) {
        return prop.getName((Comparable) value);
    }
}
