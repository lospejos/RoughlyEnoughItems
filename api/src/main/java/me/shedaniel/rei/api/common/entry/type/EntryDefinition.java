/*
 * This file is licensed under the MIT License, part of Roughly Enough Items.
 * Copyright (c) 2018, 2019, 2020, 2021 shedaniel
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

package me.shedaniel.rei.api.common.entry.type;

import me.shedaniel.rei.api.client.entry.renderer.EntryRenderer;
import me.shedaniel.rei.api.common.entry.EntrySerializer;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.comparison.ComparisonContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagContainer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * A definition of an {@link EntryType}, an interface to provide information from an object type.
 *
 * @param <T> the type of entry
 * @see EntryTypeRegistry
 */
public interface EntryDefinition<T> {
    /**
     * Returns the type of the entry.
     *
     * @return the type of the entry
     */
    Class<T> getValueType();
    
    /**
     * Returns the type of this definition. The type is also used for comparing the type of two definitions,
     * as the definition does not guarantee object and reference equality.
     *
     * @return the type of this definition
     */
    EntryType<T> getType();
    
    /**
     * Returns the renderer for this entry, this is used to render the entry, and provide tooltip.
     * External plugins can extend this method using {@link me.shedaniel.rei.api.client.entry.renderer.EntryRendererRegistry}
     * to provide custom renderers.
     *
     * @return the renderer for this entry
     */
    @Environment(EnvType.CLIENT)
    EntryRenderer<T> getRenderer();
    
    /**
     * Returns the identifier for an entry, used in identifier search argument type,
     * and appending the mod name to the tooltip.
     *
     * @param entry the entry
     * @param value the value of the entry
     * @return the identifier for an entry
     */
    @Nullable
    ResourceLocation getIdentifier(EntryStack<T> entry, T value);
    
    /**
     * Returns whether the entry is empty, empty entries are not displayed,
     * and are considered invalid.
     * Empty entries will be treated equally to {@link EntryStack#empty()}.
     *
     * @param entry the entry
     * @param value the value of the entry
     * @return whether the entry is empty
     */
    boolean isEmpty(EntryStack<T> entry, T value);
    
    /**
     * Returns a copy for an entry.
     *
     * @param entry the entry
     * @param value the value of the entry
     * @return a copy for an entry
     */
    T copy(EntryStack<T> entry, T value);
    
    /**
     * Returns a normalized copy for an entry.
     * The returned stack should be functionally equivalent to the original stack,
     * but should have a normalized state.
     * <p>
     * For example, an {@link net.minecraft.world.item.ItemStack} should have its
     * amount removed, but its tags kept.
     *
     * @param entry the entry
     * @param value the value of the entry
     * @return a normalized copy for an entry
     */
    T normalize(EntryStack<T> entry, T value);
    
    /**
     * Returns a wildcard copy for an entry.
     * The returned stack should be the bare minimum to match the original stack.
     * <p>
     * For example, an {@link net.minecraft.world.item.ItemStack} should have its
     * amount and tags removed.
     *
     * @param entry the entry
     * @param value the value of the entry
     * @return a wildcard copy for an entry
     * @since 6.2
     */
    default T wildcard(EntryStack<T> entry, T value) {
        return normalize(entry, value);
    }
    
    long hash(EntryStack<T> entry, T value, ComparisonContext context);
    
    boolean equals(T o1, T o2, ComparisonContext context);
    
    @Nullable
    EntrySerializer<T> getSerializer();
    
    Component asFormattedText(EntryStack<T> entry, T value);
    
    Collection<ResourceLocation> getTagsFor(TagContainer tagContainer, EntryStack<T> entry, T value);
    
    @ApiStatus.NonExtendable
    default <O> EntryDefinition<O> cast() {
        return (EntryDefinition<O>) this;
    }
    
    default void fillCrashReport(CrashReport report, CrashReportCategory category, EntryStack<T> entry) {
        category.setDetail("Entry definition class name", () -> getClass().getCanonicalName());
    }
}

