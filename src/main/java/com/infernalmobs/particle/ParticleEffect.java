package com.infernalmobs.particle;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;

import java.util.List;

/**
 * 简单粒子效果：由形状（ParticleSource）+ 粒子类型 + 密度 构成，支持一次播放。
 */
public final class ParticleEffect {

    private final ParticleSource source;
    private final Particle particle;
    private final int density;
    private final double offsetX, offsetY, offsetZ;
    private final double extra;
    private final int count;

    private ParticleEffect(Builder b) {
        this.source = b.source;
        this.particle = b.particle;
        this.density = b.density;
        this.offsetX = b.offsetX;
        this.offsetY = b.offsetY;
        this.offsetZ = b.offsetZ;
        this.extra = b.extra;
        this.count = b.count;
    }

    public static Builder create() {
        return new Builder();
    }

    /**
     * 在给定中心点播放（线形会忽略 base，使用线自带的起点/终点世界）。
     * base 可为 null，部分 source（如 line、concentricRings）内部自备中心。
     */
    public void play(Location base) {
        List<Location> points = source.getPoints(base, density);
        for (Location loc : points) {
            if (loc == null || loc.getWorld() == null) continue;
            // Use no-data overload: passing Boolean here would be interpreted as particle data and crash for Void particles.
            loc.getWorld().spawnParticle(particle, loc, count, offsetX, offsetY, offsetZ, extra);
        }
    }

    /**
     * 在实体脚底位置播放。
     */
    public void playOn(Entity entity) {
        if (entity == null || !entity.isValid()) return;
        play(entity.getLocation());
    }

    public static final class Builder {
        private ParticleSource source;
        private Particle particle = Particle.FLAME;
        private int density = 16;
        private double offsetX = 0.05, offsetY = 0.05, offsetZ = 0.05;
        private double extra = 0.02;
        private int count = 1;

        public Builder source(ParticleSource source) {
            this.source = source;
            return this;
        }

        public Builder particle(Particle particle) {
            this.particle = particle;
            return this;
        }

        public Builder density(int density) {
            this.density = Math.max(1, density);
            return this;
        }

        public Builder offset(double x, double y, double z) {
            this.offsetX = x;
            this.offsetY = y;
            this.offsetZ = z;
            return this;
        }

        public Builder extra(double extra) {
            this.extra = extra;
            return this;
        }

        public Builder count(int count) {
            this.count = Math.max(1, count);
            return this;
        }

        public ParticleEffect build() {
            if (source == null) throw new IllegalStateException("ParticleSource is required");
            return new ParticleEffect(this);
        }

        public void play(Location base) {
            build().play(base);
        }

        public void playOn(Entity entity) {
            build().playOn(entity);
        }
    }
}
