package quickcarpet.commands;

import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.ServerTask;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.UserCache;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import quickcarpet.feature.player.FakeServerPlayerEntity;
import quickcarpet.feature.player.PlayerActionPack;
import quickcarpet.feature.player.PlayerActionPack.Action;
import quickcarpet.feature.player.PlayerActionPack.ActionType;
import quickcarpet.settings.Settings;
import quickcarpet.utils.Constants.PlayerCommand.Keys;
import quickcarpet.utils.mixin.extensions.ActionPackOwner;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static com.mojang.brigadier.arguments.FloatArgumentType.floatArg;
import static com.mojang.brigadier.arguments.FloatArgumentType.getFloat;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.minecraft.command.argument.DimensionArgumentType.dimension;
import static net.minecraft.command.argument.DimensionArgumentType.getDimensionArgument;
import static net.minecraft.command.argument.EntityArgumentType.entity;
import static net.minecraft.command.argument.EntityArgumentType.getEntity;
import static net.minecraft.command.argument.RotationArgumentType.getRotation;
import static net.minecraft.command.argument.RotationArgumentType.rotation;
import static net.minecraft.command.argument.Vec3ArgumentType.getVec3;
import static net.minecraft.command.argument.Vec3ArgumentType.vec3;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static quickcarpet.utils.Constants.PlayerCommand.Texts.*;
import static quickcarpet.utils.Messenger.*;

public class PlayerCommand {
    // TODO: allow any order like execute
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var player = literal("player")
            .requires(s -> s.hasPermissionLevel(Settings.commandPlayer))
            .then(argument("player", word())
                .suggests((c, b) -> CommandSource.suggestMatching(getPlayers(c.getSource()), b))
                .then(literal("stop").executes(PlayerCommand::stop))
                .then(makeActionCommand("use", ActionType.USE))
                .then(makeActionCommand("jump", ActionType.JUMP))
                .then(makeActionCommand("attack", ActionType.ATTACK))
                .then(makeActionCommand("drop", ActionType.DROP_ITEM))
                .then(makeActionCommand("dropStack", ActionType.DROP_STACK))
                .then(makeActionCommand("swapHands", ActionType.SWAP_HANDS))
                .then(literal("reach").then(argument("reach", floatArg(0,5)).executes(c -> reach(c,getFloat(c, "reach")))))
                .then(literal("dropAll").executes(PlayerCommand::dropAll))
                .then(literal("kill").executes(PlayerCommand::kill))
                .then(literal("shadow"). executes(PlayerCommand::shadow))
                .then(literal("login").executes(PlayerCommand::login))
                .then(literal("mount").executes(manipulation(PlayerActionPack::mount)))
                .then(literal("dismount").executes(manipulation(PlayerActionPack::dismount)))
                .then(literal("sneak").executes(manipulation(PlayerActionPack::toggleSneaking)))
                .then(literal("sprint").executes(manipulation(PlayerActionPack::toggleSprinting)))
                .then(literal("fly").executes(manipulation(PlayerActionPack::toggleFlying)))
                .then(literal("skin")
                    .then(literal("cape").executes(manipulation(ap -> ap.toggleModelPart(PlayerModelPart.CAPE))))
                    .then(literal("jacket").executes(manipulation(ap -> ap.toggleModelPart(PlayerModelPart.JACKET))))
                    .then(literal("left_sleeve").executes(manipulation(ap -> ap.toggleModelPart(PlayerModelPart.LEFT_SLEEVE))))
                    .then(literal("right_sleeve").executes(manipulation(ap -> ap.toggleModelPart(PlayerModelPart.RIGHT_SLEEVE))))
                    .then(literal("left_pants_leg").executes(manipulation(ap -> ap.toggleModelPart(PlayerModelPart.LEFT_PANTS_LEG))))
                    .then(literal("right_pants_leg").executes(manipulation(ap -> ap.toggleModelPart(PlayerModelPart.RIGHT_PANTS_LEG))))
                    .then(literal("hat").executes(manipulation(ap -> ap.toggleModelPart(PlayerModelPart.HAT))))
                ).then(literal("gamemode")
                    .then(literal("spectator").requires(s -> s.hasPermissionLevel(Settings.commandCameramode) || s.hasPermissionLevel(2)).executes(ctx -> changeGameMode(ctx, GameMode.SPECTATOR)))
                    .then(literal("creative").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> changeGameMode(ctx, GameMode.CREATIVE)))
                    .then(literal("survival").executes(ctx -> changeGameMode(ctx, GameMode.SURVIVAL)))
                    .then(literal("adventure").executes(ctx -> changeGameMode(ctx, GameMode.ADVENTURE)))
                ).then(literal("look")
                    .then(literal("north").executes(manipulation(ap -> ap.look(Direction.NORTH))))
                    .then(literal("south").executes(manipulation(ap -> ap.look(Direction.SOUTH))))
                    .then(literal("east").executes(manipulation(ap -> ap.look(Direction.EAST))))
                    .then(literal("west").executes(manipulation(ap -> ap.look(Direction.WEST))))
                    .then(literal("up").executes(manipulation(ap -> ap.look(Direction.UP))))
                    .then(literal("down").executes(manipulation(ap -> ap.look(Direction.DOWN))))
                    .then(literal("at")
                        .then(argument("entity", entity()).executes(c -> manipulate(c, ap ->
                                ap.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES.positionAt(getEntity(c, "entity"))))))
                        .then(argument("position", vec3()).executes(c -> manipulate(c, ap ->
                                ap.lookAt(getVec3(c, "position"))))))
                    .then(argument("direction", rotation())
                        .executes(c -> manipulate(c, ap -> ap.look(getRotation(c, "direction").toAbsoluteRotation(c.getSource())))))
                ).then(literal("turn")
                    .then(literal("left").executes(c -> manipulate(c, ap -> ap.turn(-90, 0))))
                    .then(literal("right").executes(c -> manipulate(c, ap -> ap.turn(90, 0))))
                    .then(literal("back").executes(c -> manipulate(c, ap -> ap.turn(180, 0))))
                    .then(argument("rotation", rotation())
                        .executes(c -> manipulate(c, ap -> ap.turn(getRotation(c, "rotation").toAbsoluteRotation(c.getSource())))))
                ).then(literal("move")
                    .then(literal("forward").executes(c -> manipulate(c, ap -> ap.setForward(1))))
                    .then(literal("backward").executes(c -> manipulate(c, ap -> ap.setForward(-1))))
                    .then(literal("left").executes(c -> manipulate(c, ap -> ap.setSideways(1))))
                    .then(literal("right").executes(c -> manipulate(c, ap -> ap.setSideways(-1))))
                ).then(literal("spawn").executes(PlayerCommand::spawn)
                    .then(literal("at").then(argument("position", vec3()).executes(PlayerCommand::spawn)
                        .then(literal("facing").then(argument("direction", rotation()).executes(PlayerCommand::spawn)
                            .then(literal("in").then(argument("dimension", dimension()).executes(PlayerCommand::spawn)
                                .then(literal("as")
                                    .then(literal("spectator").requires(s -> s.hasPermissionLevel(Settings.commandCameramode) || s.hasPermissionLevel(2)).executes(ctx -> spawn(ctx, GameMode.SPECTATOR)))
                                    .then(literal("creative").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> spawn(ctx, GameMode.CREATIVE)))
                                    .then(literal("survival").executes(ctx -> spawn(ctx, GameMode.SURVIVAL)))
                                    .then(literal("adventure").executes(ctx -> spawn(ctx, GameMode.ADVENTURE)))
            )))))))));
        dispatcher.register(player);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> makeActionCommand(String actionName, ActionType type) {
        var once = literal("once").executes(c -> action(c, type, Action.once()));
        var continuous = literal("continuous").executes(c -> action(c, type, Action.continuous()));
        var interval = literal("inteval")
            .then(argument("ticks", integer(2))
                .executes(c -> action(c, type, Action.interval(getInteger(c, "ticks"), -1)))
                .then(argument("count", integer(1))
                    .executes(c -> action(c, type, Action.interval(getInteger(c, "ticks"), getInteger(c, "count"))))
                )
            );
        var perTick = literal("perTick").then(argument("times", integer(1,10))
                .executes(c -> action(c, type, Action.perTick(getInteger(c, "times")))));
        return literal(actionName)
            .executes(c -> action(c, type, Action.once()))
            .then(once)
            .then(continuous)
            .then(interval)
            .then(perTick);
    }

    private static Collection<String> getPlayers(ServerCommandSource source) {
        Set<String> players = Sets.newLinkedHashSet(Arrays.asList("Steve", "Alex"));
        players.addAll(source.getPlayerNames());
        return players;
    }

    private static ServerPlayerEntity getPlayer(CommandContext<ServerCommandSource> context) {
        String playerName = getString(context, "player");
        MinecraftServer server = context.getSource().getServer();
        return server.getPlayerManager().getPlayer(playerName);
    }

    private static boolean isOp(CommandContext<ServerCommandSource> context, PlayerEntity sendingPlayer) {
        return context.getSource().getServer().getPlayerManager().isOperator(sendingPlayer.getGameProfile());
    }

    private static boolean isSameOrFake(PlayerEntity sendingPlayer, PlayerEntity targetedPlayer) {
        return targetedPlayer == sendingPlayer || targetedPlayer instanceof FakeServerPlayerEntity;
    }

    private static boolean canManipulate(CommandContext<ServerCommandSource> context, PlayerEntity targetedPlayer) {
        PlayerEntity sendingPlayer = context.getSource().getPlayer();
        // Server console can manipulate
        if (sendingPlayer == null) return true;
        if (isOp(context, sendingPlayer)) return true;
        if (isSameOrFake(sendingPlayer, targetedPlayer)) return true;
        m(context.getSource(), NOT_OPERATOR);
        return false;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean canManipulate(CommandContext<ServerCommandSource> context) {
        PlayerEntity player = getPlayer(context);
        if (player != null) return canManipulate(context, player);
        m(context.getSource(), ONLY_EXISTING);
        return false;
    }

    private static boolean canManipulateFakePlayer(CommandContext<ServerCommandSource> context) {
        PlayerEntity player = getPlayer(context);
        if (!canManipulate(context, player)) return false;
        if (player instanceof FakeServerPlayerEntity) return true;
        m(context.getSource(), NOT_FAKE);
        return false;
    }

    private static <T> CompletableFuture<T> toFuture(Consumer<Consumer<T>> callback) {
        CompletableFuture<T> future = new CompletableFuture<>();
        callback.accept(future::complete);
        return future;
    }

    private static CompletableFuture<GameProfile> findByName(UserCache cache, String name) {
        return toFuture(cb -> cache.findByNameAsync(name, p -> cb.accept(p.orElse(null))));
    }

    private static CompletableFuture<GameProfile> getSpawnableProfile(CommandContext<ServerCommandSource> context) {
        String playerName = getString(context, "player");
        MinecraftServer server = context.getSource().getServer();
        PlayerManager manager = server.getPlayerManager();
        PlayerEntity player = manager.getPlayer(playerName);
        if (player != null) {
            m(context.getSource(), ts(Keys.ALREADY_ONLINE, Formatting.RED, s(playerName, Formatting.BOLD)));
            return CompletableFuture.completedFuture(null);
        }
        return findByName(server.getUserCache(), playerName)
            .thenApply(profile -> checkSpawnableProfile(context, profile, playerName));
    }

    private static GameProfile checkSpawnableProfile(CommandContext<ServerCommandSource> context, @Nullable GameProfile profile, String name) {
        PlayerManager manager = context.getSource().getServer().getPlayerManager();
        if (profile == null) {
            m(context.getSource(), ts(Keys.DOES_NOT_EXIST, Formatting.RED, s(name, Formatting.BOLD)));
            return null;
        }
        if (manager.getUserBanList().contains(profile)) {
            m(context.getSource(), ts(Keys.BANNED, Formatting.RED, s(name, Formatting.BOLD)));
            return null;
        }
        if (manager.isWhitelistEnabled() && manager.isWhitelisted(profile) && !context.getSource().hasPermissionLevel(2)) {
            m(context.getSource(), ts(Keys.WHITELISTED, Formatting.RED));
            return null;
        }
        return profile;
    }

    private static int kill(CommandContext<ServerCommandSource> context) {
        if (!canManipulateFakePlayer(context)) return 0;
        getPlayer(context).kill();
        return 1;
    }

    @FunctionalInterface
    interface SupplierWithCommandSyntaxException<T> {
        T get() throws CommandSyntaxException;
    }

    private static <T> T tryGetArg(SupplierWithCommandSyntaxException<T> a, SupplierWithCommandSyntaxException<T> b) throws CommandSyntaxException {
        try {
            return a.get();
        } catch (IllegalArgumentException e) {
            return b.get();
        }
    }

    private static int spawn(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return spawn(context, null);
    }

    private static FakeServerPlayerEntity.Properties getSpawnProperties(CommandContext<ServerCommandSource> context, GameMode gameMode) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        Vec3d pos = tryGetArg(() -> getVec3(context, "position"), source::getPosition);
        Vec2f facing = tryGetArg(() -> getRotation(context, "direction").toAbsoluteRotation(source), source::getRotation);
        ServerWorld dim = tryGetArg(() -> getDimensionArgument(context, "dimension"), source::getWorld);
        GameMode mode = source.getServer().getDefaultGameMode();
        boolean flying = false;
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player != null) {
            mode = player.interactionManager.getGameMode();
            flying = player.getAbilities().flying;
        }
        if (gameMode != null) mode = gameMode;
        return new FakeServerPlayerEntity.Properties(pos.x, pos.y, pos.z, facing.y, facing.x, dim.getRegistryKey(), mode, flying);
    }

    private static int spawn(CommandContext<ServerCommandSource> context, GameMode gameMode) throws CommandSyntaxException {
        FakeServerPlayerEntity.Properties props = getSpawnProperties(context, gameMode);
        getSpawnableProfile(context).thenAccept(profile -> {
            if (profile == null) return;
            MinecraftServer server = context.getSource().getServer();
            server.send(new ServerTask(server.getTicks(), () -> FakeServerPlayerEntity.createFake(profile, server, props)));
        });
        return 1;
    }

    private static int login(CommandContext<ServerCommandSource> context) {
        getSpawnableProfile(context).thenAccept(profile -> {
            MinecraftServer server = context.getSource().getServer();
            server.send(new ServerTask(server.getTicks(), () -> FakeServerPlayerEntity.createFake(profile, server)));
        });
        return 1;
    }

    private static int stop(CommandContext<ServerCommandSource> context) {
        if (!canManipulate(context)) return 0;
        ServerPlayerEntity player = getPlayer(context);
        ((ActionPackOwner) player).quickcarpet$getActionPack().stop();
        return 1;
    }

    private static int reach(CommandContext<ServerCommandSource> context, float dist) {
        if (!canManipulate(context)) return 0;
        ServerPlayerEntity player = getPlayer(context);
        ((ActionPackOwner) player).quickcarpet$getActionPack().reach = dist;
        return 1;
    }

    @FunctionalInterface
    interface PlayerAction {
        void doAction(PlayerActionPack actionPack) throws CommandSyntaxException;
    }

    private static int manipulate(CommandContext<ServerCommandSource> context, PlayerAction action) throws CommandSyntaxException {
        if (!canManipulate(context)) return 0;
        ServerPlayerEntity player = getPlayer(context);
        action.doAction(((ActionPackOwner) player).quickcarpet$getActionPack());
        return 1;
    }

    private static Command<ServerCommandSource> manipulation(PlayerAction action) {
        return c -> manipulate(c, action);
    }

    private static int action(CommandContext<ServerCommandSource> context, ActionType type, Action action) throws CommandSyntaxException {
        return manipulate(context, ap -> ap.start(type, action));
    }

    private static int shadow(CommandContext<ServerCommandSource> context) {
        if (!canManipulate(context)) return 0;
        ServerPlayerEntity player = getPlayer(context);
        if (player instanceof FakeServerPlayerEntity) {
            m(context.getSource(), SHADOW_FAKE);
            return 0;
        }
        FakeServerPlayerEntity.createShadow(player.server, player);
        return 1;
    }

    private static int dropAll(CommandContext<ServerCommandSource> context) {
        if (!canManipulate(context)) return 0;
        ServerPlayerEntity player = getPlayer(context);
        int count = player.getInventory().size();
        for (int i = 0; i < count; i++) {
            player.dropItem(player.getInventory().getStack(i), true);
        }
        player.getInventory().clear();
        return 1;
    }

    private static int changeGameMode(CommandContext<ServerCommandSource> context, GameMode mode) {
        if (!canManipulate(context)) return 0;
        ServerPlayerEntity player = getPlayer(context);
        player.interactionManager.changeGameMode(mode);
        return 1;
    }
}
