package galaxyraiders.core.game

import galaxyraiders.Config
import galaxyraiders.ports.RandomGenerator
import galaxyraiders.ports.ui.Controller
import galaxyraiders.ports.ui.Controller.PlayerCommand
import galaxyraiders.ports.ui.Visualizer
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONArray
import java.lang.Runtime.getRuntime
import java.io.FileWriter
import java.io.PrintWriter
import java.nio.charset.Charset
import java.io.File
import java.time.LocalDateTime
import kotlin.system.measureTimeMillis
import kotlin.system.exitProcess

const val MILLISECONDS_PER_SECOND: Int = 1000
const val RADIUS_WEIGHT: Int = 300
const val MASS_WEIGHT: Int = 500
const val NORMALIZER: Int = 10000

object GameEngineConfig {
  private val config = Config(prefix = "GR__CORE__GAME__GAME_ENGINE__")

  val frameRate = config.get<Int>("FRAME_RATE")
  val spaceFieldWidth = config.get<Int>("SPACEFIELD_WIDTH")
  val spaceFieldHeight = config.get<Int>("SPACEFIELD_HEIGHT")
  val asteroidProbability = config.get<Double>("ASTEROID_PROBABILITY")
  val coefficientRestitution = config.get<Double>("COEFFICIENT_RESTITUTION")

  val msPerFrame: Int = MILLISECONDS_PER_SECOND / this.frameRate
}

@Suppress("TooManyFunctions")
class GameEngine(
  val generator: RandomGenerator,
  val controller: Controller,
  val visualizer: Visualizer,
) {
  val field = SpaceField(
    width = GameEngineConfig.spaceFieldWidth,
    height = GameEngineConfig.spaceFieldHeight,
    generator = generator
  )

  val datetime: LocalDateTime = LocalDateTime.now()

  var playing = true

  fun execute() {
      Runtime.getRuntime().addShutdownHook(Thread {
        updateScoreboard()
    })
      while (true) {
      val duration = measureTimeMillis { this.tick() }

      Thread.sleep(
        maxOf(0, GameEngineConfig.msPerFrame - duration)
      )
    }
  }

  fun execute(maxIterations: Int) {
    repeat(maxIterations) {
      this.tick()
    }
  }

  fun tick() {
    this.updateScoreboard()
    this.processPlayerInput()
    this.updateSpaceObjects()
    this.renderSpaceField()
  }

  fun processPlayerInput() {
    this.controller.nextPlayerCommand()?.also {
      when (it) {
        PlayerCommand.MOVE_SHIP_UP ->
          this.field.ship.boostUp()
        PlayerCommand.MOVE_SHIP_DOWN ->
          this.field.ship.boostDown()
        PlayerCommand.MOVE_SHIP_LEFT ->
          this.field.ship.boostLeft()
        PlayerCommand.MOVE_SHIP_RIGHT ->
          this.field.ship.boostRight()
        PlayerCommand.LAUNCH_MISSILE ->
          this.field.generateMissile()
        PlayerCommand.PAUSE_GAME ->
          this.playing = !this.playing
      }
    }
  }
 
  fun updateScoreboard() {
    val path = "src/main/kotlin/galaxyraiders/core/score/Scoreboard.json"
    val json = JSONObject()
    val file = File(path)
    val jsonArray = if (file.exists() && file.length() > 0) {
      JSONArray(file.readText())
  } else {
      JSONArray()
  }

    try {
        json.put("datetime", datetime)
        json.put("asteroidsDestroyed", this.field.asteroidsDestroyed)
        json.put("points", this.field.points)
        jsonArray.put(json)
      } catch (e: JSONException) {
        e.printStackTrace()
    }
 
    try {
        PrintWriter(FileWriter(path, Charset.defaultCharset()))
            .use { it.write(jsonArray.toString()) } 
    } catch (e: Exception) {
        e.printStackTrace()
    }
    exitProcess(0)
  }
/* 
  fun updateLeaderboard() {
    val scoreboardFilePath = "~/score/Scoreboard.json"
    val scoreboardFile = File(scoreboardFilePath)

    val scoreboardJsonArray = JSONArray(scoreboardFile.readText())

    val sortedScores = scoreboardJsonArray.toList()
        .sortedByDescending { score: JSONObject -> score.getInt("points") }

    val leaderboardJsonArray = JSONArray(sortedScores.take(3))

    val leaderboardFilePath = "~/score/Leaderboard.json"
    val leaderboardFile = File(leaderboardFilePath)

    leaderboardFile.writeText(leaderboardJsonArray.toString())
  }
*/
  fun updateSpaceObjects() {
    if (!this.playing) return
    this.handleCollisions()
    this.moveSpaceObjects()
    this.trimSpaceObjects()
    this.generateAsteroids()
    this.clearExplosions()
    this.triggerExplosions()
  }

  fun clearExplosions() {
    this.field.clearExplosions()
  }

  fun triggerExplosions() {
    this.field.triggerExplosions()
  }

  fun pointsForAsteroid(asteroid: Asteroid): Int {
    return ((RADIUS_WEIGHT * asteroid.radius + MASS_WEIGHT * asteroid.mass) / NORMALIZER).toInt()
  }

  fun handleCollisions() {
    this.field.spaceObjects.forEachPair {
        (first, second) ->
      if (first.impacts(second)) {
        if (checkExplosion(first, second)) {
          print("Asteroids Destroyed: ")
          print(this.field.asteroidsDestroyed)
          print(" Points: ")
          println(this.field.points)

          if (first is Asteroid) {
            this.field.points += pointsForAsteroid(first)
          } else if (second is Asteroid) {
            this.field.points += pointsForAsteroid(second)
          }
          this.field.generateExplosion(first.center)
          this.field.clearObject(first)
          this.field.clearObject(second)
        } else
          first.collideWith(second, GameEngineConfig.coefficientRestitution)
      }
    }
  }

  fun checkExplosion(first: SpaceObject, second: SpaceObject): Boolean {
    return (first is Missile && second is Asteroid) || (first is Asteroid && second is Missile)
  }

  fun moveSpaceObjects() {
    this.field.moveShip()
    this.field.moveAsteroids()
    this.field.moveMissiles()
  }

  fun trimSpaceObjects() {
    this.field.trimAsteroids()
    this.field.trimMissiles()
    this.field.trimExplosions()
  }

  fun generateAsteroids() {
    val probability = generator.generateProbability()

    if (probability <= GameEngineConfig.asteroidProbability) {
      this.field.generateAsteroid()
    }
  }

  fun renderSpaceField() {
    this.visualizer.renderSpaceField(this.field)
  }
}

fun <T> List<T>.forEachPair(action: (Pair<T, T>) -> Unit) {
  for (i in 0 until this.size) {
    for (j in i + 1 until this.size) {
      action(Pair(this[i], this[j]))
    }
  }
}
