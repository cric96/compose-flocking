package it.unibo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.delay
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random


/**
 * Represents a flock of boids implementing the classic flocking algorithm.
 * Optimized for performance in Compose applications.
 */
class Flock(
    private val width: Float,
    private val height: Float,
    boidsCount: Int = 100,
    private val maxSpeed: Float = 3f,
    private val maxForce: Float = 0.05f,
    private val separationWeight: Float = 1.5f,
    private val alignmentWeight: Float = 1.0f,
    private val cohesionWeight: Float = 1.0f,
    private val perceptionRadius: Float = 50f
) {
    // Vector class to handle position and velocity
    data class Vector(val x: Float, val y: Float) {
        fun add(other: Vector): Vector = Vector(x + other.x, y + other.y)
        fun sub(other: Vector): Vector = Vector(x - other.x, y - other.y)
        fun mult(scalar: Float): Vector = Vector(x * scalar, y * scalar)
        fun div(scalar: Float): Vector = Vector(x / scalar, y / scalar)
        fun limit(max: Float): Vector {
            val magnitudeSq = x * x + y * y
            return if (magnitudeSq > max * max) {
                val ratio = max / sqrt(magnitudeSq)
                Vector(x * ratio, y * ratio)
            } else {
                this
            }
        }
        fun dist(other: Vector): Float {
            val dx = other.x - x
            val dy = other.y - y
            return sqrt(dx * dx + dy * dy)
        }
        fun normalize(): Vector {
            val mag = sqrt(x * x + y * y)
            return if (mag > 0) Vector(x / mag, y / mag) else this
        }
        fun heading(): Double = atan2(y.toDouble(), x.toDouble())
    }

    // Boid class representing an individual agent in the flock
    inner class Boid(
        var position: Vector,
        var velocity: Vector,
        var acceleration: Vector = Vector(0f, 0f)
    ) {
        fun update() {
            velocity = velocity.add(acceleration).limit(maxSpeed)
            position = position.add(velocity)
            acceleration = Vector(0f, 0f) // Reset acceleration

            // Wrap around the screen
            if (position.x < 0) position = Vector(width, position.y)
            if (position.y < 0) position = Vector(position.x, height)
            if (position.x > width) position = Vector(0f, position.y)
            if (position.y > height) position = Vector(position.x, 0f)
        }

        fun applyForce(force: Vector) {
            acceleration = acceleration.add(force)
        }
    }

    // Internal state to hold the boids
    private val _boids = mutableStateOf(List(boidsCount) {
        Boid(
            position = Vector(Random.nextFloat() * width, Random.nextFloat() * height),
            velocity = Vector(Random.nextFloat() * 2 - 1, Random.nextFloat() * 2 - 1)
        )
    })

    // Public access to boids state
    val boids: State<List<Boid>> = _boids

    // Update the flock each frame
    fun update() {
        val currentBoids = _boids.value.toMutableList()

        // Apply flocking behaviors to each boid
        for (boid in currentBoids) {
            val separation = separate(boid, currentBoids)
            val alignment = align(boid, currentBoids)
            val cohesion = cohere(boid, currentBoids)

            // Apply weights to forces
            boid.applyForce(separation.mult(separationWeight))
            boid.applyForce(alignment.mult(alignmentWeight))
            boid.applyForce(cohesion.mult(cohesionWeight))

            // Update boid position
            boid.update()
        }

        // Update the state
        _boids.value = currentBoids
    }

    // Separation: steer to avoid crowding local flockmates
    private fun separate(boid: Boid, boids: List<Boid>): Vector {
        var steer = Vector(0f, 0f)
        var count = 0

        for (other in boids) {
            val d = boid.position.dist(other.position)
            if (other !== boid && d < perceptionRadius) {
                val diff = boid.position.sub(other.position).normalize().div(d)
                steer = steer.add(diff)
                count++
            }
        }

        if (count > 0) {
            steer = steer.div(count.toFloat())
        }

        if (steer.x != 0f || steer.y != 0f) {
            steer = steer.normalize().mult(maxSpeed).sub(boid.velocity).limit(maxForce)
        }

        return steer
    }

    // Alignment: steer towards average heading of local flockmates
    private fun align(boid: Boid, boids: List<Boid>): Vector {
        var sum = Vector(0f, 0f)
        var count = 0

        for (other in boids) {
            val d = boid.position.dist(other.position)
            if (other !== boid && d < perceptionRadius) {
                sum = sum.add(other.velocity)
                count++
            }
        }

        if (count > 0) {
            sum = sum.div(count.toFloat())
            sum = sum.normalize().mult(maxSpeed)
            return sum.sub(boid.velocity).limit(maxForce)
        }

        return Vector(0f, 0f)
    }

    // Cohesion: steer to move toward the average position of local flockmates
    private fun cohere(boid: Boid, boids: List<Boid>): Vector {
        var sum = Vector(0f, 0f)
        var count = 0

        for (other in boids) {
            val d = boid.position.dist(other.position)
            if (other !== boid && d < perceptionRadius) {
                sum = sum.add(other.position)
                count++
            }
        }

        if (count > 0) {
            sum = sum.div(count.toFloat())
            return seek(boid, sum)
        }

        return Vector(0f, 0f)
    }

    // Seek: steer towards a target
    private fun seek(boid: Boid, target: Vector): Vector {
        val desired = target.sub(boid.position).normalize().mult(maxSpeed)
        return desired.sub(boid.velocity).limit(maxForce)
    }
}
/**
 * A composable function that renders the boids within a flock.
 *
 * @param flock The flock of boids to render
 * @param boidSize The size of each boid (default: 6f)
 * @param boidColor The color of the boids (default: Color.White)
 * @param backgroundColor The background color of the canvas (default: Color.Black)
 * @param updateIntervalMillis The interval between updates in milliseconds (default: 16)
 */
@Composable
fun FlockingSimulation(
    flock: Flock,
    boidSize: Float = 6f,
    boidColor: Color = Color.White,
    backgroundColor: Color = Color.Black,
    updateIntervalMillis: Long = 16
) {
    val boids by flock.boids
    var ticksCounter by remember { mutableStateOf(0) }
    // Update the flock periodically
    LaunchedEffect(flock) {
        while (true) {
            flock.update()
            delay(updateIntervalMillis)
            ticksCounter++
        }
    }
    key(ticksCounter) {
        Canvas(modifier = Modifier.fillMaxSize()) {

            // Draw background
            drawRect(backgroundColor)

            // Draw each boid as a triangle
            boids.forEach { boid ->
                // Draw a triangle for each boid
                val heading = boid.velocity.heading()

                val angle = (heading * 180f / PI).toFloat() + 90f

                rotate(angle, Offset(boid.position.x, boid.position.y)) {
                    val x = boid.position.x
                    val y = boid.position.y

                    // Triangle points
                    val p1 = Offset(x, y - boidSize)
                    val p2 = Offset(x - boidSize / 2, y + boidSize / 2)
                    val p3 = Offset(x + boidSize / 2, y + boidSize / 2)

                    drawPath(
                        androidx.compose.ui.graphics.Path().apply {
                            moveTo(p1.x, p1.y)
                            lineTo(p2.x, p2.y)
                            lineTo(p3.x, p3.y)
                            close()
                        },
                        color = boidColor
                    )
                }
            }
        }
    }
}

