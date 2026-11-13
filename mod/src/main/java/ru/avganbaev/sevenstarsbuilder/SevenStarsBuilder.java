package ru.avganbaev.sevenstarsbuilder;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Mod(SevenStarsBuilder.MODID)
public final class SevenStarsBuilder {
    public static final String MODID = "sevenstars_builder";
    private static final KeyMapping NEXT = new KeyMapping("key.sevenstars_builder.next", GLFW.GLFW_KEY_RIGHT_BRACKET, "category.sevenstars_builder");
    private static final KeyMapping ROTATE = new KeyMapping("key.sevenstars_builder.rotate", GLFW.GLFW_KEY_R, "category.sevenstars_builder");
    private static final KeyMapping CONFIRM = new KeyMapping("key.sevenstars_builder.confirm", GLFW.GLFW_KEY_ENTER, "category.sevenstars_builder");
    private static final KeyMapping CANCEL = new KeyMapping("key.sevenstars_builder.cancel", GLFW.GLFW_KEY_BACKSPACE, "category.sevenstars_builder");
    private static final List<BuildDefinition> BUILDS = List.of(BuildDefinition.europeanHouse(), BuildDefinition.starterTower());
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static int selected = 0, rotation = 0, total = 0;
    private static boolean preview = true;

    public SevenStarsBuilder() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::keys);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void keys(RegisterKeyMappingsEvent e) { e.register(NEXT); e.register(ROTATE); e.register(CONFIRM); e.register(CANCEL); }

    private static BuildDefinition build() { return BUILDS.get(selected); }

    private static BlockPos target() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        HitResult r = mc.player.pick(80.0D, 1.0F, false);
        if (r.getType() != HitResult.Type.BLOCK) return null;
        BlockHitResult h = (BlockHitResult) r;
        return h.getBlockPos().relative(h.getDirection());
    }

    private static void msg(LocalPlayer p, String s, boolean chat) { p.displayClientMessage(Component.literal("[7Stars] " + s), !chat); }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) { QUEUE.clear(); return; }
        while (NEXT.consumeClick()) { selected = (selected + 1) % BUILDS.size(); preview = true; msg(p, "Выбрано: " + build().name(), false); }
        while (ROTATE.consumeClick()) { rotation = (rotation + 1) & 3; preview = true; msg(p, "Поворот: " + (rotation * 90) + "°", false); }
        while (CANCEL.consumeClick()) {
            if (!QUEUE.isEmpty()) { QUEUE.clear(); msg(p, "Стройка остановлена", true); }
            else { preview = !preview; msg(p, preview ? "Призрак включён" : "Призрак выключен", false); }
        }
        while (CONFIRM.consumeClick()) {
            BlockPos pos = target();
            if (!preview || pos == null || !QUEUE.isEmpty()) continue;
            QUEUE.addAll(build().commands(pos, rotation));
            total = QUEUE.size();
            preview = false;
            msg(p, "Строю: " + build().name() + " (" + total + " команд)", true);
        }
        if (!QUEUE.isEmpty()) {
            p.commandUnsigned(QUEUE.poll());
            if (QUEUE.isEmpty()) msg(p, "Готово!", true);
            else if (QUEUE.size() % 10 == 0) msg(p, "Осталось команд: " + QUEUE.size() + " из " + total, false);
        }
    }

    @SubscribeEvent
    public void render(RenderLevelStageEvent e) {
        if (!preview || e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        BlockPos origin = target();
        if (origin == null) return;
        GhostRenderer.render(e.getPoseStack(), e.getCamera().getPosition(), origin, build().cells(rotation));
    }
}
