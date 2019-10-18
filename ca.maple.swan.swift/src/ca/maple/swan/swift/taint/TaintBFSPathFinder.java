package ca.maple.swan.swift.taint;

import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.util.collections.FilterIterator;
import com.ibm.wala.util.graph.Graph;

import java.util.Iterator;
import java.util.function.Predicate;

public class TaintBFSPathFinder extends com.ibm.wala.util.graph.traverse.BFSPathFinder<Statement> {

    private final TaintSolver S;
    private final Graph<Statement> G;

    public TaintBFSPathFinder(Graph<Statement> g, Statement src, Statement target, TaintSolver s) {
        super(g, src, target);
        this.S = s;
        this.G = g;
    }

    @Override
    protected Iterator<? extends Statement> getConnected(Statement n) {
        return new FilterIterator<>(G.getSuccNodes(n), new Predicate<Statement>() {
            @Override
            public boolean test(Statement statement) {
                return S.getOut(n).isTainted();
            }
        });
    }

}
