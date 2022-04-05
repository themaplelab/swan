package ca.ualberta.maple.swan.spds.probe;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/** Writes a call graph to a text file. */
public class TextWriter {
	/** Write a call graph to a Text file. */
	public void write(CallGraph cg, OutputStream file) throws IOException {
		// UTF-8 important to handle methods with unicode characters
		PrintWriter out = new PrintWriter(new OutputStreamWriter(file, "UTF-8"), true);

		initializeMaps();

		// Collect up all the methods and classes appearing in the call graph.
		for (ProbeMethod m : cg.entryPoints()) {
			addMethod(m);
		}

		for (CallEdge e : cg.edges()) {
			addMethod(e.src());
			addMethod(e.dst());
		}

		// Assign ids to all method and class nodes.
		assignIDs();

		outputClasses(out);
		outputMethods(out);

		// Output entry points.
		for (ProbeMethod m : cg.entryPoints()) {
			out.println(Util.EntrypointTag);
			out.println(getId(m));
		}

		// Output call edges.
		for (CallEdge e : cg.edges()) {
			out.println(Util.EdgeTag);
			out.println(getId(e.src()));
			out.println(getId(e.dst()));
			out.println(e.weight());
			out.println(e.context());
		}

		file.close();
		out.close();
	}

	/** Write a set of polymorphic invoke instructions to a text file. */
	public void write(Polymorphic poly, OutputStream file) throws IOException {
		PrintWriter out = new PrintWriter(new OutputStreamWriter(file, "UTF-8"), true);

		initializeMaps();

		poly.stmts().forEach(s -> addStmt((ProbeStmt) s));

		assignIDs();

		outputClasses(out);
		outputMethods(out);
		outputStmts(out);

		file.close();
		out.close();
	}
	
	/** Read a call graph from a text file. */
	public void write(CallGraph cg, String file) throws IOException {
		if (file.endsWith("txt.gzip")) {
			write(cg, new GZIPOutputStream(new FileOutputStream(file)));
		} else if (file.endsWith("txt")) {
			write(cg, new FileOutputStream(file));
		} else if (file.endsWith("gxl.gzip") || file.endsWith("gxl")) {
			throw new IOException(
					"No longer using GXL as file format. It generates large files. We recommend using txt.gzip instead.");
		} else {
			throw new IOException("undefined file extension.");
		}
	}

	/* End of public methods. */

	private Set<ProbeField> fields;
	private Set<ProbeParameter> parameters;
	private Set<ProbeStmt> stmts;
	private Set<ProbeMethod> methods;
	private Set<ProbeClass> classes;
	private Set<ProbePtSet> ptsets;
	private Set<ProbeFieldSet> fieldsets;
	private Map<Object, Integer> idMap;

	private void addStmt( ProbeStmt s ) {
		stmts.add(s);
		addMethod( s.method() );
	}

	private void addMethod(ProbeMethod m) {
		methods.add(m);
		addClass(m.cls());
	}

	private void addClass(ProbeClass c) {
		classes.add(c);
	}

	private String getId(ProbeStmt s) {
		Integer id = idMap.get(s);
		return "id" + id.toString();
	}

	private String getId(ProbeMethod m) {
		Integer id = idMap.get(m);
		return "id" + id.toString();
	}

	private String getId(ProbeClass cl) {
		Integer id = idMap.get(cl);
		return "id" + id.toString();
	}

	private void outputClasses(PrintWriter out) {
		for (ProbeClass cl : classes) {
			outputClass(out, cl);
		}
	}

	private void outputClass(PrintWriter out, ProbeClass cl) {
		out.println(Util.ClassTag);
		out.println(getId(cl));
		out.println(cl.pkg());
		out.println(cl.name());
	}

	private void outputMethods(PrintWriter out) {
		for (ProbeMethod m : methods) {
			outputMethod(out, m);
		}
	}

	private void outputMethod(PrintWriter out, ProbeMethod m) {
		out.println(Util.MethodTag);
		out.println(getId(m));
		out.println(m.name());
		out.println(m.signature());
		out.println(getId(m.cls()));
	}

	private void outputStmts(PrintWriter out) {
		for (ProbeStmt s : stmts) {
			outputStmt(out, s);
		}
	}

	private void outputStmt(PrintWriter out, ProbeStmt s) {
		out.println(Util.StmtTag);
		out.println(getId(s));
		out.println(s.offset());
		out.println(getId(s.method()));
	}

	private void initializeMaps() {
		stmts = new HashSet<ProbeStmt>();
		fields = new HashSet<ProbeField>();
		methods = new HashSet<ProbeMethod>();
		classes = new HashSet<ProbeClass>();
		parameters = new HashSet<ProbeParameter>();
		ptsets = new HashSet<ProbePtSet>();
		fieldsets = new HashSet<ProbeFieldSet>();
	}

	/** Assign ids to all method and class nodes. */
	private void assignIDs() {
		int id = 1;
		idMap = new HashMap<Object, Integer>();
		for (ProbeStmt s : stmts) {
			idMap.put(s, new Integer(id++));
		}
		for (ProbeMethod m : methods) {
			idMap.put(m, new Integer(id++));
		}
		for (ProbeField f : fields) {
			idMap.put(f, new Integer(id++));
		}
		for (ProbeClass cl : classes) {
			idMap.put(cl, new Integer(id++));
		}
		for (ProbeParameter p : parameters) {
			idMap.put(p, new Integer(id++));
		}
		for (ProbePtSet p : ptsets) {
			idMap.put(p, new Integer(id++));
		}
		for (ProbeFieldSet p : fieldsets) {
			idMap.put(p, new Integer(id++));
		}
	}
}
