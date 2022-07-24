package quickcarpet.client;

import fi.dy.masa.malilib.config.IConfigHandler;

import java.util.function.Supplier;

@SuppressWarnings("Convert2MethodRef")
public class ClientSetting<T> {
    private static final boolean HAS_MALILIB;
    static {
        boolean malilib = false;
        try {
            IConfigHandler.class.getName();
            malilib = true;
        } catch (LinkageError ignored) {}
        HAS_MALILIB = malilib;
    }

    public static final ClientSetting<Boolean> SYNC_LOW_TPS = new ClientSetting<>("syncLowTps", true, () -> Configs.Generic.SYNC_LOW_TPS.getBooleanValue());
    public static final ClientSetting<Boolean> SYNC_HIGH_TPS = new ClientSetting<>("syncHighTps", false, () -> Configs.Generic.SYNC_HIGH_TPS.getBooleanValue());
    public static final ClientSetting<Boolean> MOVING_BLOCK_CULLING = new ClientSetting<>("movingBlockCulling", false, () -> Configs.Rendering.MOVING_BLOCK_CULLING.getBooleanValue());
    public static final ClientSetting<Boolean> CREATIVE_NO_CLIP = new ClientSetting<>("creativeNoClip", false, () -> Configs.Generic.CREATIVE_NO_CLIP.getBooleanValue());
    public static final ClientSetting<Boolean> CREATIVE_NO_CLIP_OVERRIDE = new ClientSetting<>("creativeNoClipOverride", false, () -> Configs.Generic.CREATIVE_NO_CLIP_OVERRIDE.getBooleanValue());
    public static final ClientSetting<Boolean> SOUND_ENGINE_FIX = new ClientSetting<>("soundEngineFix", true, () -> Configs.Generic.SOUND_ENGINE_FIX.getBooleanValue());
    public static final ClientSetting<Boolean> REMOVE_NBT_SIZE_LIMIT = new ClientSetting<>("removeNbtSizeLimit", true, () -> Configs.Generic.REMOVE_NBT_SIZE_LIMIT.getBooleanValue());

    public final String id;
    public final T defaultValue;
    private final Supplier<T> malilibGetter;
    private ClientSetting(String id, T defaultValue, Supplier<T> malilibGetter) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.malilibGetter = malilibGetter;
    }

    public T get() {
        if (!HAS_MALILIB) return defaultValue;
        return malilibGetter.get();
    }
}
