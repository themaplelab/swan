package ca.maple.swan.swift.visualization;

import com.ibm.wala.cast.ir.translator.AbstractCodeEntity;
import com.ibm.wala.cast.tree.CAstNode;
import com.ibm.wala.cast.util.CAstPrinter;
import guru.nidi.graphviz.attribute.Attributes;
import guru.nidi.graphviz.attribute.Color;
import guru.nidi.graphviz.attribute.Label;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.model.MutableNode;

import java.io.File;
import java.util.ArrayList;

import static guru.nidi.graphviz.model.Factory.mutGraph;
import static guru.nidi.graphviz.model.Factory.mutNode;

public class ASTtoDot {

    public static void print(ArrayList<AbstractCodeEntity> entities) {
        try {
            File dir = new File("dot/");
            if (!dir.exists()) {
                dir.mkdir();
            } else {
                for(String f: dir.list()) {
                    new File(dir.getPath(), f).delete();
                }
                dir.delete();
                dir.mkdir();
            }
            File dotFile = new File("dot/" + entities.get(0).getName().replaceAll(" ", "_") + ".png");
            dotFile.createNewFile();
            MutableGraph g = mutGraph(entities.get(0).getName()).setDirected(true);

            for (AbstractCodeEntity entity: entities) {
                MutableNode mNode = mutNode(entity.getName()).add(Color.RED);
                buildGraph(mNode, entity.getAST());
                g.add(mNode);
            }

            Graphviz.fromGraph(g).render(Format.PNG).toFile(dotFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void buildGraph(MutableNode mNode, CAstNode cNode) {
        MutableNode newNode;
        String hash = System.identityHashCode(cNode) + mNode.hashCode() + "\n";
        switch (cNode.getKind()) {
            case CAstNode.DECL_STMT: {
                return;
            }
            case CAstNode.CONSTANT: {
                newNode = mutNode(hash + cNode.toString());
                break;
            }
            case CAstNode.BLOCK_STMT: {
                newNode = mutNode(hash + cNode.getChild(0).getChild(0).getValue());
                mNode.addLink(newNode);
                for (CAstNode child: cNode.getChildren()) {
                    buildGraph(newNode, child);
                }
                return;
            }
            case CAstNode.LABEL_STMT: {
                return;
            }
            case CAstNode.VAR: {
                newNode = mutNode(hash + CAstPrinter.kindAsString(cNode.getKind()));
                break;
            }
            case CAstNode.ASSIGN: {
                if ((cNode.getChild(0).getKind() == CAstNode.VAR) && (cNode.getChild(1).getKind() == CAstNode.VAR)) {
                    newNode = mutNode(hash + CAstPrinter.kindAsString(cNode.getKind()) +
                            "\n" + cNode.getChild(0).getChild(0).getValue() + "  <-- " + cNode.getChild(1).getChild(0).getValue());
                    mNode.addLink(newNode);
                    return;
                } else {
                    newNode = mutNode(hash + CAstPrinter.kindAsString(cNode.getKind()));
                }
                break;
            }
            default: {
                newNode = mutNode(hash + CAstPrinter.kindAsString(cNode.getKind()));
                break;
            }
        }
        mNode.addLink(newNode);
        for (CAstNode child: cNode.getChildren()) {
            buildGraph(newNode, child);
        }
    }
}
