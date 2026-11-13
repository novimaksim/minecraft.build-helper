package ru.avganbaev.sevenstarsbuilder;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Matrix4f;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class GhostRenderer {
    private GhostRenderer() {}

    public static void render(PoseStack pose, Vec3 cam, BlockPos o, List<BuildDefinition.Cell> cells) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableTexture();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f m = pose.last().pose();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (BuildDefinition.Cell c : cells) {
            float x = (float) (o.getX() + c.x() - cam.x), y = (float) (o.getY() + c.y() - cam.y), z = (float) (o.getZ() + c.z() - cam.z);
            int h = c.block().hashCode();
            float r = 0.35f + ((h & 0xFF) / 255f) * 0.4f, g = 0.55f + (((h >> 8) & 0xFF) / 255f) * 0.4f, bl = 0.6f + (((h >> 16) & 0xFF) / 255f) * 0.4f;
            cube(b, m, x, y, z, r, g, bl, 0.4f);
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }

    private static void cube(BufferBuilder b, Matrix4f m, float x, float y, float z, float r, float g, float bl, float a) {
        float x2 = x + 1, y2 = y + 1, z2 = z + 1;
        quad(b, m, x, y, z, x2, y, z, x2, y2, z, x, y2, z, r, g, bl, a);
        quad(b, m, x, y, z2, x, y2, z2, x2, y2, z2, x2, y, z2, r, g, bl, a);
        quad(b, m, x, y, z, x, y, z2, x2, y, z2, x2, y, z, r, g, bl, a);
        quad(b, m, x, y2, z, x2, y2, z, x2, y2, z2, x, y2, z2, r, g, bl, a);
        quad(b, m, x, y, z, x, y2, z, x, y2, z2, x, y, z2, r, g, bl, a);
        quad(b, m, x2, y, z, x2, y, z2, x2, y2, z2, x2, y2, z, r, g, bl, a);
    }

    private static void quad(BufferBuilder b, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float r, float g, float bl, float a) {
        b.vertex(m, ax, ay, az).color(r, g, bl, a).endVertex();
        b.vertex(m, bx, by, bz).color(r, g, bl, a).endVertex();
        b.vertex(m, cx, cy, cz).color(r, g, bl, a).endVertex();
        b.vertex(m, dx, dy, dz).color(r, g, bl, a).endVertex();
    }
}
