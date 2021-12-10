package ca.ualberta.maple.swan.spds.probe;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Reads a call graph from a text file. */
public class TextReader {
	/** Read a call graph from a text file. */
	public CallGraph readCallGraph(InputStream file) throws IOException {

		BufferedReader in = new BufferedReader(new InputStreamReader(file, "UTF-8"));
		CallGraph ret = new CallGraph();

		while (true) {
			String line = in.readLine();
			if (line == null)
				break;

			if (line.equals(Util.ClassTag)) {
				String id = in.readLine();
				String pkg = in.readLine();
				String name = in.readLine();

				ProbeClass cls = ObjectManager.v().getClass(pkg, name);
				nodeToClass.put(id, cls);
			} else if (line.equals(Util.MethodTag)) {
				String id = in.readLine();
				String name = in.readLine();
				String signature = in.readLine();
				String cls = in.readLine();

				ProbeMethod m = ObjectManager.v().getMethod(nodeToClass.get(cls), name, signature);
				nodeToMethod.put(id, m);
			} else if (line.equals(Util.EntrypointTag)) {
				String id = in.readLine();

				ret.entryPoints().add(nodeToMethod.get(id));
			} else if (line.equals(Util.EdgeTag)) {
				String src = in.readLine();
				String dst = in.readLine();
				String weight = in.readLine();
				String context = in.readLine();

				ret.edges()
						.add(new CallEdge(nodeToMethod.get(src), nodeToMethod.get(dst), Double.parseDouble(weight),
								context));
			} else {
				throw new RuntimeException("Unexpected line: " + line);
			}
		}

		in.close();
		return ret;
	}

	/** Read a set of polymorphic call sites from a text file */
	public Polymorphic readPolymorphic(InputStream file) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(file, "UTF-8"));
		Polymorphic ret = new Polymorphic();

		while (true) {
			String line = in.readLine();
			if (line == null) {
				break;
			}

			if (line.equals(Util.ClassTag)) {
				String id = in.readLine();
				String pkg = in.readLine();
				String name = in.readLine();

				ProbeClass cls = ObjectManager.v().getClass(pkg, name);
				nodeToClass.put(id, cls);

			} else if (line.equals(Util.MethodTag)) {
				String id = in.readLine();
				String name = in.readLine();
				String signature = in.readLine();
				String cls = in.readLine();

				ProbeMethod m = ObjectManager.v().getMethod(nodeToClass.get(cls), name, signature);
				nodeToMethod.put(id, m);

			} else if (line.equals(Util.StmtTag)) {
				String id = in.readLine();
				String offset = in.readLine();
				String method = in.readLine();

				ProbeStmt s = ObjectManager.v().getStmt(nodeToMethod.get(method), Integer.parseInt(offset));
				ret.stmts().add(s);

			} else {
				throw new RuntimeException("Unexpected line: " + line);
			}
		}

		in.close();
		return ret;
	}

	/** Read a call graph from a text file. */
	public CallGraph readCallGraph(String file) throws IOException {
		if (file.endsWith("txt.gzip")) {
			return readCallGraph(new GZIPInputStream(new FileInputStream(file)));
		} else if (file.endsWith("txt")) {
			return readCallGraph(new FileInputStream(file));
		} else if (file.endsWith("gxl.gzip") || file.endsWith("gxl")) {
			throw new IOException(
					"No longer using GXL as file format. It generates large files. We recommend using txt.gzip instead.");
		} else {
			throw new IOException("undefined file extension.");
		}
	}

	/** Read a set of polymorphic call sites from a text file */
	public Polymorphic readPolymorphic(String file) throws IOException {
		if (file.endsWith("txt.gzip")) {
			return readPolymorphic(new GZIPInputStream(new FileInputStream(file)));
		} else if (file.endsWith("txt")) {
			return readPolymorphic(new FileInputStream(file));
		} else if (file.endsWith("gxl.gzip") || file.endsWith("gxl")) {
			throw new IOException(
					"No longer using GXL as file format. It generates large files. We recommend using txt.gzip instead.");
		} else {
			throw new IOException("undefined file extension.");
		}
	}

	/* End of public methods. */

	private Map<String, ProbeClass> nodeToClass = new HashMap<String, ProbeClass>();
	private Map<String, ProbeMethod> nodeToMethod = new HashMap<String, ProbeMethod>();
}
