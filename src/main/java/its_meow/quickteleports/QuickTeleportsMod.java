package its_meow.quickteleports;

import static net.minecraft.util.text.TextFormatting.GOLD;
import static net.minecraft.util.text.TextFormatting.GREEN;
import static net.minecraft.util.text.TextFormatting.RED;
import static net.minecraft.util.text.TextFormatting.YELLOW;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import its_meow.quickteleports.util.FTC;
import its_meow.quickteleports.util.HereTeleport;
import its_meow.quickteleports.util.Teleport;
import its_meow.quickteleports.util.ToTeleport;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.GameProfileArgument;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

@Mod(QuickTeleportsMod.MOD_ID)
@Mod.EventBusSubscriber(modid = QuickTeleportsMod.MOD_ID)
public class QuickTeleportsMod {

    public static final String MOD_ID = "quickteleports";

    public static HashMap<Teleport, Integer> tps = new HashMap<Teleport, Integer>();

    public QuickTeleportsMod() {
        TpConfig.setupConfig();
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, TpConfig.SERVER_CONFIG);
    }

    @SubscribeEvent
    public static void onServerStarting(FMLServerStartingEvent event) {
        CommandDispatcher<CommandSource> d = event.getCommandDispatcher();

        // tpa
        d.register(Commands.literal("tpa").requires(source -> {
            try {
                return source.asPlayer() != null;
            } catch(CommandSyntaxException e) {
                return false;
            }
        }).then(Commands.argument("target", GameProfileArgument.gameProfile()).executes(command -> {
            Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(command, "target");
            if(profiles.size() > 1) {
                sendMessage(command.getSource(), new FTC(RED, "指定一个玩家作为参数!"));
                return 0;
            }
            GameProfile profile = getFirstProfile(profiles);
            if(!isGameProfileOnline(profile)) {
                sendMessage(command.getSource(), new FTC(RED, "该玩家不在线!"));
                return 0;
            }
            if(profile.getId().equals(command.getSource().asPlayer().getGameProfile().getId())) {
                sendMessage(command.getSource(), new FTC(RED, "你不能传送到自己身边!"));
                return 0;
            }
            String sourceName = command.getSource().asPlayer().getName().getString();
            ServerPlayerEntity targetPlayer = event.getServer().getPlayerList().getPlayerByUUID(profile.getId());
            Teleport remove = QuickTeleportsMod.getRequestTP(sourceName);
            if(remove != null) {
                QuickTeleportsMod.tps.remove(remove);
                QuickTeleportsMod.notifyCanceledTP(remove);
            }

            ToTeleport teleport = new ToTeleport(sourceName, targetPlayer.getName().getString());
            QuickTeleportsMod.tps.put(teleport, TpConfig.CONFIG.timeout.get() * 20);
            sendMessage(targetPlayer, new FTC(GREEN, sourceName), new FTC(GOLD, " 已请求传送给您。 输入 "), new FTC(YELLOW, "/tpaccept"), new FTC(GOLD, "同意传送."));
            sendMessage(command.getSource(), new FTC(GOLD, "被要求传送到 "), new FTC(GREEN, targetPlayer.getName().getString()), new FTC(GOLD, "."));
            return 1;
        })));

        // tpaccept
        d.register(Commands.literal("tpaccept").requires(source -> {
            try {
                return source.asPlayer() != null;
            } catch(CommandSyntaxException e) {
                return false;
            }
        }).executes(command -> {

            Teleport tp = QuickTeleportsMod.getSubjectTP(command.getSource().asPlayer().getName().getString());

            if(tp == null) {
                sendMessage(command.getSource(), new FTC(RED, "您没有待处理的传送请求!"));
                return 0;
            }

            QuickTeleportsMod.tps.remove(tp);
            ServerPlayerEntity playerRequesting = event.getServer().getPlayerList().getPlayerByUsername(tp.getRequester());
            ServerPlayerEntity playerMoving = event.getServer().getPlayerList().getPlayerByUsername(tp.getSubject());

            if(playerMoving == null) {
                sendMessage(command.getSource(), new FTC(RED, "正在传送的玩家不再存在!"));
                return 0;
            }

            if(tp instanceof ToTeleport) {
                ServerPlayerEntity holder = playerMoving;
                playerMoving = playerRequesting;
                playerRequesting = holder;
            }

            sendMessage(playerRequesting, new FTC(GREEN, "接受传送请求."));
            sendMessage(playerMoving, new FTC(GREEN, (tp instanceof ToTeleport ? "您的传送请求已被接受。" : "你现在正在被传送.")));

            double posX = playerRequesting.func_226277_ct_();
            double posY = playerRequesting.func_226278_cu_();
            double posZ = playerRequesting.func_226281_cx_();
            playerMoving.func_200619_a(playerRequesting.func_71121_q(), posX, posY, posZ, playerRequesting.rotationYaw, 0);
            return 1;
        }));

        // tpahere
        d.register(Commands.literal("tpahere").requires(source -> {
            try {
                return source.asPlayer() != null;
            } catch(CommandSyntaxException e) {
                return false;
            }
        }).then(Commands.argument("target", GameProfileArgument.gameProfile()).executes(command -> {
            Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(command, "target");
            if(profiles.size() > 1) {
                sendMessage(command.getSource(), new FTC(RED, "指定一个玩家作为参数!"));
                return 0;
            }
            GameProfile profile = getFirstProfile(profiles);
            if(!isGameProfileOnline(profile)) {
                sendMessage(command.getSource(), new FTC(RED, "该玩家不在线!"));
                return 0;
            }
            if(profile.getId().equals(command.getSource().asPlayer().getGameProfile().getId())) {
                sendMessage(command.getSource(), new FTC(RED, "你不能向自己发送传送请求!"));
                return 0;
            }
            String sourceName = command.getSource().asPlayer().getName().getString();
            Teleport remove = QuickTeleportsMod.getRequestTP(sourceName);
            if(remove != null) {
                QuickTeleportsMod.tps.remove(remove);
                QuickTeleportsMod.notifyCanceledTP(remove);
            }
            ServerPlayerEntity targetPlayer = event.getServer().getPlayerList().getPlayerByUUID(profile.getId());

            HereTeleport tp = new HereTeleport(sourceName, targetPlayer.getName().getString());
            QuickTeleportsMod.tps.put(tp, TpConfig.CONFIG.timeout.get() * 20);
            sendMessage(targetPlayer, new FTC(GREEN, sourceName), new FTC(GOLD, " 已经要求你传送到他们那里。 输入 "), new FTC(YELLOW, "/tpaccept"), new FTC(GOLD, " 同意传送."));
            sendMessage(command.getSource(), new FTC(GOLD, "已请求 "), new FTC(GREEN, targetPlayer.getName().getString()), new FTC(GOLD, " 传送给你."));

            return 1;
        })));
    }

    private static boolean isGameProfileOnline(GameProfile profile) {
        ServerPlayerEntity player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayerByUUID(profile.getId());
        if(player != null) {
            if(ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers().contains(player)) {
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

    @SubscribeEvent
    public static void onTick(ServerTickEvent event) {
        HashSet<Teleport> toRemove = new HashSet<Teleport>();
        for(Teleport tp : tps.keySet()) {
            int time = tps.get(tp);
            if(time > 0) {
                time--;
                tps.put(tp, time);
            } else if(time <= 0) {
                toRemove.add(tp);
                notifyTimeoutTP(tp);
            }
        }

        for(Teleport remove : toRemove) {
            tps.remove(remove);
        }
    }

    public static void notifyTimeoutTP(Teleport tp) {
        @SuppressWarnings("resource")
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerPlayerEntity tper = server.getPlayerList().getPlayerByUsername(tp.getRequester());
        ServerPlayerEntity target = server.getPlayerList().getPlayerByUsername(tp.getSubject());
        if(target != null) {
            sendMessage(target, new FTC(GOLD, "来自 "), new FTC(GREEN, tp.getRequester()), new FTC(GOLD, " 的传送请求已过期."));
        }
        if(tper != null) {
            sendMessage(tper, new FTC(GOLD, "传送到 "), new FTC(GREEN, tp.getSubject()), new FTC(GOLD, " has timed out after not being accepted."));
        }
    }

    public static void notifyCanceledTP(Teleport tp) {
        @SuppressWarnings("resource")
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerPlayerEntity tper = server.getPlayerList().getPlayerByUsername(tp.getRequester());
        ServerPlayerEntity target = server.getPlayerList().getPlayerByUsername(tp.getSubject());
        if(target != null) {
            sendMessage(target, new FTC(GOLD, "来自 "), new FTC(GREEN, tp.getRequester()), new FTC(GOLD, " 的传送请求已过期."));
        }
        if(tper != null) {
            sendMessage(tper, new FTC(GOLD, "传送到 "), new FTC(GREEN, tp.getSubject()), new FTC(GOLD, " 的传送请求已过期."));
        }
    }

    public static void sendMessage(CommandSource source, ITextComponent... styled) throws CommandSyntaxException {
        sendMessage(source.asPlayer(), styled);
    }

    public static void sendMessage(PlayerEntity source, ITextComponent... styled) {
        if(styled.length > 0) {
            ITextComponent comp = styled[0];
            if(styled.length > 1) {
                for(int i = 1; i < styled.length; i++) {
                    comp.appendSibling(styled[i]);
                }
            }
            source.sendMessage(comp);
        }
    }

}