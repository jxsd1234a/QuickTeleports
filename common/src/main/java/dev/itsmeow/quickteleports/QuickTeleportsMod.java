package dev.itsmeow.quickteleports;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.architectury.injectables.annotations.ExpectPlatform;
import dev.itsmeow.quickteleports.util.HereTeleport;
import dev.itsmeow.quickteleports.util.Teleport;
import dev.itsmeow.quickteleports.util.ToTeleport;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Predicate;

public class QuickTeleportsMod {

    public static final String MOD_ID = "quickteleports";
    public static final String CONFIG_FIELD_NAME = "teleport_request_timeout";
    public static final String CONFIG_FIELD_COMMENT = "Timeout until a teleport request expires, in seconds.";
    public static final int CONFIG_FIELD_VALUE = 30;
    public static final int CONFIG_FIELD_MIN = 0;
    public static final int CONFIG_FIELD_MAX = Integer.MAX_VALUE;

    public static HashMap<Teleport, Integer> tps = new HashMap<>();

    public static class FTC extends TextComponent {

        public FTC(ChatFormatting color, String msg) {
            super(msg);
            this.setStyle(Style.EMPTY.withColor(color));
        }

    }

    public static void registerCommands(CommandDispatcher dispatcher) {
        Predicate<CommandSourceStack> isPlayer = source -> {
            try {
                return source.getPlayerOrException() != null;
            } catch(CommandSyntaxException e) {
                return false;
            }
        };
        // tpa
        dispatcher.register(Commands.literal("tpa").requires(isPlayer).then(Commands.argument("target", GameProfileArgument.gameProfile()).executes(command -> {
            ServerPlayer player = command.getSource().getPlayerOrException();
            MinecraftServer server = player.getServer();
            Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(command, "target");
            if(profiles.size() > 1) {
                sendMessage(command.getSource(), false, new FTC(ChatFormatting.RED, "指定一名玩家作为参数！"));
                return 0;
            }
            GameProfile profile = getFirstProfile(profiles);
            if(!isGameProfileOnline(server, profile)) {
                sendMessage(command.getSource(), false, new FTC(ChatFormatting.RED, "该玩家不在线！"));
                return 0;
            }
            if(profile.getId().equals(player.getGameProfile().getId())) {
                sendMessage(command.getSource(), false, new FTC(ChatFormatting.RED, "你不能传送给自己！"));
                return 0;
            }
            String sourceName = player.getName().getString();
            ServerPlayer targetPlayer = server.getPlayerList().getPlayer(profile.getId());
            Teleport remove = QuickTeleportsMod.getRequestTP(sourceName);
            if(remove != null) {
                QuickTeleportsMod.tps.remove(remove);
                QuickTeleportsMod.notifyCanceledTP(server, remove);
            }

            ToTeleport teleport = new ToTeleport(sourceName, targetPlayer.getName().getString());
            QuickTeleportsMod.tps.put(teleport, getTeleportTimeout() * 20);
            sendMessage(targetPlayer.createCommandSourceStack(), true, new FTC(ChatFormatting.GREEN, sourceName), new FTC(ChatFormatting.GOLD, " 已请求传送给您。 输入 "), new FTC(ChatFormatting.YELLOW, "/tpaccept"), new FTC(ChatFormatting.GOLD, " 同意该请求."));
            sendMessage(command.getSource(), true, new FTC(ChatFormatting.GOLD, "被要求传送到 "), new FTC(ChatFormatting.GREEN, targetPlayer.getName().getString()), new FTC(ChatFormatting.GOLD, "."));
            return 1;
        })));

        // tpaccept
        dispatcher.register(Commands.literal("tpaccept").requires(isPlayer).executes(command -> {
            ServerPlayer player = command.getSource().getPlayerOrException();
            MinecraftServer server = player.getServer();
            Teleport tp = QuickTeleportsMod.getSubjectTP(player.getName().getString());

            if(tp == null) {
                sendMessage(command.getSource(), false, new FTC(ChatFormatting.RED, "您没有待处理的传送请求！"));
                return 0;
            }

            QuickTeleportsMod.tps.remove(tp);
            ServerPlayer playerRequesting = server.getPlayerList().getPlayerByName(tp.getRequester());
            ServerPlayer playerMoving = server.getPlayerList().getPlayerByName(tp.getSubject());

            if(playerMoving == null) {
                sendMessage(command.getSource(), false, new FTC(ChatFormatting.RED, "传送的玩家已经不存在了！"));
                return 0;
            }

            if(tp instanceof ToTeleport) {
                ServerPlayer holder = playerMoving;
                playerMoving = playerRequesting;
                playerRequesting = holder;
            }

            sendMessage(playerRequesting.createCommandSourceStack(), true, new FTC(ChatFormatting.GREEN, "接受传送请求."));
            sendMessage(playerMoving.createCommandSourceStack(), true, new FTC(ChatFormatting.GREEN, (tp instanceof ToTeleport ? "您的传送请求已被接受." : "你现在正在被传送.")));

            double posX = playerRequesting.getX();
            double posY = playerRequesting.getY();
            double posZ = playerRequesting.getZ();
            playerMoving.teleportTo(playerRequesting.getLevel(), posX, posY, posZ, playerRequesting.getYRot(), 0F);
            return 1;
        }));

        // tpahere
        dispatcher.register(Commands.literal("tpahere").requires(isPlayer).then(Commands.argument("target", GameProfileArgument.gameProfile()).executes(command -> {
            ServerPlayer player = command.getSource().getPlayerOrException();
            MinecraftServer server = player.getServer();
            Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(command, "target");
            if(profiles.size() > 1) {
                sendMessage(command.getSource(), false, new FTC(ChatFormatting.RED, "指定一个玩家作为参数!"));
                return 0;
            }
            GameProfile profile = getFirstProfile(profiles);
            if(!isGameProfileOnline(server, profile)) {
                sendMessage(command.getSource(), false, new FTC(ChatFormatting.RED, "该玩家不在线!"));
                return 0;
            }
            if(profile.getId().equals(player.getGameProfile().getId())) {
                sendMessage(command.getSource(), false, new FTC(ChatFormatting.RED, "你不能向自己发送传送请求!"));
                return 0;
            }
            String sourceName = player.getName().getString();
            Teleport remove = QuickTeleportsMod.getRequestTP(sourceName);
            if(remove != null) {
                QuickTeleportsMod.tps.remove(remove);
                QuickTeleportsMod.notifyCanceledTP(server, remove);
            }
            ServerPlayer targetPlayer = server.getPlayerList().getPlayer(profile.getId());

            HereTeleport tp = new HereTeleport(sourceName, targetPlayer.getName().getString());
            QuickTeleportsMod.tps.put(tp, getTeleportTimeout() * 20);
            sendMessage(targetPlayer.createCommandSourceStack(), true, new FTC(ChatFormatting.GREEN, sourceName), new FTC(ChatFormatting.GOLD, " 已经要求你传送到他们那里。 输入 "), new FTC(ChatFormatting.YELLOW, "/tpaccept"), new FTC(ChatFormatting.GOLD, " 同意该请求."));
            sendMessage(command.getSource(), true, new FTC(ChatFormatting.GOLD, "Requested "), new FTC(ChatFormatting.GREEN, targetPlayer.getName().getString()), new FTC(ChatFormatting.GOLD, " 传送给你."));

            return 1;
        })));
    }

    @ExpectPlatform
    public static int getTeleportTimeout() {
        throw new RuntimeException();
    }

    private static boolean isGameProfileOnline(MinecraftServer server, GameProfile profile) {
        ServerPlayer player = server.getPlayerList().getPlayer(profile.getId());
        if(player != null) {
            if(server.getPlayerList().getPlayers().contains(player)) {
                return true;
            }
        }
        return false;
    }

    private static GameProfile getFirstProfile(Collection<GameProfile> profiles) {
        for(GameProfile profile : profiles) {
            return profile;
        }
        return null;
    }

    @Nullable
    public static Teleport getSubjectTP(String name) {
        for(Teleport pair : QuickTeleportsMod.tps.keySet()) {
            if(pair.getSubject().equalsIgnoreCase(name)) {
                return pair;
            }
        }
        return null;
    }

    @Nullable
    public static Teleport getRequestTP(String name) {
        for(Teleport pair : QuickTeleportsMod.tps.keySet()) {
            if(pair.getRequester().equalsIgnoreCase(name)) {
                return pair;
            }
        }
        return null;
    }

    public static void serverTick(MinecraftServer server) {
        HashSet<Teleport> toRemove = new HashSet<>();
        for(Teleport tp : tps.keySet()) {
            int time = tps.get(tp);
            if(time > 0) {
                time--;
                tps.put(tp, time);
            } else if(time <= 0) {
                toRemove.add(tp);
                notifyTimeoutTP(server, tp);
            }
        }

        for(Teleport remove : toRemove) {
            tps.remove(remove);
        }
    }

    public static void notifyTimeoutTP(MinecraftServer server, Teleport tp) {
        ServerPlayer tper = server.getPlayerList().getPlayerByName(tp.getRequester());
        ServerPlayer target = server.getPlayerList().getPlayerByName(tp.getSubject());
        if(target != null) {
            sendMessage(target.createCommandSourceStack(), true, new FTC(ChatFormatting.GOLD, "来自 "), new FTC(ChatFormatting.GREEN, tp.getRequester()), new FTC(ChatFormatting.GOLD, " 的传送请求已过期."));
        }
        if(tper != null) {
            sendMessage(tper.createCommandSourceStack(), true, new FTC(ChatFormatting.GOLD, "传送到"), new FTC(ChatFormatting.GREEN, tp.getSubject()), new FTC(ChatFormatting.GOLD, " 的传送请求已过期."));
        }
    }

    public static void notifyCanceledTP(MinecraftServer server, Teleport tp) {
        ServerPlayer tper = server.getPlayerList().getPlayerByName(tp.getRequester());
        ServerPlayer target = server.getPlayerList().getPlayerByName(tp.getSubject());
        if(target != null) {
            sendMessage(target.createCommandSourceStack(), true, new FTC(ChatFormatting.GOLD, "来自 "), new FTC(ChatFormatting.GREEN, tp.getRequester()), new FTC(ChatFormatting.GOLD, " 的传送请求已取消."));
        }
        if(tper != null) {
            sendMessage(tper.createCommandSourceStack(), true, new FTC(ChatFormatting.GOLD, "传送到 "), new FTC(ChatFormatting.GREEN, tp.getSubject()), new FTC(ChatFormatting.GOLD, " 的传送请求已取消."));
        }
    }

    public static void sendMessage(CommandSourceStack source, boolean success, TextComponent... styled) {
        if(styled.length > 0) {
            TextComponent comp = styled[0];
            if(styled.length > 1) {
                for(int i = 1; i < styled.length; i++) {
                    comp.append(styled[i]);
                }
            }
            if(success) {
                source.sendSuccess(comp, false);
            } else {
                source.sendFailure(comp);
            }
        }
    }

}
