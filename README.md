# 7Stars Mods

Набор инструментов для Minecraft 1.19.2 (Forge): мод-строитель и генераторы построек.

## Структура

```
7stars.mods/
├── mod/                  # исходники мода 7Stars Builder (Forge, Java 17)
│   ├── build.gradle
│   └── src/main/
│       ├── java/ru/avganbaev/sevenstarsbuilder/
│       │   ├── SevenStarsBuilder.java   # команды /sb, клавиши, управление призраком
│       │   ├── ScriptParser.java        # разбор текстовых скриптов построек
│       │   ├── BuildDefinition.java     # постройка: операции, повороты, команды
│       │   ├── WorldExporter.java       # экспорт зоны мира в скрипт
│       │   └── GhostRenderer.java       # отрисовка призрака
│       └── resources/assets/sevenstars_builder/lang/   # ru_ru.json, en_us.json
├── scripts/              # генераторы построек (Python 3)
│   ├── city_island.py        # остров с городом (v1)
│   └── city_island_v2.py     # остров с городом, шахтный микрорайон, трасса (v2)
├── builds/               # готовые скрипты построек (.txt), создаются генераторами
├── PLAN.md               # исходный план мода
└── build.txt             # команда сборки
```

## Сборка мода

Нужны JDK 17 и Gradle 7.6.4 (лежит в `.tools/gradle-7.6.4`).

```bash
cd mod
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
../.tools/gradle-7.6.4/bin/gradle build
```

Готовый jar появится в `mod/build/libs/`. Его нужно положить в папку `mods` экземпляра Minecraft.

Сейчас сборка может не проходить из-за проверки сертификата `maven.minecraftforge.net`. Временный обход:

```bash
../.tools/gradle-7.6.4/bin/gradle build -Dnet.minecraftforge.gradle.check.certs=false
```

Правильно — проверить VPN или прокси, которые подменяют сертификаты, и добавить их корневой сертификат в Java.

## Постройки

### Формат скрипта

Текстовый файл, одна команда в строке:

```
name Название
fill x1 y1 z1 x2 y2 z2 minecraft:stone
set x y z minecraft:oak_log[axis=y]
```

Поддерживаются `name`, `fill`, `set`, `def`, `let`, `loop`. Координаты относительные, от минимального угла. Комментарии начинаются с `#`. Лимит: 20000 операций, 32768 блоков на один `fill`.

### Генерация

```bash
python3 scripts/city_island.py      # -> builds/city_island.txt
python3 scripts/city_island_v2.py   # -> builds/city_island_v2.txt
```

Скрипт в буфер обмена (`pbcopy < builds/city_island_v2.txt`), в игре `/sb paste`.

## Команды в игре

| Команда | Что делает |
|---|---|
| `/sb paste` | вставить скрипт из буфера, включить призрак |
| `/sb list` | список сохранённых построек |
| `/sb use имя` | включить сохранённую постройку |
| `/sb hide` | скрыть призрак |
| `/sb pos1 [x y z]`, `/sb pos2 [x y z]` | задать углы зоны (по прицелу или координатами) |
| `/sb export имя` | сохранить зону между `pos1` и `pos2` в скрипт |

Сохранённые постройки лежат в `config/sevenstars_builds/` внутри папки экземпляра игры.

## Клавиши

Категория «7Stars Builder» в `Настройки → Управление`.

| Клавиша | Действие |
|---|---|
| `]` | следующая постройка |
| G | зафиксировать призрак |
| стрелки | сдвиг призрака (Shift — на 5 блоков) |
| PgUp / PgDn | поднять / опустить призрак |
| R | повернуть (Shift — обратно) |
| Enter | построить после фиксации |
| Backspace | остановить очередь / скрыть призрак |

Клавиши призрака работают только при включённом призраке. `]` и Backspace работают всегда.

## Статус

- Генераторы проверены запуском в песочнице: скрипты создаются, координаты и лимиты соответствуют правилам.
- Мод ещё не собран и не запускался в игре: сборка блокируется сертификатом Forge.
- Постройки `city_island*.txt` не проверялись в игре.

## Лицензия

Не указана.
