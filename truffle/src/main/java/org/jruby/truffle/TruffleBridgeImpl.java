/*
 * Copyright (c) 2013, 2015 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.source.BytesDecoder;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import org.jruby.TruffleBridge;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.truffle.nodes.RubyNode;
import org.jruby.truffle.nodes.TopLevelRaiseHandler;
import org.jruby.truffle.nodes.control.SequenceNode;
import org.jruby.truffle.nodes.core.*;
import org.jruby.truffle.nodes.rubinius.ByteArrayNodesFactory;
import org.jruby.truffle.pack.parser.PackParser;
import org.jruby.truffle.runtime.RubyContext;
import org.jruby.truffle.runtime.backtrace.Backtrace;
import org.jruby.truffle.runtime.control.RaiseException;
import org.jruby.truffle.runtime.core.RubyArray;
import org.jruby.truffle.runtime.core.RubyClass;
import org.jruby.truffle.runtime.core.RubyException;
import org.jruby.truffle.runtime.util.FileUtils;
import org.jruby.truffle.translator.NodeWrapper;
import org.jruby.truffle.translator.TranslatorDriver;
import org.jruby.util.ByteList;
import org.jruby.util.cli.Options;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TruffleBridgeImpl implements TruffleBridge {

    private static final boolean PRINT_RUNTIME = Options.TRUFFLE_PRINT_RUNTIME.load();

    private final org.jruby.Ruby runtime;
    private final RubyContext truffleContext;

    public TruffleBridgeImpl(org.jruby.Ruby runtime) {
        assert runtime != null;

        this.runtime = runtime;

        // Set up a context

        truffleContext = new RubyContext(runtime);
    }

    @Override
    public void init() {
        if (PRINT_RUNTIME) {
            runtime.getInstanceConfig().getError().println("jruby: using " + Truffle.getRuntime().getName());
        }

        // Bring in core method nodes

        RubyClass rubyObjectClass = truffleContext.getCoreLibrary().getObjectClass();
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, ArrayNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, BasicObjectNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, BindingNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, BignumNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, ClassNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, DirNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, ExceptionNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, FalseClassNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, FiberNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, FileNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, FixnumNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, FloatNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, HashNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, IntegerNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, IONodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, KernelNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, MainNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, MatchDataNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, MathNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, ModuleNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, MutexNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, NumericNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, ObjectSpaceNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, ProcessNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, ProcNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, RangeNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, RegexpNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, StringNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, SymbolNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, ThreadNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, TrueClassNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, PrimitiveNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, EncodingNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, EncodingConverterNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, MethodNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, UnboundMethodNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, ByteArrayNodesFactory.getFactories());
        CoreMethodNodeManager.addCoreMethodNodes(rubyObjectClass, TimeNodesFactory.getFactories());

        // Give the core library manager a chance to tweak some of those methods

        truffleContext.getCoreLibrary().initializeAfterMethodsAdded();

        // Set program arguments

        for (IRubyObject arg : ((org.jruby.RubyArray) runtime.getObject().getConstant("ARGV")).toJavaArray()) {
            assert arg != null;

            truffleContext.getCoreLibrary().getArgv().slowPush(truffleContext.makeString(arg.toString()));
        }

        // Set the load path

        final RubyArray loadPath = (RubyArray) truffleContext.getCoreLibrary().getGlobalVariablesObject().getInstanceVariable("$:");

        final String home = runtime.getInstanceConfig().getJRubyHome();

        // We don't want JRuby's stdlib paths, but we do want any extra paths set by -I and things like that

        final List<String> excludedLibPaths = new ArrayList<>();
        excludedLibPaths.add(new File(home, "lib/ruby/2.2/site_ruby").toString());
        excludedLibPaths.add(new File(home, "lib/ruby/shared").toString());
        excludedLibPaths.add(new File(home, "lib/ruby/stdlib").toString());

        for (IRubyObject path : ((org.jruby.RubyArray) runtime.getLoadService().getLoadPath()).toJavaArray()) {
            if (!excludedLibPaths.contains(path.toString())) {
                loadPath.slowPush(truffleContext.makeString(new File(path.toString()).getAbsolutePath()));
            }
        }

        // Load our own stdlib path

        // Libraries copied unmodified from MRI
        loadPath.slowPush(truffleContext.makeString(new File(home, "lib/ruby/truffle/mri").toString()));

        // Our own implementations
        loadPath.slowPush(truffleContext.makeString(new File(home, "lib/ruby/truffle/truffle").toString()));

        // Libraries from RubySL
        loadPath.slowPush(truffleContext.makeString(new File(home, "lib/ruby/truffle/rubysl/rubysl-strscan/lib").toString()));

        // Shims
        loadPath.slowPush(truffleContext.makeString(new File(home, "lib/ruby/truffle/shims").toString()));

        // Load libraries required from the command line (-r LIBRARY)
        for (String requiredLibrary : truffleContext.getRuntime().getInstanceConfig().getRequiredLibraries()) {
            try {
                truffleContext.getFeatureManager().require(requiredLibrary, null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // Hook

        if (truffleContext.getHooks() != null) {
            truffleContext.getHooks().afterInit(truffleContext);
        }
    }

    @Override
    public Object execute(final Object self, final org.jruby.ast.RootNode rootNode) {
	return execute(TranslatorDriver.ParserContext.TOP_LEVEL, self, null, rootNode);
    }

    public Object execute(final TranslatorDriver.ParserContext parserContext, final Object self, final MaterializedFrame parentFrame, final org.jruby.ast.RootNode rootNode) {
        truffleContext.getCoreLibrary().getGlobalVariablesObject().getOperations().setInstanceVariable(
                truffleContext.getCoreLibrary().getGlobalVariablesObject(), "$0",
                truffleContext.toTruffle(runtime.getGlobalVariables().get("$0")));

        final String inputFile = rootNode.getPosition().getFile();
        final Source source;

        if (inputFile.equals("-e")) {
            // Assume UTF-8 for the moment
            source = Source.fromBytes(runtime.getInstanceConfig().inlineScript(), "-e", new BytesDecoder.UTF8BytesDecoder());
        } else {
            source = truffleContext.getSourceManager().forFile(inputFile);
        }

        truffleContext.getFeatureManager().setMainScriptSource(source);

        truffleContext.load(source, null, new NodeWrapper() {
            @Override
            public RubyNode wrap(RubyNode node) {
                RubyContext context = node.getContext();
                SourceSection sourceSection = node.getSourceSection();
                return SequenceNode.sequence(context, sourceSection,
                        new SetTopLevelBindingNode(context, sourceSection),
                        new TopLevelRaiseHandler(context, sourceSection, node));
            }
        });
        return truffleContext.getCoreLibrary().getNilObject();
    }

    @Override
    public Object toTruffle(IRubyObject object) {
        return truffleContext.toTruffle(object);
    }

    @Override
    public void shutdown() {
        try {
            truffleContext.shutdown();
        } catch (RaiseException e) {
            final RubyException rubyException = e.getRubyException();

            for (String line : Backtrace.DISPLAY_FORMATTER.format(e.getRubyException().getContext(), rubyException, rubyException.getBacktrace())) {
                System.err.println(line);
            }
        }
    }

    @Override
    public Packer parsePack(String format, boolean extended) {
        final CallTarget packCallTarget = new PackParser().parse(format, extended);

        return new Packer() {

            @Override
            public ByteList pack(Object[] store, int size) {
                return (ByteList) packCallTarget.call(store, size);
            }

        };
    }

}
