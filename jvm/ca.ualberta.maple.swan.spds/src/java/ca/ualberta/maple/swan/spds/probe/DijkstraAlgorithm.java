package ca.ualberta.maple.swan.spds.probe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DijkstraAlgorithm {

	private final List<CallEdge> edges;
	private Set<ProbeMethod> settledNodes;
	private Set<ProbeMethod> unSettledNodes;
	private Map<ProbeMethod, ProbeMethod> predecessors;
	private Map<ProbeMethod, Integer> distance;

	public DijkstraAlgorithm(CallGraph graph) {
		this.edges = new ArrayList<CallEdge>(graph.edges());
	}

	public void execute(ProbeMethod source) {
		settledNodes = new HashSet<ProbeMethod>();
		unSettledNodes = new HashSet<ProbeMethod>();
		distance = new HashMap<ProbeMethod, Integer>();
		predecessors = new HashMap<ProbeMethod, ProbeMethod>();
		distance.put(source, 0);
		unSettledNodes.add(source);
		while (unSettledNodes.size() > 0) {
			ProbeMethod node = getMinimum(unSettledNodes);
			settledNodes.add(node);
			unSettledNodes.remove(node);
			findMinimalDistances(node);
		}
	}

	private void findMinimalDistances(ProbeMethod node) {
		List<ProbeMethod> adjacentNodes = getNeighbors(node);
		for (ProbeMethod target : adjacentNodes) {
			if (getShortestDistance(target) > getShortestDistance(node) + getDistance(node, target)) {
				distance.put(target, getShortestDistance(node) + getDistance(node, target));
				predecessors.put(target, node);
				unSettledNodes.add(target);
			}
		}

	}

	private int getDistance(ProbeMethod node, ProbeMethod target) {
		for (CallEdge edge : edges) {
			if (edge.src().equals(node) && edge.dst().equals(target)) {
				return 1; // all edges have the same weight
			}
		}
		throw new RuntimeException("Should not happen");
	}

	private List<ProbeMethod> getNeighbors(ProbeMethod node) {
		List<ProbeMethod> neighbors = new ArrayList<ProbeMethod>();
		for (CallEdge edge : edges) {
			if (edge.src().equals(node) && !isSettled(edge.dst())) {
				neighbors.add(edge.dst());
			}
		}
		return neighbors;
	}

	private ProbeMethod getMinimum(Set<ProbeMethod> vertexes) {
		ProbeMethod minimum = null;
		for (ProbeMethod vertex : vertexes) {
			if (minimum == null) {
				minimum = vertex;
			} else {
				if (getShortestDistance(vertex) < getShortestDistance(minimum)) {
					minimum = vertex;
				}
			}
		}
		return minimum;
	}

	private boolean isSettled(ProbeMethod vertex) {
		return settledNodes.contains(vertex);
	}

	private int getShortestDistance(ProbeMethod destination) {
		Integer d = distance.get(destination);
		if (d == null) {
			return Integer.MAX_VALUE;
		} else {
			return d;
		}
	}

	/*
	 * This method returns the path from the source to the selected target and NULL if no path exists
	 */
	public LinkedList<ProbeMethod> getPath(ProbeMethod target) {
		LinkedList<ProbeMethod> path = new LinkedList<ProbeMethod>();
		ProbeMethod step = target;
		// check if a path exists
		if (predecessors.get(step) == null) {
			return null;
		}
		path.add(step);
		while (predecessors.get(step) != null) {
			step = predecessors.get(step);
			path.add(step);
		}
		// Put it into the correct order
		Collections.reverse(path);
		return path;
	}
}