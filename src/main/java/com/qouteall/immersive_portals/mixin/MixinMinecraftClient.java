package com.qouteall.immersive_portals.mixin;

import com.qouteall.immersive_portals.Globals;
import com.qouteall.immersive_portals.ModMain;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {
    @Inject(at = @At("HEAD"), method = "init()V")
    private void init(CallbackInfo info) {
        System.out.println("This line is printed by an example mod mixin!");
    }
    
    @Inject(
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/world/ClientWorld;tick(Ljava/util/function/BooleanSupplier;)V"
        ),
        method = "Lnet/minecraft/client/MinecraftClient;tick()V"
    )
    private void onClientTick(CallbackInfo ci) {
        ModMain.clientTickSignal.emit();
    }
    
    @Inject(
        method = "Lnet/minecraft/client/MinecraftClient;setWorld(Lnet/minecraft/client/world/ClientWorld;)V",
        at = @At("TAIL")
    )
    private void onSetWorld(ClientWorld clientWorld_1, CallbackInfo ci) {
        if (clientWorld_1 == null) {
            Globals.clientWorldLoader.cleanUp();
        }
    }
}
