package mods.flammpfeil.slashblade.client.renderer.model;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Created by Furia on 2016/02/06.
 */
public class BladeModelManager {

    private static final class SingletonHolder {
        private static final BladeModelManager instance = new BladeModelManager();
    }

    public static BladeModelManager getInstance() {
        return SingletonHolder.instance;
    }

    WavefrontObject defaultModel;
    public static final Identifier resourceDefaultModel = Identifier.fromNamespaceAndPath("slashblade", "model/blade.obj");
    public static final Identifier resourceDefaultTexture = Identifier.fromNamespaceAndPath("slashblade", "model/blade.png");

    public static final Identifier resourceDurabilityModel = Identifier.fromNamespaceAndPath("slashblade", "model/util/durability.obj");
    public static final Identifier resourceDurabilityTexture = Identifier.fromNamespaceAndPath("slashblade", "model/util/durability.png");

    LoadingCache<Identifier, WavefrontObject> cache;

    private BladeModelManager() {
        cache = CacheBuilder.newBuilder()
                .build(
                CacheLoader.asyncReloading(new CacheLoader<Identifier, WavefrontObject>() {
                    @Override
                    public WavefrontObject load(Identifier key) throws Exception {
                        try{
                            return new WavefrontObject(key);
                        }catch(Exception e){
                            return defaultModel;
                        }
                    }

                }, Executors.newCachedThreadPool())
        );
    }

    public void reload(ResourceManager resources){
        cache.invalidateAll();

        defaultModel = new WavefrontObject(resourceDefaultModel);
    }

    public WavefrontObject getModel(Identifier loc) {
        if(defaultModel == null) defaultModel = new WavefrontObject(resourceDefaultModel);
        if(loc != null){
            try {
                return cache.get(loc);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return defaultModel;
    }

}
