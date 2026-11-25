package com.lycoris.loconautics.content.camera;

import com.lycoris.loconautics.LoconauticsConstants;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Client-side camera capture pipeline.
 *
 * Frames are rendered locally into small off-screen targets. Only camera
 * identifiers/positions are synchronized by the game; video pixels never
 * travel over the network.
 */
public final class CameraFeedManager {

    public static final int WIDTH = 320;
    public static final int HEIGHT = 180;
    private static final long FRAME_INTERVAL_MS = 200L;

    private static final Map<GlobalPos, RenderTarget> TARGETS = new HashMap<>();
    private static final Map<GlobalPos, Long> LAST_CAPTURE = new HashMap<>();
    private static final Set<GlobalPos> REQUESTED = new HashSet<>();
    private static final Set<GlobalPos> FAILED = new HashSet<>();

    @Nullable
    private static ShaderInstance shader;
    @Nullable
    private static GlobalPos currentlyCaptured;

    private static final RenderType CAMERA_SCREEN = RenderType.create(
            "loconautics_camera_screen",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(CameraFeedManager::shader))
                    .createCompositeState(false));

    private CameraFeedManager() {
    }

    @Nullable
    private static ShaderInstance shader() {
        return shader;
    }

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            ShaderInstance instance = new ShaderInstance(event.getResourceProvider(),
                    LoconauticsConstants.id("camera_feed"), DefaultVertexFormat.POSITION_TEX);
            event.registerShader(instance, loaded -> shader = loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("Loconautics camera feed shader could not be loaded", exception);
        }
    }

    public static void request(GlobalPos camera) {
        if (currentlyCaptured == null) {
            REQUESTED.add(camera);
        }
    }

    /**
     * Called after the normal world render. Rendering from this hook avoids
     * recursively capturing a monitor that happens to be visible in the feed.
     */
    public static void captureRequested(DeltaTracker partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || REQUESTED.isEmpty() || currentlyCaptured != null) {
            REQUESTED.clear();
            return;
        }

        Set<GlobalPos> requested = Set.copyOf(REQUESTED);
        REQUESTED.clear();
        long now = System.currentTimeMillis();

        for (GlobalPos globalPos : requested) {
            if (!globalPos.dimension().equals(mc.level.dimension())) continue;
            if (now - LAST_CAPTURE.getOrDefault(globalPos, 0L) < FRAME_INTERVAL_MS) continue;
            if (!mc.level.isLoaded(globalPos.pos())) continue;
            if (!(mc.level.getBlockEntity(globalPos.pos()) instanceof CameraBlockEntity cameraBlockEntity)) continue;

            RenderTarget target = TARGETS.computeIfAbsent(globalPos,
                    ignored -> new TextureTarget(WIDTH, HEIGHT, true, Minecraft.ON_OSX));
            captureOne(mc, mc.level, globalPos, cameraBlockEntity, target, partialTick);
            LAST_CAPTURE.put(globalPos, now);
        }
    }

    private static void captureOne(Minecraft mc, Level level, GlobalPos globalPos,
                                   CameraBlockEntity cameraBlockEntity, RenderTarget target,
                                   DeltaTracker partialTick) {
        Entity oldCameraEntity = mc.cameraEntity;
        RenderTarget oldMainTarget = mc.getMainRenderTarget();
        Camera camera = mc.gameRenderer.getMainCamera();
        float oldEyeHeight = camera.eyeHeight;
        float oldEyeHeightOld = camera.eyeHeightOld;
        boolean oldOutline = true;
        Marker cameraEntity = new Marker(EntityType.MARKER, level);
        Vec3 cameraPosition = Vec3.atCenterOf(globalPos.pos()).add(
                cameraBlockEntity.facing().getStepX() * 0.18,
                0.08,
                cameraBlockEntity.facing().getStepZ() * 0.18);

        try {
            cameraEntity.setPos(cameraPosition);
            cameraEntity.setYRot(cameraBlockEntity.yaw());
            cameraEntity.setXRot(cameraBlockEntity.pitch());
            mc.cameraEntity = cameraEntity;
            camera.setup(level, cameraEntity, false, false,
                    partialTick.getGameTimeDeltaPartialTick(false));
            camera.eyeHeight = camera.eyeHeightOld = 0.0f;

            currentlyCaptured = globalPos;
            target.clear(true);
            target.bindWrite(true);
            mc.mainRenderTarget = target;
            mc.gameRenderer.setRenderBlockOutline(false);
            mc.gameRenderer.renderLevel(DeltaTracker.ONE);
            FAILED.remove(globalPos);
        } catch (Throwable throwable) {
            if (FAILED.add(globalPos)) {
                LoconauticsConstants.LOGGER.error("Failed to render camera feed at {}", globalPos, throwable);
            }
        } finally {
            currentlyCaptured = null;
            mc.cameraEntity = oldCameraEntity;
            mc.mainRenderTarget = oldMainTarget;
            oldMainTarget.bindWrite(true);
            camera.eyeHeight = oldEyeHeight;
            camera.eyeHeightOld = oldEyeHeightOld;
            Entity restoreEntity = oldCameraEntity == null ? mc.player : oldCameraEntity;
            camera.setup(level, restoreEntity, !mc.options.getCameraType().isFirstPerson(),
                    mc.options.getCameraType().isMirrored(),
                    partialTick.getGameTimeDeltaPartialTick(true));
            camera.eyeHeight = oldEyeHeight;
            camera.eyeHeightOld = oldEyeHeightOld;
            mc.gameRenderer.setRenderBlockOutline(oldOutline);
            cameraEntity.discard();
        }
    }

    public static void renderMonitor(GlobalPos camera, PoseStack pose, MultiBufferSource buffers) {
        RenderTarget target = TARGETS.get(camera);
        ShaderInstance activeShader = shader;
        if (target == null || activeShader == null || currentlyCaptured != null) return;

        activeShader.setSampler("DiffuseSampler", target.getColorTextureId());
        if (activeShader.MODEL_VIEW_MATRIX != null) {
            activeShader.MODEL_VIEW_MATRIX.set(pose.last().pose());
        }
        if (activeShader.PROJECTION_MATRIX != null) {
            activeShader.PROJECTION_MATRIX.set(Minecraft.getInstance().gameRenderer.getProjectionMatrix(90.0f));
        }
        activeShader.apply();

        VertexConsumer consumer = buffers.getBuffer(CAMERA_SCREEN);
        var matrix = pose.last().pose();
        float left = 2.5f / 16.0f;
        float right = 5.5f / 16.0f;
        float front = 1.5f / 16.0f;
        float back = 6.5f / 16.0f;
        float y = 2.035f / 16.0f;

        consumer.addVertex(matrix, left, y, front).setUv(0, 0).setColor(0xFFFFFFFF);
        consumer.addVertex(matrix, left, y, back).setUv(0, 1).setColor(0xFFFFFFFF);
        consumer.addVertex(matrix, right, y, back).setUv(1, 1).setColor(0xFFFFFFFF);
        consumer.addVertex(matrix, right, y, front).setUv(1, 0).setColor(0xFFFFFFFF);

        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(CAMERA_SCREEN);
        }
        activeShader.clear();
    }

    public static boolean isCapturing() {
        return currentlyCaptured != null;
    }

    public static void clear() {
        for (RenderTarget target : TARGETS.values()) {
            target.destroyBuffers();
        }
        TARGETS.clear();
        LAST_CAPTURE.clear();
        REQUESTED.clear();
        FAILED.clear();
        currentlyCaptured = null;
    }
}
