package com.infernalmobs.service;

import com.infernalmobs.config.ConfigLoader;
import com.infernalmobs.config.SkillConfig;
import com.infernalmobs.factory.MobFactory;
import com.infernalmobs.registry.SkillRegistry;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Vex;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** 管理运行期怨念：融合、粒子显示、过期清理和炒鸡恼鬼生成。 */
public final class RevenantManager {

    private static final String REVENANT_TEAM_NAME = "revenant_team";

    private final ConfigLoader config;
    private final List<RevenantNode> nodes = new ArrayList<>();
    private MobFactory mobFactory;

    public RevenantManager(ConfigLoader config) {
        this.config = config;
    }

    public void setMobFactory(MobFactory mobFactory) {
        this.mobFactory = mobFactory;
    }

    /** 在死亡位置加入怨念，并吸收融合半径内的全部旧怨念。 */
    public void add(Location location, int level, long currentTick) {
        if (location == null || location.getWorld() == null || level <= 0) return;
        SkillConfig cfg = config.getSkillConfig("revenant");
        if (cfg == null) return;

        double radius = Math.max(0.0, cfg.getDouble("merge-radius", 8.0));
        int total = level;
        for (Iterator<RevenantNode> it = nodes.iterator(); it.hasNext();) {
            RevenantNode node = it.next();
            if (sameWorld(location, node.location())
                    && location.distanceSquared(node.location()) <= radius * radius) {
                total += node.level();
                it.remove();
            }
        }

        int threshold = Math.max(1, cfg.getInt("summon-threshold", 20));
        if (total >= threshold) {
            summon(location, cfg);
            return;
        }

        long duration = Math.max(1L, cfg.getInt("duration-ticks", 600));
        nodes.add(new RevenantNode(location.clone(), total, currentTick + duration));
    }

    /** 由战斗主线程周期调用；区块未加载时只保留数据，不播放粒子。 */
    public void tick(long currentTick) {
        SkillConfig cfg = config.getSkillConfig("revenant");
        if (cfg == null) return;
        Particle particle = Particle.SOUL;
        int count = Math.max(1, cfg.getInt("particle-count", 4));
        for (Iterator<RevenantNode> it = nodes.iterator(); it.hasNext();) {
            RevenantNode node = it.next();
            if (currentTick >= node.expireTick()) {
                it.remove();
                continue;
            }
            Location loc = node.location();
            World world = loc.getWorld();
            if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;
            double size = Math.min(1.2, 0.35 + node.level() * 0.03);
            world.spawnParticle(particle, loc.clone().add(0, 0.35, 0), count,
                    size, 0.25, size, 0.01);
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
        for (int i = 0; i < summonCount; i++) {
            Vex vex = (Vex) world.spawnEntity(location, EntityType.VEX);
            vex.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                    PotionEffect.INFINITE_DURATION, 0, false, false, false));
            team.addEntity(vex);
            addFollowRange(vex);
            mobFactory.mechanizeWithAffixes(vex, location, effectiveAffixCount, affixes);
        }
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
