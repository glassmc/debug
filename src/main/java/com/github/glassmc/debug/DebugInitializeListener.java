package com.github.glassmc.debug;

import com.github.glassmc.loader.api.GlassLoader;
import com.github.glassmc.loader.api.Listener;
import com.github.glassmc.loader.api.loader.TransformerOrder;

public class DebugInitializeListener implements Listener {

    @Override
    public void run() {
        GlassLoader.getInstance().registerTransformer(DebugStandardTransformer.class, TransformerOrder.LAST);
        GlassLoader.getInstance().registerTransformer(DebugDumpTransformer.class, TransformerOrder.LAST);

        GlassLoader.getInstance().registerAPI(new Debug());

        GlassLoader.getInstance().runHooks("debug-initialize");
    }

}
