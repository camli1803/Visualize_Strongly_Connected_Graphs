package org.jgrapht.alg.connectivity;

import org.jgrapht.*;
import org.jgrapht.alg.interfaces.StrongConnectivityVisualization;
import org.jgrapht.graph.*;
import org.jgrapht.util.*;

import java.util.*;

/**
 * Computes strongly connected components of a directed graph. The algorithm is
 * implemented after "Cormen et al: Introduction to algorithms", Chapter 22.5.
 * It has a running time of $O(V + E)$.
 *
 * <p>
 * Unlike {@link ConnectivityInspector}, this class does not implement
 * incremental inspection. The full algorithm is executed at the first call of
 * {@link CustomKosaraju#stronglyConnectedSets()} or
 * {@link CustomKosaraju#isStronglyConnected()}.
 *
 * @param <V> the graph vertex type
 * @param <E> the graph edge type
 *
 * @author Christian Soltenborn
 * @author Christian Hammer
 */
public class CustomKosaraju<V, E> extends AbstractStrongConnectivityInspector<V, E>
        implements StrongConnectivityVisualization {
  // stores the vertices, ordered by their finishing time in first dfs
  private LinkedList<VertexData<V>> orderedVertices;

  // maps vertices to their VertexData object
  private Map<V, VertexData<V>> vertexToVertexData;

  private List<VisualizationStep> vis;

  private int groupCount;

  public List<VisualizationStep> getVis() {
    return this.vis;
  }

  /**
   * Constructor
   *
   * @param graph the input graph
   * @throws NullPointerException if the input graph is null
   */
  public CustomKosaraju(Graph<V, E> graph) {
    super(graph);
  }

  @Override
  public List<Set<V>> stronglyConnectedSets() {
    if (stronglyConnectedSets == null) {
      orderedVertices = new LinkedList<>();
      stronglyConnectedSets = new ArrayList<>();
      vis = new ArrayList<>();
      groupCount = 0;

      // create VertexData objects for all vertices, store them
      createVertexData();

      // perform the first round of DFS, result is an ordering
      // of the vertices by decreasing finishing time
      for (VertexData<V> data : vertexToVertexData.values()) {
        if (!data.isDiscovered()) {
          dfsVisit(graph, data, null);
          groupCount += 1;
        }
      }

      // 'create' inverse graph (i.e. every edge is reversed)
      Graph<V, E> inverseGraph = new EdgeReversedGraph<>(graph);

      // get ready for next dfs round
      resetVertexData();
      String stack = "";
      for (VertexData<V> data : orderedVertices) {
        stack = data.getVertex() + " " + stack;
      }
      this.vis.add(new VisualizationStep(2, 0, "kosarajuAlgStack", stack));
      this.vis.add(new VisualizationStep(2, 0, "inverseGraph", ""));
      groupCount = 0;

      // second dfs round: vertices are considered in decreasing
      // finishing time order; every tree found is a strongly
      // connected set
      for (VertexData<V> data : orderedVertices) {
        if (!data.isDiscovered()) {
          // new strongly connected set
          Set<V> set = new HashSet<>();
          stronglyConnectedSets.add(set);
          dfsVisit(inverseGraph, data, set);
          groupCount += 1;
        }
      }

      // clean up for garbage collection
      this.vis.add(new VisualizationStep(2, 0, "inverseGraph", ""));
      orderedVertices = null;
      vertexToVertexData = null;
    }

    return stronglyConnectedSets;
  }

  /*
   * Creates a VertexData object for every vertex in the graph and stores them in
   * a HashMap.
   */
  private void createVertexData() {
    vertexToVertexData = CollectionUtil.newHashMapWithExpectedSize(graph.vertexSet().size());

    for (V vertex : graph.vertexSet()) {
      vertexToVertexData.put(vertex, new VertexData2<>(vertex, false, false));
    }
  }

  /*
   * The subroutine of DFS. NOTE: the set is used to distinguish between 1st and
   * 2nd round of DFS. set == null: finished vertices are stored (1st round). set
   * != null: all vertices found will be saved in the set (2nd round)
   */
  private void dfsVisit(Graph<V, E> visitedGraph, VertexData<V> vertexData, Set<V> vertices) {
    Deque<VertexData<V>> stack = new ArrayDeque<>();
    stack.add(vertexData);
    if (vertices == null) {
      System.out.println("step0/dfs: " + vertexData.getVertex());
      this.vis.add(new VisualizationStep(0, groupCount, "dfs", vertexData.getVertex().toString()));
    }

    while (!stack.isEmpty()) {
      VertexData<V> data = stack.removeLast();

      if (!data.isDiscovered()) {
        data.setDiscovered(true);
        if (vertices == null) {
          System.out.println("step1/visit: " + data.getVertex());
          this.vis.add(new VisualizationStep(1, groupCount, "visit", data.getVertex().toString()));
        } else {
          System.out.println("step3/visit: " + data.getVertex());
          this.vis.add(new VisualizationStep(2, groupCount, "visit", data.getVertex().toString()));
        }

        if (vertices != null) {
          vertices.add(data.getVertex());
        }

        stack.add(new VertexData1<>(data, true, true));

        // follow all edges
        for (E edge : visitedGraph.outgoingEdgesOf(data.getVertex())) {
          VertexData<V> targetData = vertexToVertexData.get(visitedGraph.getEdgeTarget(edge));

          if (!targetData.isDiscovered()) {
            // the "recursion"
            stack.add(targetData);
          }
        }
      } else if (data.isFinished() && vertices == null) {
        this.vis.add(new VisualizationStep(1, groupCount, "push2Stack", data.getFinishedData().getVertex().toString()));
        orderedVertices.addFirst(data.getFinishedData());
      }
    }
  }

  /*
   * Resets all VertexData objects.
   */
  private void resetVertexData() {
    for (VertexData<V> data : vertexToVertexData.values()) {
      data.setDiscovered(false);
      data.setFinished(false);
    }
  }

  /*
   * Lightweight class storing some data for every vertex.
   */
  private abstract static class VertexData<V> {
    private byte bitfield;

    private VertexData(boolean discovered, boolean finished) {
      this.bitfield = 0;
      setDiscovered(discovered);
      setFinished(finished);
    }

    private boolean isDiscovered() {
      return (bitfield & 1) == 1;
    }

    private boolean isFinished() {
      return (bitfield & 2) == 2;
    }

    private void setDiscovered(boolean discovered) {
      if (discovered) {
        bitfield |= 1;
      } else {
        bitfield &= ~1;
      }
    }

    private void setFinished(boolean finished) {
      if (finished) {
        bitfield |= 2;
      } else {
        bitfield &= ~2;
      }
    }

    abstract VertexData<V> getFinishedData();

    abstract V getVertex();
  }

  private static final class VertexData1<V> extends VertexData<V> {
    private final VertexData<V> finishedData;

    private VertexData1(VertexData<V> finishedData, boolean discovered, boolean finished) {
      super(discovered, finished);
      this.finishedData = finishedData;
    }

    @Override
    VertexData<V> getFinishedData() {
      return finishedData;
    }

    @Override
    V getVertex() {
      return null;
    }
  }

  private static final class VertexData2<V> extends VertexData<V> {
    private final V vertex;

    private VertexData2(V vertex, boolean discovered, boolean finished) {
      super(discovered, finished);
      this.vertex = vertex;
    }

    @Override
    VertexData<V> getFinishedData() {
      return null;
    }

    @Override
    V getVertex() {
      return vertex;
    }
  }
}