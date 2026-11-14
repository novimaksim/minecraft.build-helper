#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Остров с мини-городом для 7Stars Builder (Minecraft 1.19.2, ванильные блоки + car:asphalt).

Что генерируется:
  - natural-остров: неровный берег, пляж, холмы вне города;
  - ровная зона города: 15 жилых домов в 3 микрорайонах, дворы с деревьями и дорожками;
  - парк на холмах, одна большая фабрика, площадка на 5 вертолётных мест;
  - дороги car:asphalt с тротуарами и фонарями.
Разметку на площадке не рисуем: её добавишь сам.

Координаты относительные: x, z в 0..N-1, y = 0 — дно острова.
SEA — уровень воды, BASE — уровень города. Ставь постройку так, чтобы
y = 0 пришёлся на дно, а вода оказалась на уровне SEA.

Запуск: python3 scripts/city_island.py  ->  builds/city_island.txt
Дальше: скопировать файл в буфер обмена и в игре выполнить /sb paste.
"""
import math
import os

N = 128                 # размер квадрата
C = (N - 1) / 2         # центр острова
R = 62.0                # радиус острова
SEA = 12                # уровень воды
BASE = 18               # уровень города
FALL = 0.94             # с какого радиуса начинается спуск к воде
FLOORS, FH = 3, 4       # этажей и высота этажа
MAX_FILL = 32768
MAX_OPS = 20000
GLASS = "minecraft:glass_pane"
AIR = "minecraft:air"

ops = []


def emit(s):
    ops.append(s)


def fill(x1, y1, z1, x2, y2, z2, block):
    """fill с автоматическим делением, если объём больше лимита /fill."""
    x1, x2 = sorted((x1, x2))
    y1, y2 = sorted((y1, y2))
    z1, z2 = sorted((z1, z2))
    dx, dy, dz = x2 - x1 + 1, y2 - y1 + 1, z2 - z1 + 1
    if dx * dy * dz <= MAX_FILL:
        emit(f"fill {x1} {y1} {z1} {x2} {y2} {z2} {block}")
    elif dx >= dz:
        m = x1 + dx // 2 - 1
        fill(x1, y1, z1, m, y2, z2, block)
        fill(m + 1, y1, z1, x2, y2, z2, block)
    else:
        m = z1 + dz // 2 - 1
        fill(x1, y1, z1, x2, y2, m, block)
        fill(x1, y1, m + 1, x2, y2, z2, block)


def put(x, y, z, block):
    emit(f"set {x} {y} {z} {block}")


# ---------- зоны ----------
CITY = (25, 25, 102, 102)     # ровная зона города (x1, z1, x2, z2)
PARK = (25, 92, 60, 102)      # парк на холмах
PADS = (38, 12, 85, 19)       # площадка для вертолётов
NROAD = (61, 12, 65, 24)      # участок дороги к площадке


def inr(x, z, r):
    return r[0] <= x <= r[2] and r[1] <= z <= r[3]


def is_flat(x, z):
    if inr(x, z, PADS) or inr(x, z, NROAD):
        return True
    return inr(x, z, CITY) and not inr(x, z, PARK)


# ---------- рельеф ----------
def island_r(x, z):
    """Нормированный радиус с неровным контуром берега."""
    dx, dz = x - C, z - C
    th = math.atan2(dz, dx)
    wob = (1 + 0.025 * math.sin(5 * th + 0.7)
             + 0.015 * math.sin(9 * th + 1.3)
             + 0.01 * math.sin(17 * th))
    return math.hypot(dx, dz) / R * wob


def hills(x, z):
    v = 1.5 + 1.4 * math.sin(x * 0.13 + 0.6) + 1.2 * math.cos(z * 0.11)
    return min(3, max(0, round(v)))


def surface(x, z):
    """Высота верхнего блока или None, если клетка уже в воде."""
    rr = island_r(x, z)
    if rr > 1.0:
        return None
    if is_flat(x, z):
        return BASE
    t = min(1.0, max(0.0, (1.0 - rr) / (1.0 - FALL)))
    t = t * t * (3 - 2 * t)
    return round(SEA + (BASE - SEA) * t + hills(x, z) * t)


def column(x, z):
    """Слои колонки: камень, подложка и верхний блок. None — вода."""
    h = surface(x, z)
    if h is None:
        return None
    sand = h <= SEA + 1
    top = "minecraft:sand" if sand else "minecraft:grass_block"
    sub = "minecraft:sand" if sand else "minecraft:dirt"
    return ((0, h - 4, "minecraft:stone"), (h - 3, h - 1, sub), (h, h, top))


def terrain():
    """Объединяет одинаковые колонки в прямоугольники, чтобы ops было меньше."""
    cols = [[column(x, z) for z in range(N)] for x in range(N)]
    active = {}

    def close(key, x_start, x_end):
        z1, z2, layers = key
        for y1, y2, block in layers:
            fill(x_start, y1, z1, x_end, y2, z2, block)

    for x in range(N + 1):
        segs = set()
        if x < N:
            z = 0
            while z < N:
                c = cols[x][z]
                if c is None:
                    z += 1
                    continue
                z2 = z
                while z2 + 1 < N and cols[x][z2 + 1] == c:
                    z2 += 1
                segs.add((z, z2, c))
                z = z2 + 1
        for key in [k for k in active if k not in segs]:
            close(key, active.pop(key), x - 1)
        for key in segs:
            active.setdefault(key, x)


# ---------- инфраструктура ----------
def helipad():
    """5 вертолётных площадок 8x8 в ряд. Разметку делаешь сам."""
    for i in range(5):
        x0 = 38 + i * 10
        fill(x0, BASE, 12, x0 + 7, BASE, 19, "minecraft:smooth_quartz")


def roads():
    walk = "minecraft:smooth_stone"
    for x1, z1, x2, z2 in [(25, 60, 60, 60), (66, 60, 102, 60), (25, 66, 60, 66), (66, 66, 102, 66),
                           (60, 20, 60, 60), (66, 20, 66, 60), (60, 66, 60, 102), (66, 66, 66, 102)]:
        fill(x1, BASE, z1, x2, BASE, z2, walk)
    for x1, z1, x2, z2 in [(25, 61, 102, 65), (61, 19, 65, 102)]:
        fill(x1, BASE, z1, x2, BASE, z2, "car:asphalt")


def lamps():
    for z in range(22, 102, 8):
        if 56 <= z <= 70:
            continue
        for x in (59, 67):
            put(x, BASE + 1, z, "minecraft:oak_fence")
            put(x, BASE + 2, z, "minecraft:lantern")


def tree(x, z):
    g = surface(x, z)
    fill(x - 2, g + 3, z - 2, x + 2, g + 5, z + 2, "minecraft:oak_leaves[persistent=true]")
    fill(x, g + 1, z, x, g + 4, z, "minecraft:oak_log[axis=y]")


def courtyard(box, trees, px, pz):
    x1, z1, x2, z2 = box
    fill(x1, BASE, pz, x2, BASE, pz + 1, "minecraft:cobblestone")
    fill(px, BASE, z1, px + 1, BASE, z2, "minecraft:cobblestone")
    for x, z in trees:
        tree(x, z)


def park():
    for x, z in [(28, 95), (36, 95), (44, 95), (52, 95), (30, 100), (40, 100), (50, 100)]:
        tree(x, z)
    for x, z in [(33, 98), (58, 93)]:
        g = surface(x, z)
        put(x, g + 1, z, "minecraft:oak_stairs[facing=north,half=bottom]")


def factory():
    x1, z1, x2, z2 = 28, 68, 55, 90
    y0 = BASE
    fill(x1, y0, z1, x2, y0, z2, "minecraft:smooth_stone")
    fill(x1, y0 + 1, z1, x2, y0 + 14, z2, "minecraft:gray_concrete")
    fill(x1 + 1, y0 + 1, z1 + 1, x2 - 1, y0 + 13, z2 - 1, AIR)
    fill(x1 + 3, y0 + 1, z1 + 3, x1 + 6, y0 + 6, z1 + 6, "minecraft:iron_block")
    fill(x2 - 7, y0 + 1, z2 - 7, x2 - 4, y0 + 6, z2 - 4, "minecraft:iron_block")
    fill(x1 + 8, y0 + 1, z1 + 10, x2 - 8, y0 + 1, z1 + 10, "minecraft:iron_block")
    fill(x1 + 10, y0 + 1, z1 + 1, x1 + 10, y0 + 12, z1 + 1, "minecraft:iron_bars")
    for x in range(x1 + 2, x2 - 1, 3):
        put(x, y0 + 8, z1, GLASS)
        put(x, y0 + 8, z2, GLASS)
    for z in range(z1 + 2, z2 - 1, 3):
        put(x1, y0 + 8, z, GLASS)
        put(x2, y0 + 8, z, GLASS)
    fill(41, y0 + 1, z1, 42, y0 + 3, z1, AIR)
    fill(57, y0, 70, 59, y0 + 30, 72, "minecraft:bricks")


# ---------- дома ----------
def furnish(ax, az, y, ylamp):
    put(ax, y, az, "minecraft:red_bed[facing=south,part=head]")
    put(ax, y, az + 1, "minecraft:red_bed[facing=south,part=foot]")
    put(ax + 2, y, az, "minecraft:crafting_table")
    put(ax + 2, y, az + 2, "minecraft:chest[facing=west,type=single]")
    put(ax, y, az + 4, "minecraft:bookshelf")
    put(ax + 1, y, az + 4, "minecraft:potted_poppy")
    put(ax + 2, y, az + 4, "minecraft:furnace[facing=north,lit=false]")
    put(ax + 1, y, az + 2, "minecraft:red_carpet")
    put(ax + 1, ylamp, az + 2, "minecraft:lantern[hanging=true]")


def apartment(x0, z0):
    """Дом 9x7, 3 этажа, две квартиры на этаж."""
    base, x1, z1 = BASE, x0 + 8, z0 + 6
    top = base + FLOORS * FH
    fill(x0, base, z0, x1, base, z1, "minecraft:stone_bricks")
    fill(x0, base + 1, z0, x1, top, z1, "minecraft:bricks")
    for k in range(FLOORS):
        yk = base + k * FH
        fill(x0 + 1, yk + 1, z0 + 1, x1 - 1, yk + FH - 1, z1 - 1, AIR)
        if k > 0:
            fill(x0 + 1, yk, z0 + 1, x1 - 1, yk, z1 - 1, "minecraft:oak_planks")
        fill(x0 + 4, yk + 1, z0 + 1, x0 + 4, yk + FH - 1, z1 - 1, "minecraft:bricks")
        put(x0 + 4, yk + 1, z0 + 3, AIR)
        put(x0 + 4, yk + 2, z0 + 3, AIR)
        wy = yk + 2
        for x in (x0 + 1, x0 + 3, x0 + 5, x0 + 7):
            put(x, wy, z0, GLASS)
        for x in (x0 + 1, x0 + 2, x0 + 3, x0 + 5, x0 + 6, x0 + 7):
            put(x, wy, z1, GLASS)
        for z in (z0 + 2, z0 + 4):
            put(x0, wy, z, GLASS)
            put(x1, wy, z, GLASS)
        for ax in (x0 + 1, x0 + 5):
            furnish(ax, z0 + 1, yk + 1, yk + FH - 1)
    fill(x0, top, z0, x1, top, z1, "minecraft:stone_bricks")
    put(x0 + 2, top + 1, z0 + 2, "minecraft:iron_block")
    for x in (x0 + 2, x0 + 6):
        fill(x, base + 1, z0, x, base + 2, z0, AIR)


BUILDINGS = []
for ox, oy in ((25, 25), (68, 25), (68, 68)):      # три микрорайона
    BUILDINGS += [(ox, oy), (ox + 12, oy), (ox + 24, oy), (ox, oy + 20), (ox + 24, oy + 20)]


# ---------- сборка ----------
def main():
    emit("name Остров-город")
    emit("# Остров с мини-городом (сгенерировано city_island.py)")
    terrain()
    helipad()
    roads()
    lamps()
    for x, z in BUILDINGS:
        apartment(x, z)
    courtyard((34, 32, 48, 44), [(36, 34), (46, 34), (36, 42), (46, 42)], 40, 37)
    courtyard((77, 32, 91, 44), [(79, 34), (89, 34), (79, 42), (89, 42)], 84, 37)
    courtyard((77, 75, 91, 87), [(79, 77), (89, 77), (79, 85), (89, 85)], 84, 81)
    park()
    factory()
    write()


def write():
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "builds", "city_island.txt")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(ops) + "\n")
    print(f"Готово: {len(ops)} строк -> {os.path.normpath(out)}")
    if len(ops) > MAX_OPS:
        print("ВНИМАНИЕ: больше 20000 операций, мод откажется читать скрипт")


if __name__ == "__main__":
    main()
