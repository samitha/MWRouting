package mw

import scala.collection.immutable.Map
import breeze.linalg._
import util.Visualizer

object main {
  def main(args: Array[String]): Unit = {
    Simulations.launchGraphTest(200)
//    Simulations.launchParallelGraphTest(200)
//    Simulations.launchZeroSumGame(2000)
//    Simulations.launchZeroSumGameAdversarial(200)
  }
}

object Simulations {
  def launchParallelGraphTest(T: Int) {
    val latencies: Array[Double => Double] = 
      Array(
          x => 3 * x, 
          x => 2 + 2 * x, 
          x => x * x, 
          x => 2 * (x + 1) * (x + 1) - 1)
    val totalFlow = 2.
    val randomizedStart = true
    val sim = new ParallelRoutingSim(totalFlow, latencies, randomizedStart)
    sim.eps = t=>.1
    sim.launch(T)
  }

  def launchGraphTest(T: Int) { 
    val adj: Map[Int, List[(Int, Double => Double)]] = Map(
      0 -> List((1, x => x*x+2.5), (4, x => x / 2)),
      1 -> List(),
      2 -> List((3, x => x+1.), (4, x => .5)),
      3 -> List((8, x=>1)),
      4 -> List((5, x => 3 * x * x), (6, x => x*x*x)),
      5 -> List((1, x => x / 3), (3, x => x/4)), 
      6 -> List((1, x => x*x / 2), (3, x => x)),
      7 -> List((2, x => x*x / 2)),
      8 -> List()
      )
    
    val sourceSinks = Array((0, 1), (2, 3), (7, 8))
    val totalFlows = Array(1., 1., 1.)
    val randomizedStart = true
    
    val sim = new RoutingGameSim(adj, sourceSinks, totalFlows, randomizedStart)
    val eps = (t:Int)=>
      .1
//      .5
//      .8
//      1./(10+t)
//      1./math.sqrt(t)
    sim.eps(0) = eps
    sim.eps(1) = eps
    sim.eps(2) = eps
    
    sim.launch(T)
  }

  
  // Zero Sum Games
  val A = DenseMatrix((.5, 1., 0.), (0., .5, 1.), (1., 0., .5), (.5, 0., 0.))
  // here the column player responds optimally
  // we plot the average strategy
  def launchZeroSumGame(T: Int) {
    val randomize = true
    val sim = new ZeroSumGameSim(A, true, randomize)
    sim.eps(0) = t=>1./(1+t)
    sim.eps(1) = t=>1./(1+t)
    sim.launch(T)
  }

  def launchZeroSumGameAdversarial(T: Int) {
    val randomize = true
    val sim = new ZeroSumGameAdversarialSim(A, true, randomize)
    sim.eps = t=>10./(10+t)
    sim.launch(T)
  }
}
