package galaxyraiders.core.game

import galaxyraiders.core.physics.Point2D
import galaxyraiders.core.physics.Vector2D


class Explosion(
  isTriggered: Boolean,
  initialPosition: Point2D,
  initialVelocity: Vector2D,
  radius: Double,
  mass: Double
) :
  SpaceObject("Explosion", 'X', initialPosition, initialVelocity, radius, mass) {
    var triggered: Boolean = isTriggered
    fun trigger() {
      this.triggered = true
    }
    fun isTriggered(): Boolean {
      return this.triggered
    }
  }
