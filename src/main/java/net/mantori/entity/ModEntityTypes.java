package net.mantori.entity;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.mantori.Mantori;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.Heightmap;

public class ModEntityTypes implements ModInitializer {

    public static final EntityType<GreaterAphid> GREATER_APHID_ENTITY_TYPE =
            Registry.register(Registry.ENTITY_TYPE, new Identifier(Mantori.MOD_ID, "greater_aphid"),
                    FabricEntityTypeBuilder.createMob()
                            .spawnGroup(SpawnGroup.CREATURE)
                            .entityFactory(GreaterAphid::new)
                            .defaultAttributes(GreaterAphid::createBaseHorseAttributes)
                            .dimensions(EntityDimensions.fixed(2.0F, 3.0F))
                            .spawnRestriction(SpawnRestriction.Location.NO_RESTRICTIONS, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, GreaterAphid::canSpawnIgnoreLightLevel)
                            .build());

    public static final EntityType<LesserAphid> LESSER_APHID_ENTITY_TYPE =
            Registry.register(Registry.ENTITY_TYPE, new Identifier(Mantori.MOD_ID, "lesser_aphid"),
                    FabricEntityTypeBuilder.createMob()
                            .spawnGroup(SpawnGroup.CREATURE)
                            .entityFactory(LesserAphid::new)
                            .defaultAttributes(LesserAphid::createAttributes)
                            .dimensions(EntityDimensions.fixed(1.24F, 1.0F))
                            .spawnRestriction(SpawnRestriction.Location.NO_RESTRICTIONS, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, LesserAphid::canSpawnIgnoreLightLevel)
                            .build());

    public static void registerModEntities() {
        Mantori.LOGGER.info("Registering mod entities for " + Mantori.MOD_ID);
    }

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(GREATER_APHID_ENTITY_TYPE, GreaterAphid.createBaseHorseAttributes());
        FabricDefaultAttributeRegistry.register(LESSER_APHID_ENTITY_TYPE, LesserAphid.createAttributes());
    }
}
