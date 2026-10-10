package com.exemplo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Meu Client - Forge 1.8.9 (arquivo único)
 *
 * Módulos:
 *  - Sprint automático
 *  - HUD (FPS + coordenadas)
 *  - Brilho máximo (fullbright)
 *  - Zoom (segure a tecla C)
 *
 * Menu: Shift direito (abre e fecha)
 */
@Mod(modid = "meuclient", name = "Meu Client", version = "1.0", clientSideOnly = true)
public class MeuClient {

    // ===== Estado dos módulos =====
    public static boolean autoSprint = true;
    public static boolean hud = true;
    public static boolean fullbright = false;

    private static float gammaSalvo = -1f;

    // ===== Teclas =====
    public static KeyBinding menuKey;
    public static KeyBinding zoomKey;

    @EventHandler
    public void init(FMLInitializationEvent event) {
        menuKey = new KeyBinding("Abrir menu", Keyboard.KEY_RSHIFT, "Meu Client");
        zoomKey = new KeyBinding("Zoom", Keyboard.KEY_C, "Meu Client");
        ClientRegistry.registerKeyBinding(menuKey);
        ClientRegistry.registerKeyBinding(zoomKey);
        MinecraftForge.EVENT_BUS.register(this);
    }

    // ===== Abrir menu =====
    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (menuKey.isPressed()) {
            Minecraft.getMinecraft().displayGuiScreen(new MenuGui());
        }
    }

    // ===== Lógica por tick =====
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings == null) return;

        // Sprint automático
        if (autoSprint
                && mc.thePlayer.movementInput.moveForward > 0
                && !mc.thePlayer.isSneaking()
                && !mc.thePlayer.isCollidedHorizontally
                && mc.thePlayer.getFoodStats().getFoodLevel() > 6) {
            mc.thePlayer.setSprinting(true);
        }

        // Brilho máximo
        if (fullbright) {
            if (gammaSalvo < 0) gammaSalvo = mc.gameSettings.gammaSetting;
            mc.gameSettings.gammaSetting = 100f;
        } else if (gammaSalvo >= 0) {
            mc.gameSettings.gammaSetting = gammaSalvo;
            gammaSalvo = -1f;
        }
    }

    // ===== Zoom =====
    @SubscribeEvent
    public void onFov(FOVUpdateEvent event) {
        if (zoomKey != null && zoomKey.isKeyDown()) {
            event.newfov = event.newfov * 0.25f;
        }
    }

    // ===== HUD =====
    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        if (!hud) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings.showDebugInfo) return;

        int y = 4;
        mc.fontRendererObj.drawStringWithShadow("Meu Client", 4, y, 0x55FFFF);
        y += 11;
        mc.fontRendererObj.drawStringWithShadow("FPS: " + Minecraft.getDebugFPS(), 4, y, 0xFFFFFF);
        y += 11;
        mc.fontRendererObj.drawStringWithShadow(
                String.format("XYZ: %.1f / %.1f / %.1f",
                        mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ),
                4, y, 0xFFFFFF);
    }

    // ===== Menu (classe interna, mesmo arquivo) =====
    public static class MenuGui extends GuiScreen {

        @Override
        public void initGui() {
            int cx = width / 2;
            int cy = height / 2;
            buttonList.clear();
            buttonList.add(new GuiButton(0, cx - 100, cy - 40, 200, 20, ""));
            buttonList.add(new GuiButton(1, cx - 100, cy - 16, 200, 20, ""));
            buttonList.add(new GuiButton(2, cx - 100, cy + 8, 200, 20, ""));
            buttonList.add(new GuiButton(3, cx - 100, cy + 40, 200, 20, "Fechar"));
            atualizarTextos();
        }

        private String rotulo(String nome, boolean on) {
            return nome + ": " + (on ? "ON" : "OFF");
        }

        private void atualizarTextos() {
            for (GuiButton b : buttonList) {
                if (b.id == 0) b.displayString = rotulo("Sprint automatico", autoSprint);
                if (b.id == 1) b.displayString = rotulo("HUD (FPS/XYZ)", hud);
                if (b.id == 2) b.displayString = rotulo("Brilho maximo", fullbright);
            }
        }

        @Override
        protected void actionPerformed(GuiButton button) throws IOException {
            switch (button.id) {
                case 0: autoSprint = !autoSprint; break;
                case 1: hud = !hud; break;
                case 2: fullbright = !fullbright; break;
                case 3: mc.displayGuiScreen(null); return;
            }
            atualizarTextos();
        }

        @Override
        protected void keyTyped(char c, int keyCode) throws IOException {
            if (keyCode == Keyboard.KEY_RSHIFT) {
                mc.displayGuiScreen(null);
                return;
            }
            super.keyTyped(c, keyCode);
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            drawCenteredString(fontRendererObj, "Meu Client", width / 2, height / 2 - 70, 0x55FFFF);
            drawCenteredString(fontRendererObj, "Zoom: segure a tecla C", width / 2, height / 2 + 70, 0xAAAAAA);
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }
}
