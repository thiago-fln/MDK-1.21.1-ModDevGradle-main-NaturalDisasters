package com.naturaldisasters.client;

import com.naturaldisasters.ClientConfig;
import com.naturaldisasters.NaturalDisasters;

import java.lang.reflect.Method;

/**
 * Detects an active Iris (or Oculus) shader pack through Iris' public API, by reflection so Iris stays an
 * optional dependency. When a pack is active the mod switches to rendering paths that shader packs understand:
 * tornado funnels are drawn as regular translucent entities (lit and shadowed by the pack) and cloud puffs use the
 * entity shader instead of the unlit position/texture/colour shader.
 */
public final class IrisCompat {
    private static Object api;
    private static Method inUse;
    private static boolean lookedUp;
    private static boolean cached;
    private static long lastCheck;

    private IrisCompat() {}

    public static boolean shaderPackActive() {
        ClientConfig.ShaderMode mode = ClientConfig.SHADER_COMPAT.get();
        if (mode == ClientConfig.ShaderMode.ON) return true;
        if (mode == ClientConfig.ShaderMode.OFF) return false;
        long now = System.currentTimeMillis();
        if (now - lastCheck < 1000) return cached;
        lastCheck = now;
        cached = query();
        return cached;
    }

    private static boolean query() {
        if (!lookedUp) {
            lookedUp = true;
            try {
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = c.getMethod("getInstance").invoke(null);
                inUse = c.getMethod("isShaderPackInUse");
                NaturalDisasters.LOGGER.info("Iris detected: shader pack compatibility available");
            } catch (ReflectiveOperationException | LinkageError e) {
                api = null;
            }
        }
        if (api == null) return false;
        try {
            return (boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
