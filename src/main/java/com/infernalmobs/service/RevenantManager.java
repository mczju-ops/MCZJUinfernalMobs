package com.infernalmobs.service;

import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.api.InfernalMobHandle;
import com.infernalmobs.api.event.affix.triggered.InfernalMobRevenantEvent;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.registry.SkillRegistry;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Vex;
import org.bukkit.GameMode;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** 管理运行期怨念：融合、粒子显示、过期清理和炒鸡恼鬼生成。 */
public final class RevenantManager {

    private static final String REVENANT_TEAM_NAME = "revenant_team";

    private final JavaPlugin plugin;
    private final ConfigLoader config;
    private final List<RevenantNode> nodes = new ArrayList<>();
    private MobFactory mobFactory;

    public RevenantManager(JavaPlugin plugin, ConfigLoader config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void setMobFactory(MobFactory mobFactory) {
        this.mobFactory = mobFactory;
    }

    /** 在死亡位置加入怨念，并吸收融合半径内的全部旧怨念。 */
    public void add(Location location, int level, long currentTick,
                    LivingEntity mob, Player target, InfernalMobHandle handle) {
        if (location == null || location.getWorld() == null || level <= 0) return;
        SkillConfig cfg = config.getSkillConfig("revenant");
        if (cfg == null) return;

        double radius = Math.max(0.0, cfg.getDouble("merge-radius", 8.0));
        int total = level;
        List<Location> absorbedLocations = new ArrayList<>();
        List<RevenantNode> absorbedNodes = new ArrayList<>();
        for (Iterator<RevenantNode> it = nodes.iterator(); it.hasNext();) {
            RevenantNode node = it.next();
            if (sameWorld(location, node.location())
                    && location.distanceSquared(node.location()) <= radius * radius) {
                total += node.level();
                absorbedLocations.add(node.location().clone());
                absorbedNodes.add(node);
                it.remove();
            }
        }

        int threshold = Math.max(1, cfg.getInt("summon-threshold", 20));
        Location newLocation = findGroundLocation(location);
        int absorbedLevel = total - level;
        InfernalMobRevenantEvent event = new InfernalMobRevenantEvent(
                mob, target, handle, level, newLocation, level, absorbedLevel,
                absorbedLocations.size(), total, threshold);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            nodes.addAll(absorbedNodes);
            return;
        }
        newLocation = event.getRevenantLocation();
        if (total >= threshold) {
            Runnable summon = () -> summon(newLocation, cfg);
            if (absorbedLocations.isEmpty()) {
                startThresholdSequence(newLocation, radius, threshold, summon);
            } else {
                playMergeAnimation(newLocation, absorbedLocations, radius,
                        () -> startThresholdSequence(newLocation, radius, threshold, summon));
            }
            return;
        }

        long duration = Math.max(1L, cfg.getInt("duration-ticks", 600));
        nodes.add(new RevenantNode(newLocation, total, currentTick + duration));
        if (!absorbedLocations.isEmpty()) {
            playMergeAnimation(newLocation, absorbedLocations, radius, null);
        }
    }

    /** 由战斗主线程周期调用；区块未加载时只保留数据，不播放粒子。 */
    public void tick(long currentTick) {
        SkillConfig cfg = config.getSkillConfig("revenant");
        if (cfg == null) return;
        for (Iterator<RevenantNode> it = nodes.iterator(); it.hasNext();) {
            RevenantNode node = it.next();
            if (currentTick >= node.expireTick()) {
                it.remove();
                continue;
            }
            Location loc = node.location();
            World world = loc.getWorld();
            if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;
            int threshold = Math.max(1, cfg.getInt("summon-threshold", 20));
            int count = particleCount(node.level(), threshold);
            world.spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0, 0.35, 0), count,
                    0.35, 0.35, 0.35, 0.01);
        }
    }

    public void shutdown() {
        nodes.clear();
    }

    private void summon(Location location, SkillConfig cfg) {
        World world = location.getWorld();
        if (world == null || mobFactory == null) return;
        List<String> affixes = cfg.getStringList("summon-affixes");
        int effectiveAffixCount = countEffectiveAffixes(affixes);
        if (effectiveAffixCount <= 0) return;

        int summonCount = Math.max(1, Math.min(64, cfg.getInt("summon-count", 1)));
        Team team = getRevenantTeam();
        Player nearestPlayer = findNearestPlayer(location, 64.0);
        for (int i = 0; i < summonCount; i++) {
            Vex vex = (Vex) world.spawnEntity(location, EntityType.VEX);
            vex.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                    PotionEffect.INFINITE_DURATION, 0, false, false, false));
            team.addEntity(vex);
            addFollowRange(vex);
            mobFactory.mechanizeWithAffixes(vex, location, effectiveAffixCount, affixes);
            if (nearestPlayer != null) vex.setTarget(nearestPlayer);
        }
        broadcastSummonSound(location, cfg.getDouble("merge-radius", 8.0));
    }

    private static Player findNearestPlayer(Location location, double radius) {
        World world = location.getWorld();
        if (world == null) return null;
        double radiusSquared = radius * radius;
        Player nearest = null;
        double nearestDistanceSquared = radiusSquared;
        for (Player player : world.getPlayers()) {
            if (!player.isValid() || player.isDead() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) continue;
            double distanceSquared = player.getLocation().distanceSquared(location);
            if (distanceSquared <= nearestDistanceSquared) {
                nearest = player;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return nearest;
    }

    /** 播放旧怨念向新位置汇聚的粒子流，并在终点播放尖啸。 */
    private void playMergeAnimation(Location target, List<Location> sources,
                                    double radius, Runnable afterAnimation) {
        if (sources.isEmpty()) {
            playShriekPulse(target, radius);
            if (afterAnimation != null) afterAnimation.run();
            return;
        }
        final int duration = 20;
        new BukkitRunnable() {
            private int tick;

            @Override
            public void run() {
            int currentTick = tick++;
            if (currentTick > duration) {
                cancel();
                if (afterAnimation != null) afterAnimation.run();
                else playShriekPulse(target, radius);
                return;
            }
            double progress = currentTick / (double) duration;
            World world = target.getWorld();
            if (world == null || !world.isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4)) return;
            for (Location source : sources) {
                if (!sameWorld(source, target)) continue;
                Location point = source.clone().add(
                        (target.getX() - source.getX()) * progress,
                        (target.getY() - source.getY()) * progress,
                        (target.getZ() - source.getZ()) * progress);
                world.spawnParticle(Particle.SCULK_SOUL, point, 2, 0.03, 0.03, 0.03, 0.01);
            }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static void playShriekPulse(Location location, double radius) {
        World world = location.getWorld();
        if (world == null) return;
        double radiusSquared = radius * radius;
        for (org.bukkit.entity.Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= radiusSquared) {
                player.playSound(location, "minecraft:block.sculk_shrieker.shriek", 1.0f, 1.5f);
            }
        }
        world.spawnParticle(Particle.SHRIEK, location.clone().add(0, 0.35, 0),
                1, 0, 0, 0, 0, 0);
    }

    /** 达到阈值后的最终蓄力阶段：尖啸开始后继续维持灵魂粒子两秒。 */
    private void startThresholdSequence(Location location, double radius,
                                        int threshold, Runnable summon) {
        playShriekPulse(location, radius);
        new BukkitRunnable() {
            private int elapsed;

            @Override
            public void run() {
                if (elapsed >= 40) {
                    cancel();
                    if (summon != null) summon.run();
                    return;
                }
                World world = location.getWorld();
                if (world != null && world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    world.spawnParticle(Particle.SCULK_SOUL,
                            location.clone().add(0, 0.35, 0), particleCount(threshold, threshold),
                            0.35, 0.35, 0.35, 0.01);
                }
                elapsed++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private static int particleCount(int level, int threshold) {
        double ratio = Math.min(1.0, Math.max(0.0, level / (double) threshold));
        return Math.max(1, Math.min(6, (int) Math.ceil(1.0 + ratio * 4.0)));
    }

    /** 在原位置下方最多检查三格，若找到可站立表面则把怨念贴到表面上方。 */
    private static Location findGroundLocation(Location original) {
        World world = original.getWorld();
        if (world == null) return original.clone();
        int blockX = original.getBlockX();
        int blockZ = original.getBlockZ();
        int baseY = original.getBlockY();
        for (int offset = 0; offset <= 3; offset++) {
            int currentY = baseY - offset;
            Block space = world.getBlockAt(blockX, currentY, blockZ);
            Block support = world.getBlockAt(blockX, currentY - 1, blockZ);
            if (space.isPassable() && support.getType().isSolid()) {
                return new Location(world, original.getX(), support.getY() + 1.05,
                        original.getZ(), original.getYaw(), original.getPitch());
            }
        }
        return original.clone();
    }

    private static void broadcastSummonSound(Location location, double radius) {
        World world = location.getWorld();
        if (world == null) return;
        double radiusSquared = radius * radius;
        for (org.bukkit.entity.Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= radiusSquared) {
                player.playSound(location, "minecraft:entity.wither.spawn", 0.3f, 2.0f);
            }
        }
        world.spawnParticle(Particle.ENTITY_EFFECT, location.clone().add(0, 0.6, 0),
                40, 0.35, 0.5, 0.35, 0.0, Color.BLACK);
    }

    private int countEffectiveAffixes(List<String> affixes) {
        int count = 0;
        for (String id : affixes) {
            if (id == null) continue;
            SkillConfig skillConfig = config.getSkillConfig(id);
            if (SkillRegistry.has(id) && skillConfig != null
                    && skillConfig.isHolderAllowed(EntityType.VEX)) count++;
        }
        return count;
    }

    private static Team getRevenantTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(REVENANT_TEAM_NAME);
        if (team == null) team = scoreboard.registerNewTeam(REVENANT_TEAM_NAME);
        if (!team.hasColor() || !NamedTextColor.BLACK.equals(team.color())) {
            team.color(NamedTextColor.BLACK);
        }
        return team;
    }

    private static void addFollowRange(Vex vex) {
        var attribute = vex.getAttribute(Attribute.FOLLOW_RANGE);
        if (attribute == null) return;
        attribute.addModifier(new AttributeModifier(
                new NamespacedKey("mczju", "revenant_follow_range"),
                64.0, AttributeModifier.Operation.ADD_NUMBER));
    }

    private static boolean sameWorld(Location a, Location b) {
        return a.getWorld() != null && a.getWorld().equals(b.getWorld());
    }

    private record RevenantNode(Location location, int level, long expireTick) {}
}
