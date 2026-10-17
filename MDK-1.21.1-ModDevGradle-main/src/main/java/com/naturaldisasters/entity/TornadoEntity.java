package com.naturaldisasters.entity;

import com.naturaldisasters.Config;
import com.naturaldisasters.NaturalDisasters;
import com.naturaldisasters.client.ClientHooks;
import com.naturaldisasters.network.ShakePayload;
import com.naturaldisasters.weather.DisasterData;
import com.naturaldisasters.weather.WeatherEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Base tornado (classic F1-F5). The entity itself is just an invisible centre point; it produces a spiral force
 * field (Rankine vortex: tangential speed grows linearly inside the core and decays as 1/r outside) that drags
 * mobs, players, items and falling blocks inward, around and up. Blocks are destroyed according to their
 * explosion resistance compared to the tornado's Fujita category. Variants extend this class.
 */
public class TornadoEntity extends Entity {
    public static final ResourceKey<DamageType> TORNADO_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, NaturalDisasters.id("tornado"));

    private static final EntityDataAccessor<Float> DATA_INTENSITY = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEADING = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_CLOCKWISE = SynchedEntityData.defineId(TornadoEntity.class, EntityDataSerializers.BOOLEAN);

    /** Highest explosion resistance destroyed outright per Fujita category (index 0 unused). Stone (6.0) only gets scratched. */
    protected static final float[] DESTROY_POWER = {0.7f, 1.0f, 2.0f, 3.0f, 4.5f, 5.5f};

    protected int life;
    protected int maxLife = 3600;
    protected double heading;
    protected double speed = 0.13;
    protected int stormId = -1;
    /** Blocks currently held in the air by this tornado, oldest first. */
    protected final ArrayDeque<FallingBlockEntity> debris = new ArrayDeque<>();

    /** Client-side handles (Object so the class stays loadable on dedicated servers). */
    public Object clientSound;
    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;

    public TornadoEntity(EntityType<? extends TornadoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    @Nullable
    public static TornadoEntity spawn(ServerLevel level, TornadoVariant variant, double x, double z, float intensity,
                                      double heading, int life, int stormId) {
        TornadoEntity t = variant.entityType().create(level);
        if (t == null) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        t.moveTo(x, y, z, 0f, 0f);
        t.setIntensity(intensity);
        t.heading = heading;
        t.entityData.set(DATA_HEADING, (float) heading);
        t.entityData.set(DATA_CLOCKWISE, DisasterData.get(level).southern);
        t.maxLife = Math.max(200, life);
        t.stormId = stormId;
        t.speed = 0.09 + level.random.nextDouble() * 0.08;
        t.onCreated(level.random);
        level.addFreshEntity(t);
        return t;
    }

    protected void onCreated(RandomSource random) {}

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_INTENSITY, 1f);
        builder.define(DATA_SCALE, 0f);
        builder.define(DATA_HEADING, 0f);
        builder.define(DATA_CLOCKWISE, false);
    }

    // ------------------------------------------------------------------ properties shared by server physics and client rendering

    public TornadoVariant getVariant() {
        return TornadoVariant.CLASSIC;
    }

    public float getIntensity() {
        return entityData.get(DATA_INTENSITY);
    }

    public void setIntensity(float intensity) {
        entityData.set(DATA_INTENSITY, Mth.clamp(intensity, 1f, 5.99f));
    }

    public float getScale() {
        return entityData.get(DATA_SCALE);
    }

    protected void setScale(float scale) {
        entityData.set(DATA_SCALE, scale);
    }

    public int getCategory() {
        return Mth.clamp((int) getIntensity(), 1, 5);
    }

    /** Radius (blocks) of the destructive core at ground level. Grows with intensity. */
    public float getCoreRadius() {
        return (4f + getIntensity() * 4.5f) * Math.max(0.15f, getScale()) * coreMultiplier();
    }

    // ---- shape hooks overridden by variants (used by physics, particles and the renderer)

    /** Width multiplier of this variant relative to a classic tornado of the same category. */
    public float coreMultiplier() {
        return 1f;
    }

    /** Funnel width profile: multiplier of the core radius at height fraction t. */
    public float radiusProfile(double t) {
        return (float) (0.55 + 2.8 * Math.pow(t, 2.3));
    }

    /** How strongly the column bends and snakes. */
    public float swayFactor() {
        return 1f;
    }

    /** Heading of the ground motion in radians (synced so the funnel can lean back against it). */
    public float getHeading() {
        return entityData.get(DATA_HEADING);
    }

    /** +1 = counter-clockwise (northern hemisphere), -1 = clockwise (southern hemisphere). */
    public float spinDirection() {
        return entityData.get(DATA_CLOCKWISE) ? -1f : 1f;
    }

    /** Spawns the tornado already fully formed instead of growing from a thin touchdown. */
    public void skipFormation() {
        life = Math.max(life, 120);
        setScale(1f);
    }

    public float getPullRadius() {
        return getCoreRadius() * 2.6f + 6f;
    }

    /** Height of the storm cloud base the funnel hangs from (shared with the storm cloud renderer). */
    public static final float CLOUD_BASE_Y = 176f;

    public double getFunnelTopY() {
        return Math.max(getY() + 60, CLOUD_BASE_Y);
    }

    /** Angular speed of the visible rotation in radians per tick. Faster for stronger tornadoes. */
    public float getSpinSpeed() {
        return 0.12f + getIntensity() * 0.05f;
    }

    /** Funnel radius at height fraction t (0 = ground, 1 = cloud base): narrow rope at the ground widening into the cloud. */
    public float funnelRadius(double t) {
        return getCoreRadius() * radiusProfile(t);
    }

    public static float funnelRadius(float core, double t) {
        return core * (float) (0.55 + 2.8 * Math.pow(t, 2.3));
    }

    /**
     * Horizontal displacement of the funnel axis at height fraction t. Weak tornadoes (EF0-EF1) are thin and bend
     * a lot, strong ones stand almost straight. The column also leans back against its direction of travel,
     * because the ground circulation drags behind the parent cloud.
     */
    public Vec3 funnelOffset(double t, float time) {
        float weakness = Mth.clamp((6f - getIntensity()) / 5f, 0f, 1f); // 1 for EF1, ~0 for EF5
        double bend = (getCoreRadius() * 0.5 + 6 + weakness * weakness * 34) * swayFactor();
        double shape = Math.pow(t, 1.3);
        int seed = getId();
        double sx = Math.sin(time * 0.021 + t * (2.6 + weakness * 2.5) + seed) * bend * shape;
        double sz = Math.cos(time * 0.017 + t * (2.2 + weakness * 2.0) + seed * 0.7) * bend * shape;
        double lean = (8 + weakness * 22) * swayFactor() * t * t;
        float h = getHeading();
        return new Vec3(sx - Mth.cos(h) * lean, 0, sz - Mth.sin(h) * lean);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (lerpSteps > 0) {
                double nx = getX() + (lerpX - getX()) / lerpSteps;
                double ny = getY() + (lerpY - getY()) / lerpSteps;
                double nz = getZ() + (lerpZ - getZ()) / lerpSteps;
                lerpSteps--;
                setPos(nx, ny, nz);
            }
            ClientHooks.tickTornado(this);
            return;
        }
        serverTick((ServerLevel) level());
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpSteps = steps + 2;
    }

    protected void serverTick(ServerLevel level) {
        life++;
        float grow = Math.min(1f, life / 120f);
        float decay = Math.min(1f, (maxLife - life) / 200f);
        setScale(Math.max(0f, Math.min(grow, decay)));
        if (life >= maxLife) {
            dissipate(level);
            return;
        }
        move(level);
        if (getScale() > 0.2f) {
            applyForces(level);
            if (canDestroyBlocks(level)) {
                destroyBlocks(level);
                groundEffects(level);
            }
        }
        variantTick(level);
        if (isRemoved()) return;
        if (life % 20 == 0) debris.removeIf(Entity::isRemoved);
        if (life % 10 == 0) {
            WeatherEngine.reportTornado(level, this);
            shakeNearbyPlayers(level);
        }
    }

    protected void dissipate(ServerLevel level) {
        debris.clear(); // the remaining debris simply falls back down
        WeatherEngine.removeTornado(level, getUUID());
        discard();
    }

    protected void move(ServerLevel level) {
        heading += (random.nextDouble() - 0.5) * wanderAmount();
        if (life % 5 == 0) entityData.set(DATA_HEADING, (float) heading);
        double spd = speed * Config.TORNADO_SPEED.get();
        double nx = getX() + Math.cos(heading) * spd;
        double nz = getZ() + Math.sin(heading) * spd;
        BlockPos col = BlockPos.containing(nx, getY(), nz);
        if (!level.isLoaded(col)) return;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
        double ny = getY() + Mth.clamp(ground - getY(), -1.0, 1.0);
        setPos(nx, ny, nz);
    }

    protected void shakeNearbyPlayers(ServerLevel level) {
        float range = getPullRadius() + 40;
        for (ServerPlayer p : level.players()) {
            double d = Math.sqrt(p.distanceToSqr(getX(), p.getY(), getZ()));
            if (d > range) continue;
            float intensity = (0.08f + getCategory() * 0.05f) * (float) (1 - d / range) * getScale();
            if (intensity > 0.02f) PacketDistributor.sendToPlayer(p, new ShakePayload(intensity, 14));
        }
    }

    // ------------------------------------------------------------------ vortex forces

    protected float strength() {
        return (0.55f + getIntensity() * 0.22f) * getScale();
    }

    protected void applyForces(ServerLevel level) {
        applyVortex(level, getX(), getZ(), getCoreRadius(), getPullRadius(), strength());
    }

    /** Applies one independent vortex force field centred at (cx, cz). */
    protected void applyVortex(ServerLevel level, double cx, double cz, float core, float pull, float strength) {
        double top = getFunnelTopY();
        AABB box = new AABB(cx - pull, getY() - 8, cz - pull, cx + pull, top, cz + pull);
        List<Entity> list = level.getEntities(this, box, this::canAffect);
        for (Entity e : list) pushEntity(level, e, cx, cz, core, pull, strength, top);
    }

    protected boolean canAffect(Entity e) {
        if (e instanceof TornadoEntity || e.isSpectator() || e.isPassenger() || e instanceof HangingEntity) return false;
        if (e.getType().is(Tags.EntityTypes.BOSSES)) return false;
        if (e instanceof Player p) {
            return Config.AFFECT_PLAYERS.get() && !p.isCreative() && !p.getAbilities().flying;
        }
        return e.isAlive();
    }

    protected void pushEntity(ServerLevel level, Entity e, double cx, double cz, float core, float pull, float strength, double top) {
        double dx = e.getX() - cx, dz = e.getZ() - cz;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > pull) return;
        if (d < 0.05) {
            dx = 0.05;
            d = 0.05;
        }
        double nx = dx / d, nz = dz / d;
        double dir = spinDirection();
        double tx = -nz * dir, tz = nx * dir; // cyclonic: counter-clockwise in the north, clockwise in the south
        double f = 1.0 - d / pull;

        double inward = strength * (0.035 + 0.05 * f) * f;
        double tangential = strength * 0.16 * (d < core ? d / core : core / d) * (0.4 + 0.6 * f);
        double lift = d < core * 1.1 ? strength * (0.075 + 0.06 * (1 - d / (core * 1.1))) : strength * 0.015 * f;

        double height = e.getY() - getY(), funnelHeight = top - getY();
        if (height > funnelHeight * 0.75) {
            inward = -strength * 0.12; // flung out of the top of the funnel
            lift = Math.min(lift, 0.02);
        } else if (height > funnelHeight * 0.5) {
            lift *= 0.6;
        }

        double mass = massFactor(e);
        Vec3 v = e.getDeltaMovement();
        double vx = v.x + (-nx * inward + tx * tangential) * mass;
        double vz = v.z + (-nz * inward + tz * tangential) * mass;
        double vy = v.y + lift * mass;
        double hs = Math.sqrt(vx * vx + vz * vz), max = 0.9 + strength * 0.9;
        if (hs > max) {
            vx *= max / hs;
            vz *= max / hs;
        }
        vy = Mth.clamp(vy, -2.0, 0.6 + strength * 0.4);
        e.setDeltaMovement(vx, vy, vz);
        e.hasImpulse = true;
        if (e instanceof ServerPlayer) e.hurtMarked = true;
        onEntityInVortex(level, e, d, core);
    }

    protected double massFactor(Entity e) {
        if (e instanceof Player) return 0.7;
        if (e instanceof ItemEntity) return 1.3;
        if (e instanceof FallingBlockEntity) return 1.2;
        if (e instanceof LivingEntity) return e.getBbWidth() > 1.5f ? 0.55 : 0.85;
        return 1.0;
    }

    protected void onEntityInVortex(ServerLevel level, Entity e, double distance, float core) {
        if (distance < core && life % 20 == 0 && e instanceof LivingEntity && getCategory() >= 2) {
            e.hurt(damageSource(level), 0.5f + getCategory() * 0.75f);
        }
    }

    protected DamageSource damageSource(ServerLevel level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TORNADO_DAMAGE));
    }

    // ------------------------------------------------------------------ block destruction

    protected boolean canDestroyBlocks(ServerLevel level) {
        if (!Config.BLOCK_DAMAGE.get()) return false;
        return !Config.RESPECT_MOB_GRIEFING.get() || level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    protected float destroyPower(int categoryBonus) {
        return DESTROY_POWER[Mth.clamp(getCategory() + categoryBonus, 1, 5)];
    }

    protected void destroyBlocks(ServerLevel level) {
        int budget = (int) ((3 + getIntensity() * 5) * Config.DESTRUCTION_RATE.get() * getScale());
        destroyArea(level, getX(), getZ(), getCoreRadius() * 1.05f, budget, destroyPower(0));
    }

    protected void destroyArea(ServerLevel level, double cx, double cz, float radius, int budget, float power) {
        int cat = getCategory();
        for (int i = 0; i < budget; i++) {
            double a = random.nextDouble() * Math.PI * 2, rr = Math.sqrt(random.nextDouble()) * radius;
            int x = Mth.floor(cx + Math.cos(a) * rr), z = Mth.floor(cz + Math.sin(a) * rr);
            if (!level.isLoaded(new BlockPos(x, (int) getY(), z))) continue;
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            if (top > getY() + 45 || top < getY() - 12) continue;
            int depth = random.nextFloat() < 0.15f + cat * 0.05f ? random.nextInt(1 + cat / 2) : 0;
            tryDamageBlock(level, new BlockPos(x, top - depth, z), power);
        }
    }

    protected void tryDamageBlock(ServerLevel level, BlockPos pos, float power) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.liquid()) return;
        if (state.getDestroySpeed(level, pos) < 0) return;
        float resistance = state.getBlock().getExplosionResistance();
        if (state.hasBlockEntity()) {
            if (getCategory() >= 4 && resistance <= power && random.nextFloat() < 0.25f) level.destroyBlock(pos, true);
            return;
        }
        // ground scouring: the vortex strips the grass off and leaves a bare track of dirt behind it
        BlockState scoured = scour(state);
        if (scoured != null && random.nextFloat() < 0.45f) {
            level.setBlock(pos, scoured, Block.UPDATE_ALL);
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
            return;
        }
        if (resistance <= power) {
            float chance = 0.3f + 0.7f * (1f - resistance / Math.max(power, 0.01f));
            if (random.nextFloat() > chance) {
                // survived, but strong winds still debark the trees
                if (getCategory() >= 3 && random.nextFloat() < 0.3f) debark(level, pos, state);
                return;
            }
            boolean flimsy = state.canBeReplaced() || state.getCollisionShape(level, pos).isEmpty()
                    || (state.is(BlockTags.LEAVES) && random.nextFloat() < 0.75f);
            if (flimsy) {
                level.destroyBlock(pos, false);
            } else if (random.nextFloat() < 0.65f) {
                liftBlock(level, pos, state);
            } else {
                level.destroyBlock(pos, Config.DEBRIS_DROPS.get() && random.nextFloat() < 0.25f);
            }
        } else if (resistance <= power * 2.2f && random.nextFloat() < 0.06f) {
            // too strong to rip out: the surface only gets scratched (and tree trunks lose their bark)
            if (debark(level, pos, state)) return;
            BlockState scratched = scratch(state);
            if (scratched != null) level.setBlock(pos, scratched, Block.UPDATE_ALL);
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        }
    }

    /**
     * Marks left on the ground. Suction spots orbiting slowly inside the core scour the grass in loops; as the
     * tornado moves on, the loops line up into the cycloidal marks seen in real damage surveys. In the inflow
     * around the funnel, tall grass, ferns and flowers are flattened.
     */
    protected void groundEffects(ServerLevel level) {
        int cat = getCategory();
        float core = getCoreRadius();
        if (cat >= 2) {
            int spots = cat >= 4 ? 3 : 2;
            float orbit = core * 0.65f;
            float a0 = level.getGameTime() * (0.04f + cat * 0.005f) * spinDirection();
            for (int k = 0; k < spots; k++) {
                float a = a0 + k * Mth.TWO_PI / spots;
                scourColumn(level, Mth.floor(getX() + Mth.cos(a) * orbit), Mth.floor(getZ() + Mth.sin(a) * orbit));
            }
        }
        float pull = getPullRadius();
        for (int i = 0; i < 3 + cat; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = core + random.nextDouble() * (pull - core);
            int x = Mth.floor(getX() + Math.cos(a) * d), z = Mth.floor(getZ() + Math.sin(a) * d);
            if (!level.isLoaded(new BlockPos(x, (int) getY(), z))) continue;
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
            BlockState s = level.getBlockState(pos);
            if (!s.isAir() && s.canBeReplaced() && !s.liquid() && random.nextFloat() < 0.5f) level.destroyBlock(pos, false);
        }
    }

    private void scourColumn(ServerLevel level, int x, int z) {
        if (!level.isLoaded(new BlockPos(x, (int) getY(), z))) return;
        BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
        if (Math.abs(pos.getY() - getY()) > 6) return;
        BlockState s = level.getBlockState(pos);
        BlockState scoured = scour(s);
        if (scoured == null) return;
        BlockState above = level.getBlockState(pos.above());
        if (!above.isAir() && above.canBeReplaced()) level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(pos, scoured, Block.UPDATE_ALL);
    }

    /** Grass, podzol, mycelium and paths are stripped down to dirt; strong tornadoes leave coarse dirt. */
    @Nullable
    protected BlockState scour(BlockState s) {
        if (!(s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MYCELIUM) || s.is(Blocks.DIRT_PATH))) return null;
        return getCategory() >= 3 && random.nextFloat() < 0.5f ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState();
    }

    /** Replaces a log/wood block with its stripped version (oak_log -> stripped_oak_log), keeping its axis. */
    protected static boolean debark(ServerLevel level, BlockPos pos, BlockState s) {
        if (!s.is(BlockTags.LOGS)) return false;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
        if (id.getPath().startsWith("stripped_")) return false;
        Block stripped = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "stripped_" + id.getPath()));
        if (stripped == Blocks.AIR) return false;
        level.setBlock(pos, stripped.withPropertiesOf(s), Block.UPDATE_ALL);
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(s));
        return true;
    }

    @Nullable
    protected static BlockState scratch(BlockState s) {
        if (s.is(Blocks.STONE)) return Blocks.COBBLESTONE.defaultBlockState();
        if (s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE)) return Blocks.GRAVEL.defaultBlockState();
        if (s.is(Blocks.STONE_BRICKS)) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        if (s.is(Blocks.DEEPSLATE)) return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        if (s.is(Blocks.DEEPSLATE_BRICKS)) return Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        if (s.is(Blocks.DEEPSLATE_TILES)) return Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState();
        if (s.is(Blocks.NETHER_BRICKS)) return Blocks.CRACKED_NETHER_BRICKS.defaultBlockState();
        if (s.is(Blocks.POLISHED_BLACKSTONE_BRICKS)) return Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        return null;
    }

    /** Rips a block out as a physical falling block that gets carried by the vortex. */
    protected void liftBlock(ServerLevel level, BlockPos pos, BlockState state) {
        int max = Config.MAX_SUSPENDED_BLOCKS.get();
        if (max <= 0) {
            level.destroyBlock(pos, Config.DEBRIS_DROPS.get());
            return;
        }
        while (debris.size() >= max) evictOldestDebris(level);
        FallingBlockEntity fbe = FallingBlockEntity.fall(level, pos, state);
        fbe.dropItem = Config.DEBRIS_DROPS.get();
        fbe.setHurtsEntities(1.0f + getCategory() * 0.5f, 20 + getCategory() * 8);
        fbe.setDeltaMovement((random.nextDouble() - 0.5) * 0.3, 0.45 + random.nextDouble() * 0.35, (random.nextDouble() - 0.5) * 0.3);
        debris.addLast(fbe);
    }

    /** Lag guard: the oldest suspended block (usually the highest in the funnel) is deleted or dropped as an item. */
    protected void evictOldestDebris(ServerLevel level) {
        FallingBlockEntity old = debris.pollFirst();
        if (old == null || old.isRemoved()) return;
        if (Config.EXCESS_DEBRIS_DROPS.get() && level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, old.blockPosition());
            ItemStack stack = new ItemStack(old.getBlockState().getBlock());
            if (!stack.isEmpty()) {
                ItemEntity item = new ItemEntity(level, ground.getX() + 0.5, ground.getY() + 0.25, ground.getZ() + 0.5, stack);
                item.setDefaultPickUpDelay();
                level.addFreshEntity(item);
            }
        }
        old.discard();
    }

    public int getSuspendedBlockCount() {
        return debris.size();
    }

    // ------------------------------------------------------------------ variants

    /** Hook for variant behaviour. The base tornado turns into a firenado over lava or large fires. */
    protected void variantTick(ServerLevel level) {
        if (life % 10 != 0 || !canBecomeFirenado()) return;
        int heat = 0;
        float core = getCoreRadius();
        for (int i = 0; i < 16; i++) {
            double a = random.nextDouble() * Math.PI * 2, rr = Math.sqrt(random.nextDouble()) * core;
            int x = Mth.floor(getX() + Math.cos(a) * rr), z = Mth.floor(getZ() + Math.sin(a) * rr);
            BlockPos col = new BlockPos(x, (int) getY(), z);
            if (!level.isLoaded(col)) continue;
            BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
            BlockState s = level.getBlockState(top);
            if (s.getFluidState().is(FluidTags.LAVA)) heat += 3;
            else if (s.is(BlockTags.FIRE)) heat += 1;
        }
        if (heat >= 4) {
            transformInto(level, TornadoVariant.FIRENADO);
            return;
        }
        // half of the classic tornadoes "rope out": in their last phase they thin into a long, snaking rope
        if (getVariant() == TornadoVariant.CLASSIC && getId() % 2 == 0 && life > maxLife * 0.8 && maxLife - life > 150) {
            transformInto(level, TornadoVariant.ROPE);
        }
    }

    /** Random heading change per tick (radians). */
    protected double wanderAmount() {
        return 0.06;
    }

    protected boolean canBecomeFirenado() {
        return true;
    }

    protected void transformInto(ServerLevel level, TornadoVariant variant) {
        TornadoEntity next = variant.entityType().create(level);
        if (next == null) return;
        next.moveTo(getX(), getY(), getZ(), 0f, 0f);
        next.setIntensity(getIntensity());
        next.setScale(getScale());
        next.life = life;
        next.maxLife = maxLife;
        next.heading = heading;
        next.entityData.set(DATA_HEADING, (float) heading);
        next.entityData.set(DATA_CLOCKWISE, entityData.get(DATA_CLOCKWISE));
        next.speed = speed;
        next.stormId = stormId;
        next.debris.addAll(debris);
        next.onCreated(random);
        level.addFreshEntity(next);
        WeatherEngine.onTornadoReplaced(level, this, next);
        debris.clear();
        discard();
    }

    // ------------------------------------------------------------------ misc entity behaviour

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 768 * 768;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        float r = Math.max(8f, getCoreRadius() * 4f);
        return new AABB(getX() - r, getY() - 4, getZ() - r, getX() + r, getFunnelTopY() + 10, getZ() + r);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setIntensity(tag.getFloat("Intensity"));
        entityData.set(DATA_HEADING, (float) tag.getDouble("Heading"));
        entityData.set(DATA_CLOCKWISE, tag.getBoolean("Clockwise"));
        life = tag.getInt("Life");
        maxLife = tag.getInt("MaxLife");
        heading = tag.getDouble("Heading");
        speed = tag.contains("Speed") ? tag.getDouble("Speed") : 0.13;
        stormId = tag.getInt("StormId");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("Intensity", getIntensity());
        tag.putInt("Life", life);
        tag.putInt("MaxLife", maxLife);
        tag.putDouble("Heading", heading);
        tag.putDouble("Speed", speed);
        tag.putInt("StormId", stormId);
        tag.putBoolean("Clockwise", entityData.get(DATA_CLOCKWISE));
    }
}
