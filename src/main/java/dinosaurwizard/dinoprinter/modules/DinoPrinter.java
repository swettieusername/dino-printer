/**

  Dino Printer by dinosaurwizard

**/

package dinosaurwizard.dinoprinter.modules;

import dinosaurwizard.dinoprinter.utils.BlockPrint;
import dinosaurwizard.dinoprinter.utils.BlockInteract;
import dinosaurwizard.dinoprinter.utils.RotationManager;
import dinosaurwizard.dinoprinter.utils.BaritoneBridge;
import dinosaurwizard.dinoprinter.DinoPrinterAddon;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import net.fabricmc.loader.api.FabricLoader;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.systems.modules.player.AutoGap;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DinoPrinter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");
    private final SettingGroup sgInventory = settings.createGroup("Inventory");
    private final SettingGroup sgMovement = settings.createGroup("Movement");
    private final SettingGroup sgPause = settings.createGroup("Pause");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("The tick delay between placing blocks.")
        .defaultValue(0)
        .range(0, 10)
        .build()
    );

    private final Setting<Integer> placeRetryDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-retry-delay")
        .description("Delay in ticks to retry placing a block if it failed the first time.")
        .defaultValue(20)
        .min(0)
        .sliderMax(100)
        .build()
    );

    public final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("How far away from the player you can place a block.")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many blocks to place in one tick.")
        .defaultValue(1)
        .min(1)
        .sliderMax(20)
        .build()
    );

    public final Setting<Boolean> wallPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("wall-place")
        .description("Allow placement through walls. Turn off to use raytracing.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> airPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("air-place")
        .description("Allow placement in the air.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> sneakPlace = sgGeneral.add(new BoolSetting.Builder()
        .name("sneak-place")
        .description("Sends sneaking packets when placing blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates towards the blocks being placed.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> rotateLegitMove = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate-legit-move")
        .description("Respect movement inputs when rotating. Required for certain anti-cheats.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SortAlgorithm> firstAlgorithm = sgGeneral.add(new EnumSetting.Builder<SortAlgorithm>()
        .name("first-sorting-mode")
        .description("The blocks you want to place first.")
        .defaultValue(SortAlgorithm.None)
        .build()
    );

    private final Setting<SortingSecond> secondAlgorithm = sgGeneral.add(new EnumSetting.Builder<SortingSecond>()
        .name("second-sorting-mode")
        .description("Second pass of sorting eg. place first blocks higher and closest to you.")
        .defaultValue(SortingSecond.None)
        .visible(() -> firstAlgorithm.get().applySecondSorting)
        .build()
    );

    // Advanced

    public final Setting<Boolean> rotationPlace = sgAdvanced.add(new BoolSetting.Builder()
        .name("rotation-place")
        .description("Respect block rotation.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> strictRotation = sgAdvanced.add(new BoolSetting.Builder()
        .name("strict-rotation")
        .description("Determines if rotatable blocks can only be placed legitimently.")
        .defaultValue(false)
        .visible(rotationPlace::get)
        .build()
    );

    public final Setting<Boolean> halfBlocks = sgAdvanced.add(new BoolSetting.Builder()
        .name("half-blocks")
        .description("Respect block half. Necessary for properly placing slabs, stairs and trapdoors.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> incrementalStates = sgAdvanced.add(new BoolSetting.Builder()
        .name("incremental-states")
        .description("Respect states that need multiple placements. Necessary for double-slabs, candles, vines, and snow layers.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> miscStates = sgAdvanced.add(new BoolSetting.Builder()
        .name("misc-states")
        .description("Respect miscellaneous states. Blocks like doors, beds, and lanterns are affected.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> fluids = sgAdvanced.add(new BoolSetting.Builder()
        .name("fluids")
        .description("Allow placement of fluids using their respective buckets.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> exitSigns = sgAdvanced.add(new BoolSetting.Builder()
        .name("exit-signs")
        .description("Auto exit sign screens.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> interact = sgAdvanced.add(new BoolSetting.Builder()
        .name("interact")
        .description("Auto interact with blocks to match their state to the schematic. Useful for noteblocks, redstone components, trapdoors, etc.")
        .defaultValue(false)
        .build()
    );

    // Inventory

    private final Setting<Boolean> autoSwitch = sgInventory.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Switches to placeable blocks in your hotbar automatically.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swapBack = sgInventory.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Switches to your previous slot after placing blocks.")
        .defaultValue(true)
        .visible(autoSwitch::get)
        .build()
    );

    private final Setting<Boolean> allowInventory = sgInventory.add(new BoolSetting.Builder()
        .name("allow-inventory")
        .description("Allows blocks to be moved into your hotbar from your inventory.")
        .defaultValue(true)
        .visible(autoSwitch::get)
        .build()
    );

    private final Setting<Boolean> stationaryMove = sgInventory.add(new BoolSetting.Builder()
        .name("stationary-move")
        .description("Only allows blocks to be moved from your inventory if you are standing still. Required for certain anti-cheats.")
        .defaultValue(false)
        .visible(() -> autoSwitch.get() && allowInventory.get())
        .build()
    );

    private final Setting<Boolean> hotbarPriority = sgInventory.add(new BoolSetting.Builder()
        .name("hotbar-priority")
        .description("Blocks in your hotbar will have placement priority over blocks inside your inventory.")
        .defaultValue(true)
        .visible(() -> autoSwitch.get() && allowInventory.get())
        .build()
    );

    private final Setting<Boolean> creativeRefill = sgInventory.add(new BoolSetting.Builder()
        .name("creative-refill")
        .description("Automatically takes missing items from Creative tabs into the 9-slot hotbar, replacing existing block stacks when needed.")
        .defaultValue(false)
        .build()
    );

    private final Setting<MovementMode> movementMode = sgMovement.add(new EnumSetting.Builder<MovementMode>()
        .name("movement-mode")
        .description("Automatically approaches missing schematic blocks using Baritone pathfinding in Survival mode.")
        .defaultValue(MovementMode.Disabled)
        .build()
    );

    private final Setting<Integer> movementSearchRadius = sgMovement.add(new IntSetting.Builder()
        .name("movement-search-radius")
        .description("Radius used to find the next missing schematic block before moving to a schematic region.")
        .defaultValue(32)
        .range(8, 128)
        .sliderMax(64)
        .visible(() -> movementMode.get() != MovementMode.Disabled)
        .build()
    );

    private final Setting<Integer> slotLockTicks = sgInventory.add(new IntSetting.Builder()
        .name("slot-lock-ticks")
        .description("The time in ticks to lock a slot that was used for swapping items.")
        .defaultValue(5)
        .min(0)
        .sliderMax(20)
        .visible(() -> autoSwitch.get() && allowInventory.get())
        .build()
    );

    private final Setting<Integer> antiOverrideTicks = sgInventory.add(new IntSetting.Builder()
        .name("anti-override-ticks")
        .description("The time in ticks to stop S2C sync packets overriding your inventory slots, for the purpose of stopping misplaces.")
        .defaultValue(5)
        .min(0)
        .sliderMax(20)
        .build()
    );

    // Pause

    private final Setting<Boolean> pauseOnUse = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-use")
        .description("Pauses while using an item.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnMine = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-mine")
        .description("Pauses while mining blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnKillAura = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-kill-aura")
        .description("Pauses while using kill aura.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnLag = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-lag")
        .description("Pauses when the server stops responding.")
        .defaultValue(true)
        .build()
    );

    // Render

    private final Setting<Boolean> swing = sgRender.add(new BoolSetting.Builder()
        .name("swing")
        .description("Render your hand swinging when placing blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders block placements.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> fadeTime = sgRender.add(new IntSetting.Builder()
        .name("fade-time")
        .description("Time for the rendering to fade, in ticks.")
        .defaultValue(8)
        .min(0)
        .sliderMax(20)
        .visible(render::get)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the shapes are rendered.")
        .defaultValue(ShapeMode.Both)
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The side color to render the placement as.")
        .defaultValue(new SettingColor(177, 236, 248, 100))
        .visible(() -> render.get() && shapeMode.get().sides())
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The line color to render the placement as.")
        .defaultValue(new SettingColor(230, 250, 255))
        .visible(() -> render.get() && shapeMode.get().lines())
        .build()
    );

    private int placeTimer = 0;
    private int cacheTimer = 0;
    private int pendingInventorySlot = -1;
    private int pendingHotbarSlot = -1;
    private long lastSignPlaceTime = 0;
    public final Set<BlockPos> cachedPositions = new ObjectOpenHashSet<>();
    private final List<BlockInteract> cachedInteracts = new ArrayList<>();
    private final List<BlockPrint> blockPrints = new ArrayList<>();
    private final List<BlockInteract> blockInteracts = new ArrayList<>();
    private final List<PlacedFade> placedFades = new ArrayList<>();
    private final List<ItemStack> mainInventory = new ArrayList<>(SlotUtils.MAIN_END + 1);
    private final List<PrintSlot> printSlots = createPrintSlots();

    private BlockPos movementTarget;
    private boolean movementTargetIsBlock;
    private boolean warnedBaritoneMissing;
    private boolean warnedMovementModeMismatch;

    private List<MovementBounds> movementScanBounds = new ArrayList<>();
    private int movementScanBoundsIndex;
    private int movementScanX, movementScanY, movementScanZ;
    private int movementScanPlacementCount = -1;
    private boolean movementScanInitialized;
    private boolean movementScanComplete;
    private WorldSchematic movementScanWorld;

    private List<PrintSlot> createPrintSlots() {
        List<PrintSlot> slots = new ArrayList<>(SlotUtils.MAIN_END + 1);
        for (int i = 0; i <= SlotUtils.MAIN_END; i++) {
            slots.add(new PrintSlot(i));
        }
        return slots;
    }

    public DinoPrinter() {
        super(Categories.World, "dino-printer", "Prints rendered litematica schematics.");
    }

    @Override
    public void onActivate() {
        reset();
        warnedBaritoneMissing = false;
        warnedMovementModeMismatch = false;
    }

    @Override
    public void onDeactivate() {
        stopMovementNavigation();
        reset();
    }

    private void reset() {
        placeTimer = 0;
        cacheTimer = 0;
        pendingInventorySlot = -1;
        pendingHotbarSlot = -1;
        lastSignPlaceTime = 0;
        cachedPositions.clear();
        cachedInteracts.clear();
        blockPrints.clear();
        blockInteracts.clear();
        placedFades.clear();
        mainInventory.clear();
        movementTarget = null;
        movementTargetIsBlock = false;
        resetMovementScan();

        for (PrintSlot slot : printSlots) {
            slot.expire();
        }
    }

    private boolean isCreativeGameMode() {
        return mc.player != null && mc.gameMode != null
            && mc.gameMode.getPlayerMode() == GameType.CREATIVE
            && mc.player.hasInfiniteMaterials();
    }

    private boolean isSurvivalGameMode() {
        return mc.player != null && mc.gameMode != null
            && mc.gameMode.getPlayerMode() == GameType.SURVIVAL;
    }

    private boolean isBaritoneLoaded() {
        return FabricLoader.getInstance().isModLoaded("baritone-meteor")
            || FabricLoader.getInstance().isModLoaded("baritone");
    }

    private boolean isMovementAutomationReady() {
        return movementMode.get() == MovementMode.Survival
            && isSurvivalGameMode()
            && isBaritoneLoaded();
    }

    private void refreshInventorySnapshot() {
        mainInventory.clear();
        for (int i = 0; i <= SlotUtils.MAIN_END; i++) {
            mainInventory.add(mc.player.getInventory().getItem(i).copy());
        }
    }

    private boolean canAcquireItemForMovement(Item item) {
        if (isCreativeRefillActive()) {
            return autoSwitch.get() || isCreativeHotbarSlotUsable(mc.player.getInventory().getSelectedSlot());
        }

        if (!autoSwitch.get()) {
            return mainInventory.get(mc.player.getInventory().getSelectedSlot()).getItem() == item;
        }

        int slot = getItemSlot(item);
        if (slot == -1) return false;
        return slot <= SlotUtils.HOTBAR_END || canInventoryMove();
    }

    private void updateAutoMovement(WorldSchematic worldSchematic) {
        if (movementMode.get() == MovementMode.Disabled) {
            stopMovementNavigation();
            return;
        }

        if (!isSurvivalGameMode()) {
            stopMovementNavigation();
            if (!warnedMovementModeMismatch) {
                DinoPrinterAddon.LOG.warn("Dino Printer Survival movement requires the player to be in Survival mode.");
                warnedMovementModeMismatch = true;
            }
            return;
        }

        warnedMovementModeMismatch = false;
        if (!isBaritoneLoaded()) {
            stopMovementNavigation();
            if (!warnedBaritoneMissing) {
                DinoPrinterAddon.LOG.warn("Dino Printer Survival movement requires Meteor Baritone 26.2 (baritone-meteor) in the mods folder.");
                warnedBaritoneMissing = true;
            }
            return;
        }
        warnedBaritoneMissing = false;

        if (movementTarget != null && isMovementTargetComplete(worldSchematic)) {
            movementTarget = null;
            movementTargetIsBlock = false;
        }

        // A coarse region waypoint gets the player to the schematic while the bounded
        // background scan continues looking for an actual missing block.
        if (movementTarget != null && !movementTargetIsBlock) {
            BlockPos scannedTarget = scanGlobalMovementTarget(worldSchematic);
            if (scannedTarget != null) {
                movementTarget = scannedTarget;
                movementTargetIsBlock = true;
            } else if (movementScanComplete) {
                stopMovementNavigation();
                return;
            }
        }

        if (movementTarget == null) {
            movementTarget = findNextMovementTarget(worldSchematic);
            movementTargetIsBlock = movementTarget != null;

            if (movementTarget == null) {
                movementTarget = scanGlobalMovementTarget(worldSchematic);
                movementTargetIsBlock = movementTarget != null;
            }

            if (movementTarget == null && !movementScanComplete) {
                movementTarget = findCoarseMovementTarget();
                movementTargetIsBlock = false;
            }
        }

        if (movementTarget == null) {
            if (movementScanComplete) stopMovementNavigation();
            return;
        }

        if (movementMode.get() == MovementMode.Survival) {
            int goalRange = movementTargetIsBlock
                ? Math.max(1, (int) Math.floor(placeRange.get()) - 1)
                : 2;
            BaritoneBridge.pathTo(movementTarget, goalRange);
        }
    }

    private boolean isMovementTargetComplete(WorldSchematic worldSchematic) {
        if (movementTarget == null || mc.player == null || mc.level == null) return true;

        if (!movementTargetIsBlock) {
            return mc.player.blockPosition().distSqr(movementTarget) <= 9;
        }

        BlockState required = worldSchematic.getBlockState(movementTarget);
        BlockState existing = mc.level.getBlockState(movementTarget);
        return !BlockPrint.isValid(movementTarget, required, existing, this);
    }

    private List<MovementBounds> getMovementBounds() {
        List<MovementBounds> result = new ArrayList<>();

        for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
            if (!placement.isEnabled()) continue;

            var schematic = placement.getSchematic();
            if (schematic == null) continue;

            for (Map.Entry<String, SubRegionPlacement> entry : placement.getEnabledRelativeSubRegionPlacements().entrySet()) {
                SubRegionPlacement subRegion = entry.getValue();

                Vec3i relativePosition = transformSchematicVector(
                    subRegion.getPos(),
                    placement.getMirror(),
                    placement.getRotation()
                );

                Vec3i size = schematic.getAreaSize(entry.getKey());
                size = transformSchematicVector(size, placement.getMirror(), placement.getRotation());
                size = transformSchematicVector(size, subRegion.getMirror(), subRegion.getRotation());

                int offsetX = Math.min(size.getX() + 1, 0);
                int offsetY = Math.min(size.getY() + 1, 0);
                int offsetZ = Math.min(size.getZ() + 1, 0);

                BlockPos origin = placement.getOrigin()
                    .offset(relativePosition)
                    .offset(offsetX, offsetY, offsetZ);

                int sizeX = Math.abs(size.getX());
                int sizeY = Math.abs(size.getY());
                int sizeZ = Math.abs(size.getZ());
                if (sizeX == 0 || sizeY == 0 || sizeZ == 0) continue;

                result.add(new MovementBounds(
                    origin.getX(), origin.getY(), origin.getZ(),
                    origin.getX() + sizeX - 1,
                    origin.getY() + sizeY - 1,
                    origin.getZ() + sizeZ - 1
                ));
            }
        }

        return result;
    }

    // Same mirror/rotation transform used by Baritone's 26.2 Litematica adapter.
    private static Vec3i transformSchematicVector(Vec3i in, Mirror mirror, Rotation rotation) {
        int x = in.getX();
        int z = in.getZ();

        if (mirror == Mirror.LEFT_RIGHT) z = -z;
        else if (mirror == Mirror.FRONT_BACK) x = -x;

        return switch (rotation) {
            case CLOCKWISE_90 -> new Vec3i(-z, in.getY(), x);
            case CLOCKWISE_180 -> new Vec3i(-x, in.getY(), -z);
            case COUNTERCLOCKWISE_90 -> new Vec3i(z, in.getY(), -x);
            default -> new Vec3i(x, in.getY(), z);
        };
    }

    private BlockPos findNextMovementTarget(WorldSchematic worldSchematic) {
        if (mc.player == null || mc.level == null) return null;

        int px = mc.player.getBlockX();
        int py = mc.player.getBlockY();
        int pz = mc.player.getBlockZ();
        int radius = movementSearchRadius.get();
        int radiusSq = radius * radius;
        int bestDistance = Integer.MAX_VALUE;
        BlockPos best = null;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (MovementBounds bounds : getMovementBounds()) {
            int minX = Math.max(bounds.minX, px - radius);
            int minY = Math.max(bounds.minY, py - radius);
            int minZ = Math.max(bounds.minZ, pz - radius);
            int maxX = Math.min(bounds.maxX, px + radius);
            int maxY = Math.min(bounds.maxY, py + radius);
            int maxZ = Math.min(bounds.maxZ, pz + radius);

            if (minX > maxX || minY > maxY || minZ > maxZ) continue;

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        int dx = x - px;
                        int dy = y - py;
                        int dz = z - pz;
                        int distance = dx * dx + dy * dy + dz * dz;
                        if (distance > radiusSq || distance >= bestDistance) continue;

                        mutable.set(x, y, z);
                        if (!DataManager.getRenderLayerRange().isPositionWithinRange(mutable)) continue;
                        if (!Level.isInSpawnableBounds(mutable)) continue;

                        BlockState required = worldSchematic.getBlockState(mutable);
                        BlockState existing = mc.level.getBlockState(mutable);
                        if (!BlockPrint.isValid(mutable, required, existing, this)) continue;

                        Item item = BlockPrint.asItem(required);
                        if (!canAcquireItemForMovement(item)) continue;

                        bestDistance = distance;
                        best = new BlockPos(mutable);
                    }
                }
            }
        }

        return best;
    }

    private BlockPos findCoarseMovementTarget() {
        if (mc.player == null) return null;

        BlockPos playerPos = mc.player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (MovementBounds bounds : getMovementBounds()) {
            // Once inside a subregion, only move toward a different subregion.
            // This prevents endless corner-to-corner wandering when a schematic is complete.
            if (bounds.contains(playerPos)) continue;

            int x = Math.max(bounds.minX, Math.min(bounds.maxX, playerPos.getX()));
            int y = Math.max(bounds.minY, Math.min(bounds.maxY, playerPos.getY()));
            int z = Math.max(bounds.minZ, Math.min(bounds.maxZ, playerPos.getZ()));
            BlockPos candidate = new BlockPos(x, y, z);
            double distance = playerPos.distSqr(candidate);

            if (distance <= 9 || distance >= bestDistance) continue;

            best = candidate;
            bestDistance = distance;
        }

        return best;
    }

    private void resetMovementScan() {
        movementScanBounds.clear();
        movementScanBoundsIndex = 0;
        movementScanX = 0;
        movementScanY = 0;
        movementScanZ = 0;
        movementScanPlacementCount = -1;
        movementScanInitialized = false;
        movementScanComplete = false;
        movementScanWorld = null;
    }

    private void initializeMovementScan(WorldSchematic worldSchematic) {
        movementScanBounds = getMovementBounds();
        BlockPos playerPos = mc.player.blockPosition();
        movementScanBounds.sort(Comparator.comparingDouble(bounds -> bounds.distanceToSqr(playerPos)));

        movementScanBoundsIndex = 0;
        movementScanWorld = worldSchematic;
        movementScanPlacementCount = DataManager.getSchematicPlacementManager().getAllSchematicsPlacements().size();
        movementScanInitialized = true;
        movementScanComplete = movementScanBounds.isEmpty();

        if (!movementScanComplete) {
            MovementBounds first = movementScanBounds.get(0);
            movementScanX = first.minX;
            movementScanY = first.minY;
            movementScanZ = first.minZ;
        }
    }

    private void ensureMovementScanInitialized(WorldSchematic worldSchematic) {
        int placementCount = DataManager.getSchematicPlacementManager().getAllSchematicsPlacements().size();
        if (!movementScanInitialized || movementScanWorld != worldSchematic
            || movementScanPlacementCount != placementCount) {
            initializeMovementScan(worldSchematic);
        }
    }

    private boolean advanceMovementScanCursor() {
        while (movementScanBoundsIndex < movementScanBounds.size()) {
            MovementBounds bounds = movementScanBounds.get(movementScanBoundsIndex);

            movementScanY++;
            if (movementScanY > bounds.maxY) {
                movementScanY = bounds.minY;
                movementScanZ++;
            }

            if (movementScanZ > bounds.maxZ) {
                movementScanZ = bounds.minZ;
                movementScanX++;
            }

            if (movementScanX <= bounds.maxX) return true;

            movementScanBoundsIndex++;
            if (movementScanBoundsIndex < movementScanBounds.size()) {
                MovementBounds next = movementScanBounds.get(movementScanBoundsIndex);
                movementScanX = next.minX;
                movementScanY = next.minY;
                movementScanZ = next.minZ;
            }
        }

        movementScanComplete = true;
        return false;
    }

    private BlockPos scanGlobalMovementTarget(WorldSchematic worldSchematic) {
        if (mc.player == null || mc.level == null) return null;

        ensureMovementScanInitialized(worldSchematic);
        if (movementScanComplete) return null;

        final int scanBudget = 8192;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int scanned = 0; scanned < scanBudget && movementScanBoundsIndex < movementScanBounds.size(); scanned++) {
            MovementBounds bounds = movementScanBounds.get(movementScanBoundsIndex);

            mutable.set(movementScanX, movementScanY, movementScanZ);
            if (DataManager.getRenderLayerRange().isPositionWithinRange(mutable)
                && Level.isInSpawnableBounds(mutable)) {
                BlockState required = worldSchematic.getBlockState(mutable);
                BlockState existing = mc.level.getBlockState(mutable);

                if (BlockPrint.isValid(mutable, required, existing, this)
                    && canAcquireItemForMovement(BlockPrint.asItem(required))) {
                    // Keep the cursor on this position until it is built or becomes invalid.
                    return new BlockPos(mutable);
                }
            }

            if (!advanceMovementScanCursor()) break;
        }

        return null;
    }

    private void stopMovementNavigation() {
        movementTarget = null;
        movementTargetIsBlock = false;

        if (isBaritoneLoaded()) {
            BaritoneBridge.stop();
        }
    }

    public void onTickPreRotate() {
        WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();
        if (worldSchematic == null) {
            toggle();
            return;
        }

        // Tick slot timers
        for (PrintSlot slot : printSlots) {
            slot.lockTimer = Math.min(slotLockTicks.get(), slot.lockTimer + 1);
            slot.syncTimer = Math.min(antiOverrideTicks.get(), slot.syncTimer + 1);
        }

        // Remove stale cached interacts
        cachedInteracts.removeIf(blockInteract -> {
            return blockInteract.timer++ > 20;
        });

        if (pendingInventorySlot != -1 && pendingHotbarSlot != -1) {
            InvUtils.quickSwap().fromId(pendingHotbarSlot).to(pendingInventorySlot);
            pendingInventorySlot = -1;
            pendingHotbarSlot = -1;
        }

        // Rendering must be active
        if (!Configs.Visuals.ENABLE_RENDERING.getBooleanValue() || !Configs.Visuals.ENABLE_SCHEMATIC_RENDERING.getBooleanValue()) {
            stopMovementNavigation();
            return;
        }

        // Pause the printer and movement while another action requires control.
        if (shouldPause()) {
            stopMovementNavigation();
            return;
        }

        // Clear cached positions every so often
        if (!cachedPositions.isEmpty() && cacheTimer++ >= placeRetryDelay.get()) {
            cachedPositions.clear();
            cacheTimer = 0;
            resetMovementScan();
        }

        // Delay
        if (placeTimer++ < placeDelay.get()) return;

        // Find print locations
        int px = mc.player.getBlockX(), py = mc.player.getBlockY(), pz = mc.player.getBlockZ();
        int radius = (int) Math.ceil(placeRange.get());
        int minY = Math.max(mc.level.getMinY(), py - radius);
        int maxY = Math.min(mc.level.getMaxY(), py + radius);

        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        for (int x = px - radius; x <= px + radius; x++) {
            for (int z = pz - radius; z <= pz + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    int dx = x - px, dy = y - py, dz = z - pz;
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;

                    blockPos.set(x, y, z);
                    BlockState required = worldSchematic.getBlockState(blockPos);
                    BlockState existing = mc.level.getBlockState(blockPos);

                    // Only rendered schematic blocks can be placed
                    if (!DataManager.getRenderLayerRange().isPositionWithinRange(blockPos)) continue;

                    // Must be within world boundaries
                    if (!Level.isInSpawnableBounds(blockPos)) continue;

                    if (BlockPrint.isValid(blockPos, required, existing, this)) {
                        BlockPrint blockPrint = new BlockPrint(new BlockPos(blockPos), required, existing, this);
                        if (blockPrint.canPlace()) {
                            blockPrints.add(blockPrint);
                        }
                        continue;
                    }

                    if (interact.get() && BlockInteract.isValid(required, existing)) {
                        BlockInteract blockInteract = new BlockInteract(new BlockPos(blockPos), this);
                        if (!cachedInteracts.contains(blockInteract) && blockInteract.canInteract()) {
                            blockInteracts.add(blockInteract);
                        }
                    }
                }
            }
        }

        // Snapshot the inventory before movement selection so Survival can avoid targets
        // whose required item cannot currently be selected or moved into the hotbar.
        if (movementMode.get() != MovementMode.Disabled || !blockPrints.isEmpty()) {
            refreshInventorySnapshot();
        }

        if (isMovementAutomationReady()) {
            blockPrints.removeIf(blockPrint -> !canAcquireItemForMovement(blockPrint.asItem()));
        }

        if (blockPrints.isEmpty() && blockInteracts.isEmpty()) {
            updateAutoMovement(worldSchematic);
            return;
        }

        stopMovementNavigation();

        // Sort blocks
        sortBlockPrints();

        // Creative refill is performed once per tick and before any placements are queued.
        // This prevents a later refill from replacing a hotbar slot already referenced by a rotation callback.
        if (isCreativeRefillActive()) {
            for (BlockPrint blockPrint : blockPrints) {
                Item item = blockPrint.asItem();
                if (hasItemInHotbar(item)) continue;

                getCreativeItemSlot(item);
                break;
            }
        }

        // Place blocks!
        int count = 0;
        for (BlockPrint blockPrint : blockPrints) {
            if (count >= blocksPerTick.get()) break;

            Item item = blockPrint.asItem();
            int inventorySlot = getItemSlot(item);
            if (inventorySlot == -1) continue;

            int hotbarSlot = inventorySlot;

            // Move items into hotbar if allowed
            if (inventorySlot > SlotUtils.HOTBAR_END) {
                if (!canInventoryMove()) continue;

                hotbarSlot = getSwapSlot();
                if (hotbarSlot == -1) continue;

                printSlots.get(hotbarSlot).reset();
                printSlots.get(inventorySlot).reset();

                // Simulate swap for our simulated inventory
                ItemStack hotbarStack = mainInventory.get(hotbarSlot);
                ItemStack inventoryStack = mainInventory.get(inventorySlot);
                mainInventory.set(hotbarSlot, inventoryStack);
                mainInventory.set(inventorySlot, hotbarStack);

                // If locked on reset, exit out early.
                if (printSlots.get(hotbarSlot).isLocked()) {
                    pendingInventorySlot = inventorySlot;
                    pendingHotbarSlot = hotbarSlot;
                    blockPrints.clear();
                    blockInteracts.clear();
                    return;
                }

                // Simulate count decrement for our simulated inventory
                inventoryStack.consume(1, mc.player);
            }

            if (!autoSwitch.get() && mc.player.getInventory().getSelectedSlot() != hotbarSlot) continue;

            if (exitSigns.get() && blockPrint.required.getBlock() instanceof SignBlock) {
                lastSignPlaceTime = System.currentTimeMillis();
            }

            printSlots.get(hotbarSlot).syncTimer = 0;

            place(blockPrint, inventorySlot, hotbarSlot);

            cachedPositions.add(blockPrint.blockPos);
            count++;
        }

        // Interact!
        for (BlockInteract blockInteract : blockInteracts) {
            if (count >= blocksPerTick.get()) break;

            interact(blockInteract);

            cachedInteracts.add(blockInteract);
            count++;
        }

        blockPrints.clear();
        blockInteracts.clear();
        placeTimer = 0;
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        // Cancel sign screens when applicable
        if (!exitSigns.get() || !(event.screen instanceof AbstractSignEditScreen)) return;
        if (System.currentTimeMillis() - lastSignPlaceTime > 500) return;

        event.setCancelled(true);
    }

    private boolean shouldPause() {
        if (pauseOnUse.get()) {
            if (Modules.get().get(AutoEat.class).eating || Modules.get().get(AutoGap.class).isEating()) return true;

            if (mc.player.isUsingItem()) return true;
        }

        if (pauseOnMine.get() && mc.gameMode.isDestroying()) return true;

        if (pauseOnKillAura.get() && Modules.get().get(KillAura.class).attacking) return true;

        if (pauseOnLag.get() && TickRate.INSTANCE.getTimeSinceLastTick() >= 1.5f) return true;

        return false;
    }


    /// Inventory

    private class PrintSlot {
        public final int slot;
        public int lockTimer;
        public int syncTimer;

        PrintSlot(int slot) {
            this.slot = slot;
        }

        public void reset() {
            lockTimer = 0;
            syncTimer = 0;
        }

        public void expire() {
            lockTimer = slotLockTicks.get();
            syncTimer = antiOverrideTicks.get();
        }

        public boolean isLocked() {
            return lockTimer < slotLockTicks.get();
        }

        public boolean isCancelSync() {
            return syncTimer < antiOverrideTicks.get();
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event)  {
        // A completed scan may become stale when inventory or schematic blocks change.
        if (movementScanComplete && (event.packet instanceof ClientboundContainerSetSlotPacket
            || event.packet instanceof ClientboundContainerSetContentPacket
            || event.packet instanceof ClientboundBlockUpdatePacket)) {
            resetMovementScan();
        }

        // Cancel incoming packets that cause misplaces
        // Misplaces are caused when the server resyncs the player but the player already changed their slots again
        if (event.packet instanceof ClientboundContainerSetSlotPacket packet) {
            AbstractContainerMenu handler = mc.player.containerMenu;
            if (packet.getContainerId() != handler.containerId) return;

            int packetSlot = packet.getSlot();
            if (packetSlot < 0 || packetSlot >= handler.slots.size()) return;

            Slot screenSlot = handler.getSlot(packetSlot);
            if (screenSlot.container != mc.player.getInventory()) return;

            int slot = screenSlot.getContainerSlot();
            if (slot < 0 || slot >= printSlots.size()) return;

            if (printSlots.get(slot).isCancelSync()) {
                event.cancel();
            }
        } else if (event.packet instanceof ClientboundContainerSetContentPacket packet) {
            AbstractContainerMenu handler = mc.player.containerMenu;
            if (packet.containerId() != handler.containerId) return;

            List<ItemStack> contents = packet.items();
            for (PrintSlot printSlot : printSlots) {
                if (!printSlot.isCancelSync()) continue;

                int slot = handler.findSlot(mc.player.getInventory(), printSlot.slot).orElse(-1);
                if (slot >= 0 && slot < contents.size()) {
                    contents.set(slot, mc.player.getInventory().getItem(printSlot.slot).copy());
                }
            }
        // Remove cached interact if the server confirmed an update
        } else if (event.packet instanceof ClientboundBlockUpdatePacket packet) {
            cachedInteracts.removeIf(blockInteract -> {
                return blockInteract.blockPos.equals(packet.getPos());
            });
        }
    }

    // Find the slot of an item we can print with.
    // When Creative refill is active, only the 9 hotbar slots are considered.
    private int getItemSlot(Item item) {
        int start = isCreativeRefillActive()
            ? (autoSwitch.get() ? SlotUtils.HOTBAR_START : mc.player.getInventory().getSelectedSlot())
            : 0;
        int end = isCreativeRefillActive()
            ? (autoSwitch.get() ? SlotUtils.HOTBAR_END : start)
            : SlotUtils.MAIN_END;

        for (int i = start; i <= end; i++) {
            ItemStack stack = mainInventory.get(i);
            if (item != stack.getItem()) continue;

            // If the slot is locked, do not schedule a placement from it this tick.
            if (isSlotLocked(i)) return -1;
            return i;
        }

        return -1;
    }

    private boolean hasItemInHotbar(Item item) {
        for (int i = SlotUtils.HOTBAR_START; i <= SlotUtils.HOTBAR_END; i++) {
            if (mainInventory.get(i).getItem() == item) return true;
        }

        return false;
    }

    private boolean isCreativeRefillActive() {
        return creativeRefill.get()
            && mc.player != null
            && mc.gameMode != null
            && mc.gameMode.getPlayerMode() == GameType.CREATIVE
            && mc.player.hasInfiniteMaterials();
    }

    // Get a missing item from the currently available Creative inventory tabs.
    private int getCreativeItemSlot(Item item) {
        if (!isCreativeRefillActive() || mc.getConnection() == null) return -1;

        // Creative slot packets are addressed against the player's 45-slot inventory menu.
        // Do not send them while another container menu is open (its slot IDs are different).
        if (mc.player.containerMenu != mc.player.inventoryMenu) return -1;

        var connection = mc.getConnection();
        if (!connection.isFeatureEnabled(item.requiredFeatures())) return -1;

        boolean hasPermissions = mc.player.canUseGameMasterBlocks() && mc.options.operatorItemsTab().get();
        CreativeModeTabs.tryRebuildTabContents(connection.enabledFeatures(), hasPermissions, mc.player.level().registryAccess());

        ItemStack creativeStack = ItemStack.EMPTY;
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            for (ItemStack stack : tab.getDisplayItems()) {
                if (!stack.isEmpty() && stack.getItem() == item) {
                    creativeStack = stack;
                    break;
                }
            }
            if (!creativeStack.isEmpty()) break;
        }
        if (creativeStack.isEmpty()) return -1;

        int inventorySlot = findCreativeHotbarSlot();
        if (inventorySlot == -1) return -1;

        // Inventory indices 0-8 map to InventoryMenu slot IDs 36-44.
        int menuSlot = mc.player.inventoryMenu.findSlot(mc.player.getInventory(), inventorySlot).orElse(-1);
        if (menuSlot < 36 || menuSlot > 44) return -1;

        ItemStack stack = creativeStack.copyWithCount(creativeStack.getMaxStackSize());

        // Mirror the Creative action locally and send the vanilla serverbound Creative slot packet.
        mc.player.getInventory().setItem(inventorySlot, stack.copy());
        mainInventory.set(inventorySlot, stack.copy());

        // Suppress stale inventory sync packets for this slot, as with the normal swap path.
        printSlots.get(inventorySlot).syncTimer = 0;
        mc.gameMode.handleCreativeModeItemAdd(stack, menuSlot);

        return inventorySlot;
    }

    // Prefer an empty hotbar slot; if all 9 are occupied, replace an existing block stack.
    private int findCreativeHotbarSlot() {
        int selectedSlot = mc.player.getInventory().getSelectedSlot();

        if (!autoSwitch.get()) {
            return isCreativeHotbarSlotUsable(selectedSlot) ? selectedSlot : -1;
        }

        for (int i = 0; i < 9; i++) {
            int slot = (selectedSlot + i) % 9;
            if (isCreativeHotbarSlotUsable(slot) && mainInventory.get(slot).isEmpty()) return slot;
        }

        for (int i = 0; i < 9; i++) {
            int slot = (selectedSlot + i) % 9;
            if (isCreativeHotbarSlotUsable(slot) && mainInventory.get(slot).getItem() instanceof BlockItem) return slot;
        }

        return -1;
    }

    private boolean isCreativeHotbarSlotUsable(int slot) {
        if (slot < SlotUtils.HOTBAR_START || slot > SlotUtils.HOTBAR_END || isSlotLocked(slot)) return false;

        ItemStack stack = mainInventory.get(slot);
        return stack.isEmpty() || stack.getItem() instanceof BlockItem;
    }

    // Find a good slot to swap items into
    private int getSwapSlot() {
        // First look for an empty slot in our inventory to use
        for (int i = 0; i < 9; i++) {
            if (isSlotLocked(i)) continue;

            if (mainInventory.get(i).isEmpty()) return i;
        }
        // Otherwise swap with an item thats not valuable
        for (int i = 0; i < 9; ++i) {
            int j = (mc.player.getInventory().getSelectedSlot() + i) % 9;
            if (isSlotLocked(j)) continue;

            if (isSwappable(mainInventory.get(j))) return j;
        }
        return -1;
    }

    // Determine if an item can swap out of the hotbar
    private boolean isSwappable(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof BlockItem) return true;

        if (stack.isEnchanted()) return false;

        if (item == Items.EXPERIENCE_BOTTLE || item == Items.TOTEM_OF_UNDYING) return false;

        if (item.components().get(DataComponents.FOOD) != null) {
            return item == Items.ROTTEN_FLESH || item == Items.SPIDER_EYE;
        }
        return true;
    }

    private boolean isSlotLocked(int slot) {
        if (slot < 0 || slot >= printSlots.size()) return false;

        return printSlots.get(slot).isLocked();
    }

    private boolean canInventoryMove() {
        if (!autoSwitch.get() || !allowInventory.get()) return false;

        if (stationaryMove.get() && isPlayerMoving()) return false;

        return true;
    }

    private boolean isPlayerMoving() {
        // Grim requirements
        Input input = mc.player.input.keyPresses;
        return input.forward() || input.backward() || input.left() || input.right() || input.jump() || mc.player.isSprinting() || mc.player.isShiftKeyDown();
    }


    /// Placement

    // Custom placement that is far better than meteor's standard
    private void place(BlockPrint blockPrint, int inventorySlot, int hotbarSlot) {
        if (rotate.get() || blockPrint.shouldRotatePlace()) {
            RotationManager.rotate(blockPrint.getYaw(), blockPrint.getPitch(), () -> {
                if (blockPrint.isPointValid()) {
                    interactPlace(blockPrint, inventorySlot, hotbarSlot);
                }
            });
        } else {
            interactPlace(blockPrint, inventorySlot, hotbarSlot);
        }
    }

    private void interactPlace(BlockPrint blockPrint, int inventorySlot, int hotbarSlot) {
        // Send our inputs with sneaking injected
        boolean isSneaking = mc.player.isShiftKeyDown();
        Input old = mc.player.input.keyPresses;
        if (blockPrint.sneaking && !isSneaking) {
            Input sneak = new Input(old.forward(), old.backward(), old.left(), old.right(), old.jump(), true, old.sprint());
            mc.player.input.keyPresses = sneak;
            mc.getConnection().send(new ServerboundPlayerInputPacket(sneak));
            mc.player.setShiftKeyDown(true);
        }

        if (autoSwitch.get()) {
            if (inventorySlot > SlotUtils.HOTBAR_END) {
                InvUtils.quickSwap().fromId(hotbarSlot).to(inventorySlot);
            }

            InvUtils.swap(hotbarSlot, swapBack.get());
        }

        // Buckets have their own unique logic
        if (blockPrint.fluid) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            swingHand();
        // Otherwise do standard interaction
        } else if (mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockPrint.hit).consumesAction()) {
            swingHand();
        }

        if (autoSwitch.get() && swapBack.get()) InvUtils.swapBack();

        // Go back to our old inputs
        if (blockPrint.sneaking && !isSneaking) {
            mc.player.input.keyPresses = old;
            mc.getConnection().send(new ServerboundPlayerInputPacket(old));
            mc.player.setShiftKeyDown(isSneaking);
        }

        if (render.get()) {
            placedFades.add(new PlacedFade((float) fadeTime.get(), blockPrint.blockPos));
        }
    }

    private void interact(BlockInteract blockInteract) {
        if (rotate.get()) {
            RotationManager.rotate(blockInteract.getYaw(), blockInteract.getPitch(), () -> {
                if (blockInteract.isPointValid()) {
                    interactBlock(blockInteract);
                }
            });
        } else {
            interactBlock(blockInteract);
        }
    }

    private void interactBlock(BlockInteract blockInteract) {
        // Send our inputs with sneaking removed
        boolean isSneaking = mc.player.isShiftKeyDown();
        Input old = mc.player.input.keyPresses;
        if (isSneaking) {
            Input sneak = new Input(old.forward(), old.backward(), old.left(), old.right(), old.jump(), false, old.sprint());
            mc.player.input.keyPresses = sneak;
            mc.getConnection().send(new ServerboundPlayerInputPacket(sneak));
            mc.player.setShiftKeyDown(false);
        }

        if (mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, blockInteract.hit).consumesAction()) {
            swingHand();
        }

        // Go back to our old inputs
        if (isSneaking) {
            mc.player.input.keyPresses = old;
            mc.getConnection().send(new ServerboundPlayerInputPacket(old));
            mc.player.setShiftKeyDown(isSneaking);
        }
    }

    private void swingHand() {
        if (swing.get()) mc.player.swing(InteractionHand.MAIN_HAND);
        else mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
    }

    /// Rendering

    private static class PlacedFade {
        public float ticks;
        public final BlockPos pos;

        PlacedFade(float ticks, BlockPos pos) {
            this.ticks = ticks;
            this.pos = pos;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        placedFades.removeIf(fade -> {
            fade.ticks -= event.tickDelta;
            if (fade.ticks <= 0) return true;

            double fadeMultiplier = fade.ticks / fadeTime.get();
            Color fadedSide = new Color(sideColor.get()).a((int) (fadeMultiplier * sideColor.get().a));
            Color fadedLine = new Color(lineColor.get()).a((int) (fadeMultiplier * lineColor.get().a));
            event.renderer.box(fade.pos, fadedSide, fadedLine, shapeMode.get(), 0);
            return false;
        });
    }


    /// Sorting

    private void sortBlockPrints() {
        Comparator<BlockPrint> comparator = firstAlgorithm.get().algorithm;

        if (firstAlgorithm.get().applySecondSorting && secondAlgorithm.get() != SortingSecond.None) {
            comparator = secondAlgorithm.get().algorithm.thenComparing(comparator);
        }

        if (autoSwitch.get() && allowInventory.get() && hotbarPriority.get()) {
            comparator = hotbarAlgorithm.thenComparing(comparator);
        }

        blockPrints.sort(comparator);
    }

    private static final class MovementBounds {
        final int minX, minY, minZ;
        final int maxX, maxY, maxZ;

        MovementBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }

        double distanceToSqr(BlockPos pos) {
            int dx = pos.getX() < minX ? minX - pos.getX() : pos.getX() > maxX ? pos.getX() - maxX : 0;
            int dy = pos.getY() < minY ? minY - pos.getY() : pos.getY() > maxY ? pos.getY() - maxY : 0;
            int dz = pos.getZ() < minZ ? minZ - pos.getZ() : pos.getZ() > maxZ ? pos.getZ() - maxZ : 0;
            return (double) dx * dx + (double) dy * dy + (double) dz * dz;
        }
    }

    public enum MovementMode {
        Disabled,
        Survival
    }

    @SuppressWarnings("unused")
    public enum SortAlgorithm {
        None(false, (a, b) -> 0),
        TopDown(true, Comparator.comparingInt(p -> -p.blockPos.getY())),
        DownTop(true, Comparator.comparingInt(p -> p.blockPos.getY())),
        Nearest(false, Comparator.comparingDouble(p -> distanceToPlayer(p.blockPos))),
        Furthest(false, Comparator.comparingDouble(p -> -distanceToPlayer(p.blockPos)));

        final boolean applySecondSorting;
        final Comparator<BlockPrint> algorithm;

        SortAlgorithm(boolean applySecondSorting, Comparator<BlockPrint> algorithm) {
            this.applySecondSorting = applySecondSorting;
            this.algorithm = algorithm;
        }
    }

    @SuppressWarnings("unused")
    public enum SortingSecond {
        None(SortAlgorithm.None.algorithm),
        Nearest(SortAlgorithm.Nearest.algorithm),
        Furthest(SortAlgorithm.Furthest.algorithm);

        final Comparator<BlockPrint> algorithm;

        SortingSecond(Comparator<BlockPrint> algorithm) {
            this.algorithm = algorithm;
        }
    }

    private static double distanceToPlayer(BlockPos pos) {
        if (MeteorClient.mc.player == null) return 0;

        return MeteorClient.mc.player.position().distanceToSqr(Vec3.atCenterOf(pos));
    }

    private static final Comparator<BlockPrint> hotbarAlgorithm = (a, b) -> {
        Item itemA = a.asItem();
        Item itemB = b.asItem();
        boolean aInHotbar = InvUtils.findInHotbar(itemStack -> itemA == itemStack.getItem()).found();
        boolean bInHotbar = InvUtils.findInHotbar(itemStack -> itemB == itemStack.getItem()).found();

        return Boolean.compare(bInHotbar, aInHotbar);
    };
}
