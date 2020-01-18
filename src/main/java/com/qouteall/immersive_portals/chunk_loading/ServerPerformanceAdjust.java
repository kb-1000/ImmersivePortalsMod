package com.qouteall.immersive_portals.chunk_loading;

import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.McHelper;
import com.qouteall.immersive_portals.ModMain;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.MetricsData;

import java.util.Arrays;
import java.util.WeakHashMap;

//dynamically adjust the player's loading distance
//if a player is loading many chunks through portals then his loading distance will decrease
//if a player is travelling too fast (through or not through portal) then his loading distance will decrease
//if the server memory is tight then loading distance will decrease
public class ServerPerformanceAdjust {
    private static class PlayerProfile {
        public int loadingChunkNum = 0;
        
        public int chunkLoadingFactor = 0;
        
        public void tick() {
            //exponentially decrease
            chunkLoadingFactor = ((int) (((double) chunkLoadingFactor) * 0.999));
        }
        
        public void onNewChunkLoaded() {
            chunkLoadingFactor++;
            loadingChunkNum++;
        }
        
        public void onNewChunkUnloaded() {
            loadingChunkNum--;
        }
        
        public boolean shouldLoadFewerChunks() {
            return isTravellingFast() || isLoadingTooMuchChunks();
        }
        
        public boolean isLoadingTooMuchChunks() {
            int valve = McHelper.getRenderDistanceOnServer() * McHelper.getRenderDistanceOnServer() * 20;
            return loadingChunkNum > valve;
        }
    
        public boolean isTravellingFast() {
            int valve = McHelper.getRenderDistanceOnServer() * McHelper.getRenderDistanceOnServer() * 30;
            return chunkLoadingFactor > valve;
        }
    }
    
    private static double serverLagFactor = 0;
    private static boolean isServerLagging = false;
    private static WeakHashMap<ServerPlayerEntity, PlayerProfile> playerProfileMap = new WeakHashMap<>();
    
    public static void init() {
        NewChunkTrackingGraph.beginWatchChunkSignal.connect(
            (player, chunkPos) -> getPlayerProfile(player).onNewChunkLoaded()
        );
        NewChunkTrackingGraph.endWatchChunkSignal.connect(
            (player, chunkPos) -> getPlayerProfile(player).onNewChunkUnloaded()
        );
        ModMain.postServerTickSignal.connect(ServerPerformanceAdjust::tick);
    }
    
    private static PlayerProfile getPlayerProfile(ServerPlayerEntity player) {
        return playerProfileMap.computeIfAbsent(
            player, k -> new PlayerProfile()
        );
    }
    
    private static void tick() {
        playerProfileMap.values().forEach(PlayerProfile::tick);
    
        //exponentially decrease
        serverLagFactor *= 0.998;
    
        MetricsData profile = McHelper.getServer().getMetricsData();
        double averageTickTimeNano = Arrays.stream(profile.getSamples()).average().orElse(0);
        if (averageTickTimeNano > Helper.secondToNano(1.0 / 20)) {
            serverLagFactor += 1;
        }
    
        if (!isServerLagging) {
            if (serverLagFactor > 100) {
                Helper.log("Server is lagging. Reduce Loading Distance");
                isServerLagging = true;
            }
        }
        else {
            if (serverLagFactor < 30) {
                Helper.log("Server is not lagging now. Return to Normal Loading Distance");
                isServerLagging = false;
            }
        }
    }
    
    public static int getGeneralLoadingDistance() {
        int distance = McHelper.getRenderDistanceOnServer();
        if (isServerLagging) {
            return distance / 2;
        }
        return distance;
    }
    
    public static int getPlayerLoadingDistance(ServerPlayerEntity player) {
        int distance = McHelper.getRenderDistanceOnServer();
        if (isServerLagging) {
            return distance / 2;
        }
        PlayerProfile profile = getPlayerProfile(player);
        if (profile.shouldLoadFewerChunks()) {
            return distance / 2;
        }
        else {
            return distance;
        }
    }
    
    public static boolean getIsServerLagging() {
        return isServerLagging;
    }
    
}
