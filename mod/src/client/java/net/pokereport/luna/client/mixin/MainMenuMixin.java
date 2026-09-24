package net.pokereport.luna.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SplashTextRenderer;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableTextWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.pokereport.luna.client.menu.IconLinkButton;
import net.pokereport.luna.client.menu.MenuBackground;
import net.pokereport.luna.client.menu.ServerStatusProbe;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = TitleScreen.class, priority = 5000)
public abstract class MainMenuMixin extends Screen {

    @Shadow
    @Nullable
    private SplashTextRenderer splashText;

    @Unique
    private static final String SERVER_ADDRESS = "play.pokereport.online:25565";

    @Unique
    private static final Identifier LOGO_TEXTURE = Identifier.of("lunaeternal", "textures/gui/title/title_network.png");
    @Unique
    private static final int LOGO_TEX_W = 2048;
    @Unique
    private static final int LOGO_TEX_H = 512;
    @Unique
    private static final int LOGO_DRAW_W = 256;
    @Unique
    private static final int LOGO_DRAW_H = 64;
    @Unique
    private static final int LOGO_TOP_MARGIN = 24;

    @Unique
    private static final Identifier ICON_DISCORD = Identifier.of("lunaeternal", "textures/gui/title/social/discord.png");
    @Unique
    private static final Identifier ICON_TIENDA = Identifier.of("lunaeternal", "textures/gui/title/social/tienda.png");
    @Unique
    private static final Identifier ICON_WIKI = Identifier.of("lunaeternal", "textures/gui/title/social/wiki.png");

    @Unique
    private static final String URL_DISCORD = "https://discord.gg/pokereport";
    @Unique
    private static final String URL_TIENDA = net.pokereport.luna.client.Enlaces.TIENDA;
    @Unique
    private static final String URL_WIKI = "https://wiki.pokereport.net/";

    protected MainMenuMixin(Text title) {
        super(title);
    }

    @Inject(method = "renderPanoramaBackground", at = @At("HEAD"), cancellable = true)
    private void pokereport$replacePanorama(DrawContext context, float delta, CallbackInfo ci) {
        MenuBackground.render(context, this.width, this.height);
        ci.cancel();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void pokereport$purgeTopLeftWidgets(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        List<Element> toRemove = new ArrayList<>();
        for (Element element : this.children()) {
            if (element instanceof ClickableWidget widget) {
                if (widget.getX() < 60 && widget.getY() < 60) {
                    toRemove.add(widget);
                    continue;
                }
            }
            // Flashback adds its replay button relative to the vanilla menu.
            // Our compact PokéReport layout moves the vanilla rows afterwards,
            // which used to leave Flashback directly on top of Accessibility:
            // its icon was visible, but Accessibility received every click.
            // Pin it beside the main Network button on every render because
            // Flashback may recalculate its own position during the first tick.
            if (element instanceof ButtonWidget button
                    && button.getMessage().getContent() instanceof TranslatableTextContent translatable
                    && "flashback.open_replays".equals(translatable.getKey())) {
                int mainRowY = this.height / 4 + 48 + 20 + 4;
                button.setPosition(this.width / 2 + 104, mainRowY);
            }
        }
        if (!toRemove.isEmpty()) {
            toRemove.forEach(this::remove);
        }
    }

    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/LogoDrawer;draw(Lnet/minecraft/client/gui/DrawContext;IF)V"
            )
    )
    private void pokereport$replaceLogo(LogoDrawer instance, DrawContext context, int screenWidth, float alpha, Operation<Void> original) {
        int x = (this.width / 2) - (LOGO_DRAW_W / 2);
        RenderSystem.enableBlend();
        context.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        context.drawTexture(LOGO_TEXTURE, x, LOGO_TOP_MARGIN, LOGO_DRAW_W, LOGO_DRAW_H, 0.0F, 0.0F, LOGO_TEX_W, LOGO_TEX_H, LOGO_TEX_W, LOGO_TEX_H);
        context.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void pokereport$customizeMainMenu(CallbackInfo ci) {
        this.splashText = new SplashTextRenderer("Luna Eternal");

        List<Element> toRemove = new ArrayList<>();
        ButtonWidget optionsButton = null;

        for (Element element : this.children()) {
            if (element instanceof ClickableWidget widget) {
                // Eliminar cualquier botón residual en la esquina superior izquierda (ej. Jukebox de MusicNotification)
                if (widget.getX() < 60 && widget.getY() < 60) {
                    toRemove.add(widget);
                    continue;
                }
            }
            if (element instanceof ButtonWidget button) {
                Text message = button.getMessage();
                if (message.getContent() instanceof TranslatableTextContent translatable) {
                    String key = translatable.getKey();
                    if ("menu.singleplayer".equals(key) || "menu.multiplayer".equals(key) || "menu.online".equals(key)) {
                        toRemove.add(button);
                    } else if ("menu.options".equals(key)) {
                        optionsButton = button;
                    }
                }
            }
        }

        toRemove.forEach(this::remove);

        int mainRowY = this.height / 4 + 48 + 20 + 4;
        int optionsRowY = mainRowY + 20 + 4;

        if (optionsButton != null) {
            int currentOptionsY = optionsButton.getY();
            int deltaY = optionsRowY - currentOptionsY;

            for (Element element : this.children()) {
                if (element instanceof ClickableWidget widget) {
                    if (Math.abs(widget.getY() - currentOptionsY) <= 4) {
                        widget.setY(widget.getY() + deltaY);
                    }
                }
            }
        }

        ButtonWidget playButton = ButtonWidget.builder(Text.literal("POKEREPORT NETWORK"), (button) -> {
            ServerInfo serverInfo = new ServerInfo(I18n.translate("selectServer.defaultName"), SERVER_ADDRESS, ServerInfo.ServerType.OTHER);
            serverInfo.setResourcePackPolicy(ServerInfo.ResourcePackPolicy.PROMPT);
            ConnectScreen.connect(this, this.client, ServerAddress.parse(SERVER_ADDRESS), serverInfo, false, null);
        }).dimensions(this.width / 2 - 100, mainRowY, 200, 20).build();

        this.addDrawableChild(playButton);

        this.pokereport$addSocialButtons(optionsRowY);
        this.pokereport$addGreetingOverlay();
        this.pokereport$addServerStatusOverlay();
        this.pokereport$pinCopyrightToBottom();
    }

    @Unique
    private void pokereport$pinCopyrightToBottom() {
        for (Element element : this.children()) {
            if (element instanceof PressableTextWidget textWidget) {
                textWidget.setY(this.height - 10);
            }
        }
    }

    @Unique
    private void pokereport$addSocialButtons(int optionsRowY) {
        int y = optionsRowY + 20 + 12;
        int count = 3;
        int buttonSize = 24;
        int gap = 8;
        int totalWidth = count * buttonSize + (count - 1) * gap;
        int startX = (this.width - totalWidth) / 2;
        int step = buttonSize + gap;

        this.pokereport$addSocialButton(startX, y, ICON_DISCORD, URL_DISCORD, "gui.lunaeternal.menu.social.discord");
        this.pokereport$addSocialButton(startX + step, y, ICON_TIENDA, URL_TIENDA, "gui.lunaeternal.menu.social.tienda");
        this.pokereport$addSocialButton(startX + step * 2, y, ICON_WIKI, URL_WIKI, "gui.lunaeternal.menu.social.wiki");
    }

    @Unique
    private void pokereport$addSocialButton(int x, int y, Identifier icon, String url, String tooltipKey) {
        Text tooltip = Text.translatable(tooltipKey);
        IconLinkButton button = new IconLinkButton(x, y, 24, icon, 16, tooltip, () -> {
            if (this.client != null) {
                this.client.setScreen(new ConfirmLinkScreen(confirmed -> {
                    if (confirmed) {
                        Util.getOperatingSystem().open(url);
                    }
                    this.client.setScreen(this);
                }, url, true));
            }
        });
        button.setTooltip(Tooltip.of(tooltip));
        this.addDrawableChild(button);
    }

    @Unique
    private void pokereport$addGreetingOverlay() {
        this.addDrawable((context, mouseX, mouseY, delta) -> {
            if (this.client == null || this.client.getSession() == null) {
                return;
            }
            String username = this.client.getSession().getUsername();
            SkinTextures skin = this.client.getSkinProvider().getSkinTextures(this.client.getGameProfile());
            int x = 6;
            int y = 6;
            int headSize = 16;
            RenderSystem.enableBlend();
            PlayerSkinDrawer.draw(context, skin, x, y, headSize);
            Text greeting = Text.translatable("gui.lunaeternal.menu.greeting", username);
            int textX = x + headSize + 4;
            int textY = y + (headSize - this.textRenderer.fontHeight) / 2;
            context.drawTextWithShadow(this.textRenderer, greeting, textX, textY, 0xFFFFFFFF);
        });
    }

    @Unique
    private void pokereport$addServerStatusOverlay() {
        this.addDrawable((context, mouseX, mouseY, delta) -> {
            ServerStatusProbe probe = ServerStatusProbe.getInstance();
            long now = Util.getMeasuringTimeMs();
            probe.tick(SERVER_ADDRESS, now);

            ServerStatusProbe.Status status = probe.status();
            int dotColor;
            MutableText text;

            switch (status) {
                case ONLINE_GOOD -> {
                    dotColor = 0xFF55FF55;
                    text = Text.translatable("gui.lunaeternal.menu.status.online");
                    if (probe.playersMax() > 0) {
                        text.append(Text.literal(probe.playersOnline() + "/" + probe.playersMax()));
                    }
                }
                case ONLINE_OK -> {
                    dotColor = 0xFFFFFF55;
                    text = Text.translatable("gui.lunaeternal.menu.status.online");
                    if (probe.playersMax() > 0) {
                        text.append(Text.literal(probe.playersOnline() + "/" + probe.playersMax()));
                    }
                }
                case CONNECTING -> {
                    dotColor = 0xFFAAAAAA;
                    text = Text.translatable("gui.lunaeternal.menu.status.connecting");
                }
                case OFFLINE -> {
                    dotColor = 0xFFFF5555;
                    text = Text.translatable("gui.lunaeternal.menu.status.offline");
                }
                default -> {
                    dotColor = 0xFFAAAAAA;
                    text = Text.translatable("gui.lunaeternal.menu.status.connecting");
                }
            }

            int textWidth = this.textRenderer.getWidth(text);
            int totalWidth = 5 + 4 + textWidth;
            int x = this.width - 6 - totalWidth;
            int y = 6;
            int dotY = y + (this.textRenderer.fontHeight - 5) / 2;

            context.fill(x - 1, dotY - 1, x + 5 + 1, dotY + 5 + 1, 0xFF000000);
            context.fill(x, dotY, x + 5, dotY + 5, dotColor);
            context.drawTextWithShadow(this.textRenderer, text, x + 5 + 4, y, 0xFFFFFFFF);
        });
    }
}
