package net.pokereport.luna.client.mixin;

import com.cobblemon.mod.common.api.moves.categories.DamageCategory;
import com.cobblemon.mod.common.api.types.ElementalType;
import name.modid.client.MoveTooltipOverlayState;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = MoveTooltipOverlayState.class, remap = false)
public interface MoveTooltipOverlayStateAccessor {
    @Accessor("queued")
    static boolean isQueued() {
        throw new AssertionError();
    }

    @Accessor("queued")
    static void setQueued(boolean queued) {
        throw new AssertionError();
    }

    @Accessor("lines")
    static List<Text> getLines() {
        throw new AssertionError();
    }

    @Accessor("lines")
    static void setLines(List<Text> lines) {
        throw new AssertionError();
    }

    @Accessor("typeColor")
    static int getTypeColor() {
        throw new AssertionError();
    }

    @Accessor("typeColor")
    static void setTypeColor(int typeColor) {
        throw new AssertionError();
    }

    @Accessor("moveType")
    static ElementalType getMoveType() {
        throw new AssertionError();
    }

    @Accessor("moveType")
    static void setMoveType(ElementalType moveType) {
        throw new AssertionError();
    }

    @Accessor("moveCategory")
    static DamageCategory getMoveCategory() {
        throw new AssertionError();
    }

    @Accessor("moveCategory")
    static void setMoveCategory(DamageCategory moveCategory) {
        throw new AssertionError();
    }

    @Accessor("tooltipX")
    static int getTooltipX() {
        throw new AssertionError();
    }

    @Accessor("tooltipX")
    static void setTooltipX(int tooltipX) {
        throw new AssertionError();
    }

    @Accessor("tooltipY")
    static int getTooltipY() {
        throw new AssertionError();
    }

    @Accessor("tooltipY")
    static void setTooltipY(int tooltipY) {
        throw new AssertionError();
    }

    @Accessor("anchorBottomY")
    static int getAnchorBottomY() {
        throw new AssertionError();
    }

    @Accessor("anchorBottomY")
    static void setAnchorBottomY(int anchorBottomY) {
        throw new AssertionError();
    }

    @Accessor("tileBounds")
    static int[] getTileBounds() {
        throw new AssertionError();
    }

    @Accessor("tileBounds")
    static void setTileBounds(int[] tileBounds) {
        throw new AssertionError();
    }
}
