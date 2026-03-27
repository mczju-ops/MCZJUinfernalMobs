package com.infernalmobs.skill.impl;

import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 织网：受击或攻击时，概率在玩家位置放置蜘蛛网。
 */
public class DualWebberSkill implements Skill {
    private static final String GIANT_WEB_ONE_TIME_KEY = "webber_giant_hollow_sphere_done";
    private static final String GIANT_WEB_META_KEY = "infernalmobs_giant_web";

    @Override
    public String getId() {
        return "webber";
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
        Player target = ctx.getTargetPlayer();
        if (target == null || !target.isOnline()) return;

        double chance = config.getDouble("chance", 0.3);
        if (Math.random() >= chance) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;  // 削弱: 概率减小50%

        boolean canGiantVariant = ctx.getEntity() != null
                && (ctx.getEntity().getType() == EntityType.SPIDER || ctx.getEntity().getType() == EntityType.CAVE_SPIDER);

        // 可选配置（旧配置不填也能跑）：生成巨型空心蛛网球的额外概率。
        // 注：触发 webber 技能本身仍受上面的 chance / weakened 限制。
        double giantChance = config.getDouble("giant-sphere-chance", 0.15);
        int radius = config.getInt("giant-sphere-radius", 8);
        double thickness = config.getDouble("giant-sphere-thickness", 0.35);
        int lifetimeTicks = config.getInt("giant-sphere-lifetime-ticks", 200); // 默认 10s

        if (canGiantVariant
                && ctx.getMobState() != null
                && !ctx.getMobState().hasUsedOneTime(GIANT_WEB_ONE_TIME_KEY)
                && Math.random() < giantChance
                && ctx.getMobState().useOneTimeIfNotUsed(GIANT_WEB_ONE_TIME_KEY)) {
            placeGiantHollowWebSphere(ctx.getPlugin(), target, radius, thickness, lifetimeTicks);
        } else {
            // 普通 web：在玩家脚下放蛛网；脚下不是空气则尝试脚下一格上方。
            Location loc = target.getLocation().getBlock().getLocation();
            Block block = loc.getBlock();
            if (block.getType().isAir()) {
                block.setType(Material.COBWEB);
            } else {
                Block above = loc.clone().add(0, 1, 0).getBlock();
                if (above.getType().isAir()) {
                    above.setType(Material.COBWEB);
                }
            }
        }

        String soundKey = config.getString("sound", "BLOCK_COBWEB_PLACE");
        try {
            Sound s = Sound.valueOf(soundKey.toUpperCase().replace(".", "_"));
            Location loc = target.getLocation().getBlock().getLocation();
            target.getWorld().playSound(loc, s, 1f, 0.8f);
        } catch (IllegalArgumentException ignored) {}
    }

    private static void placeGiantHollowWebSphere(JavaPlugin plugin, Player player, int radius, double thickness, int lifetimeTicks) {
        if (player.getWorld() == null) return;

        Location center = player.getLocation();
        String token = java.util.UUID.randomUUID().toString();
        double cx = center.getX();
        double cy = center.getY();
        double cz = center.getZ();

        double rMin = Math.max(0, radius - thickness);
        double rMax = radius + thickness;
        double rMinSq = rMin * rMin;
        double rMaxSq = rMax * rMax;

        int minX = (int) Math.floor(cx - rMax - 1);
        int maxX = (int) Math.ceil(cx + rMax + 1);
        int minY = (int) Math.floor(cy - rMax - 1);
        int maxY = (int) Math.ceil(cy + rMax + 1);
        int minZ = (int) Math.floor(cz - rMax - 1);
        int maxZ = (int) Math.ceil(cz + rMax + 1);

        Material web = Material.COBWEB;
        java.util.List<org.bukkit.Location> placedLocations = new java.util.ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                double dy = (y + 0.5) - cy;
                for (int z = minZ; z <= maxZ; z++) {
                    double dx = (x + 0.5) - cx;
                    double dz = (z + 0.5) - cz;
                    double distSq = dx * dx + dy * dy + dz * dz;

                    // 空心球：只放在“半径为 radius 的表面”附近
                    if (distSq < rMinSq || distSq > rMaxSq) continue;

                    Block b = player.getWorld().getBlockAt(x, y, z);
                    if (!b.getType().isAir()) continue;
                    b.setType(web);
                    b.setMetadata(GIANT_WEB_META_KEY, new FixedMetadataValue(plugin, token));
                    placedLocations.add(b.getLocation().clone());
                }
            }
        }

        if (lifetimeTicks <= 0 || placedLocations.isEmpty()) return;

        // 寿命到后，只删除“仍是蛛网且带有本次 token 元数据”的方块，避免误删其它来源的蛛网/方块。
        org.bukkit.scheduler.BukkitRunnable cleaner = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (placedLocations.isEmpty()) return;
                for (org.bukkit.Location loc : placedLocations) {
                    if (loc == null || loc.getWorld() == null) continue;
                    Block b = loc.getWorld().getBlockAt(loc);
                    if (b.getType() != Material.COBWEB) continue;
                    if (!b.hasMetadata(GIANT_WEB_META_KEY)) continue;

                    boolean matches = b.getMetadata(GIANT_WEB_META_KEY).stream()
                            .anyMatch(m -> plugin.equals(m.getOwningPlugin()) && token.equals(m.value()));
                    if (!matches) continue;

                    b.removeMetadata(GIANT_WEB_META_KEY, plugin);
                    b.setType(Material.AIR);
                }
            }
        };
        cleaner.runTaskLater(plugin, lifetimeTicks);
    }
}
