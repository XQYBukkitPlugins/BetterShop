package com.bettershop.Command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class BettershopCommand implements CommandExecutor {
    
    // 存储所有子命令的处理器 - 键是子命令名称，值是处理逻辑
    private final Map<String, SubCommandHandler> subCommands = new HashMap<>();
    
    // 处理器接口
    @FunctionalInterface
    public interface SubCommandHandler {
        boolean execute(CommandSender sender, String[] args);
    }
    
    public BettershopCommand() {
        // 在构造函数中注册所有子命令
        registerSubCommands();
    }
    
    private void registerSubCommands() {
        // 注册各个子命令
        register("help", this::handleHelp);
        register("reload", this::handleReload);
    }
    
    // 注册子命令的便捷方法
    public void register(String subCommand, SubCommandHandler handler) {
        subCommands.put(subCommand.toLowerCase(), handler);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 检查是否有子命令参数
        if (args.length == 0) {
            sendCommandHelp(sender);
            return true;
        }
        
        // 获取子命令名称
        String subCmd = args[0].toLowerCase();
        
        // 查找对应的处理器
        SubCommandHandler handler = subCommands.get(subCmd);
        
        if (handler == null) {
            sender.sendMessage(ChatColor.RED + "未知的子命令: " + subCmd);
            sender.sendMessage(ChatColor.YELLOW + "使用 /" + label + " help 查看可用命令");
            return true;
        }
        
        // 执行处理器，传入去掉第一个参数后的剩余参数
        return handler.execute(sender, args);
    }
    
    // ========== 各个子命令的具体实现 ==========
    
    private boolean handleHelp(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.GOLD + "=== 可用命令列表 ===");
        sender.sendMessage(ChatColor.GREEN + "/bs help " + ChatColor.WHITE + "- 显示此帮助信息");
        if (sender.hasPermission("bettershop.reload")) sender.sendMessage(ChatColor.GREEN + "/bs reload " + ChatColor.WHITE + "- 重载插件配置");
        return true;
    }
    
    private boolean handleReload(CommandSender sender, String[] args) {
        // 检查权限
        if (!sender.hasPermission("bettershop.reload")) {
            sender.sendMessage(ChatColor.RED + "你没有权限执行此命令！");
            return true;
        }
        
        // 这里添加重载配置的逻辑
        sender.sendMessage(ChatColor.GREEN + "插件配置已重载！");
        return true;
    }
    
    private void sendCommandHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "请指定一个子命令！");
        sender.sendMessage(ChatColor.GREEN + "使用 /bettershop help 查看所有可用命令");
        
        // 给有权限的用户显示快速提示
        if (sender.hasPermission("bettershop.reload")) {
            sender.sendMessage(ChatColor.GRAY + "提示: /bettershop reload - 重载配置");
        }
    }
}