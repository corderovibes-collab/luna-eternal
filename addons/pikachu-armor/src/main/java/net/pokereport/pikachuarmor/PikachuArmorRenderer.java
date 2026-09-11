package net.pokereport.pikachuarmor;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

public final class PikachuArmorRenderer extends GeoArmorRenderer<PikachuArmorItem> {
    public PikachuArmorRenderer() { super(new PikachuArmorModel()); }

    @Override
    protected void applyBaseTransformations(BipedEntityModel<?> baseModel) {
        super.applyBaseTransformations(baseModel);
        matchScale(this.head, baseModel.head);
        matchScale(this.body, baseModel.body);
        matchScale(this.rightArm, baseModel.rightArm);
        matchScale(this.leftArm, baseModel.leftArm);
        matchScale(this.rightLeg, baseModel.rightLeg);
        matchScale(this.leftLeg, baseModel.leftLeg);
        matchScale(this.rightBoot, baseModel.rightLeg);
        matchScale(this.leftBoot, baseModel.leftLeg);
    }

    private static void matchScale(@Nullable GeoBone bone, ModelPart part) {
        if (bone != null) {
            bone.updateScale(part.xScale, part.yScale, part.zScale);
        }
    }
}

