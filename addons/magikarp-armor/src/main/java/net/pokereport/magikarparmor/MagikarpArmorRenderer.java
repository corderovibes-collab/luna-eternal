package net.pokereport.magikarparmor;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

public final class MagikarpArmorRenderer extends GeoArmorRenderer<MagikarpArmorItem> {
    public MagikarpArmorRenderer() { super(new MagikarpArmorModel()); }

    /**
     * GeoArmorRenderer ya copia rotaciones y posiciones. Diosesmon además
     * conserva la escala de cada parte del modelo vanilla; sin esto una skin
     * personalizada puede quedar separada al caminar o al cambiar de modelo.
     */
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
