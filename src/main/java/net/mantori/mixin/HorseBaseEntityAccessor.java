package net.mantori.mixin;


import net.minecraft.entity.passive.HorseBaseEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;



@Mixin(HorseBaseEntity.class)
public interface HorseBaseEntityAccessor {
        @Invoker("setEating")
        void invokeSetEating();
}

