package ca.ualberta.maple.swan.spds.probe;

/** Represents a call edge in a call graph. */

public class CallEdge implements Comparable {
	/**
	 * @param src
	 *            The method that is the source of the call.
	 * @param dst
	 *            The method that is the target of the call.
	 */
	public CallEdge(ProbeMethod src, ProbeMethod dst) {
		if (src == null)
			throw new NullPointerException();
		if (dst == null)
			throw new NullPointerException();
		this.src = src;
		this.dst = dst;
	}

	/**
	 * @param src
	 *            The method that is the source of the call.
	 * @param dst
	 *            The method that is the target of the call.
	 * @param context
	 *            Optional value distinguishing edges based on the call site context.
	 */
	public CallEdge(ProbeMethod src, ProbeMethod dst, String context) {
		this.src = src;
		this.dst = dst;
		this.context = context;
	}

	/**
	 * @param src
	 *            The method that is the source of the call.
	 * @param dst
	 *            The method that is the target of the call.
	 * @param weight
	 *            Optional value expressing the importance of this edge for sorting purposes.
	 */
	public CallEdge(ProbeMethod src, ProbeMethod dst, double weight) {
		this.src = src;
		this.dst = dst;
		this.weight = weight;
	}

	/**
	 * @param src
	 *            The method that is the source of the call.
	 * @param dst
	 *            The method that is the target of the call.
	 * @param weight
	 *            Optional value expressing the importance of this edge for sorting purposes.
	 * @param context
	 *            Optional value distinguishing edges based on the call site context.
	 */
	public CallEdge(ProbeMethod src, ProbeMethod dst, double weight, String context) {
		this.src = src;
		this.dst = dst;
		this.context = context;
		this.weight = weight;
	}

	/** Returns the method that is the source of the call. */
	public ProbeMethod src() {
		return src;
	}

	/** Returns the method that is the target of the call. */
	public ProbeMethod dst() {
		return dst;
	}

	/**
	 * An optional weight value expressing how important this edge is for sorting purposes.
	 */
	public double weight() {
		return weight;
	}

	/**
	 * An optional context that distinguishes edges based on their call site location
	 * 
	 * @return
	 */
	public String context() {
		return context;
	}

	@Override
	public int hashCode() {
		return src.hashCode() + dst.hashCode() + context.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof CallEdge))
			return false;

		CallEdge other = (CallEdge) o;
		return src.equals(other.src) && dst.equals(other.dst) && equalContexts(other);
	}

	@Override
	public String toString() {
		if (weight != 0)
			return context + " :: " + src.toString() + " ===> " + dst.toString() + " " + weight;
		return context + " :: " + src.toString() + " ===> " + dst.toString();
	}

	/**
	 * Checks for context equality. It returns true if either contexts is unknown.
	 * 
	 * @param that
	 * @return
	 */
	public boolean equalContexts(CallEdge that) {
		return context.equals(UKNOWN_CONTEXT) || that.context.equals(UKNOWN_CONTEXT) ? true : context
				.equals(that.context);
	}

	public int compareTo(Object o) {
		if (!(o instanceof CallEdge))
			throw new RuntimeException();
		CallEdge e = (CallEdge) o;
		if (weight < e.weight)
			return -1;
		if (weight > e.weight)
			return 1;
		if (System.identityHashCode(this) < System.identityHashCode(e))
			return -1;
		if (System.identityHashCode(this) > System.identityHashCode(e))
			return 1;
		return 0;
	}

	/* End of public methods. */

	private ProbeMethod src;
	private ProbeMethod dst;
	private double weight = 0;
	private String context = UKNOWN_CONTEXT;
	public static final String UKNOWN_CONTEXT = "unknown: -1";
}
