package io.github.mcaicontroller;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalBlock;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mod(ControllerMod.MOD_ID)
public final class ControllerMod {
    public static final String MOD_ID = "mc_ai_controller";

    public ControllerMod() {
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientRuntime::initialize);
    }

    private static final class ClientRuntime {
        private static final Pattern FOLLOW = Pattern.compile("(?:跟随|跟着|追踪)\\s*(?:玩家)?\\s*([A-Za-z0-9_]{1,16})", Pattern.CASE_INSENSITIVE);
        private static final Pattern COORDINATES = Pattern.compile("(?:前往|去|移动到|走到)?\\s*(?:坐标)?\\s*[（(]?\\s*(-?\\d+)\\s*[,， ]+\\s*(-?\\d+)\\s*[,， ]+\\s*(-?\\d+)\\s*[）)]?");
        private static final Pattern STOP = Pattern.compile(".*(?:停止|取消|停下来|别动|不要跟|stop|cancel).*", Pattern.CASE_INSENSITIVE);
        private static final Pattern UNLOCK = Pattern.compile(".*(?:解除急停|恢复控制|unlock|resume).*", Pattern.CASE_INSENSITIVE);

        private static KeyMapping emergencyStop;
        private static volatile boolean emergencyLatched;
        private static LocalCommandServer server;

        private static void initialize() {
            IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
            modBus.addListener(ClientRuntime::registerKeys);
            MinecraftForge.EVENT_BUS.register(ClientRuntime.class);
            server = new LocalCommandServer(ClientRuntime::executeOnClient);
            server.start();
        }

        private static void registerKeys(RegisterKeyMappingsEvent event) {
            emergencyStop = new KeyMapping(
                "key.mc_ai_controller.emergency_stop",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F8,
                "key.categories.mc_ai_controller"
            );
            event.register(emergencyStop);
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            if (emergencyStop != null && emergencyStop.consumeClick()) {
                emergencyLatched = true;
                stopEverything();
                showMessage("§c[MC AI] 紧急停止已触发。输入“解除急停”后才能接受新任务。");
            }

            if (emergencyLatched) {
                releaseAllControlledInputs();
                try {
                    primaryBaritone().getInputOverrideHandler().clearAllKeys();
                } catch (RuntimeException ignored) {
                }
            }
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            stopEverything();
        }

        private static String dispatch(String rawCommand) {
            String command = rawCommand == null ? "" : rawCommand.trim();
            if (command.isEmpty()) return "错误：请输入任务。";

            if (UNLOCK.matcher(command).matches()) {
                emergencyLatched = false;
                stopEverything();
                showMessage("§a[MC AI] 急停已解除，当前仍处于停止状态。");
                return "急停已解除；当前没有运行中的任务。";
            }

            if (STOP.matcher(command).matches()) {
                stopEverything();
                showMessage("§e[MC AI] 任务已停止。§r");
                return emergencyLatched ? "任务已停止；急停仍处于锁定状态。" : "任务已停止，控制按键已释放。";
            }

            if (emergencyLatched) return "拒绝：急停处于锁定状态。请先输入“解除急停”。";

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.level == null) {
                return "错误：请先进入单人世界或服务器。";
            }

            Matcher follow = FOLLOW.matcher(command);
            if (follow.find()) return followPlayer(follow.group(1));

            Matcher coordinates = COORDINATES.matcher(command);
            if (coordinates.find()) {
                try {
                    int x = Integer.parseInt(coordinates.group(1));
                    int y = Integer.parseInt(coordinates.group(2));
                    int z = Integer.parseInt(coordinates.group(3));
                    return goTo(x, y, z);
                } catch (NumberFormatException ex) {
                    return "错误：坐标超出可用范围。";
                }
            }

            return "无法识别。第一版支持：前往坐标 X Y Z、跟随玩家 玩家名、停止任务、解除急停。";
        }

        private static String goTo(int x, int y, int z) {
            stopEverything();
            primaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalBlock(x, y, z));
            showMessage("§a[MC AI] 正在前往 " + x + " " + y + " " + z + "§r");
            return "已开始前往坐标 " + x + " " + y + " " + z + "。";
        }

        private static String followPlayer(String playerName) {
            Minecraft minecraft = Minecraft.getInstance();
            AbstractClientPlayer target = minecraft.level.players().stream()
                .filter(player -> player.getGameProfile().getName().equalsIgnoreCase(playerName))
                .findFirst()
                .orElse(null);
            if (target == null) return "错误：当前加载范围内找不到玩家 “" + playerName + "”。";

            stopEverything();
            String normalized = target.getGameProfile().getName().toLowerCase(Locale.ROOT);
            primaryBaritone().getFollowProcess().follow((Entity entity) ->
                entity instanceof AbstractClientPlayer player
                    && player.getGameProfile().getName().toLowerCase(Locale.ROOT).equals(normalized)
            );
            showMessage("§a[MC AI] 正在跟随 " + target.getGameProfile().getName() + "§r");
            return "已开始跟随玩家 " + target.getGameProfile().getName() + "。";
        }

        private static IBaritone primaryBaritone() {
            return BaritoneAPI.getProvider().getPrimaryBaritone();
        }

        private static void stopEverything() {
            try {
                IBaritone baritone = primaryBaritone();
                baritone.getCustomGoalProcess().onLostControl();
                baritone.getFollowProcess().onLostControl();
                baritone.getPathingBehavior().cancelEverything();
                baritone.getInputOverrideHandler().clearAllKeys();
            } catch (RuntimeException ignored) {
            }
            releaseAllControlledInputs();
        }

        private static void releaseAllControlledInputs() {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.options == null) return;
            minecraft.options.keyUp.setDown(false);
            minecraft.options.keyDown.setDown(false);
            minecraft.options.keyLeft.setDown(false);
            minecraft.options.keyRight.setDown(false);
            minecraft.options.keyJump.setDown(false);
            minecraft.options.keyShift.setDown(false);
            minecraft.options.keySprint.setDown(false);
            minecraft.options.keyAttack.setDown(false);
            minecraft.options.keyUse.setDown(false);
            minecraft.options.keyPickItem.setDown(false);
        }

        private static void showMessage(String text) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) minecraft.player.displayClientMessage(Component.literal(text), false);
        }

        private static CompletableFuture<String> executeOnClient(String command) {
            CompletableFuture<String> result = new CompletableFuture<>();
            Minecraft.getInstance().execute(() -> {
                try {
                    result.complete(dispatch(command));
                } catch (Throwable error) {
                    result.complete("执行失败：" + error.getClass().getSimpleName() + "：" + String.valueOf(error.getMessage()));
                }
            });
            return result.orTimeout(5, TimeUnit.SECONDS);
        }
    }
}
