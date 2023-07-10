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
    this.updateLeaderboard()
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
    val file = File(path)
    val jsonObject = if (file.exists() && file.length() > 0) {
        JSONObject(file.readText())
    } else {
        JSONObject()
    }
    val scoresArray = jsonObject.optJSONArray("scores")
    val jsonDatetime = scoresArray?.optJSONObject(scoresArray.length() - 1)?.getString("datetime")

    if (jsonDatetime == datetime.toString()) {
        scoresArray.optJSONObject(scoresArray.length() - 1)?.apply {
            put("asteroidsDestroyed", field.asteroidsDestroyed)
            put("points", field.points)
        }
    } else {
        val newEntry = JSONObject().apply {
            put("datetime", datetime.toString())
            put("asteroidsDestroyed", field.asteroidsDestroyed)
            put("points", field.points)
        }
        if (scoresArray == null) {
            jsonObject.put("scores", JSONArray().put(newEntry))
        } else {
            scoresArray.put(newEntry)
        }
    }

    try {
        FileWriter(path, Charset.defaultCharset()).use { writer ->
            writer.write(jsonObject.toString(2))
            writer.write(System.lineSeparator())
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
  }


  fun updateLeaderboard() {
    val scoreboardPath = "src/main/kotlin/galaxyraiders/core/score/Scoreboard.json"
    val leaderboardPath = "src/main/kotlin/galaxyraiders/core/score/Leaderboard.json"
    
    val scoreboardFile = File(scoreboardPath)
    val leaderboardFile = File(leaderboardPath)
    
    val scoreboardJsonString = scoreboardFile.readText()
    val scoreboardJson = if (scoreboardJsonString.isNotEmpty()) {
        JSONObject(scoreboardJsonString)
    } else {
        JSONObject()
    }
    
    val scoresArray = scoreboardJson.optJSONArray("scores")
    
    val sortedScoresArray = if (scoresArray != null && scoresArray.length() > 0) {
        val scoresList = (0 until scoresArray.length())
            .map { scoresArray.getJSONObject(it) }
            .sortedByDescending { it.getInt("points") }
        JSONArray(scoresList)
    } else {
        JSONArray()
    }
    
    val leaderboardJson = JSONArray()
    
    for (i in 0 until minOf(3, sortedScoresArray.length())) {
        val score = sortedScoresArray.getJSONObject(i)
        leaderboardJson.put(score)
    }
    
    leaderboardFile.writeText(leaderboardJson.toString(2))
}

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
