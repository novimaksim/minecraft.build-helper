package ru.avganbaev.sevenstarsbuilder;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;

@Mod(SevenStarsBuilder.MODID)
public final class SevenStarsBuilder {
    public static final String MODID = "sevenstars_builder";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CAT = "category.sevenstars_builder";
    private static final KeyMapping PASTE = new KeyMapping("key.sevenstars_builder.paste", GLFW.GLFW_KEY_V, CAT);
    private static final KeyMapping NEXT = new KeyMapping("key.sevenstars_builder.next", GLFW.GLFW_KEY_RIGHT_BRACKET, CAT);
    private static final KeyMapping ROTATE = new KeyMapping("key.sevenstars_builder.rotate", GLFW.GLFW_KEY_R, CAT);
    private static final KeyMapping CONFIRM = new KeyMapping("key.sevenstars_builder.confirm", GLFW.GLFW_KEY_ENTER, CAT);
    private static final KeyMapping CANCEL = new KeyMapping("key.sevenstars_builder.cancel", GLFW.GLFW_KEY_BACKSPACE, CAT);
    private static final List<BuildDefinition> BUILDS = new ArrayList<>();
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static int selected = 0, rotation = 0, total = 0;
    private static boolean preview = true;

    public SevenStarsBuilder() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::keys);
        MinecraftForge.EVENT_BUS.register(this);
        loadSaved();
    }

    private void keys(RegisterKeyMappingsEvent e) { e.register(PASTE); e.register(NEXT); e.register(ROTATE); e.register(CONFIRM); e.register(CANCEL); }

    private static Path dir() { return FMLPaths.CONFIGDIR.get().resolve("sevenstars_builds"); }

    private static void loadSaved() {
        try {
            Files.createDirectories(dir());
            try (Stream<Path> s = Files.list(dir())) {
                for (Path f : s.filter(p -> p.toString().endsWith(".txt")).sorted().toList()) {
                    try { BUILDS.add(ScriptParser.parse(Files.readString(f, StandardCharsets.UTF_8))); }
                    catch (ScriptParser.ParseException ex) { LOGGER.warn("[7Stars] {}: {}", f.getFileName(), ex.getMessage()); }
                }
            }
        } catch (IOException ex) { LOGGER.warn("[7Stars] cannot read builds", ex); }
    }

    private static BuildDefinition build() { return BUILDS.isEmpty() ? null : BUILDS.get(selected % BUILDS.size()); }

    private static void msg(String s, boolean chat) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null) p.displayClientMessage(Component.literal("[7Stars] " + s), !chat);
    }

    static void pasteFromClipboard() {
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        try {
            BuildDefinition d = ScriptParser.parse(text);
            int idx = -1;
            for (int i = 0; i < BUILDS.size(); i++) if (BUILDS.get(i).name().equalsIgnoreCase(d.name())) idx = i;
            if (idx >= 0) BUILDS.set(idx, d); else { BUILDS.add(d); idx = BUILDS.size() - 1; }
            selected = idx; preview = true;
            String file = d.name().replaceAll("[^\\p{L}\\p{N}_-]+", "_") + ".txt";
            try { Files.createDirectories(dir()); Files.writeString(dir().resolve(file), text, StandardCharsets.UTF_8); }
            catch (IOException ex) { msg("Не смог сохранить файл: " + ex.getMessage(), true); }
            msg("Загружено: " + d.name() + " (" + d.opCount() + " команд). Enter — построить", true);
        } catch (ScriptParser.ParseException ex) {
            msg("Ошибка скрипта: " + ex.getMessage(), true);
        }
    }

    private static void list() {
        if (BUILDS.isEmpty()) { msg("Построек нет. Скопируй скрипт и нажми V", true); return; }
        for (int i = 0; i < BUILDS.size(); i++) msg((i == selected ? "> " : "  ") + BUILDS.get(i).name(), true);
    }

    private static void use(String q) {
        for (int i = 0; i < BUILDS.size(); i++)
            if (BUILDS.get(i).name().toLowerCase().contains(q.toLowerCase())) { selected = i; preview = true; msg("Выбрано: " + BUILDS.get(i).name(), true); return; }
        msg("Не найдено: " + q, true);
    }

    private static BlockPos target() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        HitResult r = mc.player.pick(80.0D, 1.0F, false);
        if (r.getType() != HitResult.Type.BLOCK) return null;
        BlockHitResult h = (BlockHitResult) r;
        return h.getBlockPos().relative(h.getDirection());
    }

    @SubscribeEvent
    public void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("sb")
                .then(Commands.literal("paste").executes(c -> { pasteFromClipboard(); return 1; }))
                .then(Commands.literal("list").executes(c -> { list(); return 1; }))
                .then(Commands.literal("use").then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(c -> { use(StringArgumentType.getString(c, "name")); return 1; }))));
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) { QUEUE.clear(); return; }
        while (PASTE.consumeClick()) pasteFromClipboard();
        while (NEXT.consumeClick()) {
            if (BUILDS.isEmpty()) { list(); continue; }
            selected = (selected + 1) % BUILDS.size(); preview = true; msg("Выбрано: " + build().name(), false);
        }
        while (ROTATE.consumeClick()) { rotation = (rotation + 1) & 3; preview = true; msg("Поворот: " + (rotation * 90) + "°", false); }
        while (CANCEL.consumeClick()) {
            if (!QUEUE.isEmpty()) { QUEUE.clear(); msg("Стройка остановлена", true); }
            else { preview = !preview; msg(preview ? "Призрак включён" : "Призрак выключен", false); }
        }
        while (CONFIRM.consumeClick()) {
            BuildDefinition b = build();
            BlockPos pos = target();
            if (b == null || !preview || pos == null || !QUEUE.isEmpty()) continue;
            QUEUE.addAll(b.commands(pos, rotation));
            total = QUEUE.size();
            preview = false;
            msg("Строю: " + b.name() + " (" + total + " команд)", true);
        }
        if (!QUEUE.isEmpty()) {
            p.commandUnsigned(QUEUE.poll());
            if (QUEUE.isEmpty()) msg("Готово!", true);
            else if (QUEUE.size() % 10 == 0) msg("Осталось команд: " + QUEUE.size() + " из " + total, false);
        }
    }

    @SubscribeEvent
    public void render(RenderLevelStageEvent e) {
        BuildDefinition b = build();
        if (b == null || !preview || e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        BlockPos origin = target();
        if (origin == null) return;
        GhostRenderer.render(e.getPoseStack(), e.getCamera().getPosition(), origin, b.cells(rotation));
    }
}
