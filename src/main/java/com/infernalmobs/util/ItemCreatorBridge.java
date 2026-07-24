package com.infernalmobs.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Optional;

/** Optional bridge for MCZJUItemCreator without making it a runtime hard dependency. */
public final class ItemCreatorBridge {

    private static final String API_CLASS = "io.mczju.mczjuitemcreator.api.ItemCreatorApi";

    private ItemCreatorBridge() {}

    public static Optional<ItemStack> createItem(JavaPlugin plugin, String id, int amount) {
        Object api = findProvider(plugin);
        if (api == null || id == null || id.isBlank() || amount <= 0) return Optional.empty();
        try {
            Method createItem = api.getClass().getMethod("createItem", String.class, int.class);
            Object result = createItem.invoke(api, id, amount);
            if (result instanceof Optional<?> optional && optional.isPresent()
                    && optional.get() instanceof ItemStack item) {
                return Optional.of(item);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // ItemCreator is optional; an unavailable or incompatible provider disables this item path.
        }
        return Optional.empty();
    }

    public static boolean isAvailable(JavaPlugin plugin) {
        return findProvider(plugin) != null;
    }

    private static Object findProvider(JavaPlugin plugin) {
        if (plugin == null) return null;
        try {
            org.bukkit.plugin.Plugin itemCreator = plugin.getServer().getPluginManager()
                .getPlugin("MCZJUItemCreator");
            ClassLoader providerLoader = itemCreator != null
                ? itemCreator.getClass().getClassLoader()
                : plugin.getClass().getClassLoader();
            Class<?> apiClass = Class.forName(API_CLASS, false, providerLoader);
            RegisteredServiceProvider<?> registration = plugin.getServer().getServicesManager()
                    .getRegistration(apiClass);
            return registration != null ? registration.getProvider() : null;
        } catch (ClassNotFoundException | RuntimeException ignored) {
            return null;
        }
    }
}
