package com.infernalmobs.skill.impl;

import com.infernalmobs.api.event.affix.effect.InfernalMobWebberPlaceEvent;
import com.infernalmobs.api.event.affix.triggered.InfernalMobWebberEvent;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.skill.Skill;
import com.infernalmobs.skill.SkillContext;
import com.infernalmobs.skill.SkillType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 织网：受击或攻击时，概率在玩家位置放置蜘蛛网。
 */
public class DualWebberSkill implements Skill {
    private static final String GIANT_WEB_ONE_TIME_KEY = "webber_giant_hollow_sphere_done";
    private static final String GIANT_WEB_META_KEY = "infernalmobs_giant_web";
    private static final String WEB_META_KEY = "infernalmobs_web";

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
        LivingEntity mob = ctx.getEntity();
        if (mob == null || !mob.isValid()) return;
        Player target = ctx.getTargetPlayer();
        if (target == null || !target.isOnline()) return;

        double chance = config.getDouble("chance", 0.3);
        if (Math.random() >= chance) return;
        if (ctx.isWeakened() && Math.random() < 0.5) return;  // 削弱: 概率减小50%

        // 可选配置（旧配置不填也能跑）：生成巨型空心蛛网球的额外概率。
        // 注：触发 webber 技能本身仍受上面的 chance / weakened 限制。
        double giantChance = config.getDouble("giant-sphere-chance", 0.15);
        int radius = config.getInt("giant-sphere-radius", 8);
        double thickness = config.getDouble("giant-sphere-thickness", 0.35);
        int lifetimeTicks = config.getInt("giant-sphere-lifetime-ticks", 200);
        int webLifetimeTicks = config.getInt("web-lifetime-ticks", 100); // 普通网寿命，默认 5s

        boolean canGiantVariant = mob.getType() == EntityType.SPIDER || mob.getType() == EntityType.CAVE_SPIDER;
        boolean giantSphere = canGiantVariant
                && ctx.getMobState() != null
                && !ctx.getMobState().hasUsedOneTime(GIANT_WEB_ONE_TIME_KEY)
                && Math.random() < giantChance
                && ctx.getMobState().useOneTimeIfNotUsed(GIANT_WEB_ONE_TIME_KEY);

        Location center;
        int selectedLifetimeTicks;
        if (giantSphere) {
            center = target.getLocation();
            selectedLifetimeTicks = lifetimeTicks;
        } else {
            Block candidate = findNormalWebCandidate(target.getLocation());
            if (candidate == null) return;
            center = candidate.getLocation();
            selectedLifetimeTicks = webLifetimeTicks;
        }

        InfernalMobWebberEvent event = new InfernalMobWebberEvent(
                mob, target, ctx.getHandle(), ctx.getMobState().getProfile().getLevel(),
                giantSphere, center, selectedLifetimeTicks, radius, thickness);
        if (!ctx.fire(event)) return;

        int placedCount = event.isGiantSphere()
                ? placeGiantHollowWebSphere(ctx, target, event)
                : placeNormalWeb(ctx, target, event);
        if (placedCount == 0) return;

        String soundKey = config.getString("sound", "BLOCK_COBWEB_PLACE");
        try {
            Sound s = Sound.valueOf(soundKey.toUpperCase().replace(".", "_"));
            Location soundLocation = event.getCenter();
            World soundWorld = soundLocation.getWorld();
            if (soundWorld != null) soundWorld.playSound(soundLocation, s, 1f, 0.8f);
        } catch (IllegalArgumentException ignored) {}
    }

    private static Block findNormalWebCandidate(Location targetLocation) {
        Block block = targetLocation.getBlock();
        if (block.getType().isAir()) return block;

        Block above = targetLocation.clone().add(0, 1, 0).getBlock();
        return above.getType().isAir() ? above : null;
    }

    private static int placeNormalWeb(SkillContext ctx, Player player, InfernalMobWebberEvent event) {
        Location location = event.getCenter();
        if (location.getWorld() == null) return 0;

        Block block = location.getBlock();
        if (!block.getType().isAir()) return 0;
        InfernalMobWebberPlaceEvent placeEvent = new InfernalMobWebberPlaceEvent(
                ctx.getEntity(), player, block, ctx.getHandle(), event.getLevel(), false);
        if (!ctx.fire(placeEvent) || !block.getType().isAir()) return 0;

        block.setType(Material.COBWEB);
        if (event.getLifetimeTicks() > 0) {
            scheduleWebRemoval(ctx.getPlugin(), block, event.getLifetimeTicks());
        }
        return 1;
    }

    private static int placeGiantHollowWebSphere(SkillContext ctx, Player player, InfernalMobWebberEvent event) {
        Location center = event.getCenter();
        World world = center.getWorld();
        if (world == null) return 0;

        JavaPlugin plugin = ctx.getPlugin();
        int radius = event.getRadius();
        double thickness = event.getThickness();
        int lifetimeTicks = event.getLifetimeTicks();
        String token = lifetimeTicks > 0 ? java.util.UUID.randomUUID().toString() : null;
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

        java.util.List<org.bukkit.Location> placedLocations = new java.util.ArrayList<>();
        int placedCount = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                double dy = (y + 0.5) - cy;
                for (int z = minZ; z <= maxZ; z++) {
                    double dx = (x + 0.5) - cx;
                    double dz = (z + 0.5) - cz;
                    double distSq = dx * dx + dy * dy + dz * dz;

                    // 空心球：只放在“半径为 radius 的表面”附近
                    if (distSq < rMinSq || distSq > rMaxSq) continue;

                    Block b = world.getBlockAt(x, y, z);
                    if (!b.getType().isAir()) continue;
                    InfernalMobWebberPlaceEvent placeEvent = new InfernalMobWebberPlaceEvent(
                            ctx.getEntity(), player, b, ctx.getHandle(), event.getLevel(), true);
                    if (!ctx.fire(placeEvent) || !b.getType().isAir()) continue;

                    b.setType(Material.COBWEB);
                    placedCount++;
                    if (token != null) {
                        b.setMetadata(GIANT_WEB_META_KEY, new FixedMetadataValue(plugin, token));
                        placedLocations.add(b.getLocation().clone());
                    }
                }
            }
        }

        if (token == null || placedLocations.isEmpty()) return placedCount;

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
        return placedCount;
    }

    /** 普通蛛网定时消失：寿命到期后若方块仍是蛛网则清除。 */
    private static void scheduleWebRemoval(JavaPlugin plugin, Block block, int delayTicks) {
        String token = java.util.UUID.randomUUID().toString();
        block.setMetadata(WEB_META_KEY, new FixedMetadataValue(plugin, token));
        org.bukkit.Location loc = block.getLocation().clone();

        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (loc.getWorld() == null) return;
                Block b = loc.getWorld().getBlockAt(loc);
                if (b.getType() != Material.COBWEB) return;
                if (!b.hasMetadata(WEB_META_KEY)) return;
                boolean matches = b.getMetadata(WEB_META_KEY).stream()
                        .anyMatch(m -> plugin.equals(m.getOwningPlugin()) && token.equals(m.value()));
                if (!matches) return;
                b.removeMetadata(WEB_META_KEY, plugin);
                b.setType(Material.AIR);
            }
        }.runTaskLater(plugin, delayTicks);
    }
}
