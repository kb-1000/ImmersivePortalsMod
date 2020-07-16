package com.qouteall.immersive_portals.render;

import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.ducks.IEMatrix4f;
import com.qouteall.immersive_portals.my_util.DQuaternion;
import com.qouteall.immersive_portals.my_util.RotationHelper;
import com.qouteall.immersive_portals.portal.Portal;
import com.qouteall.immersive_portals.render.context_management.RenderInfo;
import com.qouteall.immersive_portals.render.context_management.RenderStates;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Pair;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public class TransformationManager {
    public static DQuaternion inertialRotation;
    private static long interpolationStartTime = 0;
    private static long interpolationEndTime = 1;
    public static DQuaternion portalRotation;
    
    public static final MinecraftClient client = MinecraftClient.getInstance();
    
    public static void processTransformation(Camera camera, MatrixStack matrixStack) {
        matrixStack.peek().getModel().loadIdentity();
        matrixStack.peek().getNormal().loadIdentity();
        
        DQuaternion cameraRotation = DQuaternion.getCameraRotation(camera.getPitch(), camera.getYaw());
    
        Pair<Double, Double> heh = DQuaternion.getPitchYawFromRotation(cameraRotation);
        
        DQuaternion finalRotation = getFinalRotation(cameraRotation);
        
        matrixStack.multiply(finalRotation.toMcQuaternion());
        
        RenderInfo.applyAdditionalTransformations(matrixStack);
        
    }
    
    public static boolean isAnimationRunning() {
        double progress = (RenderStates.renderStartNanoTime - interpolationStartTime) /
            ((double) interpolationEndTime - interpolationStartTime);
        
        return progress >= -0.1 && progress <= 1.1;
    }
    
    public static DQuaternion getFinalRotation(DQuaternion cameraRotation) {
        double progress = (RenderStates.renderStartNanoTime - interpolationStartTime) /
            ((double) interpolationEndTime - interpolationStartTime);
        
        if (progress < 0 || progress >= 1) {
            return cameraRotation;
        }
        
        progress = mapProgress(progress);
        
        return DQuaternion.interpolate(
            inertialRotation,
            cameraRotation,
            progress
        );
    }
    
    private static double mapProgress(double progress) {
//        return progress;
        return Math.sin(progress * (Math.PI / 2));
//        return Math.sqrt(1 - (1 - progress) * (1 - progress));
    }
    
    /**
     * Entity#getRotationVector(float, float)
     */
    private static float getYawFromViewVector(Vec3d viewVector) {
        double lx = viewVector.x;
        double lz = viewVector.z;
        double len = Math.sqrt(lx * lx + lz * lz);
        lx /= len;
        lz /= len;
        
        if (lz >= 0) {
            return (float) -Math.asin(lx) / 0.017453292F;
        }
        else {
            if (lx > 0) {
                return (float) -Math.acos(lz) / 0.017453292F;
            }
            else {
                return (float) Math.acos(lz) / 0.017453292F;
            }
        }
    }
    
    private static float getPitchFromViewVector(Vec3d viewVector) {
        return (float) -Math.asin(viewVector.y) / 0.017453292F;
    }
    
    public static void onClientPlayerTeleported(
        Portal portal
    ) {
        if (portal.rotation != null) {
            ClientPlayerEntity player = client.player;
    
            DQuaternion camRot = DQuaternion.getCameraRotation(player.pitch, player.yaw);
            DQuaternion currentCameraRotation = getFinalRotation(camRot);
            
            DQuaternion visualRotation =
                currentCameraRotation.hamiltonProduct(
                    DQuaternion.fromMcQuaternion(portal.rotation).getConjugated()
                );
            
            Vec3d oldViewVector = player.getRotationVec(RenderStates.tickDelta);
            Vec3d newViewVector;
            
            Pair<Double, Double> pitchYaw = DQuaternion.getPitchYawFromRotation(visualRotation);
            
            player.yaw = (float) (double)(pitchYaw.getRight());
            player.pitch = (float) (double)(pitchYaw.getLeft());
            
            if (player.pitch > 90) {
                player.pitch = 90 - (player.pitch - 90);
            }
            else if (player.pitch < -90) {
                player.pitch = -90 + (-90 - player.pitch);
            }
            
            player.prevYaw = player.yaw;
            player.prevPitch = player.pitch;
            player.renderYaw = player.yaw;
            player.renderPitch = player.pitch;
            player.lastRenderYaw = player.renderYaw;
            player.lastRenderPitch = player.renderPitch;
            
            DQuaternion newCameraRotation = DQuaternion.getCameraRotation(player.pitch, player.yaw);
            
            if (!DQuaternion.isClose(newCameraRotation, visualRotation, 0.001f)) {
                inertialRotation = visualRotation;
                interpolationStartTime = RenderStates.renderStartNanoTime;
                interpolationEndTime = interpolationStartTime +
                    Helper.secondToNano(1);
            }
            
            updateCamera(client);
        }
    }
    
    private static void updateCamera(MinecraftClient client) {
        Camera camera = client.gameRenderer.getCamera();
        camera.update(
            client.world,
            client.player,
            client.options.perspective > 0,
            client.options.perspective == 2,
            RenderStates.tickDelta
        );
    }
    
    public static Matrix4f getMirrorTransformation(Vec3d normal) {
        float x = (float) normal.x;
        float y = (float) normal.y;
        float z = (float) normal.z;
        float[] arr =
            new float[]{
                1 - 2 * x * x, 0 - 2 * x * y, 0 - 2 * x * z, 0,
                0 - 2 * y * x, 1 - 2 * y * y, 0 - 2 * y * z, 0,
                0 - 2 * z * x, 0 - 2 * z * y, 1 - 2 * z * z, 0,
                0, 0, 0, 1
            };
        Matrix4f matrix = new Matrix4f();
        ((IEMatrix4f) (Object) matrix).loadFromArray(arr);
        return matrix;
    }

}
