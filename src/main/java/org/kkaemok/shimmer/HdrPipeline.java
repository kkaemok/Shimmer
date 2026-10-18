package org.kkaemok.shimmer;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class HdrPipeline {
    public static final RenderPipeline PIPELINE = create();
    private HdrPipeline() {}

    private static RenderPipeline create() {
        var vanilla = RenderPipelines.PANORAMA;
        var builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("shimmer", "pipeline/panorama"))
                .withVertexShader("core/panorama")
                .withFragmentShader(Identifier.fromNamespaceAndPath("shimmer", "core/panorama"))
                .withPrimitiveTopology(vanilla.getPrimitiveTopology())
                .withVertexBinding(0, vanilla.getVertexFormatBinding(0))
                .withCull(vanilla.isCull())
                .withColorTargetState(vanilla.getColorTargetStates().getFirst());
        vanilla.getBindGroupLayouts().forEach(builder::withBindGroupLayout);
        return builder.withBindGroupLayout(BindGroupLayout.builder().withUniform("HdrSettings", UniformType.UNIFORM_BUFFER).build()).build();
    }
}
