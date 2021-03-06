/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.core.klass;

import org.truffleruby.core.module.ModuleFields;
import org.truffleruby.core.module.ModuleLayout;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectFactory;
import com.oracle.truffle.api.object.dsl.Layout;
import com.oracle.truffle.api.object.dsl.Nullable;

@Layout
public interface ClassLayout extends ModuleLayout {

    DynamicObjectFactory createClassShape(DynamicObject logicalClass,
            DynamicObject metaClass);

    DynamicObject createClass(DynamicObjectFactory factory,
            ModuleFields fields,
            boolean isSingleton,
            @Nullable DynamicObject attached,
            @Nullable DynamicObjectFactory instanceFactory,
            @Nullable DynamicObject superclass);

    boolean isClass(DynamicObject object);

    boolean isClass(Object object);

    boolean getIsSingleton(DynamicObject object);

    DynamicObject getAttached(DynamicObject object);

    DynamicObjectFactory getInstanceFactory(DynamicObject object);

    void setInstanceFactoryUnsafe(DynamicObject object, DynamicObjectFactory value);

    DynamicObject getSuperclass(DynamicObject object);

    void setSuperclass(DynamicObject object, DynamicObject value);

}
