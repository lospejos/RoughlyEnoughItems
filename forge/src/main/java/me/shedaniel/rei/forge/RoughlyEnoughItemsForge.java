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

package me.shedaniel.rei.forge;

import com.google.common.collect.Lists;
import me.shedaniel.rei.RoughlyEnoughItemsInitializer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fmllegacy.DatagenModLoader;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.Type;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Mod("roughlyenoughitems")
@ApiStatus.Internal
public class RoughlyEnoughItemsForge {
    public static final Logger LOGGER = LogManager.getFormatterLogger("REI");
    
    public RoughlyEnoughItemsForge() {
        if (!DatagenModLoader.isRunningDataGen()) {
            RoughlyEnoughItemsInitializer.onInitialize();
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> RoughlyEnoughItemsInitializer::onInitializeClient);
        }
    }
    
    public static <A, T> void scanAnnotation(Class<A> clazz, Predicate<Class<T>> predicate, TriConsumer<List<String>, Supplier<T>, Class<T>> consumer) {
        scanAnnotation(Type.getType(clazz), predicate, consumer);
    }
    
    public static <T> void scanAnnotation(Type annotationType, Predicate<Class<T>> predicate, TriConsumer<List<String>, Supplier<T>, Class<T>> consumer) {
        List<Triple<List<String>, Supplier<T>, Class<T>>> instances = Lists.newArrayList();
        for (ModFileScanData data : ModList.get().getAllScanData()) {
            List<String> modIds = data.getIModInfoData().stream()
                    .flatMap(info -> info.getMods().stream())
                    .map(IModInfo::getModId)
                    .collect(Collectors.toList());
            out:
            for (ModFileScanData.AnnotationData annotation : data.getAnnotations()) {
                if (annotationType.equals(annotation.annotationType())) {
                    try {
                        Class<T> clazz = (Class<T>) Class.forName(annotation.memberName());
                        if (predicate.test(clazz)) {
                            instances.add(new ImmutableTriple<>(modIds, () -> {
                                try {
                                    return clazz.getDeclaredConstructor().newInstance();
                                } catch (Throwable throwable) {
                                    LOGGER.error("Failed to load plugin: " + annotation.memberName(), throwable);
                                    return null;
                                }
                            }, clazz));
                        }
                    } catch (Throwable throwable) {
                        Throwable t = throwable;
                        while (t != null) {
                            if (t.getMessage() != null && t.getMessage().contains("invalid dist DEDICATED_SERVER") && !RoughlyEnoughItemsInitializer.isClient())
                                continue out;
                            t = t.getCause();
                        }
                        LOGGER.error("Failed to load plugin: " + annotation.memberName(), throwable);
                    }
                }
            }
        }
        
        for (Triple<List<String>, Supplier<T>, Class<T>> pair : instances) {
            consumer.accept(pair.getLeft(), pair.getMiddle(), pair.getRight());
        }
    }
}
