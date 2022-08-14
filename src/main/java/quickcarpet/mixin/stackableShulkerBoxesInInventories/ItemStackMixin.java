package quickcarpet.mixin.stackableShulkerBoxesInInventories;

import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import quickcarpet.settings.Settings;
import quickcarpet.utils.NBTHelper;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "getMaxCount", at = @At("HEAD"), cancellable = true)
    private void quickcarpet$stackableShulkerBoxesInInventories(CallbackInfoReturnable<Integer> cir) {
        if (Settings.stackableShulkerBoxesInInventories && NBTHelper.isEmptyShulkerBox((ItemStack) (Object) this)) {
            NBTHelper.cleanUpShulkerBoxTag((ItemStack) (Object) this);
            cir.setReturnValue(64);
        }
    }
}
