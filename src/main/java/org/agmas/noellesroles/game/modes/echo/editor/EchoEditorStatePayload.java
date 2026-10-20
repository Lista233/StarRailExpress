package org.agmas.noellesroles.game.modes.echo.editor;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 编辑工具物品的状态快照（只发回操作者本人）。
 *
 * <p>只含地图 id / 草稿 revision / 点位 id 列表 / 一条本地化消息，不含坐标、文件路径或其它玩家数据；
 * 所有列表都有硬上限，避免把超大地图整份推给客户端。
 */
public record EchoEditorStatePayload(boolean session, String mapId, String mapName, long revision,
                                     boolean declared, boolean cornerA, boolean cornerB,
                                     List<Section> sections, List<String> issues,
                                     String messageKey, List<String> messageArgs) implements CustomPacketPayload {
    public static final int MAX_SECTIONS = 12, MAX_IDS = 8, MAX_ISSUES = 8, MAX_ARGS = 3;
    public static final int MAX_TEXT = 96, MAX_KEY = 64;

    /** 一个字段的点位摘要：字段名 + 总数 + 前若干个 id。 */
    public record Section(String field, int count, List<String> ids) {
        public Section {
            Objects.requireNonNull(field); ids = List.copyOf(ids);
            if (field.length() > MAX_KEY || ids.size() > MAX_IDS) throw new IllegalArgumentException("编辑摘要超限");
        }
    }

    public static final Type<EchoEditorStatePayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath("noellesroles", "echo/editor_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EchoEditorStatePayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeBoolean(p.session()); buf.writeUtf(p.mapId(), 128); buf.writeUtf(p.mapName(), 128);
        buf.writeVarLong(p.revision()); buf.writeBoolean(p.declared());
        buf.writeBoolean(p.cornerA()); buf.writeBoolean(p.cornerB());
        buf.writeVarInt(p.sections().size());
        for (var section : p.sections()) {
            buf.writeUtf(section.field(), MAX_KEY); buf.writeVarInt(section.count()); buf.writeVarInt(section.ids().size());
            for (var id : section.ids()) buf.writeUtf(id, MAX_TEXT);
        }
        buf.writeVarInt(p.issues().size());
        for (var issue : p.issues()) buf.writeUtf(issue, MAX_TEXT);
        buf.writeUtf(p.messageKey(), MAX_KEY); buf.writeVarInt(p.messageArgs().size());
        for (var arg : p.messageArgs()) buf.writeUtf(arg, MAX_TEXT);
    }, buf -> {
        boolean session = buf.readBoolean(); String mapId = buf.readUtf(128), mapName = buf.readUtf(128);
        long revision = buf.readVarLong(); boolean declared = buf.readBoolean();
        boolean cornerA = buf.readBoolean(), cornerB = buf.readBoolean();
        int sectionCount = buf.readVarInt();
        if (sectionCount < 0 || sectionCount > MAX_SECTIONS) throw new IllegalArgumentException("编辑摘要字段过多");
        var sections = new ArrayList<Section>(sectionCount);
        for (int n = 0; n < sectionCount; n++) {
            String field = buf.readUtf(MAX_KEY); int count = buf.readVarInt(); int size = buf.readVarInt();
            if (size < 0 || size > MAX_IDS) throw new IllegalArgumentException("编辑摘要 id 过多");
            var ids = new ArrayList<String>(size);
            for (int i = 0; i < size; i++) ids.add(buf.readUtf(MAX_TEXT));
            sections.add(new Section(field, count, ids));
        }
        int issueCount = buf.readVarInt();
        if (issueCount < 0 || issueCount > MAX_ISSUES) throw new IllegalArgumentException("校验问题过多");
        var issues = new ArrayList<String>(issueCount);
        for (int n = 0; n < issueCount; n++) issues.add(buf.readUtf(MAX_TEXT));
        String messageKey = buf.readUtf(MAX_KEY); int argCount = buf.readVarInt();
        if (argCount < 0 || argCount > MAX_ARGS) throw new IllegalArgumentException("消息参数过多");
        var args = new ArrayList<String>(argCount);
        for (int n = 0; n < argCount; n++) args.add(buf.readUtf(MAX_TEXT));
        return new EchoEditorStatePayload(session, mapId, mapName, revision, declared, cornerA, cornerB,
                sections, issues, messageKey, args);
    });

    public EchoEditorStatePayload {
        mapId = mapId == null ? "" : mapId; mapName = mapName == null ? "" : mapName;
        messageKey = messageKey == null ? "" : messageKey;
        sections = List.copyOf(sections); issues = List.copyOf(issues); messageArgs = List.copyOf(messageArgs);
        if (sections.size() > MAX_SECTIONS || issues.size() > MAX_ISSUES || messageArgs.size() > MAX_ARGS)
            throw new IllegalArgumentException("编辑快照超限");
    }

    /** 空状态（未开启会话 / 无权限时回给客户端）。 */
    public static EchoEditorStatePayload empty(String messageKey, String... args) {
        return new EchoEditorStatePayload(false, "", "", 0L, false, false, false, List.of(), List.of(),
                messageKey, List.of(args));
    }

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
