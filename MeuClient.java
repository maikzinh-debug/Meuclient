package com.exemplo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.shader.Framebuffer;
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
import org.lwjgl.opengl.GL11;

import java.io.IOException;

/**
 * Meu Client - Forge 1.8.9 (arquivo unico)
 *
 * Modulos:
 *  - Sprint automatico
 *  - HUD (FPS + coordenadas)
 *  - Brilho maximo (fullbright)
 *  - Zoom (segure a tecla C)
 *  - Motion Blur (intensidade ajustavel no menu)
 *  - Hurt Cam (OFF = camera treme ao tomar dano / ON = sem tremor)
 *
 * Menu: Shift direito (abre e fecha)
 */
@Mod(modid = "meuclient", name = "Meu Client", version = "1.2", clientSideOnly = true)
public class MeuClient {

    // ===== Estado dos modulos =====
    public static boolean autoSprint = true;
    public static boolean hud = true;
    public static boolean fullbright = false;

    public static boolean motionBlur = false;
    public static float blurValor = 0.5f;   // slider de 0.0 a 1.0

    // Hurt Cam: OFF (false) = treme ao tomar dano | ON (true) = nao treme
    public static boolean hurtCam = false;

    private static float gammaSalvo = -1f;

    // ===== Motion blur (estado interno) =====
    private static Framebuffer blurFbo = null;   // metodo 1 (framebuffer)
    private static int blurTex = -1;             // metodo 2 (copia de textura)
    private static int blurW = 0;
    private static int blurH = 0;
    private static boolean blurPronto = false;
    private static String blurModo = "-";

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

    // ===== Logica por tick =====
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.gameSettings == null) return;

        // Sprint automatico
        if (autoSprint
                && mc.thePlayer.movementInput.moveForward > 0
                && !mc.thePlayer.isSneaking()
                && !mc.thePlayer.isCollidedHorizontally
                && mc.thePlayer.getFoodStats().getFoodLevel() > 6) {
            mc.thePlayer.setSprinting(true);
        }

        // Brilho maximo
        if (fullbright) {
            if (gammaSalvo < 0) gammaSalvo = mc.gameSettings.gammaSetting;
            mc.gameSettings.gammaSetting = 100f;
        } else if (gammaSalvo >= 0) {
            mc.gameSettings.gammaSetting = gammaSalvo;
            gammaSalvo = -1f;
        }
    }

    // ===== Hurt Cam (antes de cada frame) =====
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (hurtCam && mc.thePlayer != null && mc.thePlayer.getHealth() > 0) {
            // Sem hurtTime a camera nao treme ao tomar dano
            mc.thePlayer.hurtTime = 0;
        }
    }

    // ===== Zoom =====
    @SubscribeEvent
    public void onFov(FOVUpdateEvent event) {
        if (zoomKey != null && zoomKey.isKeyDown()) {
            event.newfov = event.newfov * 0.25f;
        }
    }

    // ===== Motion Blur (antes do HUD) =====
    @SubscribeEvent
    public void onPreRender(RenderGameOverlayEvent.Pre event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!motionBlur || mc.theWorld == null || mc.thePlayer == null) {
            blurPronto = false;
            return;
        }
        aplicarMotionBlur(mc);
    }

    private static void desenharQuad(int sw, int sh) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX);
        wr.pos(0, sh, 0).tex(0, 0).endVertex();
        wr.pos(sw, sh, 0).tex(1, 0).endVertex();
        wr.pos(sw, 0, 0).tex(1, 1).endVertex();
        wr.pos(0, 0, 0).tex(0, 1).endVertex();
        tess.draw();
    }

    private static void aplicarMotionBlur(Minecraft mc) {
        int w = mc.displayWidth;
        int h = mc.displayHeight;
        ScaledResolution sr = new ScaledResolution(mc);
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();
        float alpha = blurValor * 0.9f;

        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableAlpha();
        GlStateManager.enableTexture2D();

        if (OpenGlHelper.isFramebufferEnabled() && mc.getFramebuffer() != null) {
            blurModo = "FBO";
            blurViaFbo(mc, w, h, sw, sh, alpha);
        } else {
            blurModo = "COPIA";
            blurViaCopia(w, h, sw, sh, alpha);
        }

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
    }

    // Metodo 1: usa um framebuffer extra do proprio Minecraft
    private static void blurViaFbo(Minecraft mc, int w, int h, int sw, int sh, float alpha) {
        Framebuffer principal = mc.getFramebuffer();

        if (blurFbo == null || blurFbo.framebufferWidth != w || blurFbo.framebufferHeight != h) {
            if (blurFbo != null) blurFbo.deleteFramebuffer();
            blurFbo = new Framebuffer(w, h, false);
            blurFbo.setFramebufferFilter(GL11.GL_LINEAR);
            blurPronto = false;
        }

        // 1) frame anterior por cima da cena atual, com transparencia
        if (blurPronto) {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(1f, 1f, 1f, alpha);
            GlStateManager.bindTexture(blurFbo.framebufferTexture);
            desenharQuad(sw, sh);
        }

        // 2) guarda o resultado (cena misturada) para o proximo frame
        blurFbo.bindFramebuffer(true);
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.bindTexture(principal.framebufferTexture);
        desenharQuad(sw, sh);

        // deixa o canal alpha em 1 para a mistura do proximo frame
        GlStateManager.colorMask(false, false, false, true);
        GlStateManager.clearColor(0f, 0f, 0f, 1f);
        GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT);
        GlStateManager.colorMask(true, true, true, true);

        principal.bindFramebuffer(true);
        blurPronto = true;
    }

    // Metodo 2 (reserva): copia a tela para uma textura
    private static void blurViaCopia(int w, int h, int sw, int sh, float alpha) {
        if (blurTex == -1) blurTex = GL11.glGenTextures();

        if (!blurPronto || w != blurW || h != blurH) {
            GlStateManager.bindTexture(blurTex);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, 0, 0, w, h, 0);
            blurW = w;
            blurH = h;
            blurPronto = true;
            return;
        }

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1f, 1f, 1f, alpha);
        GlStateManager.bindTexture(blurTex);
        desenharQuad(sw, sh);

        GlStateManager.bindTexture(blurTex);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
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

        // Linha de diagnostico do Motion Blur (so aparece quando ele esta ON)
        if (motionBlur) {
            y += 11;
            mc.fontRendererObj.drawStringWithShadow(
                    "Blur: " + blurModo + (blurPronto ? " ativo" : " aguardando"),
                    4, y, 0xFFFF55);
        }
    }

    // ===== Menu (classe interna, mesmo arquivo) =====
    public static class MenuGui extends GuiScreen {

        @Override
        public void initGui() {
            int cx = width / 2;
            int y0 = height / 2 - 55;
            int esq = cx - 155;
            int dir = cx + 5;
            int l = 150;

            buttonList.clear();
            buttonList.add(new GuiButton(0, esq, y0, l, 20, ""));
            buttonList.add(new GuiButton(1, esq, y0 + 24, l, 20, ""));
            buttonList.add(new GuiButton(2, esq, y0 + 48, l, 20, ""));
            buttonList.add(new GuiButton(3, dir, y0, l, 20, ""));
            buttonList.add(new Slider(4, dir, y0 + 24, l, 20));
            buttonList.add(new GuiButton(5, dir, y0 + 48, l, 20, ""));
            buttonList.add(new GuiButton(6, cx - 100, y0 + 78, 200, 20, "Fechar"));
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
                if (b.id == 3) b.displayString = rotulo("Motion Blur", motionBlur);
                if (b.id == 5) b.displayString = rotulo("Hurt Cam", hurtCam);
            }
        }

        @Override
        protected void actionPerformed(GuiButton button) throws IOException {
            switch (button.id) {
                case 0: autoSprint = !autoSprint; break;
                case 1: hud = !hud; break;
                case 2: fullbright = !fullbright; break;
                case 3: motionBlur = !motionBlur; break;
                case 5: hurtCam = !hurtCam; break;
                case 6: mc.displayGuiScreen(null); return;
                default: break;
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
            int y0 = height / 2 - 55;
            drawCenteredString(fontRendererObj, "Meu Client", width / 2, y0 - 18, 0x55FFFF);
            drawCenteredString(fontRendererObj, "Zoom: segure a tecla C", width / 2, y0 + 104, 0xAAAAAA);
            drawCenteredString(fontRendererObj, "Hurt Cam ON = camera sem tremor ao tomar dano", width / 2, y0 + 115, 0xAAAAAA);
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }

    // ===== Slider simples (intensidade do Motion Blur) =====
    public static class Slider extends GuiButton {
        private boolean arrastando = false;

        public Slider(int id, int x, int y, int largura, int altura) {
            super(id, x, y, largura, altura, "");
            atualizarTexto();
        }

        private void atualizarTexto() {
            this.displayString = "Intensidade: " + (int) (blurValor * 100) + "%";
        }

        private void definirValor(int mouseX) {
            float v = (float) (mouseX - (this.xPosition + 4)) / (float) (this.width - 8);
            if (v < 0f) v = 0f;
            if (v > 1f) v = 1f;
            blurValor = v;
            atualizarTexto();
        }

        @Override
        protected int getHoverState(boolean mouseOver) {
            return 0;
        }

        @Override
        protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
            if (!this.visible) return;
            if (arrastando) {
                definirValor(mouseX);
            }
            mc.getTextureManager().bindTexture(buttonTextures);
            GlStateManager.color(1f, 1f, 1f, 1f);
            int pos = this.xPosition + (int) (blurValor * (float) (this.width - 8));
            this.drawTexturedModalRect(pos, this.yPosition, 0, 66, 4, 20);
            this.drawTexturedModalRect(pos + 4, this.yPosition, 196, 66, 4, 20);
        }

        @Override
        public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
            if (super.mousePressed(mc, mouseX, mouseY)) {
                definirValor(mouseX);
                arrastando = true;
                return true;
            }
            return false;
        }

        @Override
        public void mouseReleased(int mouseX, int mouseY) {
            arrastando = false;
        }
    }
}
