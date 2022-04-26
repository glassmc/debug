package com.github.glassmc.debug;

import com.github.glassmc.kiln.standard.internalremapper.ClassRemapper;
import com.github.glassmc.kiln.standard.internalremapper.Remapper;
import com.github.glassmc.kiln.standard.mappings.*;
import com.github.glassmc.loader.api.GlassLoader;
import com.github.glassmc.loader.api.loader.Transformer;
import com.github.glassmc.loader.api.loader.TransformerOrder;
import com.github.jezza.Toml;
import com.github.jezza.TomlArray;
import com.github.jezza.TomlTable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DebugStandardTransformer implements Transformer {

    private boolean remap = false;

    private final List<Remapper> namedRemappers;
    private final List<Remapper> obfuscatedRemappers;

    public DebugStandardTransformer() {
        File file = new File("debug_config.toml");
        List<IMappingsProvider> mappingsProviders = new ArrayList<>();
        if (file.exists()) {
            try {
                TomlTable toml = Toml.from(new FileReader(file));
                if (toml.get("remappers") != null) {
                    TomlArray array = (TomlArray) toml.get("remappers");
                    for (Object object : array) {
                        String string = (String) object;
                        String[] split = string.split(":");

                        IMappingsProvider mappingsProvider;
                        switch(split[0]) {
                            case "yarn":
                                mappingsProvider = new YarnMappingsProvider();
                                break;
                            case "mojang":
                                mappingsProvider = new MojangMappingsProvider();
                                break;
                            case "mcp":
                                mappingsProvider = new MCPMappingsProvider();
                                break;
                            case "obfuscated":
                            default:
                                mappingsProvider = new ObfuscatedMappingsProvider();
                        }

                        try {
                            mappingsProvider.setup(new File(System.getProperty("user.home") + "/.gradle/caches/kiln/minecraft/" + split[1]), split[1]);
                        } catch (NoSuchMappingsException e) {
                            e.printStackTrace();
                        }

                        mappingsProviders.add(mappingsProvider);
                    }
                }

                if (toml.get("remap") != null) {
                    this.remap = (boolean) toml.get("remap");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String environment;
        if (GlassLoader.getInstance().getShardVersion("client") != null) {
            environment = "client";
        } else {
            environment = "server";
        }

        mappingsProviders.removeIf(provider -> !GlassLoader.getInstance().getShardVersion(environment).equals(provider.getVersion()));

        this.namedRemappers = mappingsProviders.stream().map(provider -> provider.getRemapper(IMappingsProvider.Direction.TO_NAMED)).collect(Collectors.toList());
        this.obfuscatedRemappers = mappingsProviders.stream().map(provider -> provider.getRemapper(IMappingsProvider.Direction.TO_OBFUSCATED)).collect(Collectors.toList());
    }

    @Override
    public boolean canTransform(String name) {
        return !(name.startsWith("org/objectweb/asm/") || name.startsWith("com/github/glassmc/kiln/standard/internalremapper/") || name.startsWith("com/github/glassmc/debug/"));
    }

    @Override
    public boolean acceptsBlank() {
        return true;
    }

    @Override
    public byte[] transform(String name, byte[] data) {
        if (!remap) return data;

        String newName = name;

        for (Remapper remapper : obfuscatedRemappers) {
            newName = remapper.map(newName);
        }

        if (!newName.equals(name)) {
            try {
                data = GlassLoader.getInstance().getClassBytes(newName);
            } catch (NullPointerException | ClassNotFoundException e) {
                return data;
            }
        } else {
            if (data.length == 0) {
                return data;
            }
        }

        for (Remapper remapper : namedRemappers) {
            ClassReader classReader = new ClassReader(data);
            ClassWriter classWriter = new ClassWriter(0);
            ClassVisitor classVisitor = new ClassRemapper(classWriter, remapper);

            ClassNode classNode = new ClassNode();
            classReader.accept(classNode, 0);

            classNode.access = makePublic(classNode.access);

            for (MethodNode methodNode : classNode.methods) {
                methodNode.access = makePublic(methodNode.access);
            }

            for (FieldNode fieldNode : classNode.fields) {
                fieldNode.access = makePublic(fieldNode.access);
            }

            for (Consumer<ClassNode> remapper2 : GlassLoader.getInstance().getAPI(Debug.class).getRemappers()) {
                remapper2.accept(classNode);
            }

            classNode.accept(classVisitor);

            data = classWriter.toByteArray();
        }

        return data;
    }

    private int makePublic(int access) {
        if (Modifier.isPrivate(access)) {
            access -= Modifier.PRIVATE;
        } else if (Modifier.isPublic(access)) {
            return access;
        } else if (Modifier.isProtected(access)) {
            access -= Modifier.PROTECTED;
        }

        return access + Modifier.PUBLIC;
    }

}
