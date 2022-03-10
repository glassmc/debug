package com.github.glassmc.debug;

import com.github.glassmc.kiln.standard.mappings.*;
import com.github.glassmc.loader.api.GlassLoader;
import com.github.glassmc.loader.api.Listener;
import com.github.glassmc.loader.api.loader.Transformer;
import com.github.glassmc.loader.api.loader.TransformerOrder;
import com.github.jezza.Toml;
import com.github.jezza.TomlArray;
import com.github.jezza.TomlTable;
import org.apache.commons.io.FileUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DebugDumpTransformer implements Listener, Transformer {

    private final File DUMP_FOLDER = new File("dump");

    private final List<IMappingsProvider> mappingsProviders = new ArrayList<>();
    private final List<String> dumpedClasses = new ArrayList<>();

    public DebugDumpTransformer() {
        if (this.DUMP_FOLDER.exists()) {
            try {
                FileUtils.deleteDirectory(this.DUMP_FOLDER);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        DUMP_FOLDER.mkdirs();

        File file = new File("debug_config.toml");
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

                        this.mappingsProviders.add(mappingsProvider);
                    }
                }

                if (toml.get("dump") != null) {
                    TomlArray array = (TomlArray) toml.get("dump");
                    for (Object object : array) {
                        String name = (String) object;

                        for (IMappingsProvider mappingsProvider : this.mappingsProviders) {
                            if (name.startsWith("v" + mappingsProvider.getVersion().replace(".", "_"))) {
                                name = name.substring(name.indexOf("/") + 1);
                            }
                            this.dumpedClasses.add(mappingsProvider.getRemapper(IMappingsProvider.Direction.TO_OBFUSCATED).map(name));
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        GlassLoader.getInstance().registerTransformer(DebugDumpTransformer.class, TransformerOrder.LAST);
    }

    @Override
    public boolean canTransform(String name) {
        return this.dumpedClasses.contains(name);
    }

    @Override
    public byte[] transform(String name, byte[] data) {
        this.dumpClass(name, data);
        return data;
    }

    private void dumpClass(String name, byte[] data) {
        for (IMappingsProvider mappingsProvider : this.mappingsProviders) {
            Remapper remapper = mappingsProvider.getRemapper(IMappingsProvider.Direction.TO_NAMED);

            ClassReader classReader = new ClassReader(data);
            ClassWriter classWriter = new ClassWriter(0);
            ClassVisitor classVisitor = new ClassRemapper(classWriter, remapper);
            classReader.accept(classVisitor, 0);

            data = classWriter.toByteArray();
            name = remapper.map(name.replace(".", "/"));
        }

        File location = new File(DUMP_FOLDER, name + ".class");
        try {
            FileUtils.writeByteArrayToFile(location, data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
