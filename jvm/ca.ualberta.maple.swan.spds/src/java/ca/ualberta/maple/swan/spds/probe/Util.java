package ca.ualberta.maple.swan.spds.probe;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashSet;

/** Utility methods. */
public class Util {
	public static final String MethodTag = "METHOD";
	public static final String ClassTag = "CLASS";
	public static final String EntrypointTag = "ENTRYPOINT";
	public static final String EdgeTag = "CALLEDGE";
	public static final String StmtTag = "STATEMENT";

	public static final PrintStream out = init();

	private static PrintStream init() {
		try {
			return new PrintStream(System.out, true, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new Error(e);
		}
	}

	public static Collection<String> readLib(String filename) {
		if (filename == null)
			return null;
		Collection<String> ret = new HashSet<String>();
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(filename)));
			while (true) {
				String line = in.readLine();
				if (line == null)
					break;
				ret.add(line);
			}
			in.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return ret;
	}

	public static Collection filterLibs(Collection<String> libs, Collection items) {
		if (libs == null)
			return items;
		Collection ret = new HashSet();
		for (Object item : items) {
			if (item instanceof CallEdge) {
				item = ((CallEdge) item).src();
			}
			if (item instanceof ProbeStmt) {
				item = ((ProbeStmt) item).method();
			}
			if (item instanceof ProbeMethod) {
				item = ((ProbeMethod) item).cls();
			}
			if (item instanceof ProbeClass) {
				if (!libs.contains(((ProbeClass) item).pkg()))
					ret.add(item);
			} else
				throw new RuntimeException("unrecognized item " + item + " of type " + item.getClass());
		}
		return ret;
	}
}
