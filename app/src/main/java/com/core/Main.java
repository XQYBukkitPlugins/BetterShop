package com.example.plugin;

import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("插件已启用！");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("插件已禁用！");
    }
}