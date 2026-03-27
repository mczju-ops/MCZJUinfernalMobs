package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import com.infernalmobs.util.Keys;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
/**
 * 盗贼：受击或攻击时，玩家主手物品掉落在怪物身后。
 * 触发前存主手、副手快照，延迟后再读并对比：若主手物品已消失（如耐久为 0 被消耗），则不执行缴械；若主手物品还在，再照常缴械。
 */
public class DualThiefSkill implements Skill {

    @Override
    public String getId() {
        return "thief";
    }

    @Override
    public SkillType getType() {
        return SkillType.DUAL;
    }

    @Override
    public void onEquip(SkillContext ctx, SkillConfig config) {}

    @Override
    public void onUnequip(SkillContext ctx) {}

    @Override
    public void onTrigger(SkillContext ctx, SkillConfig config) {
        Player player = ctx.getTargetPlayer();
        if (player == null || !player.isOnline() || player.getGameMode() == GameMode.CREATIVE) return;

        // 触发前存主手（延迟后再对比：主手消失则不缴械）
        ItemStack mainBefore = player.getInventory().getItemInMainHand().clone();
        if (mainBefore.getType().isAir()) return;
        // 主手携带免疫缴械标记时，直接取消本次缴械
        if (hasThiefResistance(mainBefore)) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;  // 削弱: 概率减小50%

        // 掉落坐标用触发时怪物位置，延迟任务内不再用 ctx.getEntity()。这样与变身同时触发时，原实体被移除、新实体同位置生成，掉落仍落在“怪物处”正确位置
        Location mobLoc = ctx.getEntity().getLocation().clone();
        String soundKey = config.getString("sound", "ENTITY_WIND_CHARGE_THROW");
        // 反制：副手 thief_counter 会在“本次缴械成功”后封住该怪一段时间
        int counterDurationTicks = config.getInt("counter-duration-ticks", 200);
        String counterSoundKey = config.getString("counter-sound", "ENTITY_SPIDER_DEATH");
        String counterParticleKey = config.getString("counter-particle", "SPIDER_EYE");
        String lineParticleKey = config.getString("counter-line-particle", "REDSTONE");
        final long triggerTick = ctx.getCurrentTick();
        final JavaPlugin plugin = ctx.getPlugin();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                ItemStack mainNow = player.getInventory().getItemInMainHand();
                // 对比：主手物品消失了（被消耗/破损）则不执行缴械；主手还在则照常缴械
                if (mainNow.getType().isAir()) return;
                // 延迟到执行时再次判断，避免玩家在 1 tick 内换上了免疫物品
                if (hasThiefResistance(mainNow)) return;
                // 主手物品还在，照常缴械（dropAt 基于触发时保存的 mobLoc，与是否变身无关）
                Location dropAt = mobLoc.clone().add(0, 0.5, 0);

                // 画线：玩家眼睛 -> 掉落点（方便看清缴械触发方向）
                spawnLineParticles(lineParticleKey, player.getEyeLocation(), dropAt, mobLoc);

                player.getInventory().setItemInMainHand(ItemStack.empty());
                if (mobLoc.getWorld() != null) {
                    var dropped = mobLoc.getWorld().dropItemNaturally(dropAt, mainNow.clone());
                    if (dropped != null) dropped.setInvulnerable(true);
                    try {
                        Sound sound = Sound.valueOf(soundKey.toUpperCase().replace(".", "_"));
                        mobLoc.getWorld().playSound(mobLoc, sound, 0.8f, 0.6f);
                    } catch (IllegalArgumentException ignored) {}
                }

                // 反制触发：副手 thief_counter
                if (hasThiefCounter(player.getInventory().getItemInOffHand())) {
                    // 消耗副手 1 个
                    consumeThiefCounter(player);
                    // 延长该怪的 thief 冷却（用于封住缴械能力）
                    ctx.getMobState().setCooldown("thief", triggerTick + counterDurationTicks);
                    // 绘制螺旋粒子特效（蜘蛛眼粒子，围绕怪物转）
                    if (mobLoc.getWorld() != null && counterDurationTicks > 0) {
                        Particle particle = parseParticle(counterParticleKey, "SPELL_WITCH");
                        if (particle != null) spawnSpiralCounter(ctx.getEntity(), mobLoc, particle, counterDurationTicks, plugin);
                    }
                    // 音效
                    try {
                        if (mobLoc.getWorld() != null) {
                            Sound s2 = Sound.valueOf(counterSoundKey.toUpperCase().replace(".", "_"));
                            mobLoc.getWorld().playSound(mobLoc, s2, 1f, 1f);
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }.runTaskLater(ctx.getPlugin(), 1L);
    }

    /**
     * 免疫缴械标记：PDC mczju:im_thief_resistance = 1b/true。
     */
    private static boolean hasThiefResistance(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        Byte b = meta.getPersistentDataContainer().get(Keys.IM_THIEF_RESISTANCE, PersistentDataType.BYTE);
        if (b != null) return b != 0;
        String s = meta.getPersistentDataContainer().get(Keys.IM_THIEF_RESISTANCE, PersistentDataType.STRING);
        return s != null && ("1".equals(s) || "true".equalsIgnoreCase(s));
    }

    private static boolean hasThiefCounter(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        if (!meta.getPersistentDataContainer().has(Keys.MI_ID, PersistentDataType.STRING)) return false;
        String miId = meta.getPersistentDataContainer().get(Keys.MI_ID, PersistentDataType.STRING);
        return miId != null && "thief_counter".equalsIgnoreCase(miId.trim());
    }

    private static void consumeThiefCounter(Player player) {
        if (player == null) return;
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off == null || off.getType().isAir()) return;
        int amt = off.getAmount();
        if (amt <= 1) player.getInventory().setItemInOffHand(ItemStack.empty());
        else off.setAmount(amt - 1);
    }

    private void spawnLineParticles(String particleKey, Location from, Location to, Location anchor) {
        if (anchor == null || anchor.getWorld() == null || from == null || to == null) return;
        Particle p = parseParticle(particleKey, "SPELL_WITCH");
        if (p == null) return;
        var world = anchor.getWorld();
        Vector dir = to.toVector().add(new Vector(0, 0.3, 0)).subtract(from.toVector()).normalize();
        double dist = from.distance(to);
        double step = 0.16; // 更密，线更明显
        int count = (int) Math.max(1, dist / step);
        for (int i = 0; i <= count; i++) {
            Location loc = from.clone().add(dir.clone().multiply(i * step));
            // 每个采样点多喷几个粒子，提高可见度
            world.spawnParticle(p, loc, 4, 0.03, 0.03, 0.03, 0);
        }
    }

    private void spawnSpiralCounter(org.bukkit.entity.LivingEntity mob, Location fallbackLoc, Particle particle, int durationTicks, JavaPlugin plugin) {
        if (fallbackLoc == null || fallbackLoc.getWorld() == null) return;
        long total = Math.max(1, durationTicks);
        BukkitRunnable task = new BukkitRunnable() {
            long t = 0;

            @Override
            public void run() {
                if (t >= total) {
                    cancel();
                    return;
                }
                Location baseLoc = fallbackLoc;
                if (mob != null && mob.isValid() && !mob.isDead()) {
                    baseLoc = mob.getLocation();
                }

                // 螺旋：固定半径，不扩散；并围绕当前怪物位置（跟随移动）
                double angle = t * 0.35;
                double radius = 0.95;
                double y = baseLoc.getY() + 0.85 + Math.sin(t * 0.22) * 0.45;
                double x = baseLoc.getX() + Math.cos(angle) * radius;
                double z = baseLoc.getZ() + Math.sin(angle) * radius;
                Location loc = new Location(baseLoc.getWorld(), x, y, z);
                baseLoc.getWorld().spawnParticle(particle, loc, 2, 0.08, 0.08, 0.08, 0);
                // 两点补一个“圈感”
                if (t % 2 == 0) {
                    double angle2 = angle + Math.PI;
                    double x2 = baseLoc.getX() + Math.cos(angle2) * radius;
                    double z2 = baseLoc.getZ() + Math.sin(angle2) * radius;
                    Location loc2 = new Location(baseLoc.getWorld(), x2, y, z2);
                    baseLoc.getWorld().spawnParticle(particle, loc2, 2, 0.08, 0.08, 0.08, 0);
                }
                t++;
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
    }

    private static Particle parseParticle(String key, String fallbackKey) {
        if (key != null && !key.isEmpty()) {
            try {
                return Particle.valueOf(key.toUpperCase().replace(".", "_"));
            } catch (IllegalArgumentException ignored) {}
        }
        try {
            return Particle.valueOf(fallbackKey.toUpperCase().replace(".", "_"));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
