/******************************************************************************
 * Copyright (c) 2017 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 		IBM Corporation - initial API and implementation
 *		Ao Li (Github: Leeleo3x) - fixes
 *		Mark Mroz - translator entity component
 *****************************************************************************/

package ca.maple.swan.analysis.translator;

import com.ibm.wala.cast.tree.CAstType;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import java.util.*;
import com.ibm.wala.cast.tree.CAstQualifier;
import com.ibm.wala.cast.tree.CAstNodeTypeMap;
import com.ibm.wala.cast.tree.CAstAnnotation;
import com.ibm.wala.cast.tree.CAstControlFlowMap;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.tree.CAstSourcePositionMap.Position;
import com.ibm.wala.cast.ir.translator.NativeTranslatorToCAst;
import com.ibm.wala.cast.tree.CAst;
import com.ibm.wala.cast.tree.CAstEntity;
import com.ibm.wala.cast.tree.impl.CAstImpl;
import com.ibm.wala.cast.tree.rewrite.CAstRewriter.CopyKey;
import com.ibm.wala.cast.tree.rewrite.CAstRewriter.RewriteContext;
import com.ibm.wala.cast.tree.rewrite.CAstRewriterFactory;
import com.ibm.wala.cast.util.CAstPrinter;
import com.ibm.wala.cast.tree.CAstNode;
import java.util.ArrayList;

public class SwiftToCAstTranslator extends NativeTranslatorToCAst {

	public SwiftToCAstTranslator(String fileName) throws MalformedURLException {
		this(new CAstImpl(), new File(fileName).toURI().toURL(), fileName);
	}

	private SwiftToCAstTranslator(CAst Ast, URL sourceURL, String sourceFileName) {
		super(Ast, sourceURL, sourceFileName);
	}

	@Override
	public <C extends RewriteContext<K>, K extends CopyKey<K>> void addRewriter(CAstRewriterFactory<C, K> factory,
			boolean prepend) {
		assert false;
	}
	private native void translateToCAstNodes();

	@Override
	public CAstEntity translateToCAst() {
		return new CAstEntity() {

        @Override
        public int getKind() {
          return CAstEntity.FUNCTION_ENTITY;
        }

        @Override
        public String getName() {
          return sourceURL.getFile();
        }

        @Override
        public String getSignature() {
          return "()";
        }

        @Override
        public String[] getArgumentNames() {
           return new String[0];
        }

        @Override
        public CAstNode[] getArgumentDefaults() {
          return new CAstNode[0];
        }

        @Override
        public int getArgumentCount() {
          return 0;
        }

        @Override
        public Map<CAstNode, Collection<CAstEntity>> getAllScopedEntities() {
          return Collections.emptyMap();
        }

        @Override
        public Iterator<CAstEntity> getScopedEntities(CAstNode construct) {
          return Collections.emptyIterator();
        }

        private CAstNode ast;

        @Override
        public CAstNode getAST() {
          if (ast == null) {
          	//ast = translateToCAstNodes().get(0);
          }
          return ast;
        }

        @Override
        public CAstControlFlowMap getControlFlow() {
          // TODO Auto-generated method stub
          return null;
        }

        @Override
        public CAstSourcePositionMap getSourceMap() {
          // TODO Auto-generated method stub
          return null;
        }

        @Override
        public Position getPosition() {
          // TODO Auto-generated method stub
          return null;
        }

        @Override
        public CAstNodeTypeMap getNodeTypeMap() {
          // TODO Auto-generated method stub
          return null;
        }

        @Override
        public Collection<CAstQualifier> getQualifiers() {
          // TODO Auto-generated method stub
          return null;
        }

        @Override
        public CAstType getType() {
          // TODO Auto-generated method stub
          return null;
        }

        @Override
        public Collection<CAstAnnotation> getAnnotations() {
          // TODO Auto-generated method stub
          return null;
        }

        @Override
        public Position getPosition(int arg) {
          // TODO Auto-generated method stub
          return null;
        }

        @Override
        public Position getNamePosition() {
          // TODO Auto-generated method stub
          return null;
        }
      };
	}

	static {
        String sharedDir = "/ca.maple.swan.translator/build/libs/swiftWala/shared/";
        String libName = "libswiftWala";

        String SWANDir = "";
        try {
            SWANDir = System.getenv("SWAN");
        } catch (Exception e) {
            System.err.println("Error: SWAN path not set! Exiting...");
            System.exit(1);
        }

        // try to load both dylib and so (instead of checking OS)
        try {
            System.load(SWANDir + sharedDir + libName + ".dylib");
        } catch (Exception dylibException) {
            try {
                System.load(SWANDir + sharedDir + libName + ".so");
            } catch (Exception soException) {
                System.err.println("Could not find shared library!");
                soException.printStackTrace();
            }
        }
	}

	public static void main(String[] args) {
	    if (args.length != 1) {
	        System.err.println("Usage: One input file expected!");
        }
		else {
			try {
				SwiftToCAstTranslator translator = new SwiftToCAstTranslator(args[0]);
				translator.translateToCAstNodes();
			}
			catch (Exception e) {
				e.printStackTrace(System.out);
			}
		}
	}

}
