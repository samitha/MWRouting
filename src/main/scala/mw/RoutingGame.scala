package mw

import breeze.linalg._
import scala.collection.mutable.HashMap
import util.Visualizer

class Network(graph: DirectedGraph, val groupSourceSinks: Array[(Int, Int)]) {
  val edges = graph.edges
  val nbEdges = edges.size
  val nbGroups = groupSourceSinks.size
  val groupPaths: Array[Array[List[Int]]] =
    groupSourceSinks.map(s => graph.findLooplessPaths(s._1, s._2).toArray.map(_.edges.map(_.id)))

  val edgePathInc = new Array[DenseMatrix[Double]](nbGroups)
  for (k <- 0 to nbGroups - 1) {
    val paths = groupPaths(k)
    val nbPaths = paths.size
    edgePathInc(k) = DenseMatrix.zeros[Double](nbEdges, nbPaths)
    for (i <- 0 to nbEdges - 1; j <- 0 to nbPaths - 1)
      if (paths(j).contains(i))
        edgePathInc(k)(i, j) = 1
  }

  def edgeLatencies(edgeFlows: DenseVector[Double]): DenseVector[Double] = {
    val edgeLatencies = DenseVector.zeros[Double](nbEdges)
    for (edgeId <- 0 to nbEdges - 1)
      edgeLatencies(edgeId) = edges(edgeId).latency(edgeFlows(edgeId))
    edgeLatencies
  }

  def pathLatency(edgeLatencies: DenseVector[Double])(groupId: Int)(pathId: Int): Double = {
    (edgePathInc(groupId).t * edgeLatencies).apply(pathId)
  }

  def computeEdgeFlows(pathFlows: Array[DenseVector[Double]]): DenseVector[Double] = {
    val edgeLatencies = DenseVector.zeros[Double](nbEdges)
    for (k <- 0 to nbGroups - 1) {
      edgeLatencies :+= edgePathInc(k) * pathFlows(k)
    }
    edgeLatencies
  }

}

class RoutingGame(totalFlows: Array[Double], network: Network) extends Nature {
  val nbEdges = network.nbEdges
  val nbGroups = totalFlows.size
  var pathFlows: Array[DenseVector[Double]] = new Array[DenseVector[Double]](nbGroups)
  for (k <- 0 to nbGroups - 1) {
    val nbPaths = network.groupPaths(k).size
    pathFlows(k) = DenseVector.fill[Double](nbPaths) { totalFlows(k) / nbPaths }
  }

  var edgeFlows = DenseVector.zeros[Double](nbEdges)
  var edgeLatencies = DenseVector.zeros[Double](nbEdges)

  def getLatency(groupId: Int)(pathId: Int) = network.pathLatency(edgeLatencies)(groupId)(pathId)

  def getLatencies(groupId: Int) = new DenseVector[Double]((0 to pathFlows(groupId).size - 1).map(getLatency(groupId)(_)).toArray)

  def update(groupId: Int, strategy: DenseVector[Double]) = {
    pathFlows(groupId) = strategy * totalFlows(groupId)
    if (groupId == nbGroups - 1) {
      edgeFlows = network.computeEdgeFlows(pathFlows)
      edgeLatencies = network.edgeLatencies(edgeFlows)
    }
  }

}

class RoutingExpert(game: RoutingGame, groupId: Int, pathId: Int) extends Expert[RoutingGame](game) {
  def nextLoss(): Double = {
    game.getLatency(groupId)(pathId)
  }
}

class RoutingGameSim(
  adj: Map[Int, List[(Int, Double => Double)]],
  sourceSinkPairs: Array[(Int, Int)],
  totalFlows: Array[Double]) {

  val eps: Array[Int => Double] = Array(t => 10. / (10 + t), t => 10. / (10 + t))
  val graph = DirectedGraph.fromAdjacencyMap(adj)
  val network = new Network(graph, sourceSinkPairs)
  val K = network.nbGroups

  def launch(T: Int) {
    val game = new RoutingGame(totalFlows, network)

    val experts = new Array[List[RoutingExpert]](K)
    for (k <- 0 to K - 1)
      experts(k) = (0 to network.groupPaths(k).size - 1).map(new RoutingExpert(game, k, _)).toList

    val algs = new Array[MWAlgorithm[RoutingGame]](K)
    for (k <- 0 to K - 1)
      algs(k) = new MWAlgorithm[RoutingGame](k, eps(k), experts(k), game)

    val xs = DenseMatrix.zeros[Double](4, T)
    val ls = DenseMatrix.zeros[Double](4, T)
    for (t <- 0 to T - 1) {
      for (alg <- algs)
        alg.next()
      val x = DenseVector.vertcat(game.pathFlows(0), game.pathFlows(1))
      val l = DenseVector.vertcat(game.getLatencies(0), game.getLatencies(1))

      xs(::, t) := x
      ls(::, t) := l
    }
    new Visualizer("t", "mu(t)", "Flow").printData(xs)
    new Visualizer("t", "mu(t)", "Latency").printData(ls)
  }
}