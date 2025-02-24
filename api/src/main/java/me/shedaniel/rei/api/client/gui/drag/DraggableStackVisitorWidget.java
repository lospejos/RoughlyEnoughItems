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

package me.shedaniel.rei.api.client.gui.drag;

import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.ApiStatus;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * An interface to be implemented on {@link me.shedaniel.rei.api.client.gui.widgets.Widget} to accept
 * incoming {@link DraggableStack}.
 */
public interface DraggableStackVisitorWidget {
    static DraggableStackVisitorWidget from(Function<DraggingContext<Screen>, Iterable<DraggableStackVisitorWidget>> providers) {
        return new DraggableStackVisitorWidget() {
            @Override
            public DraggedAcceptorResult acceptDraggedStackWithResult(DraggingContext<Screen> context, DraggableStack stack) {
                return StreamSupport.stream(providers.apply(context).spliterator(), false)
                        .map(visitor -> visitor.acceptDraggedStackWithResult(context, stack))
                        .filter(result -> result != DraggedAcceptorResult.PASS)
                        .findFirst()
                        .orElse(DraggedAcceptorResult.PASS);
            }
            
            @Override
            public Stream<DraggableStackVisitor.BoundsProvider> getDraggableAcceptingBounds(DraggingContext<Screen> context, DraggableStack stack) {
                return StreamSupport.stream(providers.apply(context).spliterator(), false)
                        .flatMap(visitor -> visitor.getDraggableAcceptingBounds(context, stack));
            }
        };
    }
    
    @ApiStatus.ScheduledForRemoval
    @Deprecated(forRemoval = true)
    default Optional<DraggableStackVisitor.Acceptor> visitDraggedStack(DraggingContext<Screen> context, DraggableStack stack) {
        return Optional.empty();
    }
    
    /**
     * Accepts a dragged stack, implementations of this function should check if the {@code context} is within
     * boundaries of the widget.
     *
     * @param context the context of the current dragged stack on the overlay
     * @param stack   the stack being dragged
     * @return whether the stack is accepted by the widget
     */
    @ApiStatus.ScheduledForRemoval
    @Deprecated
    default boolean acceptDraggedStack(DraggingContext<Screen> context, DraggableStack stack) {
        Optional<DraggableStackVisitor.Acceptor> acceptor = visitDraggedStack(context, stack);
        if (acceptor.isPresent()) {
            acceptor.get().accept(stack);
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Accepts a dragged stack, implementations of this function should check if the {@code context} is within
     * boundaries of the widget.
     *
     * @param context the context of the current dragged stack on the overlay
     * @param stack   the stack being dragged
     * @return the result of the visitor
     */
    default DraggedAcceptorResult acceptDraggedStackWithResult(DraggingContext<Screen> context, DraggableStack stack) {
        return acceptDraggedStack(context, stack) ? DraggedAcceptorResult.CONSUMED : DraggedAcceptorResult.PASS;
    }
    
    /**
     * Returns the accepting bounds for the dragging stack, this should only be called once on drag.
     * The bounds are used to overlay to indicate to the users that the widget is accepting entries.
     *
     * @param context the context of the current dragged stack on the overlay
     * @param stack   the stack being dragged
     * @return the accepting bounds for the dragging stack in a stream
     */
    default Stream<DraggableStackVisitor.BoundsProvider> getDraggableAcceptingBounds(DraggingContext<Screen> context, DraggableStack stack) {
        return Stream.empty();
    }
    
    static DraggableStackVisitor<Screen> toVisitor(DraggableStackVisitorWidget widget) {
        return toVisitor(widget, 0.0);
    }
    
    static DraggableStackVisitor<Screen> toVisitor(DraggableStackVisitorWidget widget, double priority) {
        return new DraggableStackVisitor<>() {
            @Override
            public DraggedAcceptorResult acceptDraggedStackWithResult(DraggingContext<Screen> context, DraggableStack stack) {
                return widget.acceptDraggedStackWithResult(context, stack);
            }
            
            @Override
            public <R extends Screen> boolean isHandingScreen(R screen) {
                return true;
            }
            
            @Override
            public Stream<BoundsProvider> getDraggableAcceptingBounds(DraggingContext<Screen> context, DraggableStack stack) {
                return widget.getDraggableAcceptingBounds(context, stack);
            }
            
            @Override
            public double getPriority() {
                return priority;
            }
        };
    }
}
