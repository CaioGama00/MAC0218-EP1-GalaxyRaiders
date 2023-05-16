package galaxyraiders.core.physics
import kotlin.math.*
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties("unit", "normal", "degree", "magnitude")
data class Vector2D(val dx: Double, val dy: Double) {
  override fun toString(): String {
    return "Vector2D(dx=$dx, dy=$dy)"
  }

  val magnitude: Double
    get() = hypot(dx, dy)

  val radiant: Double
    get() = atan2(dy, dx)

  val degree: Double
    get() = Math.toDegrees(radiant)

  val unit: Vector2D
    get() {
        val mag = magnitude
        return Vector2D(dx / mag, dy / mag)
    }

  val normal: Vector2D
    get() {
      val mag = magnitude
      return Vector2D(dy / mag, -dx / mag)
    }

  operator fun times(scalar: Double): Vector2D {
    return Vector2D(dx * scalar, dy * scalar)
  }

  operator fun div(scalar: Double): Vector2D {
    return Vector2D(dx / scalar, dy / scalar)
  }

  operator fun times(v: Vector2D): Double {
    return dx * v.dx + dy * v.dy
  }

  operator fun plus(v: Vector2D): Vector2D {
    return Vector2D(dx + v.dx, dy + v.dy)
  }

  operator fun plus(p: Point2D): Point2D {
    return Point2D(p.x + dx, p.y + dy)
  }

  operator fun unaryMinus(): Vector2D {
    return Vector2D(-dx, -dy)
  }

  operator fun minus(v: Vector2D): Vector2D {
    return Vector2D(dx - v.dx, dy - v.dy)
  }

  fun scalarProject(target: Vector2D): Double {
    val magnitude = target.magnitude
    return if (magnitude != 0.0) (this * target) / magnitude else 0.0
  }

  fun vectorProject(target: Vector2D): Vector2D {
    val magnitudeSquared = target.magnitude * target.magnitude
    return target* (this * target) / magnitudeSquared 
  }
}

  operator fun Double.times(v: Vector2D): Vector2D {
    return v * this
  }