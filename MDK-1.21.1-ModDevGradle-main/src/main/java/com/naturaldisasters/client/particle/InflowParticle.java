package com.naturaldisasters.client.particle;

import com.naturaldisasters.entity.TornadoEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;

/**
 * Low dust dragged across the ground by the inflow winds: it spirals in towards the tornado from well outside
 * the funnel, speeding up as it gets closer, and vanishes into the debris cloud at the base.
 */
public class InflowParticle extends TextureSheetParticle {
    private final TornadoEntity owner;
    private final SpriteSet sprites;
    private final float baseAlpha;
    private float radius;
    private float angle;
    private final float heightAboveGround;

    public InflowParticle(ClientLevel level, TornadoEntity owner, SpriteSet sprites, float radius, float angle,
                          float r, float g, float b) {
        super(level, owner.getX(), owner.getY(), owner.getZ());
        this.owner = owner;
        this.sprites = sprites;
        this.radius = radius;
        this.angle = angle;
        this.heightAboveGround = 0.2f + random.nextFloat() * 1.5f;
        this.hasPhysics = false;
        this.gravity = 0;
        this.lifetime = 60 + random.nextInt(40);
        this.quadSize = 0.3f + random.nextFloat() * 0.6f;
        this.baseAlpha = 0.25f + random.nextFloat() * 0.2f;
        this.alpha = 0;
        setColor(r, g, b);
        pickSprite(sprites);
        place();
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        float core = owner.getCoreRadius();
        if (age++ >= lifetime || owner.isRemoved() || radius < core * 0.9f) {
            remove();
            return;
        }
        // conservation of angular momentum: the closer it gets, the faster it turns
        angle += owner.spinDirection() * Math.min(0.25f, 1.6f / Math.max(2f, radius));
        radius -= 0.25f + 6f / Math.max(4f, radius);
        place();
        setSpriteFromAge(sprites);
        float progress = age / (float) lifetime;
        alpha = baseAlpha * Math.min(1f, progress * 4f) * Math.min(1f, (1f - progress) * 3f);
    }

    private void place() {
        setPos(owner.getX() + Mth.cos(angle) * radius, owner.getY() + heightAboveGround, owner.getZ() + Mth.sin(angle) * radius);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
