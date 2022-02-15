package net.mantori.entity;

import com.google.common.collect.UnmodifiableIterator;
import net.mantori.entity.goal.AphidBondWithPlayerGoal;
import net.mantori.sounds.ModSounds;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.InventoryChangedListener;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.recipe.Ingredient;
import net.minecraft.server.ServerConfigHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Arm;
import net.minecraft.util.math.*;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public abstract class BaseAphidEntity extends AnimalEntity implements JumpingMount {
    public static final int field_30413 = 400;
    public static final int field_30414 = 499;
    public static final int field_30415 = 500;
    private static final Predicate<LivingEntity> IS_BRED_APHID = (entity) -> entity instanceof BaseAphidEntity && ((BaseAphidEntity)entity).isBred();
    private static final TargetPredicate PARENT_APHID_PREDICATE;
    private static final Ingredient BREEDING_INGREDIENT;
    private static final TrackedData<Byte> APHID_FLAGS;
    private static final TrackedData<Optional<UUID>> OWNER_UUID;
    private static final int TAMED_FLAG = 2;
    private static final int BRED_FLAG = 8;
    private static final int EATING_GRASS_FLAG = 16;
    private static final int ANGRY_FLAG = 32;
    private static final int EATING_FLAG = 64;
    public static final int field_30416 = 0;
    public static final int field_30417 = 1;
    public static final int field_30418 = 2;
    private int eatingGrassTicks;
    private int eatingTicks;
    private int angryTicks;
    public int tailWagTicks;
    public int field_6958;
    protected boolean inAir;
    protected SimpleInventory items;
    protected int temper;
    protected float jumpStrength;
    private boolean jumping;
    private float eatingGrassAnimationProgress;
    private float lastEatingGrassAnimationProgress;
    private float angryAnimationProgress;
    private float lastAngryAnimationProgress;
    private float eatingAnimationProgress;
    private float lastEatingAnimationProgress;
    protected boolean playExtraHorseSounds = true;
    protected int soundTicks;

    protected BaseAphidEntity(EntityType<? extends BaseAphidEntity> entityType, World world) {
        super(entityType, world);
        this.stepHeight = 1.0F;
    }

    protected void initGoals() {
        this.goalSelector.add(1, new EscapeDangerGoal(this, 1.2D));
        this.goalSelector.add(1, new AphidBondWithPlayerGoal(this, 1.2D));
        this.goalSelector.add(2, new AnimalMateGoal(this, 1.0D, BaseAphidEntity.class));
        this.goalSelector.add(4, new FollowParentGoal(this, 1.0D));
        this.goalSelector.add(6, new WanderAroundFarGoal(this, 0.7D));
        this.goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 6.0F));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.initCustomGoals();
    }

    protected void initCustomGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(3, new TemptGoal(this, 1.25D, Ingredient.ofItems(Items.CHORUS_FRUIT), false));
    }

    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(APHID_FLAGS, (byte)0);
        this.dataTracker.startTracking(OWNER_UUID, Optional.empty());
    }

    protected boolean getHorseFlag(int bitmask) {
        return (this.dataTracker.get(APHID_FLAGS) & bitmask) != 0;
    }

    protected void setHorseFlag(int bitmask, boolean flag) {
        byte b = this.dataTracker.get(APHID_FLAGS);
        if (flag) {
            this.dataTracker.set(APHID_FLAGS, (byte)(b | bitmask));
        } else {
            this.dataTracker.set(APHID_FLAGS, (byte)(b & ~bitmask));
        }

    }

    public boolean isTame() {
        return this.getHorseFlag(2);
    }

    @Nullable
    public UUID getOwnerUuid() {
        return (UUID)((Optional)this.dataTracker.get(OWNER_UUID)).orElse((Object)null);
    }

    public void setOwnerUuid(@Nullable UUID uuid) {
        this.dataTracker.set(OWNER_UUID, Optional.ofNullable(uuid));
    }

    public boolean isInAir() {
        return this.inAir;
    }

    public void setTame(boolean tame) {
        this.setHorseFlag(2, tame);
    }

    public void setInAir(boolean inAir) {
        this.inAir = inAir;
    }

    protected void updateForLeashLength(float leashLength) {
        if (leashLength > 6.0F && this.isEatingGrass()) {
            this.setEatingGrass(false);
        }

    }

    public boolean isEatingGrass() {
        return this.getHorseFlag(16);
    }

    public boolean isAngry() {
        return this.getHorseFlag(32);
    }

    public boolean isBred() {
        return this.getHorseFlag(8);
    }

    public void setBred(boolean bred) {
        this.setHorseFlag(8, bred);
    }

    public int getTemper() {
        return this.temper;
    }

    public void setTemper(int temper) {
        this.temper = temper;
    }

    public int addTemper(int difference) {
        int i = MathHelper.clamp(this.getTemper() + difference, 0, this.getMaxTemper());
        this.setTemper(i);
        return i;
    }

    public boolean isPushable() {
        return !this.hasPassengers();
    }

    private void playEatingAnimation() {
        this.setEating();
        if (!this.isSilent()) {
            SoundEvent soundEvent = this.getEatSound();
            if (soundEvent != null) {
                this.world.playSound(null, this.getX(), this.getY(), this.getZ(), soundEvent, this.getSoundCategory(), 1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
            }
        }

    }

    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        if (fallDistance > 1.0F) {
            this.playSound(ModSounds.LAND, 0.4F, 1.0F);
        }

        int i = this.computeFallDamage(fallDistance, damageMultiplier);
        if (i <= 0) {
            return false;
        } else {
            this.damage(damageSource, (float)i);
            if (this.hasPassengers()) {
                Iterator var5 = this.getPassengersDeep().iterator();

                while(var5.hasNext()) {
                    Entity entity = (Entity)var5.next();
                    entity.damage(damageSource, (float)i);
                }
            }

            this.playBlockFallSound();
            return true;
        }
    }

    protected int computeFallDamage(float fallDistance, float damageMultiplier) {
        return MathHelper.ceil((fallDistance * 0.2F - 6.0F) * damageMultiplier);
    }

    protected int getInventorySize() {
        return 2;
    }

    protected void updateSaddle() {
        if (!this.world.isClient) {
            this.setHorseFlag(4, !this.items.getStack(0).isEmpty());
        }
    }

    public double getJumpStrength() {
        return this.getAttributeValue(EntityAttributes.HORSE_JUMP_STRENGTH);
    }

    @Nullable
    protected SoundEvent getEatSound() {
        return null;
    }

    @Nullable
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Nullable
    protected SoundEvent getHurtSound(DamageSource source) {
        if (this.random.nextInt(3) == 0) {
            this.updateAnger();
        }

        return null;
    }

    @Nullable
    protected SoundEvent getAmbientSound() {
        if (this.random.nextInt(10) == 0 && !this.isImmobile()) {
            this.updateAnger();
        }

        return null;
    }

    @Nullable
    protected SoundEvent getAngrySound() {
        this.updateAnger();
        return null;
    }

    protected void playStepSound(BlockPos pos, BlockState state) {
        if (!state.getMaterial().isLiquid()) {
            BlockState blockState = this.world.getBlockState(pos.up());
            BlockSoundGroup blockSoundGroup = state.getSoundGroup();
            if (blockState.isOf(Blocks.SNOW)) {
                blockSoundGroup = blockState.getSoundGroup();
            }

            if (this.hasPassengers() && this.playExtraHorseSounds) {
                ++this.soundTicks;
                if (this.soundTicks > 5 && this.soundTicks % 3 == 0) {
                    this.playWalkSound(blockSoundGroup);
                } else if (this.soundTicks <= 5) {
                    this.playSound(ModSounds.FOOTSTEPS_WOOD, blockSoundGroup.getVolume() * 0.15F, blockSoundGroup.getPitch());
                }
            } else if (blockSoundGroup == BlockSoundGroup.WOOD) {
                this.playSound(ModSounds.FOOTSTEPS_WOOD, blockSoundGroup.getVolume() * 0.15F, blockSoundGroup.getPitch());
            } else {
                this.playSound(ModSounds.FOOTSTEPS, blockSoundGroup.getVolume() * 0.15F, blockSoundGroup.getPitch());
            }

        }
    }

    protected void playWalkSound(BlockSoundGroup group) {
        this.playSound(ModSounds.GALLOP, group.getVolume() * 0.15F, group.getPitch());
    }

    public static DefaultAttributeContainer.Builder createBaseHorseAttributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.HORSE_JUMP_STRENGTH).add(EntityAttributes.GENERIC_MAX_HEALTH, 53.0D).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.22499999403953552D);
    }

    public int getLimitPerChunk() {
        return 6;
    }

    public int getMaxTemper() {
        return 100;
    }

    protected float getSoundVolume() {
        return 0.8F;
    }

    public int getMinAmbientSoundDelay() {
        return 400;
    }

    public ActionResult interactHorse(PlayerEntity player, ItemStack stack) {
        boolean bl = this.receiveFood(player, stack);
        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }

        if (this.world.isClient) {
            return ActionResult.CONSUME;
        } else {
            return bl ? ActionResult.SUCCESS : ActionResult.PASS;
        }
    }

    protected boolean receiveFood(PlayerEntity player, ItemStack item) {
        boolean bl = false;
        float f = 0.0F;
        int i = 0;
        int j = 0;
        if (item.isOf(Items.CHORUS_FRUIT)) {
            f = 4.0F;
            i = 60;
            j = 5;
            if (!this.world.isClient && this.isTame() && this.getBreedingAge() == 0 && !this.isInLove()) {
                bl = true;
                this.lovePlayer(player);
            }
        } else if (item.isOf(Items.CHORUS_FLOWER)) {
            f = 10.0F;
            i = 240;
            j = 10;
            if (!this.world.isClient && this.isTame() && this.getBreedingAge() == 0 && !this.isInLove()) {
                bl = true;
                this.lovePlayer(player);
            }
        }

        if (this.getHealth() < this.getMaxHealth() && f > 0.0F) {
            this.heal(f);
            bl = true;
        }

        if (this.isBaby() && i > 0) {
            this.world.addParticle(ParticleTypes.HAPPY_VILLAGER, this.getParticleX(1.0D), this.getRandomBodyY() + 0.5D, this.getParticleZ(1.0D), 0.0D, 0.0D, 0.0D);
            if (!this.world.isClient) {
                this.growUp(i);
            }

            bl = true;
        }

        if (j > 0 && (bl || !this.isTame()) && this.getTemper() < this.getMaxTemper()) {
            bl = true;
            if (!this.world.isClient) {
                this.addTemper(j);
            }
        }

        if (bl) {
            this.playEatingAnimation();
            this.emitGameEvent(GameEvent.EAT, this.getCameraBlockPos());
        }

        return bl;
    }

    protected void putPlayerOnBack(PlayerEntity player) {
        this.setEatingGrass(false);
        this.setAngry(false);
        if (!this.world.isClient) {
            player.setYaw(this.getYaw());
            player.setPitch(this.getPitch());
            player.startRiding(this);
        }

    }

    protected boolean isImmobile() {
        return super.isImmobile() && this.hasPassengers() || this.isEatingGrass() || this.isAngry();
    }

    public boolean isBreedingItem(ItemStack stack) {
        return BREEDING_INGREDIENT.test(stack);
    }

    private void wagTail() {
        this.tailWagTicks = 1;
    }

    protected void dropInventory() {
        super.dropInventory();
        if (this.items != null) {
            for(int i = 0; i < this.items.size(); ++i) {
                ItemStack itemStack = this.items.getStack(i);
                if (!itemStack.isEmpty() && !EnchantmentHelper.hasVanishingCurse(itemStack)) {
                    this.dropStack(itemStack);
                }
            }

        }
    }

    public void tickMovement() {
        if (this.random.nextInt(200) == 0) {
            this.wagTail();
        }

        super.tickMovement();
        if (!this.world.isClient && this.isAlive()) {
            if (this.random.nextInt(900) == 0 && this.deathTime == 0) {
                this.heal(1.0F);
            }

            if (this.eatsGrass()) {
                if (!this.isEatingGrass() && !this.hasPassengers() && this.random.nextInt(300) == 0 && this.world.getBlockState(this.getBlockPos().down()).isOf(Blocks.GRASS_BLOCK)) {
                    this.setEatingGrass(true);
                }

                if (this.isEatingGrass() && ++this.eatingGrassTicks > 50) {
                    this.eatingGrassTicks = 0;
                    this.setEatingGrass(false);
                }
            }

            this.walkToParent();
        }
    }

    protected void walkToParent() {
        if (this.isBred() && this.isBaby() && !this.isEatingGrass()) {
            LivingEntity livingEntity = this.world.getClosestEntity(BaseAphidEntity.class, PARENT_APHID_PREDICATE, this, this.getX(), this.getY(), this.getZ(), this.getBoundingBox().expand(16.0D));
            if (livingEntity != null && this.squaredDistanceTo(livingEntity) > 4.0D) {
                this.navigation.findPathTo(livingEntity, 0);
            }
        }

    }

    public boolean eatsGrass() {
        return true;
    }

    public void tick() {
        super.tick();
        if (this.eatingTicks > 0 && ++this.eatingTicks > 30) {
            this.eatingTicks = 0;
            this.setHorseFlag(64, false);
        }

        if ((this.isLogicalSideForUpdatingMovement() || this.canMoveVoluntarily()) && this.angryTicks > 0 && ++this.angryTicks > 20) {
            this.angryTicks = 0;
            this.setAngry(false);
        }

        if (this.tailWagTicks > 0 && ++this.tailWagTicks > 8) {
            this.tailWagTicks = 0;
        }

        if (this.field_6958 > 0) {
            ++this.field_6958;
            if (this.field_6958 > 300) {
                this.field_6958 = 0;
            }
        }

        this.lastEatingGrassAnimationProgress = this.eatingGrassAnimationProgress;
        if (this.isEatingGrass()) {
            this.eatingGrassAnimationProgress += (1.0F - this.eatingGrassAnimationProgress) * 0.4F + 0.05F;
            if (this.eatingGrassAnimationProgress > 1.0F) {
                this.eatingGrassAnimationProgress = 1.0F;
            }
        } else {
            this.eatingGrassAnimationProgress += (0.0F - this.eatingGrassAnimationProgress) * 0.4F - 0.05F;
            if (this.eatingGrassAnimationProgress < 0.0F) {
                this.eatingGrassAnimationProgress = 0.0F;
            }
        }

        this.lastAngryAnimationProgress = this.angryAnimationProgress;
        if (this.isAngry()) {
            this.eatingGrassAnimationProgress = 0.0F;
            this.lastEatingGrassAnimationProgress = this.eatingGrassAnimationProgress;
            this.angryAnimationProgress += (1.0F - this.angryAnimationProgress) * 0.4F + 0.05F;
            if (this.angryAnimationProgress > 1.0F) {
                this.angryAnimationProgress = 1.0F;
            }
        } else {
            this.jumping = false;
            this.angryAnimationProgress += (0.8F * this.angryAnimationProgress * this.angryAnimationProgress * this.angryAnimationProgress - this.angryAnimationProgress) * 0.6F - 0.05F;
            if (this.angryAnimationProgress < 0.0F) {
                this.angryAnimationProgress = 0.0F;
            }
        }

        this.lastEatingAnimationProgress = this.eatingAnimationProgress;
        if (this.getHorseFlag(64)) {
            this.eatingAnimationProgress += (1.0F - this.eatingAnimationProgress) * 0.7F + 0.05F;
            if (this.eatingAnimationProgress > 1.0F) {
                this.eatingAnimationProgress = 1.0F;
            }
        } else {
            this.eatingAnimationProgress += (0.0F - this.eatingAnimationProgress) * 0.7F - 0.05F;
            if (this.eatingAnimationProgress < 0.0F) {
                this.eatingAnimationProgress = 0.0F;
            }
        }

    }

    private void setEating() {
        if (!this.world.isClient) {
            this.eatingTicks = 1;
            this.setHorseFlag(64, true);
        }

    }

    public void setEatingGrass(boolean eatingGrass) {
        this.setHorseFlag(16, eatingGrass);
    }

    public void setAngry(boolean angry) {
        if (angry) {
            this.setEatingGrass(false);
        }

        this.setHorseFlag(32, angry);
    }

    private void updateAnger() {
        if (this.isLogicalSideForUpdatingMovement() || this.canMoveVoluntarily()) {
            this.angryTicks = 1;
            this.setAngry(true);
        }

    }

    public void playAngrySound() {
        if (!this.isAngry()) {
            this.updateAnger();
            SoundEvent soundEvent = this.getAngrySound();
            if (soundEvent != null) {
                this.playSound(soundEvent, this.getSoundVolume(), this.getSoundPitch());
            }
        }

    }

    public boolean bondWithPlayer(PlayerEntity player) {
        this.setOwnerUuid(player.getUuid());
        this.setTame(true);
        if (player instanceof ServerPlayerEntity) {
            Criteria.TAME_ANIMAL.trigger((ServerPlayerEntity)player, this);
        }

        this.world.sendEntityStatus(this, (byte)7);
        return true;
    }

    public void travel(Vec3d movementInput) {
        if (this.isAlive()) {
            if (this.hasPassengers() && this.canBeControlledByRider()) {
                LivingEntity livingEntity = (LivingEntity)this.getPrimaryPassenger();
                this.setYaw(livingEntity.getYaw());
                this.prevYaw = this.getYaw();
                this.setPitch(livingEntity.getPitch() * 0.5F);
                this.setRotation(this.getYaw(), this.getPitch());
                this.bodyYaw = this.getYaw();
                this.headYaw = this.bodyYaw;
                float f = livingEntity.sidewaysSpeed * 0.5F;
                float g = livingEntity.forwardSpeed;
                if (g <= 0.0F) {
                    g *= 0.25F;
                    this.soundTicks = 0;
                }

                if (this.onGround && this.jumpStrength == 0.0F && this.isAngry() && !this.jumping) {
                    f = 0.0F;
                    g = 0.0F;
                }

                if (this.jumpStrength > 0.0F && !this.isInAir() && this.onGround) {
                    double d = this.getJumpStrength() * (double)this.jumpStrength * (double)this.getJumpVelocityMultiplier();
                    double e = d + this.getJumpBoostVelocityModifier();
                    Vec3d vec3d = this.getVelocity();
                    this.setVelocity(vec3d.x, e, vec3d.z);
                    this.setInAir(true);
                    this.velocityDirty = true;
                    if (g > 0.0F) {
                        float h = MathHelper.sin(this.getYaw() * 0.017453292F);
                        float i = MathHelper.cos(this.getYaw() * 0.017453292F);
                        this.setVelocity(this.getVelocity().add(-0.4F * h * this.jumpStrength, 0.0D, 0.4F * i * this.jumpStrength));
                    }

                    this.jumpStrength = 0.0F;
                }

                this.airStrafingSpeed = this.getMovementSpeed() * 0.1F;
                if (this.isLogicalSideForUpdatingMovement()) {
                    this.setMovementSpeed((float)this.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
                    super.travel(new Vec3d(f, movementInput.y, g));
                } else if (livingEntity instanceof PlayerEntity) {
                    this.setVelocity(Vec3d.ZERO);
                }

                if (this.onGround) {
                    this.jumpStrength = 0.0F;
                    this.setInAir(false);
                }

                this.updateLimbs(this, false);
                this.tryCheckBlockCollision();
            } else {
                this.airStrafingSpeed = 0.02F;
                super.travel(movementInput);
            }
        }
    }

    protected void playJumpSound() {
        this.playSound(ModSounds.JUMP, 0.4F, 1.0F);
    }

    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("EatingHaystack", this.isEatingGrass());
        nbt.putBoolean("Bred", this.isBred());
        nbt.putInt("Temper", this.getTemper());
        nbt.putBoolean("Tame", this.isTame());
        if (this.getOwnerUuid() != null) {
            nbt.putUuid("Owner", this.getOwnerUuid());
        }
    }

    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.setEatingGrass(nbt.getBoolean("EatingHaystack"));
        this.setBred(nbt.getBoolean("Bred"));
        this.setTemper(nbt.getInt("Temper"));
        this.setTame(nbt.getBoolean("Tame"));
        UUID uUID;
        if (nbt.containsUuid("Owner")) {
            uUID = nbt.getUuid("Owner");
        } else {
            String string = nbt.getString("Owner");
            uUID = ServerConfigHandler.getPlayerUuidByName(this.getServer(), string);
        }

        if (uUID != null) {
            this.setOwnerUuid(uUID);
        }
    }

    public boolean canBreedWith(AnimalEntity other) {
        return false;
    }

    protected boolean canBreed() {
        return !this.hasPassengers() && !this.hasVehicle() && this.isTame() && !this.isBaby() && this.getHealth() >= this.getMaxHealth() && this.isInLove();
    }

    @Nullable
    public PassiveEntity createChild(ServerWorld world, PassiveEntity entity) {
        return null;
    }

    protected void setChildAttributes(PassiveEntity mate, BaseAphidEntity child) {
        double d = this.getAttributeBaseValue(EntityAttributes.GENERIC_MAX_HEALTH) + mate.getAttributeBaseValue(EntityAttributes.GENERIC_MAX_HEALTH) + (double)this.getChildHealthBonus();
        child.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(d / 3.0D);
        double e = this.getAttributeBaseValue(EntityAttributes.HORSE_JUMP_STRENGTH) + mate.getAttributeBaseValue(EntityAttributes.HORSE_JUMP_STRENGTH) + this.getChildJumpStrengthBonus();
        child.getAttributeInstance(EntityAttributes.HORSE_JUMP_STRENGTH).setBaseValue(e / 3.0D);
        double f = this.getAttributeBaseValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) + mate.getAttributeBaseValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) + this.getChildMovementSpeedBonus();
        child.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(f / 3.0D);
    }

    public boolean canBeControlledByRider() {
        return this.getPrimaryPassenger() instanceof LivingEntity;
    }

    public float getEatingGrassAnimationProgress(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.lastEatingGrassAnimationProgress, this.eatingGrassAnimationProgress);
    }

    public float getAngryAnimationProgress(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.lastAngryAnimationProgress, this.angryAnimationProgress);
    }

    public float getEatingAnimationProgress(float tickDelta) {
        return MathHelper.lerp(tickDelta, this.lastEatingAnimationProgress, this.eatingAnimationProgress);
    }

    @Override
    public void setJumpStrength(int strength) {
        if (strength < 0) {
            strength = 0;
        } else {
            this.jumping = true;
            this.updateAnger();
        }

        if (strength >= 90) {
            this.jumpStrength = 1.5F;
        } else {
            this.jumpStrength = 0.7F + 0.7F * (float) strength / 90.0F;
        }
    }

    @Override
    public EntityGroup getGroup() {
        return EntityGroup.ARTHROPOD;
    }

    public boolean canJump() {
        return true;
    }

    public void startJumping(int height) {
        this.jumping = true;
        this.updateAnger();
        this.playJumpSound();
    }

    public void stopJumping() {
    }

    protected void spawnPlayerReactionParticles(boolean positive) {
        ParticleEffect particleEffect = positive ? ParticleTypes.HEART : ParticleTypes.SMOKE;

        for(int i = 0; i < 7; ++i) {
            double d = this.random.nextGaussian() * 0.02D;
            double e = this.random.nextGaussian() * 0.02D;
            double f = this.random.nextGaussian() * 0.02D;
            this.world.addParticle(particleEffect, this.getParticleX(1.0D), this.getRandomBodyY() + 0.5D, this.getParticleZ(1.0D), d, e, f);
        }

    }

    public void handleStatus(byte status) {
        if (status == 7) {
            this.spawnPlayerReactionParticles(true);
        } else if (status == 6) {
            this.spawnPlayerReactionParticles(false);
        } else {
            super.handleStatus(status);
        }

    }

    public void updatePassengerPosition(Entity passenger) {
        super.updatePassengerPosition(passenger);
        if (passenger instanceof MobEntity) {
            MobEntity mobEntity = (MobEntity)passenger;
            this.bodyYaw = mobEntity.bodyYaw;
        }

        if (this.lastAngryAnimationProgress > 0.0F) {
            float f = MathHelper.sin(this.bodyYaw * 0.017453292F);
            float g = MathHelper.cos(this.bodyYaw * 0.017453292F);
            float h = 0.7F * this.lastAngryAnimationProgress;
            float i = 0.15F * this.lastAngryAnimationProgress;
            passenger.setPosition(this.getX() + (double)(h * f), this.getY() + this.getMountedHeightOffset() + passenger.getHeightOffset() + (double)i, this.getZ() - (double)(h * g));
            if (passenger instanceof LivingEntity) {
                ((LivingEntity)passenger).bodyYaw = this.bodyYaw;
            }
        }

    }

    protected float getChildHealthBonus() {
        return 20.0F + (float)this.random.nextInt(11) + (float)this.random.nextInt(9);
    }

    protected double getChildJumpStrengthBonus() {
        return 1.5D + this.random.nextDouble() * 0.7D + this.random.nextDouble() * 0.4D + this.random.nextDouble() * 0.4D;
    }

    protected double getChildMovementSpeedBonus() {
        return (0.9D + this.random.nextDouble() * 0.3D + this.random.nextDouble() * 0.3D + this.random.nextDouble() * 0.3D) * 0.25D;
    }

    public boolean isClimbing() {
        return false;
    }

    protected float getActiveEyeHeight(EntityPose pose, EntityDimensions dimensions) {
        return dimensions.height * 0.95F;
    }

    @Nullable
    public Entity getPrimaryPassenger() {
        return this.getFirstPassenger();
    }

    @Nullable
    private Vec3d locateSafeDismountingPos(Vec3d offset, LivingEntity passenger) {
        double d = this.getX() + offset.x;
        double e = this.getBoundingBox().minY;
        double f = this.getZ() + offset.z;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        UnmodifiableIterator var10 = passenger.getPoses().iterator();

        while(var10.hasNext()) {
            EntityPose entityPose = (EntityPose)var10.next();
            mutable.set(d, e, f);
            double g = this.getBoundingBox().maxY + 0.75D;

            while(true) {
                double h = this.world.getDismountHeight(mutable);
                if ((double)mutable.getY() + h > g) {
                    break;
                }

                if (Dismounting.canDismountInBlock(h)) {
                    Box box = passenger.getBoundingBox(entityPose);
                    Vec3d vec3d = new Vec3d(d, (double)mutable.getY() + h, f);
                    if (Dismounting.canPlaceEntityAt(this.world, passenger, box.offset(vec3d))) {
                        passenger.setPose(entityPose);
                        return vec3d;
                    }
                }

                mutable.move(Direction.UP);
                if (!((double)mutable.getY() < g)) {
                    break;
                }
            }
        }

        return null;
    }

    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        Vec3d vec3d = getPassengerDismountOffset((double)this.getWidth(), (double)passenger.getWidth(), this.getYaw() + (passenger.getMainArm() == Arm.RIGHT ? 90.0F : -90.0F));
        Vec3d vec3d2 = this.locateSafeDismountingPos(vec3d, passenger);
        if (vec3d2 != null) {
            return vec3d2;
        } else {
            Vec3d vec3d3 = getPassengerDismountOffset((double)this.getWidth(), (double)passenger.getWidth(), this.getYaw() + (passenger.getMainArm() == Arm.LEFT ? 90.0F : -90.0F));
            Vec3d vec3d4 = this.locateSafeDismountingPos(vec3d3, passenger);
            return vec3d4 != null ? vec3d4 : this.getPos();
        }
    }

    protected void initAttributes() {
    }

    @Nullable
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData, @Nullable NbtCompound entityNbt) {
        if (entityData == null) {
            entityData = new PassiveData(0.2F);
        }

        this.initAttributes();
        return super.initialize(world, difficulty, spawnReason, entityData, entityNbt);
    }

    static {
        PARENT_APHID_PREDICATE = TargetPredicate.createNonAttackable().setBaseMaxDistance(16.0D).ignoreVisibility().setPredicate(IS_BRED_APHID);
        BREEDING_INGREDIENT = Ingredient.ofItems(Items.CHORUS_FRUIT, Items.CHORUS_FLOWER);
        APHID_FLAGS = DataTracker.registerData(BaseAphidEntity.class, TrackedDataHandlerRegistry.BYTE);
        OWNER_UUID = DataTracker.registerData(BaseAphidEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    }
}

