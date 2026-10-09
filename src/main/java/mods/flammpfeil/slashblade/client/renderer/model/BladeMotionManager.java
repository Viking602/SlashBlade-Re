package mods.flammpfeil.slashblade.client.renderer.model;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import jp.nyatla.nymmd.MmdException;
import jp.nyatla.nymmd.MmdVmdMotionMc;
import mods.flammpfeil.slashblade.SlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ComboState;
import mods.flammpfeil.slashblade.init.DefaultResources;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.bus.api.SubscribeEvent;

import java.io.IOException;
import java.util.concurrent.Executors;

import static mods.flammpfeil.slashblade.init.DefaultResources.ExMotionLocation;

/**
 * Created by Furia on 2016/02/06.
 */
public class BladeMotionManager {

    private static final class SingletonHolder {
        private static final BladeMotionManager instance = new BladeMotionManager();
    }

    public static BladeMotionManager getInstance() {
        return SingletonHolder.instance;
    }

    MmdVmdMotionMc defaultMotion;

    LoadingCache<Identifier, MmdVmdMotionMc> cache;

    private BladeMotionManager() {
        cache = CacheBuilder.newBuilder()
                .build(
                CacheLoader.asyncReloading(new CacheLoader<Identifier, MmdVmdMotionMc>() {
                    @Override
                    public MmdVmdMotionMc load(Identifier key) throws Exception {
                        try{
                            return new MmdVmdMotionMc(key);
                        }catch(Exception e){
                            e.printStackTrace();
                            return defaultMotion;
                        }
                    }

                }, Executors.newCachedThreadPool())
        );
    }

    public void reload(ResourceManager resources){
        cache.invalidateAll();

        try {
            defaultMotion = new MmdVmdMotionMc(ExMotionLocation);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (MmdException e) {
            e.printStackTrace();
        }
    }

    public MmdVmdMotionMc getMotion(Identifier loc) {
        if(defaultMotion == null) reload(net.minecraft.client.Minecraft.getInstance().getResourceManager());
        if(loc != null){
            try {
                return cache.get(loc);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return defaultMotion;
    }

}
