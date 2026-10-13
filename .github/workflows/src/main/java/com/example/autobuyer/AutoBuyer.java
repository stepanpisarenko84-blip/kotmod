package com.example.autobuyer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Mod("autobuyer")
public class AutoBuyer {

    // ===================== НАСТРОЙКИ =====================
    // --- продажа (/buyer -> вкладка "Ресурсы") ---
    static final String SELL_COMMAND = "/buyer";
    static final String SECTION_NAME = "Ресурсы";
    static final int SECTION_SLOT_FALLBACK = 23;      // запасной слот вкладки, если по названию не нашли

    // --- покупка (NPC "Археолог") ---
    static final String NPC_HOLOGRAM_TEXT = "археолог"; // надпись над NPC (в нижнем регистре)
    static final double NPC_MAX_DIST = 5.0;            // максимальная дистанция до NPC
    static final String MAIN_MENU_TITLE = "Главное меню";
    static final String SHOP_ITEM_NAME = "Покупка товаров";
    static final int SHOP_ITEM_FALLBACK_SLOT = 21;     // запасной слот "Покупка товаров" (по скриншоту)
    static final String SHOP_TITLE = "Купить товары";

    // что покупаем: название предмета в меню, запасной слот, сколько кликов (1 клик = 1 покупка)
    static final String[] BUY_NAMES = { "Алмаз", "Золотой слиток", "Железный слиток" };
    static final int[] BUY_FALLBACK_SLOTS = { 12, 13, 14 };
    static final int BUY_CLICKS_PER_ITEM = 64;         // ПРОВЕРЬ: сколько штук даёт один клик

    // --- общее ---
    static final int CLICK_DELAY_TICKS = 3;            // пауза между кликами (20 тиков = 1 сек)
    static final int OPEN_TIMEOUT_TICKS = 60;          // сколько ждать открытия меню

    static final Set<Item> SELL_ITEMS = new HashSet<>(Arrays.asList(
            Items.DIAMOND, Items.GOLD_INGOT, Items.IRON_INGOT
    ));
    // =====================================================

    enum State {
        OFF,
        SEND_SELL_CMD, WAIT_SECTIONS, WAIT_SELL_MENU, SELLING,
        INTERACT_NPC, WAIT_MAIN_MENU, WAIT_SHOP, BUYING
    }

    private State state = State.OFF;
    private int timer = 0;
    private int buyItemIdx = 0;
    private int buyDone = 0;
    private int totalBought = 0;
    private KeyBinding toggleKey;

    public AutoBuyer() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void clientSetup(FMLClientSetupEvent e) {
        toggleKey = new KeyBinding("Вкл/выкл автоскупщик", GLFW.GLFW_KEY_R, "AutoBuyer");
        ClientRegistry.registerKeyBinding(toggleKey);
    }

    private void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(new StringTextComponent("[AutoBuyer] " + s), false);
    }

    private void setState(State s) { state = s; timer = 0; }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) { state = State.OFF; return; }

        while (toggleKey != null && toggleKey.consumeClick()) {
            if (state == State.OFF) { msg("ВКЛ"); setState(State.SEND_SELL_CMD); }
            else { msg("ВЫКЛ"); state = State.OFF; mc.player.closeContainer(); }
        }
        if (state == State.OFF) return;

        timer++;
        ClientPlayerEntity p = mc.player;   // chat() есть только у ClientPlayerEntity, не у PlayerEntity
        Screen scr = mc.screen;
        ContainerScreen<?> cs = (scr instanceof ContainerScreen) ? (ContainerScreen<?>) scr : null;

        switch (state) {

            // ---------- ПРОДАЖА ----------
            case SEND_SELL_CMD:
                if (timer < 10) return;
                p.chat(SELL_COMMAND);
                setState(State.WAIT_SECTIONS);
                break;

            case WAIT_SECTIONS:
                if (cs != null && title(cs).contains("секцию")) {
                    if (timer < 5) return;
                    Container c = cs.getMenu();
                    int slot = findInMenuByName(c, p, SECTION_NAME, false);
                    if (slot < 0) slot = SECTION_SLOT_FALLBACK;
                    click(mc, c, slot, 0, ClickType.PICKUP);
                    setState(State.WAIT_SELL_MENU);
                } else if (timer > OPEN_TIMEOUT_TICKS) timeout("не открылось меню /buyer");
                break;

            case WAIT_SELL_MENU:
                if (cs != null && !title(cs).contains("секцию")) setState(State.SELLING);
                else if (timer > OPEN_TIMEOUT_TICKS) timeout("не открылась вкладка Ресурсы");
                break;

            case SELLING: {
                if (cs == null) { timeout("меню скупщика закрылось"); return; }
                if (timer % CLICK_DELAY_TICKS != 0) return;
                Container c = cs.getMenu();
                int idx = findSellable(c, p);
                if (idx >= 0) {
                    click(mc, c, idx, 0, ClickType.QUICK_MOVE); // shift + ЛКМ
                } else {
                    p.closeContainer();
                    setState(State.INTERACT_NPC);
                }
                break;
            }

            // ---------- ПОКУПКА ----------
            case INTERACT_NPC: {
                if (timer < 10) return;
                Entity npc = findNpc(mc, p);
                if (npc == null) { timeout("не нашёл NPC Археолог рядом (встань ближе)"); return; }
                mc.gameMode.interact(p, npc, Hand.MAIN_HAND);
                buyItemIdx = 0;
                buyDone = 0;
                totalBought = 0;
                setState(State.WAIT_MAIN_MENU);
                break;
            }

            case WAIT_MAIN_MENU:
                if (cs != null && title(cs).contains(MAIN_MENU_TITLE)) {
                    if (timer < 5) return;
                    Container c = cs.getMenu();
                    int slot = findInMenuByName(c, p, SHOP_ITEM_NAME, false);
                    if (slot < 0) slot = SHOP_ITEM_FALLBACK_SLOT;
                    click(mc, c, slot, 0, ClickType.PICKUP);
                    setState(State.WAIT_SHOP);
                } else if (timer > OPEN_TIMEOUT_TICKS) timeout("не открылось меню NPC");
                break;

            case WAIT_SHOP:
                if (cs != null && title(cs).contains(SHOP_TITLE)) {
                    if (timer < 5) return;
                    setState(State.BUYING);
                } else if (timer > OPEN_TIMEOUT_TICKS) timeout("не открылось меню покупки");
                break;

            case BUYING: {
                if (cs == null) { timeout("меню покупки закрылось"); return; }
                if (timer % CLICK_DELAY_TICKS != 0) return;

                boolean inventoryFull = p.inventory.getFreeSlot() == -1;
                if (buyItemIdx >= BUY_NAMES.length || inventoryFull) {
                    p.closeContainer();
                    if (totalBought == 0) { timeout("ничего не куплено (нет места или денег?)"); return; }
                    setState(State.SEND_SELL_CMD);
                    return;
                }

                Container c = cs.getMenu();
                int slot = findInMenuByName(c, p, BUY_NAMES[buyItemIdx], true);
                if (slot < 0) slot = BUY_FALLBACK_SLOTS[buyItemIdx];
                click(mc, c, slot, 0, ClickType.PICKUP);
                totalBought++;
                buyDone++;
                if (buyDone >= BUY_CLICKS_PER_ITEM) { buyItemIdx++; buyDone = 0; }
                break;
            }

            default:
                break;
        }
    }

    private void timeout(String reason) {
        msg("Остановка: " + reason);
        state = State.OFF;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.screen instanceof ContainerScreen) mc.player.closeContainer();
    }

    private static String title(ContainerScreen<?> cs) {
        return cs.getTitle().getString();
    }

    private static void click(Minecraft mc, Container c, int slot, int button, ClickType type) {
        mc.gameMode.handleInventoryMouseClick(c.containerId, slot, button, type, mc.player);
    }

    // ищем слот в меню (не в инвентаре игрока) по названию предмета
    private static int findInMenuByName(Container c, PlayerEntity p, String name, boolean exact) {
        for (Slot s : c.slots) {
            if (s.container == p.inventory) continue;
            ItemStack st = s.getItem();
            if (st.isEmpty()) continue;
            String n = st.getHoverName().getString();
            if (exact ? n.equals(name) : n.contains(name)) return s.index;
        }
        return -1;
    }

    // ищем в инвентаре игрока то, что можно сдать скупщику
    private static int findSellable(Container c, PlayerEntity p) {
        for (Slot s : c.slots) {
            if (s.container != p.inventory) continue;
            ItemStack st = s.getItem();
            if (!st.isEmpty() && SELL_ITEMS.contains(st.getItem())) return s.index;
        }
        return -1;
    }

    // NPC = житель, ближайший к голограмме "АРХЕОЛОГ"; если голограммы нет, ближайший житель
    private static Entity findNpc(Minecraft mc, PlayerEntity p) {
        if (mc.level == null) return null;
        ArmorStandEntity holo = null;
        double best = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ArmorStandEntity
                    && e.getName().getString().toLowerCase().contains(NPC_HOLOGRAM_TEXT)) {
                double d = p.distanceToSqr(e);
                if (d < best) { best = d; holo = (ArmorStandEntity) e; }
            }
        }
        VillagerEntity result = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof VillagerEntity)) continue;
            if (p.distanceTo(e) > NPC_MAX_DIST) continue;
            double d = (holo != null) ? holo.distanceToSqr(e) : p.distanceToSqr(e);
            if (d < bestD) { bestD = d; result = (VillagerEntity) e; }
        }
        return result;
    }
}
