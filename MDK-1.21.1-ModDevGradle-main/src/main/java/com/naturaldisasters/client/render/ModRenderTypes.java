package com.naturaldisasters.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Translucent entity shading that does NOT write to the depth buffer. A depth-writing translucent funnel made
 * water, fire and other translucent things behind it disappear, because they were drawn later and failed the
 * depth test against the (invisible) funnel surface.
 */
public final class ModRenderTypes extends RenderType {
    private static final Function<ResourceLocation, RenderType> VAPOR = Util.memoize(texture -> create(
            "naturaldisasters_vapor", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 262144, false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(texture, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)));

    private static final ShaderStateShard POSITION_TEX_COLOR = new ShaderStateShard(GameRenderer::getPositionTexColorShader);

    /** Billboarded cloud puffs: unlit (colour is computed from the sky) and unfogged so distant storms stay visible. */
    private static final Function<ResourceLocation, RenderType> CLOUD = Util.memoize(texture -> create(
            "naturaldisasters_cloud", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 262144, false, false,
            CompositeState.builder()
                    .setShaderState(POSITION_TEX_COLOR)
                    .setTextureState(new TextureStateShard(texture, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false)));

    private ModRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean crumbling,
                           boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, crumbling, sort, setup, clear);
    }

    public static RenderType cloud(ResourceLocation texture) {
        return CLOUD.apply(texture);
    }

    public static RenderType vapor(ResourceLocation texture) {
        return VAPOR.apply(texture);
    }
}
