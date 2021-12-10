package ca.ualberta.maple.swan.spds.probe;

import java.io.*;
import java.util.*;

public abstract class AbsEdgeWeights {
    public abstract double weight(ProbeMethod pm);
    public abstract double weight(CallEdge fe);
    protected void outputDotGraph(Collection edges, double[] level) {
        outputDotGraph(edges, level, 0);
    }
    private String zeroPad(int i, int digits) {
        String ret = ""+i;
        while(ret.length() < digits) ret = "0"+ret;
        return ret;
    }
    protected void outputDotGraph(Collection edges, double[] level, int iteration) {
        try {
            PrintStream out = new PrintStream(new FileOutputStream("cgdiff"+zeroPad(iteration, 4)+".dot"));
            out.println("digraph G {");
            out.println("rotate=90;");
            out.println("0 [ style=invis ];");
            for(int i = 1; i < n; i++) {
                if(subgraphReachables.contains(numToMethod.get(i))) {
                    out.println(""+i+" [ fillcolor=\"red\", style=\"filled\", label=\"\" ];");
                } else {
                    out.println(""+i+" [ fillcolor=\"gray"+(100-((int)(level[i]*100)))+"\", style=\"filled\", label=\"\" ];");
                }
            }
            for( Iterator edgeIt = edges.iterator(); edgeIt.hasNext(); ) {
                final FlowEdge edge = (FlowEdge) edgeIt.next();
                out.println(""+edge.src+" -> "+edge.dst+" [ style=\"setlinewidth("+(edge.cumulative*3)+")\", color=\"blue\" ];");
            }
            out.println("}");
            out.close();
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }
    int n = 0;
    double[] level;
    ArrayList numToMethod = new ArrayList();
    Collection subgraphReachables;
    public class FlowEdge {
        public int src;
        public int dst;
        public int heapPos = 0;
        public double cumulative;
        public String toString() {
            return cumulative+" "+numToMethod.get(src)+" ==> " +numToMethod.get(dst);
        }
        public FlowEdge( int src, int dst ) { this.src = src; this.dst = dst; }
        public double getFlow() {
            return level[src] - level[dst];
        }
        public void doFlow() {
            double diff = level[src] - level[dst];
            diff /= 2;
            diff /= 4;
            doFlow(diff);
        }
        public void doFlow(double diff) {
            cumulative += diff;
            level[src] -= diff;
            level[dst] += diff;
        }
    }
}
