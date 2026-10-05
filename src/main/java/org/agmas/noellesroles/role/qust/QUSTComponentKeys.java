package org.agmas.noellesroles.role.qust;

import net.minecraft.resources.ResourceLocation;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

/**
 * QUST 组件键的延迟初始化持有类。
 * <p>
 * 所有 QUST 组件的 {@link ComponentKey} 在此集中定义，利用 JVM 类加载机制
 * 确保 {@link ComponentRegistry#getOrCreate} 仅在首次访问 {@code Keys} 内部类时调用，
 * 而非在 ModComponents 或 QUSTRoles 的静态初始化期间触发。
 * <p>
 * 这避免了 CCA 元数据尚未处理时就调用 getOrCreate 导致 IllegalStateException。
 */
public final class QUSTComponentKeys {
    private QUSTComponentKeys() {}

    /**
     * 内部持有类 — 仅在首次访问 Keys.XXX 时由 JVM 加载并初始化。
     * 此时 CCA 元数据已经处理完毕（因为工厂注册方法已执行）。
     */
    public static final class Keys {
        public static final ComponentKey<org.agmas.noellesroles.role.qust.roles.dragon_girl.DragonGirlPlayerComponent> DRAGON_GIRL =
            ComponentRegistry.getOrCreate(ResourceLocation.fromNamespaceAndPath("qust", "dragon_girl"),
                org.agmas.noellesroles.role.qust.roles.dragon_girl.DragonGirlPlayerComponent.class);

        public static final ComponentKey<org.agmas.noellesroles.role.qust.roles.mascot.MascotPlayerComponent> MASCOT =
            ComponentRegistry.getOrCreate(ResourceLocation.fromNamespaceAndPath("qust", "mascot"),
                org.agmas.noellesroles.role.qust.roles.mascot.MascotPlayerComponent.class);

        public static final ComponentKey<org.agmas.noellesroles.role.qust.roles.american_police.AmericanPolicePlayerComponent> AMERICAN_POLICE =
            ComponentRegistry.getOrCreate(ResourceLocation.fromNamespaceAndPath("qust", "american_police"),
                org.agmas.noellesroles.role.qust.roles.american_police.AmericanPolicePlayerComponent.class);

        public static final ComponentKey<org.agmas.noellesroles.role.qust.roles.pressure_monster.PressureMonsterPlayerComponent> PRESSURE_MONSTER =
            ComponentRegistry.getOrCreate(ResourceLocation.fromNamespaceAndPath("qust", "pressure_monster"),
                org.agmas.noellesroles.role.qust.roles.pressure_monster.PressureMonsterPlayerComponent.class);

        public static final ComponentKey<org.agmas.noellesroles.role.qust.roles.minigame_master.MinigameMasterPlayerComponent> MINIGAME_MASTER =
            ComponentRegistry.getOrCreate(ResourceLocation.fromNamespaceAndPath("qust", "minigame_master"),
                org.agmas.noellesroles.role.qust.roles.minigame_master.MinigameMasterPlayerComponent.class);

        public static final ComponentKey<org.agmas.noellesroles.role.qust.roles.super_recorder.SuperRecorderPlayerComponent> SUPER_RECORDER =
            ComponentRegistry.getOrCreate(ResourceLocation.fromNamespaceAndPath("qust", "super_recorder"),
                org.agmas.noellesroles.role.qust.roles.super_recorder.SuperRecorderPlayerComponent.class);

        public static final ComponentKey<org.agmas.noellesroles.role.qust.roles.wanderer.WandererPlayerComponent> WANDERER =
            ComponentRegistry.getOrCreate(ResourceLocation.fromNamespaceAndPath("qust", "wanderer"),
                org.agmas.noellesroles.role.qust.roles.wanderer.WandererPlayerComponent.class);

        public static final ComponentKey<org.agmas.noellesroles.role.qust.roles.super_doctor.SuperDoctorPlayerComponent> SUPER_DOCTOR =
            ComponentRegistry.getOrCreate(ResourceLocation.fromNamespaceAndPath("qust", "super_doctor"),
                org.agmas.noellesroles.role.qust.roles.super_doctor.SuperDoctorPlayerComponent.class);

        public static final ComponentKey<org.agmas.noellesroles.role.qust.roles.bettor.BettorPlayerComponent> BETTOR =
            ComponentRegistry.getOrCreate(ResourceLocation.fromNamespaceAndPath("qust", "bettor"),
                org.agmas.noellesroles.role.qust.roles.bettor.BettorPlayerComponent.class);

        public static final ComponentKey<org.agmas.noellesroles.role.qust.roles.hacker.HackerRoleData> HACKER =
            ComponentRegistry.getOrCreate(ResourceLocation.fromNamespaceAndPath("qust", "hacker"),
                org.agmas.noellesroles.role.qust.roles.hacker.HackerRoleData.class);
    }
}
