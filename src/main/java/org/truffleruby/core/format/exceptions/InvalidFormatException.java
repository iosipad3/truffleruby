/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.format.exceptions;

public class InvalidFormatException extends FormatException {

    private static final long serialVersionUID = -6689191131430505495L;

    public InvalidFormatException(String message) {
        super(message);
    }

}
