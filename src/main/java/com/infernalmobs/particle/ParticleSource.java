package com.infernalmobs.particle;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 粒子数据源：提供粒子生成位置。
 * 支持两点确定的线、中心+边长的方形/三角形、中心+半径的圆，以及倾斜角参数。
 */
public interface ParticleSource {

    /**
     * 在给定基准点与密度下生成一批位置。
     * 线形会忽略 base，使用自身起点/终点所在世界；其余形状以 base 为中心。
     *
     * @param base   中心点（线形时可为 null，取线起点世界）
     * @param density 采样数量（点数）
     * @return 世界坐标位置列表，可直接用于 spawnParticle
     */
    List<Location> getPoints(Location base, int density);

    // ============ 预设实现 ============

    /**
     * 单点。
     */
    static ParticleSource point(Location pos) {
        return (base, density) -> {
            Location use = base != null ? base : pos;
            List<Location> out = new ArrayList<>(1);
            out.add(use.clone());
            return out;
        };
    }

    /**
     * 两点确定的线段。
     *
     * @param start 起点（世界坐标，含 World）
     * @param end   终点（世界坐标）
     */
    static ParticleSource line(Location start, Location end) {
        return (base, density) -> {
            int n = Math.max(1, density);
            List<Location> out = new ArrayList<>(n);
            World world = start.getWorld();
            if (world == null) return out;
            double dx = (end.getX() - start.getX()) / (n - 1);
            double dy = (end.getY() - start.getY()) / (n - 1);
            double dz = (end.getZ() - start.getZ()) / (n - 1);
            for (int i = 0; i < n; i++) {
                out.add(new Location(world,
                        start.getX() + i * dx,
                        start.getY() + i * dy,
                        start.getZ() + i * dz));
            }
            return out;
        };
    }

    /**
     * 圆形：中心 + 半径 + 法线（圆所在平面垂直于 normal）+ 起始角（倾斜/旋转）。
     *
     * @param center      中心（世界坐标，base 传入时通常用 base）
     * @param radius      半径
     * @param normal      圆平面法线，如 (0,1,0) 为水平圆
     * @param startAngleRad 起始弧度，0 为沿 local X 正方向起算
     */
    static ParticleSource circle(Location center, double radius, Vector normal, double startAngleRad) {
        return (base, density) -> {
            Location c = base != null ? base : center;
            if (c.getWorld() == null) return new ArrayList<>();
            Vector n = normal.clone().normalize();
            Vector u = getPerpendicularInPlane(n);
            Vector v = n.getCrossProduct(u).normalize();
            int nPts = Math.max(3, density);
            List<Location> out = new ArrayList<>(nPts);
            for (int i = 0; i < nPts; i++) {
                double angle = startAngleRad + 2 * Math.PI * i / nPts;
                double x = Math.cos(angle) * radius;
                double y = Math.sin(angle) * radius;
                Vector offset = u.clone().multiply(x).add(v.clone().multiply(y));
                out.add(c.clone().add(offset));
            }
            return out;
        };
    }

    /**
     * 水平圆（法线为 Y 轴），可设起始角。
     */
    static ParticleSource circle(Location center, double radius, double startAngleRad) {
        return circle(center, radius, new Vector(0, 1, 0), startAngleRad);
    }

    /**
     * 方形：中心 + 边长 + 绕竖直轴旋转角（yawRad）+ 相对水平面的倾斜角（pitchRad）。
     * 默认在 XZ 平面，边长沿 X/Z；yaw 绕 Y 轴旋转；pitch 绕局部 X 轴倾斜。
     */
    static ParticleSource square(Location center, double sideLength, double yawRad, double pitchRad) {
        return (base, density) -> {
            Location c = base != null ? base : center;
            if (c.getWorld() == null) return new ArrayList<>();
            double half = sideLength / 2;
            // 四边端点（XZ 平面，中心在原点）
            double[][] xs = {{-half, half}, {half, half}, {half, -half}, {-half, -half}};
            double[][] zs = {{-half, -half}, {-half, half}, {half, half}, {half, -half}};
            int total = Math.max(4, density);
            int perSide = Math.max(1, total / 4);
            List<Location> out = new ArrayList<>(total);
            for (int side = 0; side < 4; side++) {
                for (int i = 0; i < perSide; i++) {
                    double t = (double) i / perSide;
                    double x = xs[side][0] + t * (xs[side][1] - xs[side][0]);
                    double z = zs[side][0] + t * (zs[side][1] - zs[side][0]);
                    Vector v = rotateYawPitch(new Vector(x, 0, z), yawRad, pitchRad);
                    out.add(c.clone().add(v));
                }
            }
            return out;
        };
    }

    /**
     * 三角形（等边）：中心 + 边长 + 绕竖直轴旋转角（yawRad）+ 倾斜角（pitchRad）。
     */
    static ParticleSource triangle(Location center, double sideLength, double yawRad, double pitchRad) {
        return (base, density) -> {
            Location c = base != null ? base : center;
            if (c.getWorld() == null) return new ArrayList<>();
            double r = sideLength / Math.sqrt(3);
            List<double[]> pts = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                double angle = Math.PI / 2 + i * 2 * Math.PI / 3;
                pts.add(new double[]{r * Math.cos(angle), 0, r * Math.sin(angle)});
            }
            int total = Math.max(3, density);
            int perSide = Math.max(1, total / 3);
            List<Location> out = new ArrayList<>(total);
            for (int side = 0; side < 3; side++) {
                double[] a = pts.get(side);
                double[] b = pts.get((side + 1) % 3);
                for (int i = 0; i < perSide; i++) {
                    double t = (double) i / perSide;
                    Vector v = new Vector(
                            a[0] + t * (b[0] - a[0]),
                            a[1] + t * (b[1] - a[1]),
                            a[2] + t * (b[2] - a[2]));
                    v = rotateYawPitch(v, yawRad, pitchRad);
                    out.add(c.clone().add(v));
                }
            }
            return out;
        };
    }

    /**
     * 螺旋上升：从中心起绕 Y 轴旋转上升。
     *
     * @param center   中心（base 不为 null 时用 base）
     * @param radius   水平半径
     * @param height   总高度
     * @param rotations 旋转圈数
     */
    static ParticleSource spiral(Location center, double radius, double height, double rotations) {
        return (base, density) -> {
            Location c = base != null ? base : center;
            if (c == null || c.getWorld() == null) return new ArrayList<>();
            int n = Math.max(2, density);
            List<Location> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                double t = (double) i / (n - 1);
                double angle = t * rotations * 2 * Math.PI;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                double y = t * height;
                out.add(c.clone().add(x, y, z));
            }
            return out;
        };
    }

    /**
     * 自定义：按 (base, density) 生成位置列表。
     */
    static ParticleSource custom(BiFunction<Location, Integer, List<Location>> generator) {
        return (base, density) -> generator.apply(base, density);
    }

    // ----- 工具 -----

    static Vector getPerpendicularInPlane(Vector normal) {
        Vector n = normal.clone().normalize();
        if (Math.abs(n.getY()) < 0.9) return new Vector(0, 1, 0).subtract(n.clone().multiply(n.getY())).normalize();
        return new Vector(1, 0, 0).subtract(n.clone().multiply(n.getX())).normalize();
    }

    static Vector rotateYawPitch(Vector v, double yawRad, double pitchRad) {
        double x = v.getX(), y = v.getY(), z = v.getZ();
        double cosY = Math.cos(yawRad), sinY = Math.sin(yawRad);
        double nx = x * cosY - z * sinY;
        double nz = x * sinY + z * cosY;
        x = nx; z = nz;
        double cosP = Math.cos(pitchRad), sinP = Math.sin(pitchRad);
        double ny = y * cosP - z * sinP;
        nz = y * sinP + z * cosP;
        return new Vector(nx, ny, nz);
    }
}
