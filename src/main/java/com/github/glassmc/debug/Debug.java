package com.github.glassmc.debug;

import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Debug {

    private final List<Consumer<ClassNode>> remappers = new ArrayList<>();

    public void addRemapper(Consumer<ClassNode> remapper) {
        this.remappers.add(remapper);
    }

    public List<Consumer<ClassNode>> getRemappers() {
        return remappers;
    }

}
