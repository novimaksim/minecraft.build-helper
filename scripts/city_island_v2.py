#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Остров-город v2 для 7Stars Builder (Minecraft 1.19.2, ванильные блоки + car:asphalt).

Добавлено к v1:
  - остров больше: есть место для нового микрорайона на юге;
  - микрорайон на возвышении: 4 дома, шахтный копёр со стволом, православная
    церковь с колокольней, административный корпус шахты, два мини-магазина,
    парк на склоне, рудный двор;
  - основная нижняя дорога 2+2 полосы (12 блоков) с подъёмом к микрорайону;
  - трасса над дорогой (путепровод) и съезд с трассы к микрорайону.
Разметку не ставим, её добавляешь сам.

Все координаты в этом файле «городские». При записи скрипта они сдвигаются
на OFF, чтобы весь остров уложился в квадрат 0..OUT_N-1.

Запуск: python3 scripts/city_island_v2.py  ->  builds/city_island_v2.txt
"""
import math
import os

OFF = 24            # сдвиг городских координат в скрипт
OUT_N = 176         # размер квадрата в скрипте
CX = 63.5           # центр острова (городские координаты)
R = 86.0            # радиус острова
FALL = 0.94         # с какого радиуса начинается спуск к воде
SEA = 12            # уровень воды
BOTTOM = 8          # нижняя граница камня
BASE = 18           # уровень города
HIGH = 24           # уровень возвышения (шахтный микрорайон)
HW_Y = 25           # верх асфальта трассы
FLOORS, FH = 3, 4   # этажей и высота этажа
MAX_FILL = 32768
MAX_OPS = 20000
GLASS = "minecraft:glass_pane"
AIR = "minecraft:air"
ASPHALT = "car:asphalt"
STONE_BRICKS = "minecraft:stone_bricks"

ops = []


def emit(s):
    ops.append(s)


def _o(v):
    return v + OFF


def fill(x1, y1, z1, x2, y2, z2, block):
    """fill со сдвигом в скрипт и делением, если объём больше лимита."""
    x1, x2 = sorted((x1, x2))
    y1, y2 = sorted((y1, y2))
    z1, z2 = sorted((z1, z2))
    dx, dy, dz = x2 - x1 + 1, y2 - y1 + 1, z2 - z1 + 1
    if dx * dy * dz <= MAX_FILL:
        emit(f"fill {_o(x1)} {y1} {_o(z1)} {_o(x2)} {y2} {_o(z2)} {block}")
    elif dx >= dz:
        m = x1 + dx // 2 - 1
        fill(x1, y1, z1, m, y2, z2, block)
        fill(m + 1, y1, z1, x2, y2, z2, block)
    else:
        m = z1 + dz // 2 - 1
        fill(x1, y1, z1, x2, y2, m, block)
        fill(x1, y1, m + 1, x2, y2, z2, block)


def put(x, y, z, block):
    emit(f"set {_o(x)} {y} {_o(z)} {block}")


# ---------- зоны (городские координаты) ----------
CITY = (25, 25, 102, 102)       # ровная зона старого города
PARK = (25, 92, 60, 102)        # парк на холмах
PADS = (38, 12, 85, 19)         # площадка для вертолётов
NROAD = (61, 12, 65, 24)        # дорога к площадке
MAINROAD = (57, 103, 70, 117)   # основная дорога (до подъёма), с тротуарами
DIST = (34, 126, 92, 140)       # возвышение шахтного микрорайона
RAMP = (34, 118, 95, 125)       # подъём к возвышению
CONN = (96, 118, 116, 123)      # съезд с трассы
HWY = (-18, 106, 142, 117)      # трасса (настил над дорогой)


def inr(x, z, r):
    return r[0] <= x <= r[2] and r[1] <= z <= r[3]


def is_flat(x, z):
    if inr(x, z, PADS) or inr(x, z, NROAD) or inr(x, z, MAINROAD):
        return True
    return inr(x, z, CITY) and not inr(x, z, PARK)


def ramp_h(z):
    return round(BASE + (HIGH - BASE) * (z - RAMP[1]) / (RAMP[3] - RAMP[1]))


def conn_h(x):
    return round(BASE + 7 * (x - CONN[0]) / (CONN[2] - CONN[0]))


def road_y(z):
    """Высота основной дороги на строке z."""
    if z < RAMP[1]:
        return BASE
    if z <= RAMP[3]:
        return ramp_h(z)
    return HIGH


# ---------- рельеф ----------
def island_r(x, z):
    dx, dz = x - CX, z - CX
    th = math.atan2(dz, dx)
    wob = (1 + 0.025 * math.sin(5 * th + 0.7)
             + 0.015 * math.sin(9 * th + 1.3)
             + 0.01 * math.sin(17 * th))
    return math.hypot(dx, dz) / R * wob


def hills(x, z):
    v = 1.5 + 1.4 * math.sin(x * 0.13 + 0.6) + 1.2 * math.cos(z * 0.11)
    return min(3, max(0, round(v)))


def surface(x, z):
    """Высота верхнего блока или None, если клетка в воде."""
    rr = island_r(x, z)
    if rr > 1.0:
        return None
    if inr(x, z, DIST):
        return HIGH
    if inr(x, z, RAMP):
        return ramp_h(z)
    if inr(x, z, CONN):
        return conn_h(x)
    if is_flat(x, z):
        return BASE
    t = min(1.0, max(0.0, (1.0 - rr) / (1.0 - FALL)))
    t = t * t * (3 - 2 * t)
    return round(SEA + (BASE - SEA) * t + hills(x, z) * t)


def column(x, z):
    """Слои колонки: камень, подложка, верхний блок. None — вода."""
    h = surface(x, z)
    if h is None:
        return None
    sand = h <= SEA + 1
    top = "minecraft:sand" if sand else "minecraft:grass_block"
    sub = "minecraft:sand" if sand else "minecraft:dirt"
    return ((BOTTOM, h - 4, "minecraft:stone"), (h - 3, h - 1, sub), (h, h, top))


def terrain():
    """Объединяет одинаковые колонки в прямоугольники. Работает в городских координатах."""
    lo, hi = -OFF, OUT_N - OFF
    n = hi - lo
    cols = [[column(lo + i, lo + j) for j in range(n)] for i in range(n)]
    active = {}

    def close(key, x_start, x_end):
        z1, z2, layers = key
        for y1, y2, block in layers:
            fill(x_start, y1, z1, x_end, y2, z2, block)

    for i in range(n + 1):
        segs = set()
        if i < n:
            j = 0
            while j < n:
                c = cols[i][j]
                if c is None:
                    j += 1
                    continue
                j2 = j
                while j2 + 1 < n and cols[i][j2 + 1] == c:
                    j2 += 1
                segs.add((lo + j, lo + j2, c))
                j = j2 + 1
        for key in [k for k in active if k not in segs]:
            close(key, active.pop(key), lo + i - 1)
        for key in segs:
            active.setdefault(key, lo + i)


# ---------- старый город (как в v1) ----------
def helipad():
    for i in range(5):
        x0 = 38 + i * 10
        fill(x0, BASE, 12, x0 + 7, BASE, 19, "minecraft:smooth_quartz")


def roads():
    walk = "minecraft:smooth_stone"
    for x1, z1, x2, z2 in [(25, 60, 60, 60), (66, 60, 102, 60), (25, 66, 60, 66), (66, 66, 102, 66),
                           (60, 20, 60, 60), (66, 20, 66, 60), (60, 66, 60, 102), (66, 66, 66, 102)]:
        fill(x1, BASE, z1, x2, BASE, z2, walk)
    for x1, z1, x2, z2 in [(25, 61, 102, 65), (61, 19, 65, 102)]:
        fill(x1, BASE, z1, x2, BASE, z2, ASPHALT)


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


def apartment(x0, z0, base=BASE, floors=FLOORS):
    """Дом 9x7, этажи по FH, две квартиры на этаж."""
    x1, z1 = x0 + 8, z0 + 6
    top = base + floors * FH
    fill(x0, base, z0, x1, base, z1, STONE_BRICKS)
    fill(x0, base + 1, z0, x1, top, z1, "minecraft:bricks")
    for k in range(floors):
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
    fill(x0, top, z0, x1, top, z1, STONE_BRICKS)
    put(x0 + 2, top + 1, z0 + 2, "minecraft:iron_block")
    for x in (x0 + 2, x0 + 6):
        fill(x, base + 1, z0, x, base + 2, z0, AIR)


BUILDINGS = []
for ox, oy in ((25, 25), (68, 25), (68, 68)):      # три микрорайона старого города
    BUILDINGS += [(ox, oy), (ox + 12, oy), (ox + 24, oy), (ox, oy + 20), (ox + 24, oy + 20)]


def old_city():
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


# ---------- дорога, трасса, съезд ----------
def main_road():
    """Основная дорога 2+2 полосы (12 блоков), тротуары по краям, подъём к микрорайону."""
    for z in range(103, 141):
        y = road_y(z)
        fill(58, y, z, 69, y, z, ASPHALT)
        fill(57, y, z, 57, y, z, "minecraft:smooth_stone")
        fill(70, y, z, 70, y, z, "minecraft:smooth_stone")


def main_lamps():
    for z in range(104, 141, 9):
        y = road_y(z)
        put(56, y + 1, z, "minecraft:oak_fence")
        put(56, y + 2, z, "minecraft:lantern")


def connector():
    """Съезд: поднимается от уровня города к трассе, примыкает к подъёму микрорайона."""
    for x in range(CONN[0], CONN[2] + 1):
        y = conn_h(x)
        fill(x, y, CONN[1], x, y, CONN[3], ASPHALT)


def highway():
    """Трасса на эстакаде: настил 12 блоков (2+2 полосы), над основной дорогой."""
    x1, z1, x2, z2 = HWY
    fill(x1, 23, z1, x2, 24, z2, "minecraft:gray_concrete")
    fill(x1, HW_Y, z1, x2, HW_Y, z2, ASPHALT)
    fill(x1, HW_Y + 1, z1, x2, HW_Y + 1, z1, "minecraft:cobblestone_wall")
    fill(x1, HW_Y + 1, z2, x2, HW_Y + 1, z2, "minecraft:cobblestone_wall")
    for z in (108, 115):
        for x in range(-14, 140, 12):
            if 56 <= x <= 71:
                continue
            fill(x, 0, z, x, 22, z, STONE_BRICKS)


# ---------- шахтный микрорайон на возвышении ----------
def church(x0, z0, base):
    """Православная церковь 11x8: белые стены, золотой купол с крестом, колокольня."""
    x1, z1 = x0 + 10, z0 + 7
    fill(x0, base, z0, x1, base, z1, "minecraft:spruce_planks")
    fill(x0, base + 1, z0, x1, base + 6, z1, "minecraft:white_concrete")
    fill(x0 + 1, base + 1, z0 + 1, x1 - 1, base + 5, z1 - 1, AIR)
    for x in (x0 + 2, x0 + 4, x0 + 6):
        put(x, base + 3, z0, GLASS)
        put(x, base + 3, z1, GLASS)
    for z in (z0 + 2, z0 + 4, z0 + 6):
        put(x0, base + 3, z, GLASS)
        put(x1, base + 3, z, GLASS)
    fill(x0 + 5, base + 1, z1, x0 + 5, base + 2, z1, AIR)          # вход
    fill(x0, base + 7, z0, x1, base + 7, z1, STONE_BRICKS)         # крыша
    fill(x0 + 3, base + 8, z0 + 2, x0 + 7, base + 8, z0 + 5, "minecraft:white_concrete")
    fill(x0 + 4, base + 9, z0 + 3, x0 + 6, base + 10, z0 + 4, "minecraft:gold_block")
    put(x0 + 5, base + 11, z0 + 3, "minecraft:gold_block")
    fill(x0 + 5, base + 12, z0 + 3, x0 + 5, base + 14, z0 + 3, "minecraft:gold_block")  # крест
    put(x0 + 4, base + 13, z0 + 3, "minecraft:gold_block")
    put(x0 + 6, base + 13, z0 + 3, "minecraft:gold_block")
    fill(x0 + 8, base + 1, z0, x0 + 10, base + 17, z0 + 2, "minecraft:bricks")  # колокольня
    put(x0 + 9, base + 18, z0 + 1, "minecraft:gold_block")
    put(x0 + 9, base + 19, z0 + 1, "minecraft:gold_block")
    for z in (z0 + 3, z0 + 5):                                       # скамьи
        for x in (x0 + 2, x0 + 3, x0 + 4, x0 + 6, x0 + 7):
            put(x, base + 1, z, "minecraft:oak_stairs[facing=south,half=bottom]")
    put(x0 + 5, base + 1, z0 + 1, "minecraft:quartz_block")          # алтарь
    put(x0 + 5, base + 4, z0 + 4, "minecraft:lantern[hanging=true]")


def shop(x0, z0, base):
    """Мини-магазин 3x6: кирпич, витрина, прилавок."""
    x1, z1 = x0 + 2, z0 + 5
    fill(x0, base, z0, x1, base, z1, "minecraft:smooth_stone")
    fill(x0, base + 1, z0, x1, base + 3, z1, "minecraft:bricks")
    fill(x0 + 1, base + 1, z0 + 1, x1 - 1, base + 2, z1 - 1, AIR)
    fill(x0, base + 2, z0 + 1, x0, base + 2, z1 - 1, GLASS)
    fill(x0 + 1, base + 1, z0 + 2, x0 + 1, base + 1, z0 + 3, "minecraft:oak_planks")
    put(x0 + 1, base + 3, z0 + 2, "minecraft:lantern[hanging=true]")
    fill(x0, base + 4, z0, x1, base + 4, z1, STONE_BRICKS)


def mine(base):
    """Шахтный копёр 7x7 с металлическими ярусами и стволом 3x3."""
    for x, z in [(83, 127), (89, 127), (83, 133), (89, 133)]:
        fill(x, base + 1, z, x, base + 24, z, "minecraft:iron_block")
    for y in (base + 1, base + 9, base + 17, base + 24):
        fill(83, y, 127, 89, y, 133, "minecraft:iron_bars")
    fill(84, base - 8, 128, 88, base, 132, STONE_BRICKS)        # стенки ствола
    fill(85, base - 7, 129, 87, base, 131, AIR)                  # ствол 3x3
    fill(83, base + 1, 134, 89, base + 1, 134, "minecraft:rail[shape=east_west]")


def ore_yard(base):
    """Рудный двор у шахты: отвалы руды и угля."""
    fill(84, base + 1, 136, 85, base + 1, 137, "minecraft:coal_ore")
    fill(87, base + 1, 138, 88, base + 1, 139, "minecraft:iron_ore")
    fill(84, base + 1, 139, 85, base + 1, 139, "minecraft:coal_block")


def district():
    for x0, z0 in [(35, 126), (47, 126), (35, 134), (47, 134)]:      # 4 дома
        apartment(x0, z0, HIGH, 3)
    apartment(71, 134, HIGH, 2)                                       # административный корпус
    church(71, 126, HIGH)
    mine(HIGH)
    ore_yard(HIGH)
    shop(90, 126, HIGH)
    shop(90, 134, HIGH)
    for x, z in [(56, 130), (56, 137)]:
        tree(x, z)
    for x, z in [(38, 121), (44, 119), (50, 123), (41, 124), (90, 120), (92, 122)]:   # парк на склоне
        tree(x, z)


# ---------- сборка ----------
def main():
    emit("name Остров-город v2")
    emit("# Остров с городом, шахтный микрорайон на возвышении, трасса с путепроводом")
    terrain()
    old_city()
    main_road()
    main_lamps()
    connector()
    highway()
    district()
    write()


def write():
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "builds", "city_island_v2.txt")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(ops) + "\n")
    print(f"Готово: {len(ops)} строк -> {os.path.normpath(out)}")
    if len(ops) > MAX_OPS:
        print("ВНИМАНИЕ: больше 20000 операций, мод откажется читать скрипт")


if __name__ == "__main__":
    main()
