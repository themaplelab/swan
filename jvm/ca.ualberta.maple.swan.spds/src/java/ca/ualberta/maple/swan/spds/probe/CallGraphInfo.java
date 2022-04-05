package ca.ualberta.maple.swan.spds.probe;

import java.util.*;
import java.io.*;
import java.util.zip.*;

/** Calculates and reports the differences between two call graphs. */
public class CallGraphInfo {
	public static void usage() {
		Util.out.println("Usage: java probe.CallGraphInfo [options] graph.*");
		Util.out.println("  -m : print list of reachable methods");
		Util.out.println("  -e : print list of entry points");
		Util.out.println("  -j : ignore the Java standard library");
		Util.out.println("  -g : print list of call edges");
		Util.out.println("  -lib file : ignore methods in packages listed in file");
		Util.out.println("  -u : print list of unreachable methods");
		System.exit(1);
	}

	public static String dashLib = null;
	public static boolean dashM = false;
	public static boolean dashE = false;
	public static boolean dashG = false;
	public static boolean dashJ = false;
	public static boolean dashU = false;

	public static final void main(String[] args) throws UnsupportedEncodingException {
		boolean doneOptions = false;
		String filename = null;
		for (int i = 0; i < args.length; i++) {
			if (!doneOptions && args[i].equals("-lib"))
				dashLib = args[++i];
			else if (!doneOptions && args[i].equals("-m"))
				dashM = true;
			else if (!doneOptions && args[i].equals("-u"))
				dashU = true;
			else if (!doneOptions && args[i].equals("-e"))
				dashE = true;
			else if (!doneOptions && args[i].equals("-g"))
				dashG = true;
			else if (!doneOptions && args[i].equals("-j"))
				dashJ = true;
			else if (!doneOptions && args[i].equals("--"))
				doneOptions = true;
			else if (filename == null)
				filename = args[i];
			else
				usage();
		}
		if (filename == null)
			usage();

		Collection<String> libs = Util.readLib(dashLib);

		CallGraph a;

		try {
			if (filename.endsWith("txt.gzip")) {
				a = new TextReader().readCallGraph(new GZIPInputStream(new FileInputStream(filename)));
			} else if (filename.endsWith("txt")) {
				a = new TextReader().readCallGraph(new FileInputStream(filename));
			} else {
				throw new IOException("undefined file extension.");
			}
		} catch (IOException e) {
			throw new RuntimeException("caught IOException " + e);
		}

		Set<ProbeMethod> methods = a.nodes();

		if (dashJ) {
			for (Iterator<CallEdge> edgeIt = a.edges().iterator(); edgeIt.hasNext();) {
				final CallEdge edge = edgeIt.next();
				if (edge.dst().cls().pkg().startsWith("java.")) {
					edgeIt.remove();
					methods.remove(edge.src());
					methods.remove(edge.dst());
				}
			}
			
			for (Iterator<ProbeMethod> methodIt = a.entryPoints().iterator(); methodIt.hasNext();) {
				final ProbeMethod m = methodIt.next();
				if (m.cls().pkg().startsWith("java.")) {
					methodIt.remove();
					methods.remove(m);
				}
			}
		}

		Collection ep = Util.filterLibs(libs, a.entryPoints());
		Util.out.println("Entry points     : " + Util.filterLibs(libs, ep).size());
		Util.out.println("Edges            : " + Util.filterLibs(libs, a.edges()).size());
		Util.out.println("Methods          : " + Util.filterLibs(libs, methods).size());
		Collection rm = Util.filterLibs(libs, a.findReachables());
		Util.out.println("Reachable methods: " + rm.size());

		if (dashE) {
			Util.out.println("Entry points: ");
			for (Iterator pmIt = ep.iterator(); pmIt.hasNext();) {
				final ProbeMethod pm = (ProbeMethod) pmIt.next();
				Util.out.println(pm);
			}
		}

		if (dashM) {
			Util.out.println("Reachable methods: ");
			for (Iterator pmIt = rm.iterator(); pmIt.hasNext();) {
				final ProbeMethod pm = (ProbeMethod) pmIt.next();
				Util.out.println(pm);
			}
		}
		
		if (dashG) {
			Util.out.println("Call Edges: ");
			for (Iterator pmIt = a.edges().iterator(); pmIt.hasNext();) {
				final CallEdge pm = (CallEdge) pmIt.next();
				Util.out.println(pm);
			}
		}
		
		if(dashU) {
			Util.out.println("Unreachable methods: ");
			Collection um = methods;
			um.removeAll(rm);
			um = Util.filterLibs(libs, um);
			for (Iterator pmIt = um.iterator(); pmIt.hasNext();) {
				final ProbeMethod pm = (ProbeMethod) pmIt.next();
				Util.out.println(pm);
			}
		}
	}

}
