package galaxyraiders.core.game

import galaxyraiders.core.physics.Point2D
import galaxyraiders.core.physics.Vector2D
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("Given an explosion")
class ExplosionTest {
  private val explosion = Explosion(
    isTriggered = false,
    initialPosition = Point2D(0.0, 0.0),
    initialVelocity = Vector2D(dx = 0.0, dy = 0.0),
    radius = 3.0,
    mass = 0.0
  )

  @Test fun `it has a type Explosion `() {
    assertEquals("Explosion", explosion.type)
  }

  @Test
  fun `it has a symbol asterisk `() {
    assertEquals('*', explosion.symbol)
  }

  @Test
  fun `it shows the type Missile when converted to String `() {
    assertTrue(explosion.toString().contains("Explosion"))
  }

  @Test
  fun `it can change trigger property`() {
    explosion.trigger()
    assertEquals(true, explosion.isTriggered())
  }
}
