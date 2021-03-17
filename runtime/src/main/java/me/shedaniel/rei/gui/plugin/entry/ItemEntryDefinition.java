/*
 * This file is licensed under the MIT License, part of Roughly Enough Items.
 * Copyright (c) 2018, 2019, 2020 shedaniel
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.shedaniel.rei.gui.plugin.entry;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.ClientHelper;
import me.shedaniel.rei.api.config.ConfigObject;
import me.shedaniel.rei.api.gui.widgets.Tooltip;
import me.shedaniel.rei.api.ingredient.EntryStack;
import me.shedaniel.rei.api.ingredient.entry.*;
import me.shedaniel.rei.api.util.ImmutableLiteralText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagCollection;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class ItemEntryDefinition implements EntryDefinition<ItemStack>, EntrySerializer<ItemStack> {
    private final EntryRenderer<ItemStack> renderer = new ItemEntryRenderer();
    
    @Override
    public @NotNull Class<ItemStack> getValueType() {
        return ItemStack.class;
    }
    
    @Override
    public @NotNull EntryType<ItemStack> getType() {
        return VanillaEntryTypes.ITEM;
    }
    
    @Override
    public @NotNull EntryRenderer<ItemStack> getRenderer() {
        return renderer;
    }
    
    @Override
    public @NotNull Optional<ResourceLocation> getIdentifier(EntryStack<ItemStack> entry, ItemStack value) {
        return Optional.ofNullable(Registry.ITEM.getKey(value.getItem()));
    }
    
    @Override
    public boolean isEmpty(EntryStack<ItemStack> entry, ItemStack value) {
        return value.isEmpty();
    }
    
    @Override
    public @NotNull ItemStack copy(EntryStack<ItemStack> entry, ItemStack value) {
        return value.copy();
    }
    
    @Override
    public int hash(EntryStack<ItemStack> entry, ItemStack value, ComparisonContext context) {
        int code = 1;
        code = 31 * code + System.identityHashCode(value.getItem());
        code = 31 * code + (context.isIgnoresCount() ? 0 : value.getCount());
        code = 31 * code + (context.isIgnoresNbt() || !value.hasTag() ? 0 : value.getTag().hashCode());
        return code;
    }
    
    @Override
    public boolean equals(ItemStack o1, ItemStack o2, ComparisonContext context) {
        if (o1.getItem() != o2.getItem())
            return false;
        if (!context.isIgnoresCount() && o1.getCount() != o2.getCount())
            return false;
        if (!context.isIgnoresNbt()) {
            CompoundTag tag1 = o1.getTag();
            CompoundTag tag2 = o2.getTag();
            return tag1 == tag2 || ((tag1 != null && tag2 != null) && equalsTagWithoutCount(tag1, tag2));
        }
        return true;
    }
    
    private boolean equalsTagWithoutCount(CompoundTag o1, CompoundTag o2) {
        int o1Size = 0;
        int o2Size = 0;
        for (String key : o1.getAllKeys()) {
            if (key.equals("Count"))
                continue;
            o1Size++;
        }
        for (String key : o2.getAllKeys()) {
            if (key.equals("Count"))
                continue;
            o2Size++;
            if (o2Size > o1Size)
                return false;
        }
        if (o1Size != o2Size)
            return false;
        
        try {
            for (String key : o1.getAllKeys()) {
                if (key.equals("Count"))
                    continue;
                Tag value = o1.get(key);
                Tag otherValue = o2.get(key);
                if (!equalsTag(value, otherValue))
                    return false;
            }
        } catch (ClassCastException | NullPointerException unused) {
            return false;
        }
        
        return true;
    }
    
    private boolean equalsTag(Tag tag, Tag otherTag) {
        if (tag == null || otherTag == null) {
            return tag == otherTag;
        }
        if (tag instanceof ListTag && otherTag instanceof ListTag)
            return equalsList((ListTag) tag, (ListTag) otherTag);
        return tag.equals(otherTag);
    }
    
    private boolean equalsList(ListTag listTag, ListTag otherListTag) {
        if (listTag.size() != otherListTag.size())
            return false;
        for (int i = 0; i < listTag.size(); i++) {
            Tag value = listTag.get(i);
            Tag otherValue = otherListTag.get(i);
            if (!equalsTag(value, otherValue))
                return false;
        }
        return true;
    }
    
    @Override
    @Nullable
    public EntrySerializer<ItemStack> getSerializer() {
        return this;
    }
    
    @Override
    public boolean supportSaving() {
        return true;
    }
    
    @Override
    public boolean supportReading() {
        return true;
    }
    
    @Override
    public CompoundTag save(EntryStack<ItemStack> entry, ItemStack value) {
        return value.save(new CompoundTag());
    }
    
    @Override
    public ItemStack read(CompoundTag tag) {
        return ItemStack.of(tag);
    }
    
    private static final ReferenceSet<Item> SEARCH_BLACKLISTED = new ReferenceOpenHashSet<>();
    
    @Override
    public @NotNull Component asFormattedText(EntryStack<ItemStack> entry, ItemStack value) {
        if (!SEARCH_BLACKLISTED.contains(value.getItem()))
            try {
                return value.getHoverName();
            } catch (Throwable e) {
                e.printStackTrace();
                SEARCH_BLACKLISTED.add(value.getItem());
            }
        try {
            return new ImmutableLiteralText(I18n.get("item." + Registry.ITEM.getKey(value.getItem()).toString().replace(":", ".")));
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return new ImmutableLiteralText("ERROR");
    }
    
    @Override
    public @NotNull Collection<ResourceLocation> getTagsFor(EntryStack<ItemStack> entry, ItemStack value) {
        TagCollection<Item> collection = Minecraft.getInstance().getConnection().getTags().getItems();
        return collection == null ? Collections.emptyList() : collection.getMatchingTags(value.getItem());
    }
    
    private List<Component> tryGetItemStackToolTip(EntryStack<ItemStack> entry, ItemStack value, boolean careAboutAdvanced) {
        if (!SEARCH_BLACKLISTED.contains(value.getItem()))
            try {
                return value.getTooltipLines(Minecraft.getInstance().player, careAboutAdvanced && Minecraft.getInstance().options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL);
            } catch (Throwable e) {
                e.printStackTrace();
                SEARCH_BLACKLISTED.add(value.getItem());
            }
        return Lists.newArrayList(asFormattedText(entry, value));
    }
    
    @SuppressWarnings("deprecation")
    public class ItemEntryRenderer extends AbstractEntryRenderer<ItemStack> implements BatchEntryRenderer<ItemStack> {
        @Override
        public int getBatchId(EntryStack<ItemStack> entry) {
            return 1738923 + (getModelFromStack(entry.getValue()).usesBlockLight() ? 1 : 0);
        }
        
        private BakedModel getModelFromStack(ItemStack stack) {
            return Minecraft.getInstance().getItemRenderer().getModel(stack, null, null);
        }
        
        @Override
        public void startBatch(EntryStack<ItemStack> entry, PoseStack matrices, float delta) {
            Minecraft.getInstance().getTextureManager().bind(TextureAtlas.LOCATION_BLOCKS);
            Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).setFilter(false, false);
            RenderSystem.pushMatrix();
            RenderSystem.enableRescaleNormal();
            RenderSystem.enableAlphaTest();
            RenderSystem.defaultAlphaFunc();
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            boolean sideLit = getModelFromStack(entry.getValue()).usesBlockLight();
            if (!sideLit)
                Lighting.setupForFlatItems();
        }
        
        @Override
        public void renderBase(EntryStack<ItemStack> entry, PoseStack matrices, MultiBufferSource.BufferSource immediate, Rectangle bounds, int mouseX, int mouseY, float delta) {
            if (!entry.isEmpty() && entry.get(EntryStack.Settings.RENDER).get()) {
                ItemStack stack = entry.getValue();
                matrices.pushPose();
                matrices.translate(bounds.getCenterX(), bounds.getCenterY(), 100.0F + entry.getZ());
                matrices.scale(bounds.getWidth(), (bounds.getWidth() + bounds.getHeight()) / -2f, bounds.getHeight());
                Minecraft.getInstance().getItemRenderer().render(stack, ItemTransforms.TransformType.GUI, false, matrices, immediate, 15728880, OverlayTexture.NO_OVERLAY, getModelFromStack(stack));
                matrices.popPose();
            }
        }
        
        @Override
        public void renderOverlay(EntryStack<ItemStack> entry, PoseStack matrices, Rectangle bounds, int mouseX, int mouseY, float delta) {
            if (!entry.isEmpty() && entry.get(EntryStack.Settings.RENDER).get()) {
                Minecraft.getInstance().getItemRenderer().blitOffset = entry.getZ();
                Minecraft.getInstance().getItemRenderer().renderGuiItemDecorations(Minecraft.getInstance().font, entry.getValue(), bounds.x, bounds.y, entry.get(EntryStack.Settings.RENDER_COUNTS).get() ? entry.get(EntryStack.Settings.COUNTS).apply(entry) : "");
                Minecraft.getInstance().getItemRenderer().blitOffset = 0.0F;
            }
        }
        
        @Override
        public void endBatch(EntryStack<ItemStack> entry, PoseStack matrices, float delta) {
            RenderSystem.enableDepthTest();
            RenderSystem.disableAlphaTest();
            RenderSystem.disableRescaleNormal();
            boolean sideLit = getModelFromStack(entry.getValue()).usesBlockLight();
            if (!sideLit)
                Lighting.setupFor3DItems();
            RenderSystem.popMatrix();
        }
        
        @Override
        public @Nullable Tooltip getTooltip(EntryStack<ItemStack> entry, Point mouse) {
            if (entry.isEmpty() || !entry.get(EntryStack.Settings.TOOLTIP_ENABLED).get())
                return null;
            List<Component> toolTip = tryGetItemStackToolTip(entry, entry.getValue(), true);
            toolTip.addAll(entry.get(EntryStack.Settings.TOOLTIP_APPEND_EXTRA).apply(entry));
            if (entry.get(EntryStack.Settings.TOOLTIP_APPEND_MOD).get() && ConfigObject.getInstance().shouldAppendModNames()) {
                ClientHelper.getInstance().appendModIdToTooltips(toolTip, Registry.ITEM.getKey(entry.getValue().getItem()).getNamespace());
            }
            return Tooltip.create(toolTip);
        }
    }
}
