package org.kkaemok.shimmer.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.kkaemok.shimmer.HdrPanorama;
import org.kkaemok.shimmer.PanoramaConfig;

import java.io.IOException;

/** Draft edits are kept across resizes; only Save writes or triggers a reload. */
public final class ShimmerConfigScreen extends Screen {
    private final Screen parent;
    private boolean enabled;
    private String exposureText;
    private String toneMapping;
    private boolean overlay;
    private String colorSpace;
    private EditBox exposureBox;
    private Button saveButton;
    private Component saveError;
    private int left;
    private int top;

    public ShimmerConfigScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
        setDraft(PanoramaConfig.load());
    }

    private static Component text(String key) {
        return Component.translatable("shimmer.config." + key);
    }

    private void setDraft(PanoramaConfig config) {
        enabled = config.enabled();
        exposureText = Double.toString(config.exposure());
        toneMapping = config.toneMapping();
        overlay = config.preserveVanillaOverlay();
        colorSpace = config.untaggedColorSpace();
        saveError = null;
    }

    @Override
    protected void init() {
        int rowWidth = Math.min(320, width - 24);
        left = (width - rowWidth) / 2;
        top = Math.max(32, (height - 180) / 2);
        addRenderableWidget(CycleButton.onOffBuilder(enabled).create(left, top, rowWidth, 20,
                text("enabled"), (button, value) -> enabled = value));

        exposureBox = new EditBox(font, left + 104, top + 24, rowWidth - 160, 20, text("exposure"));
        exposureBox.setMaxLength(24);
        exposureBox.setValue(exposureText);
        exposureBox.setTooltip(Tooltip.create(text("exposure.tooltip")));
        exposureBox.setResponder(value -> {
            exposureText = value;
            saveError = null;
            updateValidation();
        });
        addRenderableWidget(exposureBox);
        addRenderableWidget(Button.builder(Component.literal("−"), button -> stepExposure(-0.5))
                .bounds(left + rowWidth - 52, top + 24, 24, 20)
                .tooltip(Tooltip.create(text("exposure.decrease"))).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> stepExposure(0.5))
                .bounds(left + rowWidth - 24, top + 24, 24, 20)
                .tooltip(Tooltip.create(text("exposure.increase"))).build());

        addRenderableWidget(CycleButton.<String>builder(value -> text("curve." + value), toneMapping)
                .withValues("ACES", "REINHARD")
                .create(left, top + 48, rowWidth, 20, text("toneMapping"), (button, value) -> toneMapping = value));
        addRenderableWidget(CycleButton.onOffBuilder(overlay)
                .withTooltip(value -> Tooltip.create(text("overlay.tooltip")))
                .create(left, top + 72, rowWidth, 20, text("overlay"), (button, value) -> overlay = value));
        addRenderableWidget(CycleButton.<String>builder(value -> text("colorSpace." + value), colorSpace)
                .withValues("LINEAR_SRGB", "RADIANCE")
                .withTooltip(value -> Tooltip.create(text("colorSpace.tooltip")))
                .create(left, top + 96, rowWidth, 20, text("colorSpace"), (button, value) -> colorSpace = value));

        int buttonWidth = (rowWidth - 8) / 3;
        addRenderableWidget(Button.builder(text("reset"), button -> {
            setDraft(PanoramaConfig.DEFAULT);
            rebuildWidgets();
        }).bounds(left, height - 28, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left + buttonWidth + 4, height - 28, buttonWidth, 20).build());
        saveButton = addRenderableWidget(Button.builder(text("save"), button -> save())
                .bounds(left + 2 * (buttonWidth + 4), height - 28, buttonWidth, 20).build());
        updateValidation();
    }

    private Double exposure() {
        try {
            double value = Double.parseDouble(exposureText);
            return Double.isFinite(value) && value >= -20 && value <= 20 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void updateValidation() {
        boolean valid = exposure() != null;
        exposureBox.setTextColor(valid ? 0xFFFFFFFF : 0xFFFF7777);
        if (saveButton != null) saveButton.active = valid;
    }

    private void stepExposure(double step) {
        Double value = exposure();
        if (value == null) return;
        exposureBox.setValue(Double.toString(Math.clamp(value + step, -20, 20)));
    }

    private void save() {
        Double value = exposure();
        if (value == null) return;
        PanoramaConfig config = new PanoramaConfig(enabled, value, toneMapping, overlay, colorSpace);
        try {
            // Always persist: this also repairs a malformed config that loaded defaults.
            config.save();
        } catch (IOException | RuntimeException e) {
            HdrPanorama.LOGGER.warn("Cannot save Shimmer settings", e);
            saveError = text("saveError");
            return;
        }
        onClose();
        // The active panorama may predate an external JSON edit, so always apply.
        minecraft.reloadResourcePacks();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        graphics.text(font, text("exposure"), left, top + 30, 0xFFFFFFFF);
        Component status = exposure() == null ? text("invalidExposure") : saveError;
        if (status == null) status = text("applyHint");
        graphics.centeredText(font, status, width / 2, top + 122,
                exposure() == null || saveError != null ? 0xFFFF7777 : 0xFFCCCCCC);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
}
