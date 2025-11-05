package com.bia10.herblore;

import com.google.inject.Provides;
import com.tonic.Logger;
import com.tonic.Static;
import com.tonic.api.entities.NpcAPI;
import com.tonic.api.threaded.Delays;
import com.tonic.api.widgets.BankAPI;
import com.tonic.api.widgets.WidgetAPI;
import com.tonic.data.ItemEx;
import com.tonic.queries.InventoryQuery;
import com.tonic.queries.NpcQuery;
import com.tonic.util.VitaPlugin;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@PluginDescriptor(
        name = "Serum 207 Maker",
        description = "Automates creating Serum 207 (Mort'ton Serum) widget on widget packet burst.",
        tags = {"herblore", "serum 207", "bank", "utility", "automation", "mort'ton"}
)
public class Serum207MakerPlugin extends VitaPlugin {
    @Inject
    private Client client;
    @Inject
    private ClientToolbar clientToolbar;

    // --- INJECT CONFIG AND PANEL CLASSES ---
    @Inject
    private Serum207MakerConfig config;
    @Inject
    private Serum207MakerSidePanel sidePanel;

    private NavigationButton navButton;

    // --- TICK COUNTING FIELDS ---
    private int startTick = -1;
    private int cycleTicks = 0;

    // --- ITEM CONSTANTS ---
    private static final int TARROMIN_POTION_UNF = ItemID.TARROMINVIAL;
    private static final int ASHES = ItemID.ASHES;
    private static final int ITEMS_PER_CYCLE = 14;

    // --- STATE FIELDS ---
    private SerumMakerState currentState = SerumMakerState.IDLE;
    private int potionsMadeTotal = 0;

    private enum SerumMakerState {
        IDLE,
        MAKING_SERUMS,
        ERROR
    }

    // --- CONFIG PROVIDER ---
    @Provides
    Serum207MakerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(Serum207MakerConfig.class);
    }

    // ------------------ PLUGIN LIFECYCLE ------------------

    @Override
    protected void startUp() {
        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
        navButton = NavigationButton.builder()
                .tooltip("Serum 207 Maker")
                .icon(icon)
                .priority(5)
                .panel(sidePanel)
                .build();
        clientToolbar.addNavigation(navButton);
        Logger.info("Serum 207 Maker initialized.");
    }

    @Override
    protected void shutDown() {
        clientToolbar.removeNavigation(navButton);
        sidePanel.shutdown();
        Logger.info("Serum 207 Maker stopped.");
    }

    // ------------------ MAIN LOOP ------------------

    @Override
    public void loop() {
        if (client.getLocalPlayer() == null || !sidePanel.isRunning()) {
            currentState = SerumMakerState.IDLE;
            sidePanel.updateStatus("Stopped", Color.GRAY);
            startTick = -1;
            return;
        }

        switch (currentState) {
            case IDLE:
                startTick = client.getTickCount();
                currentState = SerumMakerState.MAKING_SERUMS;
                break;
            case MAKING_SERUMS:
                bankAndCraftCycle();
                break;
            case ERROR:
                sidePanel.forceStop();
                sidePanel.updateStatus("ERROR. Check logs.", new Color(255, 50, 50));
                break;
        }
    }

    // ------------------ BANKING AND CRAFTING LOGIC ------------------

    /**
     * Executes the full 3-tick bank and craft cycle.
     * Does not close the bank interface.
     */
    private void bankAndCraftCycle()
    {
        // --- Tick 0: Open bank (Only runs once at the start) ---
        if (!BankAPI.isOpen())
        {
            sidePanel.updateStatus("Opening bank...", Color.ORANGE);
            if (startTick == -1) {
                startTick = client.getTickCount();
            }
            openBankInterface();
            Delays.tick(); // Wait one tick for the bank interface to open
            return;
        }

        // --- This is the start of the 3-tick cycle ---

        // --- Tick 1: Deposit & Withdraw ---
        sidePanel.updateStatus("Banking...", Color.ORANGE);
        BankAPI.depositAll();
        BankAPI.withdraw(TARROMIN_POTION_UNF, ITEMS_PER_CYCLE, false);
        BankAPI.withdraw(ASHES, ITEMS_PER_CYCLE, false);

        // --- Tick 2: Crafting Burst ---
        sidePanel.updateStatus("Crafting (Burst)...", new Color(0, 255, 0));

        List<ItemEx> unfPotions = InventoryQuery.fromInventoryId(InventoryID.INV)
                .withId(TARROMIN_POTION_UNF)
                .sort(Comparator.comparingInt(ItemEx::getSlot))
                .collect();
        List<ItemEx> ashesList = InventoryQuery.fromInventoryId(InventoryID.INV)
                .withId(ASHES)
                .sort(Comparator.comparingInt(ItemEx::getSlot))
                .collect();

        final int craftCountInBatch = Math.min(unfPotions.size(), ashesList.size());

        if (craftCountInBatch > 0) {
            Static.invoke(() -> {
                Logger.info("LowLevelBurst - Firing " + craftCountInBatch + " packets...");
                for (int i = 0; i < craftCountInBatch; i++) {
                    ItemEx ash = ashesList.get(i);
                    ItemEx potion = unfPotions.get(i);
                    WidgetAPI.onWidget(
                            InterfaceID.Inventory.ITEMS,
                            ash.getId(),
                            ash.getSlot(),
                            InterfaceID.Inventory.ITEMS,
                            potion.getId(),
                            potion.getSlot()
                    );
                }
            });

            // --- Measure Cycle Time ---
            cycleTicks = client.getTickCount() - startTick;
            potionsMadeTotal += craftCountInBatch;

            // Send new data to the panel
            sidePanel.setTotalPotionsMade(potionsMadeTotal);
            sidePanel.setLastCycleTicks(cycleTicks);

            // Reset tick measurement for the next cycle
            startTick = client.getTickCount();
        }
        // currentState remains MAKING_SERUMS, so the loop immediately repeats
    }

    // ------------------ UTILITIES (Unchanged) ------------------

    private void openBankInterface()
    {
        NPC banker = getNearestBanker();
        if (banker != null)
        {
            sidePanel.updateStatus("Interacting with Banker...", Color.CYAN);
            NpcAPI.interact(banker, "Bank");
            return;
        }

        sidePanel.updateStatus("Cannot find Banker or Bank object to use.", Color.RED);
        currentState = SerumMakerState.ERROR;
    }

    private NPC getNearestBanker() {
        if (client.getLocalPlayer() == null) return null;
        NpcQuery query = new NpcQuery().withName("Banker");
        Optional<NPC> nearestNpc = Optional.ofNullable(query.nearest(client.getLocalPlayer().getWorldLocation()));
        return nearestNpc.orElse(null);
    }
}