package com.infernalmobs.skill.impl;

import com.infernalmobs.api.event.affix.effect.InfernalMobSulfurLaunchEvent;
import com.infernalmobs.api.event.affix.triggered.InfernalMobSulfurEvent;
import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.config.SoundConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import com.infernalmobs.util.SoundPlayback;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 受击时在攻击者脚下生成硫磺喷泉：先有地面预警圈，后可喷发。
 * 预警阶段地面粒子圈 + whirlpool_ambient 音效；喷发阶段粒子柱从下到上生成 + upwards_inside 音效。
 */
public class PassiveSulfurSkill implements Skill {

    /** 每只实体只保留一个正在运行的硫磺喷泉任务；该登记只用于防止重复释放。 */
    private final Map<UUID, BukkitTask> activeFountains = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "sulfur";
    }

    @Override
    public SkillType getType() {
        return SkillType.PASSIVE;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {}

    /** 硫磺在初始事件通过后视为脱手技能，实体生命周期不会取消已释放的喷泉。 */
    @Override
    public void onUnequip(SkillContext ctx) {}

    @Override
    public void onTrigger(SkillContext ctx, SkillConfig config) {
        if (!(ctx.getTriggerEvent() instanceof EntityDamageByEntityEvent)) return;
        Player target = ctx.getTargetPlayer();
        LivingEntity mob = ctx.getEntity();
        if (target == null || !target.isOnline() || mob == null || !mob.isValid()) return;

        double chance = config.getDouble("chance", 0.25);
        if (Math.random() >= chance) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;

        int warnTicks = config.getInt("warn-ticks", 20);
        Location playerLoc = target.getLocation();
        Location center = toGroundLocation(playerLoc);
        double radius = config.getDouble("radius", 1.0);
        double upward = config.getDouble("upward", 1.15);
        double columnHeight = config.getDouble("column-height", 2.5);
        SoundConfig warnSound = config.getSound("warn-sound");
        SoundConfig eruptSound = config.getSound("erupt-sound");

        InfernalMobSulfurEvent event = new InfernalMobSulfurEvent(
                mob, target, ctx.getHandle(), ctx.getMobState().getProfile().getLevel(),
                center, warnTicks, radius, upward, columnHeight);
        if (!ctx.fire(event)) return;

        center = event.getCenter();
        if (center.getWorld() == null) return;
        warnTicks = event.getWarnTicks();
        radius = event.getRadius();
        upward = event.getUpward();
        columnHeight = event.getColumnHeight();
        // 预警音效
        SoundPlayback.broadcast(center, warnSound);

        Location finalCenter = center;
        int finalWarnTicks = warnTicks;
        double finalRadius = radius;
        double finalUpward = upward;
        double finalColumnHeight = columnHeight;
        SoundConfig finalEruptSound = eruptSound;
        InfernalMobHandle releasedHandle = ctx.getOrCreateHandle();
        int releasedLevel = ctx.getMobState().getProfile().getLevel();
        boolean releasedWeakened = ctx.isWeakened();
        var plugin = ctx.getPlugin();

        BukkitTask previous = activeFountains.remove(mob.getUniqueId());
        if (previous != null) previous.cancel();

        UUID mobUuid = mob.getUniqueId();
        BukkitTask task = new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
                if (tick < finalWarnTicks) {
                    for (int i = 0; i < 16; i++) {
                        double angle = Math.PI * 2 * i / 16 + tick * 0.15;
                        double x = Math.cos(angle) * finalRadius;
                        double z = Math.sin(angle) * finalRadius;
                        finalCenter.getWorld().spawnParticle(Particle.NOXIOUS_GAS,
                                finalCenter.getX() + x, finalCenter.getY() + 0.1, finalCenter.getZ() + z,
                                1, 0, 0, 0, 0);
                    }
                    tick++;
                    return;
                }

                if (tick == finalWarnTicks) {
                    SoundPlayback.broadcast(finalCenter, finalEruptSound);

                    for (Player p : finalCenter.getWorld().getNearbyPlayers(
                            finalCenter, finalRadius, finalRadius, finalRadius)) {
                        if (!p.isOnline() || p.isDead()) continue;
                        double factor = 1.0;
                        if (releasedWeakened && p.equals(target)) factor *= 0.5;
                        InfernalMobSulfurLaunchEvent launchEvent = new InfernalMobSulfurLaunchEvent(
                                mob, p, releasedHandle, releasedLevel,
                                finalUpward * factor);
                        // 这是喷发后的逐玩家阶段事件，不改变 sulfur 已经成功触发及提交冷却的事实。
                        plugin.getServer().getPluginManager().callEvent(launchEvent);
                        if (launchEvent.isCancelled()) continue;

                        double up = launchEvent.getUpward();
                        if (up > 0.01) {
                            p.setVelocity(p.getVelocity().setY(Math.max(p.getVelocity().getY(), up)));
                        }
                    }
                }

                int columnTick = tick - finalWarnTicks;
                if (columnTick > 10) {
                    activeFountains.remove(mobUuid);
                    cancel();
                    return;
                }
                double currentHeight = finalColumnHeight * columnTick / 10.0;
                for (double y = 0; y < currentHeight; y += 0.3) {
                    double spread = y / finalColumnHeight * 0.4;
                    finalCenter.getWorld().spawnParticle(Particle.NOXIOUS_GAS,
                            finalCenter.getX(), finalCenter.getY() + y, finalCenter.getZ(),
                            2, spread, 0.1, spread, 0.02);
                }
                tick++;
            }
        }.runTaskTimer(ctx.getPlugin(), 0L, 1L);
        activeFountains.put(mobUuid, task);
    }

    private Location toGroundLocation(Location loc) {
        Location ground = loc.clone();
        for (int y = loc.getBlockY(); y > loc.getWorld().getMinHeight(); y--) {
            ground.setY(y);
            if (ground.getBlock().getType().isSolid()) {
                ground.setY(y + 1);
                return ground;
            }
        }
        ground.setY(loc.getWorld().getMinHeight() + 1);
        return ground;
    }

}
