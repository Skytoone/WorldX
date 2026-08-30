package fr.skynex.worldx.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyIntegration {
    private static Object economy = null;
    private static boolean initialized = false;

    public static boolean setupEconomy() {
        if (initialized) return economy != null;
        initialized = true;
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        try {
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(Class.forName("net.milkbowl.vault.economy.Economy"));
            if (rsp != null) {
                economy = rsp.getProvider();
            }
        } catch (Exception ignored) {}
        return economy != null;
    }

    public static double getBalance(org.bukkit.OfflinePlayer player) {
        if (!setupEconomy()) return 0.0;
        try {
            java.lang.reflect.Method method = economy.getClass().getMethod("getBalance", org.bukkit.OfflinePlayer.class);
            return (double) method.invoke(economy, player);
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static boolean withdrawPlayer(org.bukkit.OfflinePlayer player, double amount) {
        if (!setupEconomy()) return false;
        try {
            java.lang.reflect.Method method = economy.getClass().getMethod("withdrawPlayer", org.bukkit.OfflinePlayer.class, double.class);
            Object response = method.invoke(economy, player, amount);
            java.lang.reflect.Method transactionSuccess = response.getClass().getMethod("transactionSuccess");
            return (boolean) transactionSuccess.invoke(response);
        } catch (Exception e) {
            return false;
        }
    }

    public static void depositPlayer(org.bukkit.OfflinePlayer player, double amount) {
        if (!setupEconomy()) return;
        try {
            java.lang.reflect.Method method = economy.getClass().getMethod("depositPlayer", org.bukkit.OfflinePlayer.class, double.class);
            method.invoke(economy, player, amount);
        } catch (Exception ignored) {}
    }
}
