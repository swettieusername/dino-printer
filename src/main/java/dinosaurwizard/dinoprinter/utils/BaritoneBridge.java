package dinosaurwizard.dinoprinter.utils;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.Settings;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.process.ICustomGoalProcess;
import dinosaurwizard.dinoprinter.DinoPrinterAddon;
import net.minecraft.core.BlockPos;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Optional Baritone API adapter.
 *
 * Dino Printer only takes ownership of Baritone's custom-goal process when it is idle.
 * It never cancels a goal that was started by the user or another Baritone process.
 */
public final class BaritoneBridge {
    private static IBaritone owner;
    private static GoalNear ownedGoal;
    private static boolean settingsCaptured;
    private static boolean previousAllowBreak;
    private static boolean previousAllowPlace;

    private BaritoneBridge() {
    }

    private static void disableBaritoneBuilding() {
        Settings settings = BaritoneAPI.getSettings();
        if (!settingsCaptured) {
            previousAllowBreak = settings.allowBreak.value;
            previousAllowPlace = settings.allowPlace.value;
            settingsCaptured = true;
        }

        // Baritone is navigation-only here. Dino Printer remains responsible for placement.
        settings.allowBreak.value = false;
        settings.allowPlace.value = false;
    }

    private static void restoreBaritoneBuilding() {
        if (!settingsCaptured) return;

        try {
            Settings settings = BaritoneAPI.getSettings();
            if (!settings.allowBreak.value) settings.allowBreak.value = previousAllowBreak;
            if (!settings.allowPlace.value) settings.allowPlace.value = previousAllowPlace;
        } catch (LinkageError | RuntimeException e) {
            DinoPrinterAddon.LOG.warn("Failed to restore Baritone's break/place settings.", e);
        } finally {
            settingsCaptured = false;
        }
    }

    public static boolean pathTo(BlockPos target, int range) {
        if (target == null || mc.player == null || mc.level == null || mc.getConnection() == null) return false;

        try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone == null) return false;

            ICustomGoalProcess process = baritone.getCustomGoalProcess();
            Goal currentGoal = process.getGoal();

            // Another process or the user changed Baritone's goal. Relinquish control.
            if (owner != null && owner != baritone) {
                owner = null;
                ownedGoal = null;
                restoreBaritoneBuilding();
            }

            if (ownedGoal != null && (currentGoal == null || !ownedGoal.equals(currentGoal))) {
                owner = null;
                ownedGoal = null;
                restoreBaritoneBuilding();
                return false;
            }

            GoalNear nextGoal = new GoalNear(target, Math.max(1, range));

            if (ownedGoal == null) {
                // Do not overwrite an existing user task or a previously selected custom goal.
                if (currentGoal != null || process.isActive() || baritone.getPathingBehavior().isPathing()) return false;

                disableBaritoneBuilding();
                process.setGoalAndPath(nextGoal);
                owner = baritone;
                ownedGoal = nextGoal;
                return true;
            }

            if (!ownedGoal.equals(nextGoal)) {
                if (baritone.getPathingControlManager().mostRecentInControl().orElse(null) != process) {
                    owner = null;
                    ownedGoal = null;
                    restoreBaritoneBuilding();
                    return false;
                }

                baritone.getPathingBehavior().cancelEverything();
                process.setGoalAndPath(nextGoal);
                ownedGoal = nextGoal;
            } else if (!baritone.getPathingBehavior().isPathing()
                && !nextGoal.isInGoal(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ())) {
                process.path();
            }

            return true;
        } catch (LinkageError | RuntimeException e) {
            DinoPrinterAddon.LOG.warn("Failed to communicate with the installed Baritone API.", e);
            owner = null;
            ownedGoal = null;
            restoreBaritoneBuilding();
            return false;
        }
    }

    public static void stop() {
        IBaritone baritone = owner;
        GoalNear goal = ownedGoal;
        owner = null;
        ownedGoal = null;

        try {
            if (baritone != null && goal != null && mc.player != null && mc.level != null && mc.getConnection() != null) {
                ICustomGoalProcess process = baritone.getCustomGoalProcess();
                if (goal.equals(process.getGoal())
                    && baritone.getPathingControlManager().mostRecentInControl().orElse(null) == process) {
                    baritone.getPathingBehavior().cancelEverything();
                }
            }
        } catch (LinkageError | RuntimeException e) {
            DinoPrinterAddon.LOG.warn("Failed to stop Dino Printer's Baritone path.", e);
        } finally {
            restoreBaritoneBuilding();
        }
    }
}
