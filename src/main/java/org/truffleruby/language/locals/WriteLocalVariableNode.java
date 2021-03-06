/*
 * Copyright (c) 2013, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.locals;

import org.truffleruby.language.RubyNode;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;

public class WriteLocalVariableNode extends RubyNode {

    private final FrameSlot frameSlot;

    @Child private RubyNode valueNode;
    @Child private WriteFrameSlotNode writeFrameSlotNode;

    public WriteLocalVariableNode(FrameSlot frameSlot, RubyNode valueNode) {
        this.frameSlot = frameSlot;
        this.valueNode = valueNode;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (writeFrameSlotNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            writeFrameSlotNode = insert(WriteFrameSlotNodeGen.create(frameSlot));
        }

        final Object value = valueNode.execute(frame);
        return writeFrameSlotNode.executeWrite(frame, value);
    }

    @Override
    public Object isDefined(VirtualFrame frame) {
        return coreStrings().ASSIGNMENT.createInstance();
    }

    @Override
    public String toString() {
        return super.toString() + " " + frameSlot.getIdentifier() + " = " + valueNode;
    }

}
