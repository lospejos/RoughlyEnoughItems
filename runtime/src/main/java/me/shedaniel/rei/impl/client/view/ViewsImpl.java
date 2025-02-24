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

package me.shedaniel.rei.impl.client.view;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import me.shedaniel.rei.RoughlyEnoughItemsCore;
import me.shedaniel.rei.api.client.config.ConfigObject;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.client.view.Views;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.display.DisplayMerger;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.plugins.PluginManager;
import me.shedaniel.rei.api.common.transfer.info.MenuInfo;
import me.shedaniel.rei.api.common.transfer.info.MenuInfoContext;
import me.shedaniel.rei.api.common.transfer.info.MenuInfoRegistry;
import me.shedaniel.rei.api.common.transfer.info.stack.SlotAccessor;
import me.shedaniel.rei.api.common.util.CollectionUtils;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.impl.client.gui.craftable.CraftableFilter;
import me.shedaniel.rei.impl.display.DisplaySpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.ApiStatus;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApiStatus.Internal
public class ViewsImpl implements Views {
    public static Map<DisplayCategory<?>, List<DisplaySpec>> buildMapFor(ViewSearchBuilder builder) {
        if (PluginManager.areAnyReloading()) {
            RoughlyEnoughItemsCore.LOGGER.info("Cancelled Views buildMap since plugins have not finished reloading.");
            return Maps.newLinkedHashMap();
        }
        
        Stopwatch stopwatch = Stopwatch.createStarted();
        Set<CategoryIdentifier<?>> categories = builder.getCategories();
        List<EntryStack<?>> recipesForStacks = builder.getRecipesFor();
        List<EntryStack<?>> usagesForStacks = builder.getUsagesFor();
        recipesForStacks = Stream.concat(recipesForStacks.stream(), recipesForStacks.stream().map(EntryStack::wildcard))
                .distinct()
                .collect(Collectors.toList());
        usagesForStacks = Stream.concat(usagesForStacks.stream(), usagesForStacks.stream().map(EntryStack::wildcard))
                .distinct()
                .collect(Collectors.toList());
        DisplayRegistry displayRegistry = DisplayRegistry.getInstance();
        
        Map<DisplayCategory<?>, List<Display>> result = Maps.newLinkedHashMap();
        for (CategoryRegistry.CategoryConfiguration<?> categoryConfiguration : CategoryRegistry.getInstance()) {
            DisplayCategory<?> category = categoryConfiguration.getCategory();
            if (CategoryRegistry.getInstance().isCategoryInvisible(category)) continue;
            CategoryIdentifier<?> categoryId = categoryConfiguration.getCategoryIdentifier();
            List<Display> allRecipesFromCategory = displayRegistry.get((CategoryIdentifier<Display>) categoryId);
            
            Set<Display> set = Sets.newLinkedHashSet();
            if (categories.contains(categoryId)) {
                for (Display display : allRecipesFromCategory) {
                    if (displayRegistry.isDisplayVisible(display)) {
                        set.add(display);
                    }
                }
                if (!set.isEmpty()) {
                    CollectionUtils.getOrPutEmptyList(result, category).addAll(set);
                }
                continue;
            }
            for (Display display : allRecipesFromCategory) {
                if (!displayRegistry.isDisplayVisible(display)) continue;
                if (!recipesForStacks.isEmpty()) {
                    back:
                    for (List<? extends EntryStack<?>> results : display.getOutputEntries()) {
                        for (EntryStack<?> otherEntry : results) {
                            for (EntryStack<?> recipesFor : recipesForStacks) {
                                if (EntryStacks.equalsFuzzy(otherEntry, recipesFor)) {
                                    set.add(display);
                                    break back;
                                }
                            }
                        }
                    }
                }
                if (!usagesForStacks.isEmpty()) {
                    back:
                    for (List<? extends EntryStack<?>> input : display.getInputEntries()) {
                        for (EntryStack<?> otherEntry : input) {
                            for (EntryStack<?> usagesFor : usagesForStacks) {
                                if (EntryStacks.equalsFuzzy(otherEntry, usagesFor)) {
                                    set.add(display);
                                    break back;
                                }
                            }
                        }
                    }
                }
            }
            for (EntryStack<?> usagesFor : usagesForStacks) {
                if (isStackWorkStationOfCategory(categoryConfiguration, usagesFor)) {
                    set.addAll(CollectionUtils.filterToSet(allRecipesFromCategory, displayRegistry::isDisplayVisible));
                    break;
                }
            }
            if (!set.isEmpty()) {
                CollectionUtils.getOrPutEmptyList(result, category).addAll(set);
            }
        }
        
        int generatorsCount = 0;
        
        for (Map.Entry<CategoryIdentifier<?>, List<DynamicDisplayGenerator<?>>> entry : displayRegistry.getCategoryDisplayGenerators().entrySet()) {
            CategoryIdentifier<?> categoryId = entry.getKey();
            DisplayCategory<?> category = CategoryRegistry.getInstance().get(categoryId).getCategory();
            if (CategoryRegistry.getInstance().isCategoryInvisible(category)) continue;
            Set<Display> set = new LinkedHashSet<>();
            generatorsCount += entry.getValue().size();
            
            for (DynamicDisplayGenerator<Display> generator : (List<DynamicDisplayGenerator<Display>>) (List<? extends DynamicDisplayGenerator<?>>) entry.getValue()) {
                generateLiveDisplays(displayRegistry, generator, builder, set::add);
            }
            
            if (!set.isEmpty()) {
                CollectionUtils.getOrPutEmptyList(result, category).addAll(set);
            }
        }
        
        Consumer<Display> displayConsumer = display -> {
            CollectionUtils.getOrPutEmptyList(result, CategoryRegistry.getInstance().get(display.getCategoryIdentifier()).getCategory()).add(display);
        };
        for (DynamicDisplayGenerator<Display> generator : (List<DynamicDisplayGenerator<Display>>) (List<? extends DynamicDisplayGenerator<?>>) displayRegistry.getGlobalDisplayGenerators()) {
            generatorsCount++;
            generateLiveDisplays(displayRegistry, generator, builder, displayConsumer);
        }
        
        Map<DisplayCategory<?>, List<DisplaySpec>> resultSpeced = (Map<DisplayCategory<?>, List<DisplaySpec>>) (Map) new LinkedHashMap<>(result);
        // optimize displays
        if (ConfigObject.getInstance().doMergeDisplayUnderOne()) {
            for (Map.Entry<DisplayCategory<?>, List<Display>> entry : result.entrySet()) {
                DisplayMerger<Display> merger = (DisplayMerger<Display>) entry.getKey().getDisplayMerger();
                
                if (merger != null) {
                    class Wrapped implements DisplaySpec {
                        private Display display;
                        private List<ResourceLocation> ids = null;
                        
                        public Wrapped(Display display) {
                            this.display = display;
                        }
                        
                        @Override
                        public boolean equals(Object o) {
                            if (this == o) return true;
                            if (!(o instanceof Wrapped)) return false;
                            Wrapped wrapped = (Wrapped) o;
                            return merger.canMerge(display, wrapped.display);
                        }
                        
                        @Override
                        public int hashCode() {
                            return merger.hashOf(display);
                        }
                        
                        @Override
                        public Display provideInternalDisplay() {
                            return display;
                        }
                        
                        @Override
                        public Collection<ResourceLocation> provideInternalDisplayIds() {
                            if (ids == null) {
                                ids = new ArrayList<>();
                                Optional<ResourceLocation> location = display.getDisplayLocation();
                                if (location.isPresent()) {
                                    ids.add(location.get());
                                }
                            }
                            return ids;
                        }
                        
                        public void add(Display display) {
                            Optional<ResourceLocation> location = display.getDisplayLocation();
                            if (location.isPresent()) {
                                provideInternalDisplayIds().add(location.get());
                            }
                        }
                    }
                    Map<Wrapped, Wrapped> wrappedSet = new LinkedHashMap<>();
                    List<Wrapped> wrappeds = new ArrayList<>();
                    
                    for (Display display : entry.getValue()) {
                        Wrapped wrapped = new Wrapped(display);
                        if (wrappedSet.containsKey(wrapped)) {
                            wrappedSet.get(wrapped).add(display);
                        } else {
                            wrappedSet.put(wrapped, wrapped);
                            wrappeds.add(wrapped);
                        }
                    }
                    
                    resultSpeced.put(entry.getKey(), (List<DisplaySpec>) (List) wrappeds);
                }
            }
        }
        
        String message = String.format("Built Recipe View in %s for %d categories, %d recipes for, %d usages for and %d live recipe generators.",
                stopwatch.stop(), categories.size(), recipesForStacks.size(), usagesForStacks.size(), generatorsCount);
        if (ConfigObject.getInstance().doDebugSearchTimeRequired()) {
            RoughlyEnoughItemsCore.LOGGER.info(message);
        } else {
            RoughlyEnoughItemsCore.LOGGER.trace(message);
        }
        return resultSpeced;
    }
    
    private static <T extends Display> void generateLiveDisplays(DisplayRegistry displayRegistry, DynamicDisplayGenerator<T> generator, ViewSearchBuilder builder, Consumer<T> displayConsumer) {
        for (EntryStack<?> stack : builder.getRecipesFor()) {
            Optional<List<T>> recipeForDisplays = generator.getRecipeFor(stack);
            if (recipeForDisplays.isPresent()) {
                for (T display : recipeForDisplays.get()) {
                    if (displayRegistry.isDisplayVisible(display)) {
                        displayConsumer.accept(display);
                    }
                }
            }
        }
        
        for (EntryStack<?> stack : builder.getUsagesFor()) {
            Optional<List<T>> usageForDisplays = generator.getUsageFor(stack);
            if (usageForDisplays.isPresent()) {
                for (T display : usageForDisplays.get()) {
                    if (displayRegistry.isDisplayVisible(display)) {
                        displayConsumer.accept(display);
                    }
                }
            }
        }
        
        Optional<List<T>> displaysGenerated = generator.generate(builder);
        if (displaysGenerated.isPresent()) {
            for (T display : displaysGenerated.get()) {
                if (displayRegistry.isDisplayVisible(display)) {
                    displayConsumer.accept(display);
                }
            }
        }
    }
    
    @Override
    public Collection<EntryStack<?>> findCraftableEntriesByMaterials() {
        if (PluginManager.areAnyReloading()) {
            return Collections.emptySet();
        }
        
        AbstractContainerMenu menu = Minecraft.getInstance().player.containerMenu;
        Set<EntryStack<?>> craftables = new HashSet<>();
        for (Map.Entry<CategoryIdentifier<?>, List<Display>> entry : DisplayRegistry.getInstance().getAll().entrySet()) {
            List<Display> displays = entry.getValue();
            for (Display display : displays) {
                MenuInfo<AbstractContainerMenu, Display> info = menu != null ?
                        MenuInfoRegistry.getInstance().getClient(display, menu)
                        : null;
                
                class InfoContext implements MenuInfoContext<AbstractContainerMenu, LocalPlayer, Display> {
                    private Display display;
                    
                    public InfoContext(Display display) {
                        this.display = display;
                    }
                    
                    @Override
                    public AbstractContainerMenu getMenu() {
                        return menu;
                    }
                    
                    @Override
                    public LocalPlayer getPlayerEntity() {
                        return Minecraft.getInstance().player;
                    }
                    
                    @Override
                    public MenuInfo<AbstractContainerMenu, Display> getContainerInfo() {
                        return info;
                    }
                    
                    @Override
                    public CategoryIdentifier<Display> getCategoryIdentifier() {
                        return (CategoryIdentifier<Display>) entry.getKey();
                    }
                    
                    @Override
                    public Display getDisplay() {
                        return display;
                    }
                }
                
                InfoContext context = new InfoContext(display);
                Iterable<SlotAccessor> inputSlots = info != null ? info.getInputSlots(context) : Collections.emptySet();
                int slotsCraftable = 0;
                List<EntryIngredient> requiredInput = display.getRequiredEntries();
                for (EntryIngredient slot : requiredInput) {
                    if (slot.isEmpty()) {
                        slotsCraftable++;
                        continue;
                    }
                    for (EntryStack<?> slotPossible : slot) {
                        if (CraftableFilter.INSTANCE.matches(slotPossible, inputSlots)) {
                            slotsCraftable++;
                            break;
                        }
                    }
                }
                if (slotsCraftable == display.getRequiredEntries().size()) {
                    display.getOutputEntries().stream().flatMap(Collection::stream).collect(Collectors.toCollection(() -> craftables));
                }
            }
        }
        return craftables;
    }
    
    private static <T> boolean isStackWorkStationOfCategory(CategoryRegistry.CategoryConfiguration<?> category, EntryStack<T> stack) {
        for (EntryIngredient ingredient : category.getWorkstations()) {
            if (EntryIngredients.testFuzzy(ingredient, stack)) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public void startReload() {
        
    }
}
