# minecraft.build-helper

Клиентский мод для Minecraft 1.19.2 (Forge). Строит постройки по текстовым скриптам: показывает призрак постройки в прицеле, после фиксации отправляет команды `fill` и `setblock`. Также умеет сохранить зону мира обратно в скрипт.

## Требования

- Minecraft 1.19.2
- Forge 1.19.2 (43.4.16 или новее из той же ветки)
- JDK 17 (для сборки)
- Права оператора на сервере: мод отправляет `/fill` и `/setblock`

## Структура репозитория

```
minecraft.build-helper/
├── mod/                      # исходники мода
│   ├── build.gradle          # сборка Forge, версия 1.0.0, jar sevenstars-builder-1.19.2
│   ├── settings.gradle
│   ├── gradle.properties
│   └── src/main/
│       ├── java/ru/avganbaev/sevenstarsbuilder/
│       │   ├── SevenStarsBuilder.java   # точка входа, команды /sb, клавиши, очередь команд
│       │   ├── ScriptParser.java        # разбор текстовых скриптов
│       │   ├── BuildDefinition.java     # постройка: операции, повороты, команды для сервера
│       │   ├── GhostRenderer.java       # отрисовка призрака в мире
│       │   └── WorldExporter.java       # экспорт зоны мира в скрипт
│       └── resources/
│           ├── META-INF/mods.toml
│           ├── pack.mcmeta
│           └── assets/sevenstars_builder/lang/   # ru_ru.json, en_us.json
├── .tools/gradle-7.6.4/      # Gradle для сборки (в .gitignore, нужен локально)
├── build.txt                 # готовая команда сборки
├── README.md
└── .gitignore, .gitattributes
```

## Сборка

```bash
cd mod
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
../.tools/gradle-7.6.4/bin/gradle build
```

Результат: `mod/build/libs/sevenstars-builder-1.19.2-1.0.0.jar`. Положи его в папку `mods` экземпляра Minecraft.

### Если сборка падает на сертификате Forge

```
Failed to validate certificate for host 'https://maven.minecraftforge.net/'
```

Причина обычно в VPN или прокси, которые подменяют сертификаты. Правильное решение: добавить корневой сертификат этого VPN или прокси в хранилище `cacerts` JDK через `keytool -importcert`. Временный обход, только в доверенной сети:

```bash
../.tools/gradle-7.6.4/bin/gradle build -Dnet.minecraftforge.gradle.check.certs=false
```

## Формат скрипта

Текстовый файл, одна команда в строке. Имя файла в папке построек может быть любым, имя постройки задаёт строка `name`.

```
name Название постройки
# комментарий
fill x1 y1 z1 x2 y2 z2 minecraft:stone
set x y z minecraft:oak_log[axis=y]
```

- Поддерживаются `name`, `fill`, `set`, `def`, `let`, `loop`.
- Координаты относительные, от минимального угла постройки.
- Блоки записываются как `minecraft:имя` или `мод:имя` с необязательными свойствами в квадратных скобках.
- Лимиты: 20000 операций на скрипт, 32768 блоков на один `fill`.

## Как работает

1. `/sb paste` берёт текст из буфера обмена, проверяет его парсером и сохраняет в `config/sevenstars_builds/`.
2. Призрак показывается в точке, куда смотришь. G фиксирует её, стрелки и PgUp/PgDn двигают.
3. Enter ставит все команды в очередь. Мод отправляет по одной команде в тик, поэтому большие постройки строятся долго.
4. Backspace останавливает очередь.

Сохранённые постройки загружаются при запуске игры. Файлы с расширением `.txt` в папке `config/sevenstars_builds/` видны в `/sb list`.

## Команды

| Команда | Что делает |
|---|---|
| `/sb paste` | вставить скрипт из буфера обмена и включить призрак |
| `/sb list` | список сохранённых построек, текущая отмечена `>` |
| `/sb use имя` | включить сохранённую постройку (поиск по части имени) |
| `/sb hide` | скрыть призрак |
| `/sb pos1 [x y z]` | задать первый угол зоны (по прицелу или координатами) |
| `/sb pos2 [x y z]` | задать второй угол зоны |
| `/sb export имя` | сохранить зону между `pos1` и `pos2` в `config/sevenstars_builds/имя.txt` |

Экспорт проверяет готовый скрипт парсером перед записью. Если зона больше 4 миллионов блоков, экспорт не выполняется.

## Клавиши

Категория «7Stars Builder» в `Настройки → Управление`. Названия есть на русском и английском.

| Клавиша | Действие |
|---|---|
| `]` | следующая постройка (работает всегда) |
| G | зафиксировать призрак |
| стрелки | сдвиг призрака, Shift — на 5 блоков |
| PgUp / PgDn | поднять / опустить призрак |
| R | повернуть на 90°, Shift — обратно |
| Enter | построить после фиксации |
| Backspace | остановить очередь или скрыть призрак (работает всегда) |

Клавиши призрака (G, R, Enter, стрелки, PgUp/PgDn) активны только когда призрак включён и открыт не экран. Без призрака они ничего не делают.

## Файлы в игре

- `config/sevenstars_builds/*.txt` — сохранённые постройки и экспорты.
- `mods/sevenstars-builder-1.19.2-1.0.0.jar` — сам мод.

Путь к папке экземпляра зависит от лаунчера. Например, для Prism Launcher на macOS: `~/Library/Application Support/PrismLauncher/instances/<имя>/minecraft/`.

## Разработка

- Новые блоки и поведение призрака — в `BuildDefinition.java` и `GhostRenderer.java`.
- Новые команды и клавиши — в `SevenStarsBuilder.java`. Клавиши регистрируются в `KEYS`, текст подписей — в `lang/`.
- Формат скрипта — в `ScriptParser.java`. Экспорт мира — в `WorldExporter.java`.
- Переводы: добавляй строки сразу в оба файла `lang/ru_ru.json` и `lang/en_us.json`.

## Известные ограничения

- Экспорт не сохраняет содержимое сундуков, текст табличек, баннеров и сущности (мобов, предметы, лодки). Записываются только блоки и их состояния.
- Экспорт читает только загруженные чанки. Далеко от себя зона может сохраниться не полностью.
- Мод отправляет одну команду в тик, поэтому постройку из нескольких тысяч команд строит несколько минут.
- Призрак большой постройки может подвисать на секунду при первом показе, пока считаются видимые блоки.

## Статус

- Код написан, но в этом репозитории мод не собирался и не проверялся в игре. Сборка блокировалась сертификатом Forge.
- Проверка перед релизом: сборка, запуск, `/sb paste` на простой постройке, `/sb export` на небольшой зоне, проверка клавиш и подписей в настройках.

## Лицензия

Copyright (c) 2026 novimaksim. All Rights Reserved.

Unauthorized copying, modification, redistribution, or use of this software, its source code, compiled binaries, textures, models, or assets, via any medium, is strictly prohibited without explicit written permission from the author.