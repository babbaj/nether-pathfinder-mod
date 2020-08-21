package com.babbaj.pathfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;

public class PathRenderer {

    private static final Tessellator TESSELLATOR = new Tessellator(0x200000);
    private final List<BlockPos> path;

    public PathRenderer(List<BlockPos> path) {
        this.path = path;
    }

    public static Vec3d getInterpolatedAmount(Entity entity, double ticks) {
        return new Vec3d(
            (entity.posX - entity.lastTickPosX) * ticks,
            (entity.posY - entity.lastTickPosY) * ticks,
            (entity.posZ - entity.lastTickPosZ) * ticks);
    }

    private static Vec3d interpolatedPos(Entity entity, float partialTicks) {
        return new Vec3d(entity.lastTickPosX, entity.lastTickPosY, entity.lastTickPosZ)
            .add(getInterpolatedAmount(entity, partialTicks));
    }

    private static boolean isInNether() {
        return Minecraft.getMinecraft().player.dimension == -1;
    }

    private static void resetTranslation(float partialTicks) {
        Entity renderEntity = Minecraft.getMinecraft().getRenderViewEntity();
        Vec3d translation = interpolatedPos(renderEntity, partialTicks);
        TESSELLATOR.getBuffer().setTranslation(-translation.x, -translation.y, -translation.z);
    }

    void preRender(RenderWorldLastEvent event) {
        resetTranslation(event.getPartialTicks());

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.disableDepth();
    }

    void postRender() {
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent event) {
        if (!isInNether()) return;

        final Tessellator tessellator = TESSELLATOR;
        final BufferBuilder builder = tessellator.getBuffer();

        preRender(event);
        GlStateManager.glLineWidth(1.f);

        builder.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        path.forEach(p -> {
            builder.pos(p.getX(), p.getY(), p.getZ()).color(0, 0, 255, 255).endVertex();
        });
        tessellator.draw();

        postRender();
    }
}
