package com.github.glassmc.debug.mixin;

import com.github.glassmc.debug.Debug;
import com.github.glassmc.loader.api.GlassLoader;
import com.github.glassmc.loader.api.Listener;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class DebugMixinInitializeListener implements Listener {

    @Override
    public void run() {
        Debug debug = GlassLoader.getInstance().getAPI(Debug.class);

        debug.addRemapper(classNode -> {
            if (classNode.name.startsWith("org/spongepowered/asm/mixin/")) return;

            for (MethodNode methodNode : classNode.methods) {
                for (AbstractInsnNode node : methodNode.instructions) {
                    if (node instanceof MethodInsnNode && ((MethodInsnNode) node).owner.equals("org/spongepowered/asm/mixin/injection/callback/CallbackInfo") && ((MethodInsnNode) node).name.equals("<init>")) {
                        ((LdcInsnNode) node.getPrevious().getPrevious()).cst = methodNode.name;
                    }
                }
            }
        });
    }

}
