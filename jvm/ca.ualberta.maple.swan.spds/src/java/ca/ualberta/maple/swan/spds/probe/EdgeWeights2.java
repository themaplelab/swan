package ca.ualberta.maple.swan.spds.probe;

import java.io.*;
import java.util.*;

public class EdgeWeights2 extends AbsEdgeWeights {
    public static final double THRESHOLD = 0.001;
    Map methodToNum = new HashMap();
    private void addMethod(ProbeMethod m) {
        if( methodToNum.get(m) == null ) {
            methodToNum.put(m, new Integer(n++));
            numToMethod.add(m);
        }
    }
    private int n(ProbeMethod m) {
        Integer ret = (Integer) methodToNum.get(m);
        return ret.intValue();
    }
    int m = 0;
    public abstract class FlowEdges {
        public abstract int key( FlowEdge e );
        public FlowEdges() {
            edges = new FlowEdge[m];
            index = new int[n];
        }
        int[] index;
        FlowEdge[] edges;
        int i = 0;
        public void add( FlowEdge fe ) {
            edges[i++] = fe;
        }
        public void doneAdding() {
            Arrays.sort(edges, new Comparator() {
                public int compare(Object o1, Object o2) {
                    FlowEdge fe1 = (FlowEdge) o1;
                    FlowEdge fe2 = (FlowEdge) o2;
                    if( key(fe1) != key(fe2) ) return key(fe1) - key(fe2);
                    if( fe1.src != fe2.src ) return fe1.src - fe2.src;
                    return fe1.dst - fe2.dst;
                }
            });
            for( int i = 0; i < n; i++ ) index[i] = m;
            for( int i = 0; i < m; i++ ) {
                if( index[key(edges[i])] == m ) index[key(edges[i])] = i;
            }
        }
        public void get( int key, List out ) {
            for( int i = index[key]; i < m && key(edges[i]) == key; i++ ) {
                out.add(edges[i]);
            }
        }
        public FlowEdge find( int src, int dst ) {
            for( int i = index[src]; i < m && edges[i].src == src; i++ ) {
                if( edges[i].dst == dst ) return edges[i];
            }
            return null;
        }
    }

    FlowEdges in;
    FlowEdges out;
    CallGraph supergraph;
    CallGraph subgraph;
    boolean dotGraph;
    public EdgeWeights2(CallGraph supergraph, CallGraph subgraph) {
        this(supergraph, subgraph, false);
    }
    public EdgeWeights2(CallGraph supergraph, CallGraph subgraph, boolean dotGraph) {
        this.supergraph = supergraph;
        this.subgraph = subgraph;
        this.dotGraph = dotGraph;
        subgraphReachables = subgraph.findReachables();
        addMethod(null);
        TreeSet edges = new TreeSet( new Comparator() {
            public int compare(Object o1, Object o2) {
                FlowEdge fe1 = (FlowEdge) o1;
                FlowEdge fe2 = (FlowEdge) o2;
                if( fe1.src != fe2.src ) return fe1.src - fe2.src;
                return fe1.dst - fe2.dst;
            }
        } );
        for( Iterator edgeIt = supergraph.edges().iterator(); edgeIt.hasNext(); ) {
            final CallEdge edge = (CallEdge) edgeIt.next();
            FlowEdge fe;
            addMethod(edge.dst());
            addMethod(edge.src());
            fe = new FlowEdge(n(edge.dst()), n(edge.src()));
            edges.add(fe);
        }
        for( Iterator entryIt = supergraph.entryPoints().iterator(); entryIt.hasNext(); ) {
            final ProbeMethod entry = (ProbeMethod) entryIt.next();
            addMethod(entry);
            FlowEdge fe = new FlowEdge(n(entry), 0);
            edges.add(fe);
        }
        m = edges.size();
        Heap heap = new Heap();
        level = new double[n];
        for( int i = 1; i < n; i++ ) {
            if( !subgraphReachables.contains(numToMethod.get(i)) ) {
                level[i] = 1;
            }
        }

        in = new FlowEdges() {
            public int key( FlowEdge fe ) { return fe.dst; }
        };
        out = new FlowEdges() {
            public int key( FlowEdge fe ) { return fe.src; }
        };
        for( Iterator edgeIt = edges.iterator(); edgeIt.hasNext(); ) {
            final FlowEdge edge = (FlowEdge) edgeIt.next();
            in.add(edge);
            out.add(edge);
        }
        for( Iterator iIt = methodToNum.values().iterator(); iIt.hasNext(); ) {
            final Integer i = (Integer) iIt.next();
            heap.add(node(i.intValue()));
        }
            
        in.doneAdding();
        out.doneAdding();
        heap.doneAdding();
        int iterations = 0;
        while(true) {
            Node n = (Node) heap.min();
            double flow = n.getFlow();
            if( flow < THRESHOLD ) break;
            //if(dotGraph && ((iterations%100) == 0)) {
            if(dotGraph) {
                outputDotGraph(edges, level, iterations);
            }
            iterations++;
            if( 0 == (iterations%1000) ) System.out.println("Iteration: "+iterations+" Flow: "+n.getFlow() );

            List remove = new ArrayList();
            remove.add(n);

            List removeEdges;

            removeEdges = new ArrayList();
            in.get(n.index, removeEdges);
            in.get(0,removeEdges);
            for( Iterator eIt = new ArrayList(removeEdges).iterator(); eIt.hasNext(); ) {
                final FlowEdge e = (FlowEdge) eIt.next();
                remove.add(node(e.src));
            }

            removeEdges = new ArrayList();
            out.get(n.index, removeEdges);
            for( Iterator eIt = new ArrayList(removeEdges).iterator(); eIt.hasNext(); ) {
                final FlowEdge e = (FlowEdge) eIt.next();
                remove.add(node(e.dst));
            }

            heap.removeAll(remove);

            n.doFlow();
            for( Iterator feIt = removeEdges.iterator(); feIt.hasNext(); ) {
                final FlowEdge fe = (FlowEdge) feIt.next();
                if( subgraphReachables.contains(numToMethod.get(fe.dst)) ) level[fe.dst] = 0;
            }
            level[0] = 0;
            heap.addAll(remove);
        }
        check();
        if(dotGraph) {
            outputDotGraph(edges, level, iterations);
        }
    }
    private FlowEdge find( int src, int dst ) {
        return out.find(src, dst);
    }
    public double weight(ProbeMethod m) {
        FlowEdge fe = find(n(m), 0);
        return fe.cumulative;
    }
    public double weight(CallEdge e) {
        FlowEdge fe;
        fe = find(n(e.dst()), n(e.src()));
        return fe.cumulative;
    }
    private void check() {
        for( int i = 0; i < n; i++ ) {
            double val = 1;
            List list = new ArrayList();
            in.get(i,list);
            for( Iterator feIt = list.iterator(); feIt.hasNext(); ) {
                final FlowEdge fe = (FlowEdge) feIt.next();
                val += fe.cumulative;
            }
            list = new ArrayList();
            out.get(i,list);
            for( Iterator feIt = list.iterator(); feIt.hasNext(); ) {
                final FlowEdge fe = (FlowEdge) feIt.next();
                val -= fe.cumulative;
            }
            if( i == 0 ) val = 0;
            if( subgraphReachables.contains(numToMethod.get(i))) val = 0;
            if( Math.abs(val - level[i]) > .0001 ) throw new RuntimeException( "Level of "+numToMethod.get(i)+" is "+level[i]+" but should be "+val );
        }
    }
    public Node node(int index) {
        Node ret = (Node) nodeMap.get(new Integer(index));
        if( ret == null ) {
            nodeMap.put(new Integer(index), ret = new Node(index));
        }
        return ret;
    }
    Map nodeMap = new HashMap();
    public class Node {
        public int heapPos = 0;
        private final int index;
        private Node( int index ) { this.index = index; }
        public double getFlow() {
            double max = 0;
            List edgesOut = new ArrayList();
            out.get(index, edgesOut);
            for( Iterator feIt = edgesOut.iterator(); feIt.hasNext(); ) {
                final FlowEdge fe = (FlowEdge) feIt.next();
                double flow = fe.getFlow();
                if( flow > max ) max = flow;
            }
            return max;
        }
        public void doFlow() {
            List edgesOut = new ArrayList();
            out.get(index, edgesOut);
            while(true) {
                double min = 10E30;
                int targets = 0;
                for( Iterator feIt = edgesOut.iterator(); feIt.hasNext(); ) {
                    final FlowEdge fe = (FlowEdge) feIt.next();
                    double flow = fe.getFlow();
                    if( flow > THRESHOLD ) {
                        targets++;
                        if( flow < min ) min = flow;
                    }
                }
                if( targets == 0 ) break;
                min /= targets+1;
                for( Iterator feIt = edgesOut.iterator(); feIt.hasNext(); ) {
                    final FlowEdge fe = (FlowEdge) feIt.next();
                    double flow = fe.getFlow();
                    if( flow > 0 ) {
                        fe.doFlow(min);
                    }
                }
            }
        }
    }
    public class Heap {
        private Node[] heap = new Node[n+1];
        private int size = 0;
        public void add( Node fe ) {
            set(++size, fe);
        }
        public void doneAdding() {
            for( int i = size; i > 0; i-- ) heapify(i);
        }
        private void heapify(int i) {
            sanity();
            int l = left(i);
            int r = right(i);
            int largest;
            if( l <= size && heap[l].getFlow() > heap[i].getFlow() ) {
                largest = l;
            } else {
                largest = i;
            }
            if( r <= size && heap[r].getFlow() > heap[largest].getFlow() ) {
                largest = r;
            }
            if( largest != i ) {
                Node iEdge = heap[i];
                Node largestEdge = heap[largest];
                set(i, largestEdge);
                set(largest, iEdge);
                heapify(largest);
            }
            sanity();
        }
        public Node min() {
            return heap[1];
        }
        public void remove(Node fe) {
            sanity();
            int toRemove = fe.heapPos;
            if( toRemove == 0 ) return;
            if( heap[toRemove] != fe ) throw new RuntimeException();
            if( toRemove > size ) throw new RuntimeException();
            set(toRemove, heap[size]);
            heap[size] = null;
            if( toRemove == size ) fe.heapPos = 0;
            size--;
            if( size < 0 ) throw new RuntimeException();
            heapify(toRemove);
            if( fe.heapPos != 0 ) throw new RuntimeException();
            sanity();
        }
        public void insert(Node fe) {
            if( fe.heapPos != 0 ) return;
            size++;
            int i = size;
            while( i > 1 && heap[parent(i)].getFlow() < fe.getFlow() ) {
                set(i, heap[parent(i)]);
                i = parent(i);
            }
            set(i, fe);
            sanity();
        }
        private void set(int pos, Node fe) {
            if( fe == null ) {
                for( int i = 1; i < size; i++ ) System.out.println(i+":"+heap[i]);
                throw new RuntimeException();
            }
            if( heap[pos] != null ) heap[pos].heapPos = 0;
            heap[fe.heapPos] = null;
            heap[pos] = fe;
            fe.heapPos = pos;
        }
        private int left(int i) { return 2*i; }
        private int right(int i) { return 2*i+1; }
        private int parent(int i) { return i/2; }
        public void removeAll( Collection c ) {
            for( Iterator feIt = c.iterator(); feIt.hasNext(); ) {
                final Node fe = (Node) feIt.next();
                remove(fe);
            }
        }
        public void addAll( Collection c ) {
            for( Iterator feIt = c.iterator(); feIt.hasNext(); ) {
                final Node fe = (Node) feIt.next();
                insert(fe);
            }
        }
        private void sanity() {
            if( false ) {
                for( int i = 0; i < heap.length; i++ ) {
                    if( i == 0 || i > size ) {
                        if( heap[i] != null ) throw new RuntimeException( ""+i+" is "+heap[i]+" instead of null");
                    } else {
                        if( heap[i] == null ) throw new RuntimeException( ""+i+" is null" );
                        if( heap[i].heapPos != i ) throw new RuntimeException( ""+i+" has pos "+heap[i].heapPos );
                    }
                }
            }
        }
    }
}
