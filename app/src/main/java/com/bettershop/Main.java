package com.bettershop.Main;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Level;

public class Main extends JavaPlugin {
    
    // 基准参数
    private static final double T_0 = 0.01;         // 基准手续费 1%
    private static final double T_MIN = 0.001;      // 最低 0.1%
    private static final double R_D0 = 0.02;        // 基准存款利率 2%
    private static final double R_L0 = 0.05;        // 基准贷款利率 5%
    private static final double ALPHA = 0.20;       // 目标流通占比 20%

    // 调节系数（流通偏差的影响）
    private static final double COEF_T = 0.5;     // 流通偏差→手续费 (偏差1%→调0.5%)
    private static final double COEF_RD_C = 0.3;    // 流通偏差→存款利率
    private static final double COEF_RL_C = 0.4;    // 流通偏差→贷款利率

    // 调节系数（总量偏差的影响）
    private static final double COEF_RD_B = 0.15;   // 总量偏差→存款利率
    private static final double COEF_RL_B = 0.25;   // 总量偏差→贷款利率

    private Economy economy;
    
    // 计算结果缓存
    private volatile PolicyResult cachedResult;
    private volatile boolean isCalculating = false;
    
    @Override
    public void onEnable() {
        getLogger().info("更好的商店插件已启用！");

        // 初始化 Vault 经济系统
        if (!setupEconomy()) {
            getLogger().severe("未找到经济系统插件！插件将被禁用。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        getLogger().info("已初始化经济系统: " + economy.getName());
        
        // 启动货币政策计算的后台线程
        startMonetaryPolicyCalculator();
        
        // 示例：每30秒自动更新一次计算结果
        startAutoUpdateTask();
    }
    
    @Override
    public void onDisable() {
        getLogger().info("更好的商店插件已禁用！");
    }

    /**
     * 设置 Vault 经济系统
     * @return 是否成功挂钩经济系统
     */
    private boolean setupEconomy() {
        // 检查 Vault 插件是否存在
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault 插件未找到！");
            return false;
        }
        
        // 获取经济服务提供者
        RegisteredServiceProvider<Economy> rsp = 
            getServer().getServicesManager().getRegistration(Economy.class);
        
        if (rsp == null) {
            getLogger().severe("未找到任何经济系统实现！");
            return false;
        }
        
        economy = rsp.getProvider();
        return economy != null;
    }

    /**
     * 获取经济系统实例
     * @return Economy 对象，如果未初始化则返回 null
     */
    public Economy getEconomy() {
        return economy;
    }
    
    /**
     * 启动货币政策计算的后台线程
     */
    private void startMonetaryPolicyCalculator() {
        new Thread(() -> {
            getLogger().info("货币政策计算器后台线程已启动");
            
            while (isEnabled()) {
                try {
                    // 示例：获取当前的货币数据
                    // 在实际使用中，这些数据应该从经济系统插件或数据库获取
                    double a = getBenchmarkMoney();      // 货币基准常量
                    double b = getCurrentMoneySupply();  // 当前货币总量
                    double c = getCirculatingMoney();    // 流通货币量
                    
                    // 计算货币政策参数
                    PolicyResult result = calculate(a, b, c);
                    cachedResult = result;
                    
                    // 输出计算结果
                    getLogger().info(result.toString());
                    
                    // 应用货币政策到游戏
                    applyMonetaryPolicy(result);
                    
                    // 每60秒计算一次
                    Thread.sleep(60000);
                    
                } catch (InterruptedException e) {
                    getLogger().warning("货币政策计算器线程被中断");
                    break;
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "货币政策计算出错", e);
                    try {
                        Thread.sleep(10000); // 出错后等待10秒再试
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
            
            getLogger().info("货币政策计算器后台线程已停止");
        }, "MonetaryPolicyCalculator").start();
    }
    
    /**
     * 启动自动更新任务（使用Bukkit调度器）
     */
    private void startAutoUpdateTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isCalculating && cachedResult != null) {
                    getLogger().info("当前货币政策状态:");
                    getLogger().info(String.format(
                        "手续费率: %.2f%% | 存款利率: %.2f%% | 贷款利率: %.2f%%",
                        cachedResult.transactionFeeRate * 100,
                        cachedResult.depositRate * 100,
                        cachedResult.loanRate * 100
                    ));
                }
            }
        }.runTaskTimer(this, 1200L, 1200L); // 每60秒输出一次状态
    }
    
    /**
     * 获取货币基准常量
     * 实际使用时应该从配置或经济系统获取
     */
    private double getBenchmarkMoney() {
        // 示例实现，实际应从配置读取
        return getConfig().getDouble("economy.benchmark", 1000.0);
    }
    
    /**
     * 获取当前货币总量（使用 Vault）
     * 计算服务器中所有玩家的总货币量
     */
    private double getCurrentMoneySupply() {
        if (economy == null) {
            getLogger().warning("经济系统未就绪，使用默认值");
            return 1000.0;
        }
        
        double totalMoney = 0.0;
        // 遍历所有在线玩家，累加余额
        // 注意：如果需要统计所有玩家（包括离线），需要使用其他方法
        for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
            totalMoney += economy.getBalance(player);
        }
        
        // 如果在线玩家太少，可以使用配置文件中保存的历史总量
        if (totalMoney < 0.01 && getConfig().contains("economy.lastTotalMoney")) {
            totalMoney = getConfig().getDouble("economy.lastTotalMoney");
        }
        
        // 保存当前总量到配置
        getConfig().set("economy.lastTotalMoney", totalMoney);
        saveConfig();
        
        return totalMoney;
    }
    
    /**
     * 获取流通货币量
     * 实际使用时可以通过玩家持有货币、商店交易额等计算
     */
    private double getCirculatingMoney() {
        // 示例实现，实际应计算玩家活跃持有的货币
        return 200.0;
    }
    
    /**
     * 应用货币政策到游戏
     * @param result 货币政策计算结果
     */
    private void applyMonetaryPolicy(PolicyResult result) {
        // 保存到配置文件
        getConfig().set("monetary.transactionFee", result.transactionFeeRate);
        getConfig().set("monetary.depositRate", result.depositRate);
        getConfig().set("monetary.loanRate", result.loanRate);
        saveConfig();
        
        // 触发事件，让其他插件知道货币政策已更新
        getServer().getPluginManager().callEvent(new MonetaryPolicyUpdateEvent(result));
        
        getLogger().fine("货币政策已应用并保存到配置");
    }
    
    /**
     * 计算结果类
     */
    public static class PolicyResult {
        public double transactionFeeRate;  // 交易手续费率
        public double depositRate;         // 存款利率
        public double loanRate;            // 贷款利率
        public double cTarget;             // 目标流通量
        public double sTarget;             // 目标储蓄量
        public double currentSaving;       // 当前储蓄量
        public long timestamp;              // 计算时间戳
        
        public PolicyResult() {
            this.timestamp = System.currentTimeMillis();
        }
        
        @Override
        public String toString() {
            return String.format(
                "======= 货币政策计算结果 =======\n" +
                "目标流通量: %.2f\n" +
                "目标储蓄量: %.2f\n" +
                "当前储蓄量: %.2f\n" +
                "交易手续费率: %.4f (%.2f%%)\n" +
                "存款利率: %.4f (%.2f%%)\n" +
                "贷款利率: %.4f (%.2f%%)\n" +
                "计算时间: %tc\n" +
                "=================================",
                cTarget, sTarget, currentSaving,
                transactionFeeRate, transactionFeeRate * 100,
                depositRate, depositRate * 100,
                loanRate, loanRate * 100,
                timestamp
            );
        }
    }
    
    /**
     * 计算货币政策参数
     * @param a 货币基准常量
     * @param b 当前货币总量
     * @param c 流通货币量
     * @return 包含费率/利率的结果对象
     */
    public PolicyResult calculate(double a, double b, double c) {
        // 防止并发计算
        if (isCalculating) {
            getLogger().warning("已有计算在进行中，跳过本次计算");
            return cachedResult != null ? cachedResult : new PolicyResult();
        }
        
        isCalculating = true;
        
        try {
            PolicyResult result = new PolicyResult();
            
            // 参数校验
            if (a <= 0 || b <= 0 || c < 0 || c > b) {
                throw new IllegalArgumentException(
                    String.format("参数无效: a=%.2f>0, b=%.2f>0, 0<=c<=b, c=%.2f", a, b, c)
                );
            }
            
            // 计算派生变量
            double currentSaving = b - c;           // 当前储蓄量
            double cTarget = ALPHA * a;              // 目标流通量
            double sTarget = a - cTarget;            // 目标储蓄量
            
            // 计算偏差(归一化到a)
            double deltaC = (c - cTarget) / cTarget;      // 流通偏差比例
            double deltaS = (currentSaving - sTarget) / sTarget; // 储蓄偏差比例 //内心OS：分母或许应该填a？
            double deltaB = (b - a) / a;                   // 总量偏差比例
            
            // 1. 交易手续费率
            double tf = T_0 + COEF_T * deltaC;
            result.transactionFeeRate = Math.max(T_MIN, tf);
            
            // 2. 存款利率
            double rd = R_D0 - COEF_RD_C * deltaS - COEF_RD_B * deltaB;
            result.depositRate = Math.max(0, rd);
            
            // 3. 贷款利率
            double rl = R_L0 + COEF_RL_C * (cTarget - c) / cTarget + COEF_RL_B * deltaB;
            result.loanRate = Math.max(0, rl);
            
            // 保存辅助信息
            result.cTarget = cTarget;
            result.sTarget = sTarget;
            result.currentSaving = currentSaving;
            
            return result;
            
        } finally {
            isCalculating = false;
        }
    }
    
    /**
     * 获取最新的货币政策结果
     * @return 最新的PolicyResult对象，如果尚未计算则返回null
     */
    public PolicyResult getLatestPolicy() {
        return cachedResult;
    }
    
    /**
     * 手动触发货币政策计算（异步）
     * @param a 货币基准常量
     * @param b 当前货币总量
     * @param c 流通货币量
     */
    public void calculateAsync(double a, double b, double c) {
        new Thread(() -> {
            PolicyResult result = calculate(a, b, c);
            cachedResult = result;
            getLogger().info("手动计算完成:\n" + result);
            
            // 在主线程触发事件
            new BukkitRunnable() {
                @Override
                public void run() {
                    getServer().getPluginManager().callEvent(new MonetaryPolicyUpdateEvent(result));
                }
            }.runTask(this);
            
        }, "ManualCalculation").start();
    }
}

/**
 * 货币政策更新事件
 */
class MonetaryPolicyUpdateEvent extends org.bukkit.event.Event {
    private static final org.bukkit.event.HandlerList HANDLERS = new org.bukkit.event.HandlerList();
    private final Main.PolicyResult policyResult;
    
    public MonetaryPolicyUpdateEvent(Main.PolicyResult policyResult) {
        this.policyResult = policyResult;
    }
    
    public Main.PolicyResult getPolicyResult() {
        return policyResult;
    }
    
    @Override
    public org.bukkit.event.HandlerList getHandlers() {
        return HANDLERS;
    }
    
    public static org.bukkit.event.HandlerList getHandlerList() {
        return HANDLERS;
    }
}