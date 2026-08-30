package fr.skynex.worldx.edit;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class NmsChunkWriter {

    private static boolean reflectionFailed = false;
    private static Method getHandleWorldMethod;
    private static Method getChunkMethod;
    private static Method setBlockStateMethod;
    private static Method getBlockStateFromData;
    private static Method getLightEngineMethod;
    private static Method checkBlockMethod;
    
    private static Class<?> blockPosClass;
    private static Class<?> mutableBlockPosClass;
    private static Method setMutableMethod;

    // ThreadLocal Cache Context to isolate threads and prevent race conditions
    private static final ThreadLocal<ThreadCacheContext> contextThreadLocal = ThreadLocal.withInitial(() -> {
        ThreadCacheContext context = new ThreadCacheContext();
        try {
            if (mutableBlockPosClass != null) {
                context.mutablePos = mutableBlockPosClass.getConstructor().newInstance();
            }
        } catch (Exception ignored) {
        }
        return context;
    });

    private static final Map<BlockData, Object> blockStateCache = new HashMap<>();

    private static class ThreadCacheContext {
        java.lang.ref.WeakReference<World> cachedWorldRef = null;
        Object cachedNmsLevel = null;
        Object cachedLightEngine = null;
        int cachedChunkX = Integer.MAX_VALUE;
        int cachedChunkZ = Integer.MAX_VALUE;
        Object cachedNmsChunk = null;
        Object mutablePos = null;
    }

    private static Class<?> getCraftClass(String name) throws ClassNotFoundException {
        try {
            return Class.forName("org.bukkit.craftbukkit." + name);
        } catch (ClassNotFoundException e) {
            String bukkPackage = Bukkit.getServer().getClass().getPackage().getName();
            return Class.forName(bukkPackage + "." + name);
        }
    }

    private static Method findMethodByParams(Class<?> clazz, Class<?> returnType, Class<?>... paramTypes) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (returnType != null && m.getReturnType() != returnType) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length == paramTypes.length) {
                boolean match = true;
                for (int i = 0; i < params.length; i++) {
                    if (!params[i].isAssignableFrom(paramTypes[i])) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    private static Method setUnsavedMethod;

    private static Method findMethodByName(Class<?> clazz, String name) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    static {
        try {
            Class<?> craftWorldClass = getCraftClass("CraftWorld");
            getHandleWorldMethod = craftWorldClass.getMethod("getHandle");

            Class<?> nmsLevelClass = Class.forName("net.minecraft.world.level.Level");
            try {
                getChunkMethod = nmsLevelClass.getMethod("getChunk", int.class, int.class);
            } catch (NoSuchMethodException e) {
                getChunkMethod = findMethodByParams(nmsLevelClass, null, int.class, int.class);
            }

            blockPosClass = Class.forName("net.minecraft.core.BlockPos");
            mutableBlockPosClass = Class.forName("net.minecraft.core.BlockPos$MutableBlockPos");
            setMutableMethod = mutableBlockPosClass.getMethod("set", int.class, int.class, int.class);

            Class<?> craftBlockDataClass = getCraftClass("block.data.CraftBlockData");
            getBlockStateFromData = craftBlockDataClass.getMethod("getState");

            Class<?> nmsChunkClass = Class.forName("net.minecraft.world.level.chunk.LevelChunk");
            Class<?> nmsBlockStateClass = Class.forName("net.minecraft.world.level.block.state.BlockState");
            
            try {
                setBlockStateMethod = nmsChunkClass.getMethod("setBlockState", blockPosClass, nmsBlockStateClass, boolean.class);
            } catch (NoSuchMethodException e) {
                setBlockStateMethod = findMethodByParams(nmsChunkClass, null, blockPosClass, nmsBlockStateClass, boolean.class);
            }

            try {
                setUnsavedMethod = nmsChunkClass.getMethod("setUnsaved", boolean.class);
            } catch (NoSuchMethodException e1) {
                try {
                    setUnsavedMethod = nmsChunkClass.getMethod("markUnsaved");
                } catch (NoSuchMethodException e2) {
                    setUnsavedMethod = findMethodByName(nmsChunkClass, "setUnsaved");
                }
            }

            try {
                getLightEngineMethod = nmsLevelClass.getMethod("getLightEngine");
            } catch (NoSuchMethodException e) {
                getLightEngineMethod = findMethodByParams(nmsLevelClass, Class.forName("net.minecraft.world.level.lighting.LevelLightEngine"));
            }

            Class<?> nmsLightEngineClass = Class.forName("net.minecraft.world.level.lighting.LevelLightEngine");
            try {
                checkBlockMethod = nmsLightEngineClass.getMethod("checkBlock", blockPosClass);
            } catch (NoSuchMethodException e) {
                checkBlockMethod = findMethodByParams(nmsLightEngineClass, null, blockPosClass);
            }

        } catch (Exception e) {
            reflectionFailed = true;
            Bukkit.getLogger().warning("[WorldX] Direct chunk writing reflection initialization failed: " + e.getMessage() + ". Using fallback API.");
        }
    }

    public static boolean setBlockDirectly(World world, int x, int y, int z, BlockData blockData) {
        if (reflectionFailed) {
            return false;
        }

        try {
            ThreadCacheContext ctx = contextThreadLocal.get();

            // 1. Get NMS Level (cached per thread with WeakReference to prevent World leaks)
            World cachedWorld = ctx.cachedWorldRef != null ? ctx.cachedWorldRef.get() : null;
            if (cachedWorld != world) {
                ctx.cachedWorldRef = new java.lang.ref.WeakReference<>(world);
                ctx.cachedNmsLevel = getHandleWorldMethod.invoke(world);
                ctx.cachedLightEngine = getLightEngineMethod.invoke(ctx.cachedNmsLevel);
                ctx.cachedChunkX = Integer.MAX_VALUE;
                ctx.cachedChunkZ = Integer.MAX_VALUE;
                ctx.cachedNmsChunk = null;
            }

            // 2. Get NMS LevelChunk (cached per thread by coordinates)
            int cx = x >> 4;
            int cz = z >> 4;
            if (ctx.cachedNmsChunk == null || ctx.cachedChunkX != cx || ctx.cachedChunkZ != cz) {
                ctx.cachedChunkX = cx;
                ctx.cachedChunkZ = cz;
                ctx.cachedNmsChunk = getChunkMethod.invoke(ctx.cachedNmsLevel, cx, cz);
            }

            // 3. Update Mutable BlockPos (no allocation, isolated per thread)
            setMutableMethod.invoke(ctx.mutablePos, x, y, z);

            // 4. Get NMS BlockState (globally cached, thread-safe read)
            Object nmsBlockState;
            synchronized (blockStateCache) {
                nmsBlockState = blockStateCache.get(blockData);
                if (nmsBlockState == null) {
                    nmsBlockState = getBlockStateFromData.invoke(blockData);
                    blockStateCache.put(blockData, nmsBlockState);
                }
            }

            // 5. Invoke setBlockState(blockPos, nmsBlockState, false)
            setBlockStateMethod.invoke(ctx.cachedNmsChunk, ctx.mutablePos, nmsBlockState, false);

            // 6. Mark chunk as unsaved/dirty so changes are saved to disk
            if (setUnsavedMethod != null) {
                try {
                    if (setUnsavedMethod.getParameterCount() == 1) {
                        setUnsavedMethod.invoke(ctx.cachedNmsChunk, true);
                    } else {
                        setUnsavedMethod.invoke(ctx.cachedNmsChunk);
                    }
                } catch (Exception ignored) {}
            }

            // 7. Recalculate lighting
            checkBlockMethod.invoke(ctx.cachedLightEngine, ctx.mutablePos);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isReflectionActive() {
        return !reflectionFailed;
    }

    public static void clearCache() {
        ThreadCacheContext ctx = contextThreadLocal.get();
        if (ctx != null) {
            ctx.cachedWorldRef = null;
            ctx.cachedNmsLevel = null;
            ctx.cachedLightEngine = null;
            ctx.cachedNmsChunk = null;
        }
        contextThreadLocal.remove();
        synchronized (blockStateCache) {
            blockStateCache.clear();
        }
    }
}
